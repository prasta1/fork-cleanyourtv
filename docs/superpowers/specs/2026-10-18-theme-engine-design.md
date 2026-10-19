# Theme engine — design spec

Date: 2026-10-18
Status: approved in chat, pending review of this document

## Problem

Every visual decision in the launcher is hardcoded. Roughly 40 colour values live in seven XML
drawables, three layouts, `GlassView` and `MainActivity`; shape, motion and typography values are
baked into the same files and into Java constants. The only user-facing configuration today is
build-time: `values/config.xml` is edited and `launcher/build-and-install.sh` re-run.

The result is that the only theme a user can have is the one we shipped. Third-party launchers
charge for this (see README, "Gotchas"), and there is no way to change the look without a rebuild.

## Goal

A theme engine with **pre-configured themes** the user picks on the TV, at runtime, with no rebuild.
Each theme carries dark and light variants; a `System` option follows the TV's day/night setting.

## Non-goals

- Custom themes loaded from USB/SD or any external source. Themes ship in the APK. Authoring is
  copy-a-file-and-edit, which needs a rebuild, matching how `config.xml` already works.
- Procedurally generated theme families from a seed colour.
- A build-time default theme in `values/config.xml`. The shipped default is `System`.
- Runtime switching of typeface. Only Outfit is bundled (`res/font/outfit.ttf`), so a `fontFamily`
  token would have exactly one legal value. Bundling more fonts is separate work.

## Decisions

| Question | Decision |
|---|---|
| How is a theme chosen? | Runtime picker on the TV. Long-press (hold OK) on the Settings pill. |
| Relation to day/night? | Every theme has a `<dark>` and a `<light>` variant. `System` picks by `uiMode`; an explicitly chosen theme pins the variant that was current when it was chosen. |
| What can a theme change? | Colour, shape, motion and typography. |
| Catalog | 4–5 hand-tuned presets, each a self-contained file users copy to make their own. |
| Architecture | Token model + a single `Skin` builder. Rejected: per-theme XML styles with `setTheme()` (duplication, rebuild required, no live preview) and a custom `Resources` overlay (leans on framework internals, too fragile on the low-end TV ROMs this repo targets). |

## Architecture

New package `com.gathin.tvhome.theme`. The existing four classes stay in `com.gathin.tvhome`.

**Every colour is deleted from XML.** `drawable/pill_bg.xml`, `pill_text.xml`, `pill_text_dim.xml`,
`input_row_bg.xml`, `glass_panel.xml`, `tile_bg.xml` and `status_dot.xml` are removed, as are the
`android:textColor` and `android:background` attributes in `activity_main.xml` and `item_app.xml`,
and the `android:textColor` / `android:background` / `android:drawableTint` items in the
`TopButton` style. They are rebuilt in code from a `Tokens` object. One source of truth is what
makes five presets × two variants tractable. The `ic_*.xml` vector fills are the one deliberate
exception and are explained in the colour section.

`android:windowBackground` stays in `values/styles.xml`; the `Skin` sets it programmatically at the
same time so it matches the active theme.

| Class | Responsibility | Depends on |
|---|---|---|
| `Theme` | Identity of a theme file: `id`, `label`, the two variant blocks | nothing |
| `Tokens` | Resolved, immutable token values for one variant | nothing |
| `ThemeParser` | `XmlPullParser` over `assets/themes/<id>.xml` → `Theme`; merges each variant over the built-in defaults, so every token is optional | nothing |
| `ThemeCatalog` | Enumerates `assets/themes/` and parses every file once on first access, caching by id. The files are a few KB each and the picker needs every label, so there is nothing to gain from laziness | `ThemeParser` |
| `VariantResolver` | Pure function `(themeId, followSystem, pinnedVariant, isNight) → variant name`. Exists so the light/dark rules are testable without Android | nothing |
| `ThemeStore` | `SharedPreferences` I/O around `VariantResolver`; resolves to `Tokens` for the current `uiMode` | `ThemeCatalog`, `VariantResolver` |
| `Skin` | The only thing that turns `Tokens` into pixels | `Tokens` |

`ThemeParser` must not call `android.graphics.Color.parseColor`: it parses hex to `int` with a
small local parser. This keeps the parser unit-testable on the JVM and removes a dependency on a
framework class that is easy to misuse with named colours.

