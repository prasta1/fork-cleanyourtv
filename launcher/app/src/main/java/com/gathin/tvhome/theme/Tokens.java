package com.gathin.tvhome.theme;

/**
 * Every visual value the launcher uses, resolved for one light/dark variant.
 *
 * <p>This is the single source of truth for the look of the app. No colour is set anywhere else:
 * the layouts and drawables that used to carry them are built from these values by {@link Skin}.
 *
 * <p><b>Mutable on purpose.</b> {@link ThemeParser} parses a theme file by starting from
 * {@link Defaults#tokens()} and overwriting only what the file specifies, which this shape
 * expresses in three lines instead of a 67-method builder. It is safe to mutate because a
 * {@code Tokens} is confined to the thread building it and is published once, finished.
 *
 * <p><b>copy() must be kept exhaustive.</b> {@link TokensTest} compares two instances field by
 * field with reflection, so forgetting a token here fails the build rather than silently resetting
 * one theme's colour.
 *
 * <p>Lengths are dp/sp, never px. Converting with the display density happens in {@link Skin}, so
 * a cached {@code Tokens} stays valid across a display change.
 */
public final class Tokens {

    // ---- colour (28), ARGB ----

    public int background;            // activity_main root, window background
    public int tileSurface;           // drawable/tile_bg
    public int dockBand;              // DockDecoration band fill
    public int dockBandEdge;          // DockDecoration band outline
    public int pillFillTop;           // drawable/pill_bg gradient, top
    public int pillFillBottom;        // drawable/pill_bg gradient, bottom
    public int pillStroke;            // drawable/pill_bg outline
    public int pillFocusFill;         // drawable/pill_bg and input_row_bg, focused
    public int inputRowFocus;         // drawable/input_row_bg, focused
    public int textHero;              // the focused dock app's name
    public int textClock;
    public int textAmPm;
    public int textDate;
    public int textGreeting;
    public int clockDivider;          // hairline between the clock and the date
    public int textTileLabel;         // label drawn over a banner-less tile
    public int textTileName;          // name under a focused tile
    public int textPill;              // top bar pill, unfocused
    public int textPillDim;           // Inputs row status, unfocused
    public int textPillFocus;         // top bar pill, focused
    public int textPillFocusDim;      // Inputs row status, focused
    public int glassFillTop;          // GlassView fill gradient, top
    public int glassFillBottom;       // GlassView fill gradient, bottom
    public int glassSpecular;         // GlassView top highlight
    public int glassRim;              // GlassView outline
    public int glassGlow;             // GlassView outer glow
    public int statusDot;             // drawable/status_dot
    public int separator;             // rule between input groups
    public int panelFillTop;          // glass_panel gradient, top (Inputs menu, theme picker)
    public int panelFillBottom;       // glass_panel gradient, bottom
    public int panelStroke;           // glass_panel outline
    public int pickerScrim;           // backdrop behind the theme picker

    // ---- ambient (4) ----
    // The glow behind the hero name takes its hue from the focused app's banner, so these are
    // alpha channels the sampled colour is composited onto, not colours in their own right.

    public int ambientCore;
    public int ambientEdge;
    public int ambientFallback;       // no banner to sample: this grey instead
    public float ambientSaturation;   // AppAdapter.vividColor boost applied to the sample
    public float ambientValueFloor;   // ...and the minimum value it is lifted to

    // ---- shape (10), dp ----

    public float tileRadius;
    public float dockBandRadius;
    public float pillRadius;
    public float pillHeight;
    public float pillStrokeWidth;
    public float glassRadius;
    public float glassStroke;
    public float glassInset;          // room for the glass glow
    public float panelRadius;
    public float inputRowRadius;

    // ---- motion (19) ----

    public float springStiffness;
    public float springDamping;
    public float focusScale;
    public float focusOvershoot;
    public float entranceStagger;     // LayoutAnimationController interval
    public int focusInMs;
    public int focusOutMs;
    public int glassInMs;
    public int glassOutMs;
    public int heroInMs;              // the focused dock app's name fading in
    public int heroOutMs;
    public int heroSwapOutMs;         // the name swapping to the next app
    public int heroSwapInMs;
    public int ambientInMs;           // the glow behind that name
    public int ambientOutMs;
    public int ambientSwapOutMs;
    public int ambientSwapInMs;
    public int tileNameMs;            // the name fading in under a focused tile
    public int entranceMs;

    // ---- typography (10) ----

