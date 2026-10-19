# Theme engine — implementation plan

Date: 2026-10-18
Spec: [`docs/superpowers/specs/2026-10-18-theme-engine-design.md`](../specs/2026-10-18-theme-engine-design.md)

Read the spec first. This plan is the ordered, verifiable path from the current code to that design.

## Environment (verified on this machine, 2026-10-18)

| Thing | State |
|---|---|
| JDK 17 | `/opt/homebrew/opt/openjdk@17` — installed but not on `PATH` |
| Android SDK | `/opt/homebrew/share/android-commandlinetools` has exactly `platforms/android-34` and `build-tools/34.0.0`, which is what the project needs |
| `~/Library/Android/sdk` | Has platform 36/37 only — **not** usable for this project; do not point `ANDROID_HOME` there |
| Gradle | Wrapper 8.9, AGP 8.5.2, `minSdk 26`, `compileSdk 34`, `targetSdk 29` |
| Baseline | `assembleDebug` verified green on this machine before the plan was written (5s, Gradle already cached) |
| Tests | None today |

Every Gradle command below runs as:

```bash
cd launcher
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew <task> --console=plain
```

`launcher/local.properties` is gitignored and is written on first run with `sdk.dir=$ANDROID_HOME`.

## Verification strategy

Three layers, in increasing cost:

1. **JVM unit tests** (`:app:testDebugUnitTest`) — for everything that does not need Android: `Tokens`, `ThemeParser`, `VariantResolver`, and one repo-integrity test that the shipped `tvos-black.xml` still matches the in-code defaults. Fast, runs here.
2. **`assembleDebug`** — catches wiring and resource errors. Runs here.
3. **On-device** — `TV_HOST=<ip> launcher/build-and-install.sh` plus `tools/tv.py` screenshots. Needs a real TV; only the maintainer runs this, using the checklist in the spec.

Rule for every task: it must leave `assembleDebug` green, and any task adding behaviour adds its unit test in the same commit.

## Tasks

| # | Task | Needs | Verify |
|---|---|---|---|
| 1 | Test infrastructure | — | `:app:testDebugUnitTest` |
| 2 | `Tokens` + `Defaults` | 1 | unit |
| 3 | `ThemeParser` | 2 | unit |
| 4 | `VariantResolver` | 1 | unit |
| 5 | `ThemeCatalog` + `ThemeStore` | 3, 4 | assemble |
| 6 | `Skin` + `GlassView.retheme` | 2 | assemble |
| 7 | Wire `MainActivity`, `AppAdapter`, `DockDecoration` to `Tokens` | 5, 6 | assemble + screenshot |
| 8 | Delete colour XML | 7 | assemble + screenshot |
| 9 | Theme picker panel | 7, 8 | assemble + screenshot |
| 10 | Day/night, persistence, Back | 9 | assemble + screenshot |
| 11 | Five theme files | 3 | unit |
| 12 | Docs | 11 | read |
| 13 | On-device pass | 10, 11 | TV checklist |

Tasks 2, 4 are independent of each other and can be done in either order. Everything else is a chain.

---

## Task 1 — Test infrastructure

**Files**

- `launcher/app/build.gradle` — add to `dependencies`:

  ```groovy
  testImplementation 'junit:junit:4.13.2'
  testImplementation 'xmlpull:xmlpull:1.1.3.1'
  ```

**Why the second dependency.** `ThemeParser` uses `org.xmlpull.v1`, which Android provides but the JDK does not implement. `xmlpull:xmlpull` puts an implementation on the unit-test classpath. If it fails to resolve, use `net.sf.kxml:kxml2:2.3.0` instead. The alternative — parsing with `DocumentBuilderFactory` — needs no dependency but allocates a whole DOM on a device with 1 GB of RAM, and this codebase cares about that.

Both artifacts were confirmed to resolve from Maven Central on 2026-10-19.

**On `CONTRIBUTING.md`'s "no new dependencies in the launcher without discussing it in an issue".**
Both are `testImplementation`, so neither reaches the APK and neither affects the 1 GB target — the
APK's dependency set is unchanged. This is worth a line in the PR description regardless. If a
maintainer reads the rule as covering test dependencies too, the zero-dependency way out is to parse
with `DocumentBuilderFactory` and accept the DOM cost; say so rather than discovering it in review.

