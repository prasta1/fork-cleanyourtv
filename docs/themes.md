# Themes

The launcher's whole look is a set of named values (*tokens*): colours, corner radii, motion timings
and type styles. A theme is one XML file that sets some of them. Themes ship inside the APK, so
adding one means adding a file and rebuilding.

Hold **OK** on the Settings pill and choose **Theme** to open the picker. The screen behind it re-themes as focus moves
down the list, **OK** commits, **Back** reverts. A short press on Settings still opens Android
settings.

## Light and dark

Every theme has a `<dark>` and a `<light>` block. The picker's **System** row follows the TV's
day/night setting and re-themes live when it flips; choosing a theme explicitly pins the variant
that was on screen when you chose it.

## File format

Files live in `launcher/app/src/main/assets/themes/`. The filename does not matter; the `id` attribute does.

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

- Elements are `<color>`, `<dimen>`, `<number>`, `<integer>` and `<text>`; `name` is a token from the tables below.
- **A block only has to name what it changes.** Anything left out falls back to the built-in defaults
  (the `tvos-black` dark values). Note this is per block: a `<light>` block starts from the
  defaults, not from your `<dark>` block, so repeat shape and motion overrides in both.
- A variant with no block at all copies the other one.
- Colours are `#RGB`, `#ARGB`, `#RRGGBB` (opaque) or `#AARRGGBB`. Colour names such as `blue` are not supported. Lengths are `dp` or `sp`; `px` is rejected.
- Nothing in a theme file can break the launcher. A bad value falls back to its default, an unknown
  token is ignored, and a file that is not a theme at all is skipped and left out of the picker.
  Warnings go to logcat under the tag `ThemeCatalog`.

## Adding a theme

1. Copy `tvos-black.xml` to `assets/themes/my-theme.xml`.
2. Change `id` (unique) and `label` (shown in the picker).
3. Edit values; delete any token you don't change if you like.
4. Rebuild and install (`TV_HOST=<ip> launcher/build-and-install.sh`), then hold OK on Settings and choose Theme.

`tvos-black` is the reference: its dark block lists every token and is checked against
`Defaults.java` by `DefaultsMatchShippedThemeTest`, so the numbers below cannot silently drift.
Not themed: the typeface (Outfit) and the Inputs menu's open animation.

## Token reference

76 tokens. Defaults are the `tvos-black` dark values.

### Colour (32)

`<color>`, `#RRGGBB`, `#AARRGGBB`, `#RGB` or `#ARGB`.

| Token | Default | What it controls |
|---|---|---|
| `background` | `#FF000000` | activity_main root, window background |
| `tileSurface` | `#FF1C1C1E` | drawable/tile_bg |
| `dockBand` | `#FF141416` | DockDecoration band fill |
| `dockBandEdge` | `#1FFFFFFF` | DockDecoration band outline |
| `pillFillTop` | `#2EFFFFFF` | drawable/pill_bg gradient, top |
| `pillFillBottom` | `#14FFFFFF` | drawable/pill_bg gradient, bottom |
| `pillStroke` | `#38FFFFFF` | drawable/pill_bg outline |
| `pillFocusFill` | `#F5FFFFFF` | drawable/pill_bg and input_row_bg, focused |
| `inputRowFocus` | `#F5FFFFFF` | drawable/input_row_bg, focused |
| `textHero` | `#FFFFFFFF` | the focused dock app's name |
| `textClock` | `#FFFFFFFF` |  |
| `textAmPm` | `#B3FFFFFF` |  |
| `textDate` | `#A6FFFFFF` |  |
| `textGreeting` | `#8CFFFFFF` |  |
| `clockDivider` | `#33FFFFFF` | hairline between the clock and the date |
| `textTileLabel` | `#FFFFFFFF` | label drawn over a banner-less tile |
| `textTileName` | `#F2FFFFFF` | name under a focused tile |
| `textPill` | `#FFFFFFFF` | top bar pill, unfocused |
| `textPillDim` | `#73FFFFFF` | Inputs row status, unfocused |
| `textPillFocus` | `#FF111111` | top bar pill, focused |
| `textPillFocusDim` | `#99111111` | Inputs row status, focused |
| `glassFillTop` | `#3DFFFFFF` | GlassView fill gradient, top |
| `glassFillBottom` | `#12FFFFFF` | GlassView fill gradient, bottom |
| `glassSpecular` | `#66FFFFFF` | GlassView top highlight |
| `glassRim` | `#CCFFFFFF` | GlassView outline |
| `glassGlow` | `#30FFFFFF` | GlassView outer glow |
| `statusDot` | `#FF34C759` | drawable/status_dot |
| `separator` | `#1FFFFFFF` | rule between input groups |
| `panelFillTop` | `#F22A2A2E` | glass_panel gradient, top (Inputs menu, theme picker) |
| `panelFillBottom` | `#F5161618` | glass_panel gradient, bottom |
| `panelStroke` | `#33FFFFFF` | glass_panel outline |
| `pickerScrim` | `#40000000` | backdrop behind the theme picker |