`Skin` builds every visual artifact from tokens — the pill and input-row `StateListDrawable`s and
`ColorStateList`s, the tile and dock-band paints, the glass-panel gradient, and `GlassView`'s four
paints — and exposes one applier per family of view.

### View-creating paths

A single `Skin.applyScreen` walk of the view tree is not sufficient: `RecyclerView` recycles tile
views, so tiles that were bound before a retheme keep the old colours. Every path that creates a
view consults the `Skin`, and none of them read a colour from XML.

- `Skin.applyScreen(activity, root, tokens)` — window background, root, greeting, clock, am/pm,
  date, hero name, the three pills.
- `Skin.applyTile(holder, tokens)` — called from `AppAdapter.onBindViewHolder`.
- `Skin.applyInputRow(row, tokens)` — Inputs popup rows, inflated fresh each time the menu opens.
- `glass.retheme(tokens)` — repaints `GlassView` in place.

`GlassView` gains a `retheme` method; its constructor stops hardcoding colours.

## Theme file format

One file per theme at `launcher/app/src/main/assets/themes/<id>.xml`.

```xml
<?xml version="1.0" encoding="utf-8"?>
<theme id="tvos-black" label="tvOS Black">
  <dark>
    <color name="background">#FF000000</color>
    <dimen name="tileRadius">16dp</dimen>
    <number name="focusScale">1.10</number>
    <integer name="focusInMs">260</integer>
    <text name="clock" size="52sp" weight="300" tracking="-0.04"/>
  </dark>
  <light>
    <color name="background">#FFF2F2F7</color>
  </light>
</theme>
```

Rules:

- `id` and `label` are attributes on `<theme>`; the filename is irrelevant to the app.
- Token elements are `<color>`, `<dimen>`, `<number>`, `<integer>` and `<text>`.
- Any token a variant omits falls back to the built-in defaults. Copying a file and changing four
  hex values yields a working theme — this is what makes copy-to-custom real.
- A `weight` of 100–900 maps to `fontVariationSettings`, so variable-font weights stay real weights
  rather than faux bold.
- Units: `#RRGGBB`, `#AARRGGBB`, `#RGB`, `#ARGB` for colours; `dp`/`sp`/`px` for dimens; bare
  decimals for `<number>`.

## Token reference

68 named tokens (84 scalar values). Every default below is the value currently hardcoded in the
repo; no new visual knobs are introduced except `pickerScrim` and `entranceMs`, which cover
surfaces that did not exist before this work.

Counting the tables below: 32 colour + 5 ambient + 9 shape + 14 motion + 8 typography = 68 tokens,
and 84 scalars once each typography token's `size`, `weight` and `tracking` are counted separately.

The colour table is exhaustive against the current code: extracting every `#RRGGBB` literal from
`res/` and every `0xAARRGGBB` from `java/` accounts for all 32 entries, leaving only the icon
vector fills untokenised (see below) and the two transparent stops that are derived rather than
themed.

### Colour (32) and ambient (5)