**Files**

- `launcher/app/src/test/java/com/gathin/tvhome/theme/TokensTest.java` — placeholder asserting `Defaults.tokens()` is non-null. Replaced by real assertions in Task 2. Keeps the test source set wired from the start.

**Verify** `./gradlew :app:testDebugUnitTest` — green with one test.

---

## Task 2 — `Tokens` and `Defaults`

New package `com.gathin.tvhome.theme`. Both classes are pure Java with no Android imports, which is what makes them unit-testable.

### `Tokens`

One instance per variant. **Mutable with a `copy()`**, deliberately: all 64 values are primitives, `String`s, or immutable `TypeSpec`s, so a shallow copy is genuinely safe, and the parser's whole job is "start from defaults, overwrite what the file specifies", which that shape expresses in three lines instead of a 64-method builder.

- Public fields grouped by category, each with a comment naming the file it came from.
- Colours `int` (ARGB), dimens `float` (px after conversion — store dp/sp and convert at apply time so density never leaks into the value), motion numbers, typography as `TypeSpec`.
- `static final class TypeSpec { final float sizeSp; final int weight; final float tracking; }` plus a `copy()`.
- `Tokens copy()`.

Store dimens in **dp/sp, not px**, and convert with the view's density in `Skin`. A `Tokens` cached across a display change would otherwise hold stale pixels.

### `Defaults`

`public static Tokens tokens()` returns the full 64-token default set — the values in the spec's reference tables, i.e. exactly what the launcher looks like today. This is the fallback of last resort: it is why the launcher still renders when every theme file is missing or corrupt.

**The duplication risk, and its guard:** `Defaults.java` and `assets/themes/tvos-black.xml` will hold the same numbers, and if they drift, `Defaults` silently wins. Task 11 adds `DefaultsMatchShippedThemeTest`, which parses the repo's `tvos-black.xml` from disk (relative path from the module dir — this is a repo-integrity test, not a runtime test, so it does not need `unitTests.includeAndroidResources`) and asserts its dark variant equals `Defaults.tokens()`.

**Tests** `TokensTest`

- `Defaults.tokens()` returns non-null with every field at the documented default.
- `copy()` is independent: mutate the copy's `tileRadius`, the original is unchanged.
- `TypeSpec.copy()` likewise.

---

## Task 3 — `ThemeParser`

### `Theme`

```java
public final class Theme {
    public final String id, label;
    public final Tokens dark, light;   // already merged over Defaults
}
```

Both variants are **resolved at parse time**, so consumers never deal with a missing variant. `Theme` has no Android imports.

### `ThemeParser`

`public static Theme parse(InputStream in)`; returns `null` if the document is unusable, so `ThemeCatalog` can skip it.

- Pull parser over the whole document. For each `<dark>` / `<light>` block, start from `Defaults.tokens().copy()`, then apply every child token.
- A variant block that is absent resolves to a copy of the **other** variant — that is the "sibling, then defaults" rule from the spec, in one line. Both absent leaves the defaults.
- `label` falls back to `id`; `id` falls back to the filename.
- Unknown token names are logged once and ignored, so a theme file written for a future version still loads.
- `parseColor(String)`: `#RGB`, `#ARGB`, `#RRGGBB`, `#AARRGGBB`; anything else (including colour *names*) returns `null` → default + one log line. **Do not call `android.graphics.Color.parseColor`** — that is what keeps this class on the JVM.
- Malformed `<number>` / `<integer>` / `<dimen>` / `<text>` attributes behave the same way: that token defaults, the file still loads.
- Missing required unit suffix on a `<dimen>` is a malformed token, not a silent px assumption.

**Tests** `ThemeParserTest`

- Full file with both variants → all values as written.
- Partial dark variant (one colour) → that colour overridden, all 63 others equal to `Defaults.tokens()`.
- Malformed hex (`#GGGGGG`, `blue`) → default, no exception.
- `#RGB` and `#ARGB` expand correctly.
- Missing `<light>` → `light` equals `dark`.
- Missing `<dark>` → `dark` equals `light`.
- Both missing → both equal defaults.
- Unknown token name ignored, known tokens still applied.
- Truncated/garbage XML → `parse` returns `null`.
- Missing `label` → falls back to `id`.

