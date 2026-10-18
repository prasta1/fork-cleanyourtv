# The prompt

Paste this into Claude Code (or another coding assistant with shell access) on a computer that is
on the same Wi-Fi as the TV. Works on Android TV / Google TV sets (Xiaomi, TCL, Hisense, Sony,
OnePlus, Realme, Nvidia Shield and most others). It will not work on Samsung Tizen, LG webOS or
Fire TV. If you cloned this repo, mention that: the assistant can reuse `tools/` and `launcher/`
instead of writing them from scratch, and only needs to edit `launcher/.../values/config.xml`.

Before you start, on the TV: Settings → Device Preferences → About → press OK on "Build" 7 times,
then go back → Developer options → turn on USB debugging (and Network/ADB debugging if your TV
shows it). Keep the TV on.

---

I want to debloat my Android TV and replace its launcher with a clean Apple TV style home screen.
My TV model is: <MODEL NAME HERE>. Work on the real device, step by step, and verify everything you
do with screenshots pulled from the TV. Ask me before anything that depends on my preferences.

Connect
- Find the TV on my network (hostname, ARP table, or ask me for the IP) and connect over ADB on port 5555.
- If the stock adb binary fails with "No route to host" on macOS even though the port is open, that is
  macOS Local Network privacy blocking adb. Don't debug it: use the Python `adb-shell` package in a venv
  as the ADB client instead. If its file push hangs on larger files, transfer files as base64 chunks
  through the shell and verify the md5.
- Tell me when the "Allow USB debugging?" prompt will appear on the TV so I can accept it.

Back up first
- Save the full package list, disabled-package list, getprop output and all settings to a backup folder
  before changing anything, and write a restore script that re-enables everything you disable.

Debloat
- List installed packages and sort them into: manufacturer telemetry/analytics, manufacturer content
  and ad services (PatchWall, recommendation rows, content providers), background services I probably
  don't need (share/cast duplicates, cleaners, update services), preloaded apps, and streaming apps.
- Disable the first categories with `pm disable-user --user 0`, never uninstall system packages.
- Before touching streaming apps, voice assistant, Chromecast, Miracast or the update service, ask me
  which ones I use with a checklist question and disable only what I don't tick.
- Run `pm` commands one per shell call, chained commands may silently stop after the first.

Launcher
- Don't install a third-party launcher that charges for basics like wallpapers. Build a small native
  Android TV launcher instead (Java, AndroidX RecyclerView, minSdk matching the TV, debug build is fine),
  install it over ADB, set it as the default home and disable the stock launcher. If several apps claim
  the HOME intent, set the default with `cmd package set-home-activity`.
- Design, Apple TV 4K style: pitch black background; a dock pinned at the bottom of the screen inside a
  slightly lighter rounded band holding the apps I pick (ask me which, and the order), which is its own
  layer and never scrolls; the rest of the apps in a 4-column grid above the dock, which opens when I
  press Up; one translucent "glass" highlight that travels from tile to tile instead of per-tile
  borders; the focused tile scales up slightly; the focused app's name in large light type in one fixed
  place on the left, whether it is a dock app or a grid app; that app's own banner filling the screen as
  a backdrop, zoomed past recognition so it reads as colour rather than a logo, plus a soft ambient glow
  sampled from it; apps with no banner get a plate tinted from their own colour; frosted pill buttons top
  right for Inputs, Wi-Fi and Settings, with the network name as a readout beside them rather than as the
  Wi-Fi label; a greeting, a large thin clock and the date top left in a
  downloaded open-licence typeface such as Outfit. Inputs opens a compact glass dropdown under the pill
  listing the HDMI ports, AV and Live TV, switching via the TV input framework.
- Make D-pad navigation deterministic, and handle every direction yourself so the framework's spatial
  focus search never picks a target: Up/Down move exactly one row, Up from the dock opens the grid,
  Up from the grid's top row goes to the pills, Down walks back down, and the ends of the top bar don't
  fall through to tiles. Remember the last focused tile in each zone.
- Keep it light: the TV has about 1 GB of RAM. No blur, no video, no network polling.

Verify
- After each change, inject remote key presses over ADB, take a screenshot from the TV and look at it.
  Fix what you see before moving on.
- When done, give me a restore script, a build-and-install script, and a README that explains how to
  re-enable any app and how to change the dock order or the wallpaper.

---

Expect to answer three or four checklist questions (which apps you use, which four go in the dock,
whether to keep the voice assistant, Chromecast and firmware updates). Everything is reversible.