| Token | Default | Currently in |
|---|---|---|
| `background` | `#FF000000` | `activity_main.xml` root, `styles.xml` |
| `tileSurface` | `#FF1C1C1E` | `drawable/tile_bg.xml` |
| `dockBand` | `#FF141416` | `DockDecoration` |
| `dockBandEdge` | `#1FFFFFFF` | `DockDecoration` |
| `pillFillTop` | `#2EFFFFFF` | `drawable/pill_bg.xml` gradient |
| `pillFillBottom` | `#14FFFFFF` | `drawable/pill_bg.xml` gradient |
| `pillStroke` | `#38FFFFFF` | `drawable/pill_bg.xml` |
| `pillFocusFill` | `#F5FFFFFF` | `drawable/pill_bg.xml`, `input_row_bg.xml` |
| `inputRowFocus` | `#F5FFFFFF` | `drawable/input_row_bg.xml` |
| `textHero` | `#FFFFFFFF` | `activity_main.xml` `heroName` |
| `textClock` | `#FFFFFFFF` | `activity_main.xml` `clock` |
| `textAmPm` | `#B3FFFFFF` | `activity_main.xml` `ampm` |
| `textDate` | `#A6FFFFFF` | `activity_main.xml` `date` |
| `textGreeting` | `#8CFFFFFF` | `activity_main.xml` `greeting` |
| `clockDivider` | `#33FFFFFF` | `activity_main.xml`, the hairline between clock and date |
| `textTileLabel` | `#FFFFFFFF` | `item_app.xml` `label` |
| `textTileName` | `#F2FFFFFF` | `item_app.xml` `name` |
| `textPill` | `#FFFFFFFF` | `drawable/pill_text.xml` |
| `textPillDim` | `#73FFFFFF` | `drawable/pill_text_dim.xml` |
| `textPillFocus` | `#FF111111` | `drawable/pill_text.xml` focused |
| `textPillFocusDim` | `#99111111` | `drawable/pill_text_dim.xml` focused |
| `glassFillTop` | `#3DFFFFFF` | `GlassView` fill gradient |
| `glassFillBottom` | `#12FFFFFF` | `GlassView` fill gradient |
| `glassSpecular` | `#66FFFFFF` | `GlassView` specular gradient |
| `glassRim` | `#CCFFFFFF` | `GlassView` rim |
| `glassGlow` | `#30FFFFFF` | `GlassView` outer glow |
| `statusDot` | `#FF34C759` | `drawable/status_dot.xml` |
| `separator` | `#1FFFFFFF` | `MainActivity.addSeparator` |
| `panelFillTop` | `#F22A2A2E` | `drawable/glass_panel.xml` gradient |
| `panelFillBottom` | `#F5161618` | `drawable/glass_panel.xml` gradient |
| `panelStroke` | `#33FFFFFF` | `drawable/glass_panel.xml` outline |
| `ambientCore` | alpha `#5C` | `MainActivity.setAmbient` |
| `ambientEdge` | alpha `#26` | `MainActivity.setAmbient` |
| `ambientFallback` | `#FF444444` | `AppAdapter.ambientColorAt` |
| `ambientSaturation` | `1.1` | `AppAdapter.vividColor` |
| `ambientValueFloor` | `0.5` | `AppAdapter.vividColor` |
| `pickerScrim` | `#CC000000` | new — the picker's backdrop |

The ambient glow's third gradient stop is transparent in the source colour, and `GlassView`'s
specular gradient ends at transparent; both are derived from the token rather than themed
separately.

**Icons are not themed separately.** The five `ic_*.xml` vectors keep their white fills and continue
to follow `textPill` / `textPillFocus` in the top bar and `textPill` / `textPillDim` in the Inputs
rows, exactly as `android:drawableTint="@drawable/pill_text"` does today. A dedicated `iconTint`
token was considered and dropped: it would have separated icon colour from label colour for no
gain, and splitting them would break the existing behaviour where icons invert with their pill.

### Shape (9)

| Token | Default | Currently in |
|---|---|---|
| `tileRadius` | `16dp` | `tile_bg.xml`, `AppAdapter.Holder` outline, `GlassView` |
| `dockBandRadius` | `26dp` | `DockDecoration` |
| `pillRadius` | `21dp` | `pill_bg.xml` |
| `pillHeight` | `42dp` | `styles.xml` `TopButton` |
| `glassRadius` | `20dp` | `GlassView` |
| `glassStroke` | `1.5dp` | `GlassView` rim |
| `glassInset` | `6dp` | `GlassView`, and the matching pad in `MainActivity.onTileFocused` |
| `panelRadius` | `20dp` | `drawable/glass_panel.xml` |
| `inputRowRadius` | `13dp` | `drawable/input_row_bg.xml` |

### Motion (14)

| Token | Default | Currently in |
|---|---|---|
| `springStiffness` | `260` | `MainActivity` spring setup |
| `springDamping` | `0.58` | `MainActivity` spring setup |
| `focusScale` | `1.10` | `AppAdapter.FOCUS_SCALE` |
| `focusInMs` | `260` | `AppAdapter.Holder` |
| `focusOutMs` | `180` | `AppAdapter.Holder` |
| `focusOvershoot` | `1.6` | `AppAdapter.Holder` |
| `glassInMs` | `180` | `MainActivity` |
| `glassOutMs` | `160` | `MainActivity` |
| `heroMs` | `260` | `MainActivity.showHero` |
| `heroSwapMs` | `120` | `MainActivity.showHero` |
| `ambientMs` | `600` | `MainActivity.showHero` |
| `entranceStagger` | `0.06` | `MainActivity` layout animation |
| `tileNameMs` | `220` | `AppAdapter.Holder`, the name under a focused tile |
| `entranceMs` | `420` | `res/anim/tile_enter.xml` |