---

## Task 4 — `VariantResolver`

Pure function, no Android:

```java
public static String resolve(boolean followSystem, String pinnedVariant, boolean isNight)
```

- `followSystem` → `isNight ? "dark" : "light"`.
- else → `pinnedVariant`, and if that is null or unrecognised, fall back to `isNight`.

**Tests** `VariantResolverTest` — the two rules above, plus null and garbage `pinnedVariant`.

---

## Task 5 — `ThemeCatalog` and `ThemeStore`

### `ThemeCatalog`

- Lists `assets/themes/*.xml` via `getAssets().list("themes")`, parses each once, caches, sorts by label.
- Unparseable files are logged and skipped.
- If nothing parses, or `tvos-black` is absent, a synthetic `Theme` built from `Defaults` is prepended, so the picker always has at least one row and the fallback is reachable by hand.

### `ThemeStore`

`SharedPreferences` file `tvhome.prefs`:

| Key | Type | Default |
|---|---|---|
| `theme_id` | String | `tvos-black` |
| `follow_system` | boolean | `true` |
| `pinned_variant` | String | `dark` |

Methods: `themeId()`, `followSystem()`, `pinnedVariant()`, `select(String themeId, boolean followSystem, String pinnedVariant)`, and `Tokens current(boolean isNight)` which goes through `VariantResolver` + `ThemeCatalog`. Corrupt or unrecognised stored values are treated as absent.

**Deliberately not unit tested.** It is `SharedPreferences` plumbing. Putting an interface in front of it to make it testable would be more code than the logic it guards, and `VariantResolver` already covers the rules that matter. Do not refactor this later.

**Verify** `assembleDebug`. Behaviour is covered by the Task 10 screenshot.

---

## Task 6 — `Skin` and `GlassView.retheme`

`Skin` is the only class that turns `Tokens` into pixels. It builds, for a given `Tokens`:

- pill background `StateListDrawable` (gradient fill + stroke, focused fill)
- pill text `ColorStateList`, pill dim text `ColorStateList`
- input-row background `StateListDrawable`
- tile background `GradientDrawable` (radius from `tileRadius`)
- glass panel background (gradient + stroke + radius)
- status dot `GradientDrawable`
- dock band + edge paints for `DockDecoration`
- `GlassView`'s four paints, via `GlassView.retheme(Tokens)`

and exposes appliers:

```java
void applyScreen(Activity a, View root, Tokens t)
void applyTile(View itemView, Tokens t)
void applyInputRow(View row, Tokens t)
```

**Dependency direction:** `applyScreen` finds its views with `findViewById` internally rather than taking `MainActivity` or `AppAdapter.Holder` parameters. The `theme` package therefore never imports from `com.gathin.tvhome`, so the engine stays independent of the screen it themes. The one exception is `GlassView.retheme(Tokens)`, where `GlassView` (app package) depends on `Tokens` (theme package) — a single, correct, one-way arrow.

Details that will bite otherwise:

- `pillHeight` is a token but is currently a `layout_height` in the `TopButton` style. `applyScreen` sets the height on each pill's `LayoutParams`.
- Typography goes on via `setTextSize(COMPLEX_UNIT_SP, …)`, `setFontVariationSettings("'wght' " + weight)` and `setLetterSpacing` — all available at `minSdk 26`.
- Icons are tinted with `setCompoundDrawableTintList(pillText)` on the pills and `setImageTintList` on input rows, keeping the existing invert-with-focus behaviour.
- `Skin` holds a small cache keyed by `Tokens` identity so re-theming on every picker focus change does not rebuild a dozen drawables per frame; `Tokens` has value-ish identity so reference comparison is sufficient.

### `GlassView.retheme(Tokens)`

Replaces the four hardcoded paints, the radius, the stroke width and the inset. Because the view sizes itself from `MainActivity` after `setRadius`, the inset change must be pushed again on the next focus — so `MainActivity` re-applies the inset in `onTileFocused` rather than reading it from the view.

**Verify** `assembleDebug`. Nothing calls these yet; the next task does.

---

## Task 7 — Wire the screen to `Tokens`