    public TypeSpec clock;
    public TypeSpec amPm;
    public TypeSpec date;
    public TypeSpec greeting;
    public TypeSpec hero;
    public TypeSpec pill;
    public TypeSpec tileName;
    public TypeSpec tileLabel;
    public TypeSpec inputRowTitle;      // an input's name in the Inputs menu
    public TypeSpec inputRowStatus;     // its Connected / No device state

    /**
     * A shallow copy. Every field is a primitive or an immutable {@link TypeSpec}, so the
     * typography specs are shared rather than cloned.
     */
    public Tokens copy() {
        Tokens t = new Tokens();
        t.background = background;
        t.tileSurface = tileSurface;
        t.dockBand = dockBand;
        t.dockBandEdge = dockBandEdge;
        t.pillFillTop = pillFillTop;
        t.pillFillBottom = pillFillBottom;
        t.pillStroke = pillStroke;
        t.pillFocusFill = pillFocusFill;
        t.inputRowFocus = inputRowFocus;
        t.textHero = textHero;
        t.textClock = textClock;
        t.textAmPm = textAmPm;
        t.textDate = textDate;
        t.textGreeting = textGreeting;
        t.clockDivider = clockDivider;
        t.textTileLabel = textTileLabel;
        t.textTileName = textTileName;
        t.textPill = textPill;
        t.textPillDim = textPillDim;
        t.textPillFocus = textPillFocus;
        t.textPillFocusDim = textPillFocusDim;
        t.glassFillTop = glassFillTop;
        t.glassFillBottom = glassFillBottom;
        t.glassSpecular = glassSpecular;
        t.glassRim = glassRim;
        t.glassGlow = glassGlow;
        t.statusDot = statusDot;
        t.separator = separator;
        t.panelFillTop = panelFillTop;
        t.panelFillBottom = panelFillBottom;
        t.panelStroke = panelStroke;
        t.pickerScrim = pickerScrim;
        t.ambientCore = ambientCore;
        t.ambientEdge = ambientEdge;
        t.ambientFallback = ambientFallback;
        t.ambientSaturation = ambientSaturation;
        t.ambientValueFloor = ambientValueFloor;
        t.tileRadius = tileRadius;
        t.dockBandRadius = dockBandRadius;
        t.pillRadius = pillRadius;
        t.pillHeight = pillHeight;
        t.pillStrokeWidth = pillStrokeWidth;
        t.glassRadius = glassRadius;
        t.glassStroke = glassStroke;
        t.glassInset = glassInset;
        t.panelRadius = panelRadius;
        t.inputRowRadius = inputRowRadius;
        t.springStiffness = springStiffness;
        t.springDamping = springDamping;
        t.focusScale = focusScale;
        t.focusOvershoot = focusOvershoot;
        t.entranceStagger = entranceStagger;
        t.focusInMs = focusInMs;
        t.focusOutMs = focusOutMs;
        t.glassInMs = glassInMs;
        t.glassOutMs = glassOutMs;
        t.heroInMs = heroInMs;
        t.heroOutMs = heroOutMs;
        t.heroSwapOutMs = heroSwapOutMs;
        t.heroSwapInMs = heroSwapInMs;
        t.ambientInMs = ambientInMs;
        t.ambientOutMs = ambientOutMs;
        t.ambientSwapOutMs = ambientSwapOutMs;
        t.ambientSwapInMs = ambientSwapInMs;
        t.tileNameMs = tileNameMs;
        t.entranceMs = entranceMs;
        t.clock = clock;
        t.amPm = amPm;
        t.date = date;
        t.greeting = greeting;
        t.hero = hero;
        t.pill = pill;
        t.tileName = tileName;
        t.tileLabel = tileLabel;
        t.inputRowTitle = inputRowTitle;
        t.inputRowStatus = inputRowStatus;
        return t;
    }

    /**
     * One text style: a size, a variable-font weight and a letter spacing. The weight picks one of
     * the pre-built Outfit families (200, 300, 400, 500; the nearest wins), so weights stay real
     * weights rather than faux bold.
     */
    public static final class TypeSpec {
        public final float sizeSp;
        public final int weight;
        public final float tracking;

        public TypeSpec(float sizeSp, int weight, float tracking) {
            this.sizeSp = sizeSp;
            this.weight = weight;
            this.tracking = tracking;
        }

        public TypeSpec copy() {
            return new TypeSpec(sizeSp, weight, tracking);
        }
    }
}