### Ambient (5)

The glow behind the focused dock app's name takes its hue from that app's banner. `ambientCore` and `ambientEdge` are alpha channels (`<integer>`, 0–255) the sampled colour is composited onto. `ambientSaturation` and `ambientValueFloor` boost the sample first.

| Token | Default | What it controls |
|---|---|---|
| `ambientCore` | `92` | glow alpha at the centre |
| `ambientEdge` | `38` | glow alpha at the edge |
| `ambientFallback` | `#FF444444` | no banner to sample: this grey instead |
| `ambientSaturation` | `1.1` | AppAdapter.vividColor boost applied to the sample |
| `ambientValueFloor` | `0.5` | ...and the minimum value it is lifted to |

### Shape (10)

`<dimen>`, in `dp`.

| Token | Default | What it controls |
|---|---|---|
| `tileRadius` | `16dp` |  |
| `dockBandRadius` | `26dp` |  |
| `pillRadius` | `21dp` |  |
| `pillHeight` | `42dp` |  |
| `pillStrokeWidth` | `1dp` |  |
| `glassRadius` | `20dp` |  |
| `glassStroke` | `1.5dp` |  |
| `glassInset` | `6dp` | room for the glass glow |
| `panelRadius` | `20dp` |  |
| `inputRowRadius` | `13dp` | ---- motion (19) ---- |

### Motion (19)

`<number>` for decimals, `<integer>` for milliseconds.

| Token | Default | What it controls |
|---|---|---|
| `springStiffness` | `260` |  |
| `springDamping` | `0.58` |  |
| `focusScale` | `1.10` |  |
| `focusOvershoot` | `1.6` |  |
| `entranceStagger` | `0.06` | LayoutAnimationController interval |
| `focusInMs` | `260` |  |
| `focusOutMs` | `180` |  |
| `glassInMs` | `180` |  |
| `glassOutMs` | `160` |  |
| `heroInMs` | `260` | the focused dock app's name fading in |
| `heroOutMs` | `160` |  |
| `heroSwapOutMs` | `120` | the name swapping to the next app |
| `heroSwapInMs` | `200` |  |
| `ambientInMs` | `600` | the glow behind that name |
| `ambientOutMs` | `300` |  |
| `ambientSwapOutMs` | `200` |  |
| `ambientSwapInMs` | `500` |  |
| `tileNameMs` | `220` | the name fading in under a focused tile |
| `entranceMs` | `420` | ---- typography (10) ---- |

### Typography (10)

`<text size="52sp" weight="300" tracking="-0.04"/>`. `weight` is a variable-font weight (100–900).

| Token | Default | What it controls |
|---|---|---|
| `clock` | `52sp`, weight 300, tracking -0.04 |  |
| `amPm` | `15sp`, weight 500, tracking 0.08 |  |
| `date` | `14sp`, weight 400, tracking 0.14 |  |
| `greeting` | `12sp`, weight 500, tracking 0.22 |  |
| `hero` | `48sp`, weight 300, tracking -0.02 |  |
| `pill` | `15sp`, weight 500, tracking 0.01 |  |
| `tileName` | `14sp`, weight 500, tracking 0.02 |  |
| `tileLabel` | `13sp`, weight 400, tracking 0 |  |
| `inputRowTitle` | `15sp`, weight 500, tracking 0 | an input's name in the Inputs menu |
| `inputRowStatus` | `11sp`, weight 400, tracking 0.04 | its Connected / No device state |