`AppAdapter.FOCUS_SCALE` ceases to be a `static final` and is read from tokens, since focus scale is
now a theme decision. The two `SpringAnimation`s are re-`setSpring`ed on retheme rather than
recreated. `entranceMs` is applied to the loaded `Animation` via `setDuration`, because
`LayoutAnimationController` exposes no duration setter.

### Typography (8 tokens, `{size, weight, tracking}`)

| Token | size | weight | tracking | Currently in |
|---|---|---|---|---|
| `clock` | `52sp` | 300 | `-0.04` | `activity_main.xml` |
| `amPm` | `15sp` | 500 | `0.08` | `activity_main.xml` |
| `date` | `14sp` | 400 | `0.14` | `activity_main.xml` |
| `greeting` | `12sp` | 500 | `0.22` | `activity_main.xml` |
| `hero` | `48sp` | 300 | `-0.02` | `activity_main.xml` |
| `pill` | `15sp` | 500 | `0.01` | `styles.xml` `TopButton` |
| `tileName` | `14sp` | 500 | `0.02` | `item_app.xml` |
| `tileLabel` | `13sp` | 400 | `0` | `item_app.xml` |

## Shipped themes

Five files in `assets/themes/`, each with both variants, chosen to exercise the token range:

- `tvos-black` — the current look, unchanged. The reference and the fallback default.
- `daylight` — a light theme: light background, dark text, tinted glass. Proves light variants.
- `ember` — warm dark: amber ambient bias, warmer glass and dock band.
- `mono` — high-contrast greyscale with a hard square-ish radius. Proves the shape tokens.
- `nocturne` — deep blue-black with low-contrast chrome. Proves muted ambient and dimmed text.

## Runtime flow

### Picker

An **in-place overlay panel inside `MainActivity`**, not a separate activity. Live preview forces
this: a picker activity would cover and stop the home screen, leaving nothing to preview.

- Hold OK on the Settings pill to open. Short press still opens Android settings, unchanged.
- Rows: `System` first, then each theme by label in alphabetical order. The committed choice is
  marked with a check.
- Focus moves down the list → the screen behind the panel is re-themed live through the `Skin`
  appliers, so the row under focus is exactly what the launcher will look like. Focus is the only
  thing that changes the preview; nothing is committed until OK.
- OK commits, writes prefs, dismisses. Back reverts to the theme active when the picker opened and
  dismisses.
- While open, the grid's descendants are made non-focusable so the D-pad stays in the panel, and the
  global-focus listener's `hideGlass()` call is suppressed for the duration.

`MainActivity.onBackPressed` currently does nothing ("Home screen: Back goes nowhere"). It becomes:
dismiss and revert if the picker is open, otherwise still nothing.

### Light/dark resolution

`MainActivity` declares `android:configChanges="orientation|screenSize|keyboard|keyboardHidden|uiMode"`,
so a day/night change arrives at `onConfigurationChanged` instead of recreating the activity.

`follow_system` is a separate boolean, not encoded in the theme id. `theme_id` always names a real
theme; `follow_system` decides how its variant is chosen.

- **System (`follow_system=true`)**: variant follows `uiMode`. Re-resolve tokens and re-apply in
  `onConfigurationChanged`. No restart, no flash.
- **An explicit theme (`follow_system=false`)**: pins the variant that was current when it was
  chosen, stored in `pinned_variant`. Choosing Ember while the TV is dark leaves it dark after a
  system flip to light. This is what makes `System` a distinct choice rather than a duplicate of
  picking any theme.

Selecting the `System` row sets `follow_system=true` and keeps whatever `theme_id` was already
chosen; selecting a theme row sets `theme_id` to it, `follow_system=false`, and `pinned_variant` to
the variant currently on screen. So "System" means "keep this theme, but track day/night".

