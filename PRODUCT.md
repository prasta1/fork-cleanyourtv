# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

Owners of an Android TV or Google TV set who are unhappy with the stock home screen — it is
ad-supported, pre-filled with manufacturer content rows, and cannot be customised. They are
sitting on a couch with a remote, roughly 3–5 m from the screen, holding one job: open an app
quickly. Their set is typically low-spec (about 1 GB of RAM), often a budget brand
(Xiaomi/Redmi, TCL, Hisense) rather than a Nvidia Shield.

Secondary audience: people who clone this repo to build a launcher for their own set. They
configure it themselves and follow the README.

## Product Purpose

Debloat an Android TV / Google TV set and replace its launcher with one the owner controls.
Success is narrow and observable: pressing Home shows the apps the owner actually uses, with no
sponsored row, no recommendation shelf, and no manufacturer upsell in the way.

The repository does two jobs: a list of packages to disable per TV model, and a small native
launcher that takes over the Home intent.

## Positioning

A self-hosted launcher that costs nothing and asks for nothing. Third-party TV launchers
commonly paywall basics such as a custom wallpaper, and every one of them assumes a network and
an account. This one has no network code path at all, which is both the technical constraint and
the pitch: it works on a set that has been taken offline, and it cannot be monetised because it
does not connect to anything.

The second half of the positioning is reversibility. Packages are disabled with
`pm disable-user`, never uninstalled, and every change is written to a restore script. Nothing
this project does is a one-way door.

## Operating Context

Everything happens over ADB from a laptop on the same Wi-Fi as the set. The stock Google-signed
`adb` binary is blocked by macOS Local Network privacy on many machines, so the repo ships its
own pure-Python ADB client, `tools/tv.py`, and uploads larger files as base64 chunks via
`tools/b64push.py`. Disabling packages is scripted per model under `examples/<model>/debloat.txt`
and applied with `tools/disable.sh`.

Launcher work follows a loop: edit the APK, `launcher/build-and-install.sh` with `TV_HOST` set,
then inject remote key presses and pull screenshots from the set to confirm the change landed
correctly. The owner configures dock order, hidden apps, Live TV package and grid columns by
editing `launcher/app/src/main/res/values/config.xml` rather than code.

Known platform quirks the project has already absorbed: no wireless-debugging toggle on some
sets (USB debugging alone opens network ADB on 5555); chained `pm` commands may silently stop
after the first; the stock launcher must be disabled only after the replacement is installed.

## Capabilities and Constraints

- Applies a per-model debloat list and can undo every change in it.
- Installs a native Java home screen and registers it as the HOME activity.
- Shows a pinned dock of four apps plus the remaining leanback-launcher apps, with deterministic
  D-pad navigation and last-focused-tile memory.
- Reads live TV input state to offer HDMI/AV/Live TV switching from the launcher.
- All reversible. No package is ever uninstalled.

Hard technical constraints:

- **About 1 GB of RAM on the target set.** This is the governing constraint on every visual
  decision. No blur, no video, no live wallpapers, no large bitmap compositing, no background
  services. The rendering vocabulary is deliberately tiny: one `Drawable`-based translucent
  highlight, one `GradientDrawable` radial, one `SpringAnimation`.
- **No network access in the launcher.** Nothing polls, nothing phones home, nothing needs an
  account. It must present a complete, useful screen with the set offline.
- Java with AndroidX `RecyclerView` and `dynamicanimation`; `minSdk 26`, `targetSdk 29`,
  `compileSdk 34`, debug build is acceptable.
- Not applicable to Samsung Tizen, LG webOS or Amazon Fire TV — different operating systems
  with no ADB launcher replacement path.

Confirmed requirement, not a hard standard: type must be legible at 10-foot viewing distance.
The owner considers this important but has not adopted a specific legibility standard.

Not established: no screen-reader requirement has been stated for this audience.

## Brand Commitments

Project name `tv-vibehack`, MIT licensed. Forked by the repository owner, who holds it as a
personal fork with licence to restructure the launcher freely; the only artefact that must keep
working is `launcher/build-and-install.sh`.

The Outfit typeface is bundled under the SIL Open Font Licence
(`launcher/FONT-LICENSE-OFL.txt`) and must keep shipping with its licence file.

Attribution to the upstream `tv-vibehack` project should be preserved in documentation.

## Evidence on Hand

- `docs/stock-home.jpg`, `docs/new-home.jpg`, `docs/stock-vs-new.jpg`,
  `docs/stock-vs-new-side.jpg` — real photographs of the stock Google TV home screen and the
  current custom launcher on a physical set. These are the incumbent visual truth.
- `examples/xiaomi-mi-tv-4a/debloat.txt` — one real debloat list.
- `README.md` and `PROMPT.md` — the current written intent, including the design brief that
  produced the incumbent UI.
- `launcher/app/src/main/res/values/config.xml` — the owner-facing configuration surface.

Absences future work must not fabricate: there are no user testimonials, no install or
adoption numbers, no performance measurements, and no usability testing with real owners. The
TVLegible claim is a design judgement, not a measured result.

## Product Principles

1. **Reversible by construction.** Disable, never uninstall. Anything done to someone's TV must
   have a matching undo that is written down.
2. **The hardware is the brief.** About 1 GB of RAM is a design input, not a limitation to
   apologise for. If a visual idea cannot run on the target set, it is not an idea.
3. **No network, no exceptions.** The launcher is fully useful offline. Nothing about the
   product may depend on reaching a server.
4. **The remote is the only input.** One keypress moves exactly one row. Focus never surprises
   the person holding the remote.
5. **The stock screen is the anti-reference.** Every element that survives here survived by
   being smaller, quieter, and more controllable than the thing it replaced.

## Accessibility & Inclusion

Type must be legible at the viewing distance of a living-room television. Treated as a real
requirement by the owner but without an adopted numeric standard.

No product-specific screen-reader, contrast, or input-assistance requirement has been
established for this audience, and none should be invented.