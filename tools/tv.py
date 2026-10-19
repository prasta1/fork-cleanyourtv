#!/usr/bin/env python3
"""Minimal ADB-over-TCP client for an Android TV using adb_shell (no adb binary needed).

Set TV_HOST to the TV's IP. Usage:
    tv.py --probe                      connect and print model / Android version
    tv.py <shell command...>           run one shell command
    tv.py --batch < commands.txt       one command per line, same connection
    tv.py --push <local> <remote>      small files only (see b64push.py for larger ones)
    tv.py --pull <remote> <local>
"""
import datetime
import os
import socket
import ssl
import struct
import sys
import tempfile

from adb_shell.adb_device import AdbDevice, AdbDeviceTcp
from adb_shell.auth.keygen import keygen
from adb_shell.auth.sign_pythonrsa import PythonRSASigner
from adb_shell.transport.tcp_transport import TcpTransport
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.x509.oid import NameOID

# Windows consoles often use an old code page (cp1251, cp1252 and others) that cannot print every character
# in dumpsys output. Python then stops with an error in the middle. disable.ps1 reads this output to see if
# setupwraith owns the Wi-Fi network, so print a replacement character instead of stopping.
for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(errors="replace")
    except (AttributeError, ValueError):
        pass

HOST = os.environ.get("TV_HOST")
PORT = int(os.environ.get("TV_PORT", "5555"))
if not HOST:
    sys.exit("Set TV_HOST to your TV's IP address, e.g.  TV_HOST=192.168.1.50 tools/tv.py --probe\n"
             "(find it under Settings → Network, or look for the TV in `arp -a`)")
KEYDIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "adbkey")
KEY = os.path.join(KEYDIR, "adbkey")
# Reuse the stock adb key when the TV already trusts it (platform-tools / Android Studio users), so the
# "Allow USB debugging?" prompt doesn't come back a second time for this client. From #105.
ADB_KEY = os.path.join(os.path.expanduser("~"), ".android", "adbkey")
if not os.path.exists(KEY) and os.path.exists(ADB_KEY) and os.path.exists(ADB_KEY + ".pub"):
    KEY = ADB_KEY


def get_signer():
    os.makedirs(KEYDIR, exist_ok=True)
    if not os.path.exists(KEY):
        keygen(KEY)
    with open(KEY) as f:
        priv = f.read()
    with open(KEY + ".pub") as f:
        pub = f.read()
    return PythonRSASigner(pub, priv)


STLS = 0x534C5453  # "STLS" little-endian, same convention as CNXN


def load_client_cert(ctx):
    """Give an SSLContext a client cert built from our ADB key.

    Android 11+ adbd (Wireless debugging port) upgrades the connection to TLS 1.3 and
    *requires* a client cert. Trust is by public key: the cert itself is self-signed
    and throwaway, but its key must have been paired once (`adb pair`, see README).
    ssl only loads certs from files, so it goes through a temp file that is removed at once.
    """
    with open(KEY, "rb") as f:
        priv = serialization.load_pem_private_key(f.read(), password=None)
    name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "Android Debug Bridge")])
    now = datetime.datetime.now(datetime.timezone.utc)
    cert = (x509.CertificateBuilder().subject_name(name).issuer_name(name)
            .public_key(priv.public_key()).serial_number(x509.random_serial_number())
            .not_valid_before(now - datetime.timedelta(days=1))
            .not_valid_after(now + datetime.timedelta(days=3650))
            .add_extension(x509.BasicConstraints(ca=True, path_length=None), critical=True)
            .sign(priv, hashes.SHA256()))
    pem = cert.public_bytes(serialization.Encoding.PEM) + priv.private_bytes(
        serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption())
    with tempfile.NamedTemporaryFile(suffix=".pem") as tmp:  # deleted on close
        tmp.write(pem)
        tmp.flush()
        ctx.load_cert_chain(tmp.name)