This is where the app starts reading tokens at runtime. The colour XML is still present at this point and is simply overwritten — deletion is Task 8, so this commit is reviewable on its own.

- **`MainActivity`** — resolve `Tokens` in `onCreate` and call `applyScreen`. Feed `springStiffness`/`springDamping` to both `SpringAnimation`s, re-`setSpring`ing rather than recreating them. Replace the ambient alphas in `setAmbient` with `ambientCore`/`ambientEdge`, `addSeparator`'s literal with `separator`, and the glass inset pad with `glassInset`.
- **`AppAdapter`** — `FOCUS_SCALE` stops being `static final` and is read from `Tokens`. `Holder` reads `focusInMs`/`focusOutMs`/`focusOvershoot`; the outline radius reads `tileRadius`. `onBindViewHolder` calls `applyTile` so recycled views get the active tokens.
- **`AppAdapter.vividColor`** — `ambientSaturation` and `ambientValueFloor` replace the `1.1f` / `0.5f` literals; `ambientColorAt`'s fallback becomes `ambientFallback`. These need the active `Tokens` passed in.
- **`DockDecoration`** — band colour, edge colour and radius from tokens.

**Verify** `assembleDebug`, then install and screenshot: the launcher must look **identical** to before. That is the acceptance test for this task — a silent visual regression here is the main risk of the whole refactor, so diff the screenshot against `docs/new-home.jpg` rather than eyeballing it.

---

## Task 8 — Delete the colour XML

Only now, with the runtime confirmed:

- Delete `drawable/pill_bg.xml`, `pill_text.xml`, `pill_text_dim.xml`, `input_row_bg.xml`, `glass_panel.xml`, `tile_bg.xml`, `status_dot.xml`.
- Strip `android:textColor` and `android:background` from `activity_main.xml` and `item_app.xml`; strip `android:textColor`, `android:background` and `android:drawableTint` from the `TopButton` style.
- Leave the `ic_*.xml` fills alone — see the spec's colour section for why.

**Verify** `assembleDebug` and re-screenshot. Then run the drift check:

```bash
grep -rnE '#[0-9A-Fa-f]{6,8}' launcher/app/src/main/res launcher/app/src/main/java
```

Only three kinds of hit may remain: `ic_*.xml` vector fills, `Defaults.java`, and the theme files under `assets/themes/`. Any other literal is a colour that escaped the engine.

---

## Task 9 — The picker panel

### Layout

`res/layout/picker_themes.xml`: a full-size `View` scrim using `pickerScrim`, above it a panel using the glass-panel background, containing a `LinearLayout` of rows. Rows are built in code the way `addInputRow` already does, so the picker needs no adapter or recycler view — there are six rows and a `RecyclerView` here would cost more than it saves on a 1 GB device.

Each row is theme text plus a marker for the committed choice. Use a small rounded-square `View` tinted `textPill` rather than a `✓` glyph: Outfit may not carry that codepoint, and a missing glyph on a TV is a tofu box.

### `ThemePicker`

New class in `com.gathin.tvhome`. It is a separate class because `MainActivity` is already 484 lines and this is ~120 more of self-contained behaviour.

Communicates with the host through a small interface:

```java
interface Host {
    Tokens committedTokens();
    void applyTokens(Tokens t);   // used for preview and for commit
}
```

`MainActivity` implements it. The picker holds the tokens that were committed when it opened and restores them on Back.

- Open: long-press on the Settings pill. Short press must still open Android settings — wire both on the same `TextView`.
- Focus change → `applyTokens(thatTheme's tokens for the current mode)`.
- OK → `store.select(...)`, `applyTokens`, dismiss.
- Back → `applyTokens(committed)`, dismiss.
- While open: `grid.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS)` so the D-pad cannot escape the panel, and the global-focus listener must not call `hideGlass()`.

**Verify** `assembleDebug`, install, and drive it over ADB with `tools/tv.py`: send `KEYCODE_DPAD_DOWN` repeatedly and screenshot after each to confirm live preview, then `KEYCODE_BACK` and confirm revert.

`CONTRIBUTING.md` requires deterministic D-pad navigation tested "from every edge", so the picker
must define and be tested for all four of its own edges: Up on the first row stays put, Down on the
last row stays put, Left on the first column stays put, and Right on the last column stays put. None
of them may escape the panel or fall through to the grid behind it.