### Persistence

`SharedPreferences` file `tvhome.prefs`, keys `theme_id` (default `tvos-black`), `follow_system`
(default `true`) and `pinned_variant` (default `dark`). A fresh install is therefore `tvos-black`
following the system, which is exactly the current behaviour of the app. Read once in `onCreate`;
written only on commit, never on preview.

## Failure handling

The launcher must stay usable under every one of these, because it is the TV's home screen.

| Failure | Behaviour |
|---|---|
| Unknown `theme_id` in prefs | Treated as absent → `System` |
| Corrupt prefs values | Treated as absent |
| Theme file missing `<light>` or `<dark>` | That variant falls back to its sibling, then to defaults |
| One malformed colour/number value | That token falls back and is logged; the theme still loads |
| Whole file unparseable | Skipped by the catalog and never listed |
| Zero loadable theme files | Built-in defaults live in code, so the launcher still renders |

A theme with no `label` shows its `id`. All fallbacks are logged once per token, not per frame.

## Testing

The repo has no test infrastructure; this adds the minimum that earns its keep.

- `testImplementation 'junit:junit:4.13.2'` in `app/build.gradle`.
- Pure-JVM tests for `ThemeParser`: token element parsing for all five element types, partial-file
  fallback over defaults, malformed hex, malformed number, missing variant, unparseable file, and
  the hex-parsing edge cases (`#RGB`, `#ARGB`, `#RRGGBB`, `#AARRGGBB`).
- Pure-JVM tests for `VariantResolver`: `follow_system=true` follows `isNight`; `follow_system=false`
  honours `pinned_variant` regardless of `isNight`; unknown ids resolve to the default variant.
  `ThemeStore` itself is not unit tested — it is prefs plumbing around this function.
- No Robolectric: too heavy for this repo's constraints.
- Device verification stays the existing loop — `launcher/build-and-install.sh` plus `tools/tv.py`
  screenshots over ADB.

Manual checklist on device:

1. Hold Settings opens the picker; short press still opens Android settings.
2. Every theme previews as focus passes over it.
3. Back reverts; OK commits.
4. The choice survives Home-then-return.
5. A system day/night flip re-themes live with no restart.
6. A deliberately malformed theme file is skipped and the picker still lists the rest.
7. The grid's D-pad behaviour is unchanged before, during and after the picker.

## Docs

- `docs/themes.md` — token reference and an authoring walkthrough ("copy `tvos-black.xml`, change
  four hex values").
- `README.md` — a short "Themes" section: how to pick a theme on the TV, and a pointer to
  `docs/themes.md`.

## Notes on the review

Three token counts in the chat review were wrong and are corrected here: the shape set is 9 tokens,
not 7 (`dockBandRadius` and `inputRowRadius` were undercounted); `entranceMs` was missing from the
motion list; and the colour set was undercounted by four — `clockDivider`, `panelFillTop`,
`panelFillBottom` and `panelStroke` were all missed, which would have left a light theme with a
near-black Inputs panel outlined in white. A fifth was missed during implementation, when
`tileNameMs` had to be added rather than fudging the tile-name animation out of other tokens' values.
An `iconTint` token proposed in the chat was also dropped, for the reason given in the colour
section. The totals are 68 named tokens and 84 scalars.

Two transcription errors were caught while writing `Defaults.java`, where the compiler rejected two
hex literals that were ten digits rather than eight. `textGreeting` and `textTileName` have been
corrected from `#8CFFFFFFFF` and `#F2FFFFFFFF` to `#8CFFFFFF` and `#F2FFFFFF`.

Two further ambiguities were resolved during the self-review rather than left open: `follow_system`
is a boolean distinct from `theme_id` (so "System" means "keep this theme and track day/night", not
a theme id called `system`), and `VariantResolver` exists as a separate pure class specifically so
the light/dark rules can be tested without Android.

Finally, the chat review claimed the token list was derived from the existing code. It had been
compiled by eye. `TokensTest.tokenCountMatchesTheDocumentedSet` plus a mechanical extraction of
every colour literal in `res/` and `java/` now back that claim, and the count assertion fails the
build whenever a token is added or removed so this table cannot quietly drift again.