class StlsSocket:
    """Socket stand-in for adb_shell's TcpTransport that upgrades itself to TLS.

    Why: adb_shell has no TLS support. Rather than reimplement its CNXN/AUTH logic, we
    sit at the socket layer. If the device's first reply to our CNXN is an STLS packet,
    we echo STLS back (required by the protocol -- without it adbd resets the connection),
    then run a TLS handshake as the client. Otherwise (plain adbd, e.g. port 5555) every
    call passes straight through to the real socket.
    """

    def __init__(self, sock):
        self._sock = sock  # becomes the ssl socket after the upgrade
        self._first = True

    def fileno(self):
        return self._sock.fileno()

    def send(self, data):
        return self._sock.send(data)

    def recv(self, numbytes):
        if self._first:
            self._first = False
            self._maybe_upgrade()
        return self._sock.recv(numbytes)

    def close(self):
        self._sock.close()

    def shutdown(self, how):
        try:
            self._sock.shutdown(how)
        except OSError:
            pass

    def _maybe_upgrade(self):
        # Peek (don't consume) the first command word; bulk_read already select()ed, so it is there.
        self._sock.settimeout(15)
        if int.from_bytes(self._sock.recv(4, socket.MSG_PEEK | socket.MSG_WAITALL), "little") != STLS:
            self._sock.setblocking(False)
            return
        hdr = self._sock.recv(24, socket.MSG_WAITALL)  # STLS has no payload
        version = struct.unpack("<6I", hdr)[1]
        self._sock.sendall(struct.pack("<6I", STLS, version, 0, 0, 0, STLS ^ 0xFFFFFFFF))

        ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
        # adbd's cert is self-signed and unverifiable; adb itself doesn't check it either.
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE
        load_client_cert(ctx)
        # wrap_socket detaches the original socket, so self._sock must now be the TLS one.
        self._sock = ctx.wrap_socket(self._sock)
        self._sock.settimeout(30)  # blocking reads after select() said readable


class TlsTransport(TcpTransport):
    def connect(self, transport_timeout_s):
        super().connect(transport_timeout_s)
        self._connection = StlsSocket(self._connection)


class TlsAdbDeviceTcp(AdbDeviceTcp):
    """AdbDeviceTcp that can talk to a TLS adbd. Built via AdbDevice.__init__ to inject our transport."""

    def __init__(self, host, port, default_transport_timeout_s):
        AdbDevice.__init__(self, TlsTransport(host, port), default_transport_timeout_s)


def connect(timeout=120):
    dev = TlsAdbDeviceTcp(HOST, PORT, float(timeout))
    dev.connect(rsa_keys=[get_signer()], transport_timeout_s=float(timeout), auth_timeout_s=timeout)
    return dev


def main():
    args = sys.argv[1:]
    dev = connect()
    if not args or args == ["--probe"]:
        for p in ("ro.product.model", "ro.product.device", "ro.build.version.release",
                  "ro.build.version.sdk", "ro.build.display.id", "ro.product.manufacturer"):
            print(f"{p} = {dev.shell(f'getprop {p}').strip()}")
    elif args[0] == "--push" and len(args) == 3:
        dev.push(args[1], args[2], transport_timeout_s=300.0, read_timeout_s=300.0)
        print(f"pushed {args[1]} -> {args[2]}")
    elif args[0] == "--pull" and len(args) == 3:
        dev.pull(args[1], args[2], transport_timeout_s=300.0, read_timeout_s=300.0)
        print(f"pulled {args[1]} -> {args[2]}")
    elif args[0] == "--batch":
        # one shell command per stdin line, each run separately on the same connection.
        # utf-8-sig drops the BOM Windows PowerShell may prepend to piped stdin.
        sys.stdin.reconfigure(encoding="utf-8-sig")
        for line in sys.stdin:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            out = dev.shell(line, read_timeout_s=120.0)
            sys.stdout.write(f"$ {line}\n{out}")
            if out and not out.endswith("\n"):
                sys.stdout.write("\n")
            sys.stdout.flush()
    else:
        out = dev.shell(" ".join(args), read_timeout_s=120.0)
        sys.stdout.write(out)
    dev.close()


if __name__ == "__main__":
    main()