---

## Task 10 — Day/night, persistence, Back

- `onConfigurationChanged` — if `followSystem`, re-resolve and re-apply. The manifest already lists `uiMode` in `configChanges`, so no recreate and no launch flash.
- `onBackPressed` — dismiss and revert when the picker is open; still a no-op otherwise ("Home screen: Back goes nowhere" is deliberate).
- Ensure `applyTokens` re-runs `applyScreen` **and** notifies the adapter so recycled tiles repaint.

**Verify** `assembleDebug`, install, then: choose a theme, press Home, come back, screenshot — the choice must persist. Change the TV's day/night setting with System selected and screenshot — it must re-theme without the launcher restarting.

---

## Task 11 — The five theme files

`launcher/app/src/main/assets/themes/`:

| File | Purpose |
|---|---|
| `tvos-black.xml` | The current look, dark variant **byte-equal in value to `Defaults.tokens()`**; light is its tvOS-style inversion |
| `daylight.xml` | Light theme: light background, dark text, tinted glass. Proves light variants work |
| `ember.xml` | Warm dark: amber ambient bias, warm glass and dock band |
| `mono.xml` | High-contrast greyscale, small radii. Proves the shape tokens |
| `nocturne.xml` | Deep blue-black, low-contrast chrome, dimmed text. Proves the ambient and dimmed-text tokens |

Each ships both variants, so 10 blocks in total. The first two blocks of `tvos-black.xml` must be transcribed from the spec's tables exactly — that is the file `DefaultsMatchShippedThemeTest` guards.

**Tests**

- `DefaultsMatchShippedThemeTest` — parses `src/main/assets/themes/tvos-black.xml` from the module directory and asserts its dark `Tokens` equals `Defaults.tokens()`.
- `AllThemesParseTest` — parses every file in `assets/themes/`, asserts each has a non-blank label and both variants, and that no token is left at an unintended default (a theme that only overrides `background` is fine; one that overrides nothing at all is a bug).

**Verify** unit tests, then install and screenshot each of the five, dark and light, hunting for contrast failures the tables cannot show — most likely on `textTileName` over a bright banner, and on `mono`'s glass rim against its band.

---

## Task 12 — Documentation

- `docs/themes.md` — the token reference (generated by hand from the spec tables, but it must agree with `Defaults.java`; the Task 11 test guards the numbers), the file format, and a worked example of adding a theme by copying `tvos-black.xml`.
- `README.md` — a short **Themes** section: hold OK on Settings to choose, themes ship in the APK, copy a file to make your own and rebuild. Link to `docs/themes.md`. Mention the launcher follows the TV's day/night setting when `System` is selected.

**Verify** read it against the code, not against the spec.

---

## Task 13 — On-device pass

Only with a real TV. `TV_HOST=<ip> launcher/build-and-install.sh`, then work through the spec's manual checklist, screenshotting each step:

1. Hold Settings opens the picker; short press still opens Android settings.
2. Every theme previews as focus passes over it.
3. Back reverts; OK commits.
4. The choice survives Home-then-return.
5. A system day/night flip re-themes live with no restart.
6. A deliberately malformed theme file is skipped and the picker still lists the rest.
7. Grid D-pad behaviour is unchanged before, during and after the picker.
8. The picker's own four edges hold: Up/Down/Left/Right at each boundary stay put and never escape
   the panel.

Steps 6 and 7 are the ones that get skipped and then matter. For step 6, break a copy of a theme file, `adb push` it into `assets` via a rebuild, and confirm the picker still opens.

## Definition of done

- `assembleDebug` clean; `:app:testDebugUnitTest` green.
- The Task 8 grep check shows no colour literal outside `ic_*.xml`, `Defaults.java` and `assets/themes/`.
- Screenshot of `tvos-black` / dark is visually unchanged from `docs/new-home.jpg`.
- All seven on-device checks pass.
- `docs/themes.md` and the README section exist and agree with the code.

## Out of scope

Re-stating the spec's non-goals so they do not drift in during implementation: no external/USB theme loading, no procedural theme families, no build-time default theme, no typeface switching. If one of these turns out to be needed, it is a new spec.
