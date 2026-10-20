package com.gathin.tvhome.theme;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.gathin.tvhome.R;

/**
 * Turns a {@link Tokens} into pixels. This is the only class that builds a themed drawable.
 *
 * <p>Every colour that used to live in {@code res/values} and {@code res/drawable} is built here
 * from tokens, which is what makes one place the source of truth for the launcher's look.
 *
 * <p>Three appliers, one per family of view, because a single walk of the view tree is not enough:
 * {@link android.widget.RecyclerView} recycles tiles, so a tile bound before a retheme would keep
 * its old colours. Anything that creates a view calls the matching applier.
 *
 * <p>Depends on resource ids, but on no launcher class — the theme package does not know what a
 * tile or a dock is, only what colour it should be.
 *
 * <p>Typeface is not themed: Outfit stays in the layouts. Only one font is bundled, so a font
 * token would have exactly one legal value.
 */
public final class Skin {

    private final float density;
    private final Context ctx;
    /**
     * One Typeface per weight, built once. Setting fontVariationSettings on each view rebuilds the
     * variable font every time (about 10 ms per view on a TV CPU), which stalls tile binds.
     */
    private final SparseArray<Typeface> typefaces = new SparseArray<>();
    /** res/font families, each a pre-built weight of Outfit; applyType snaps to the nearest. */
    private static final int[] WEIGHTS = {200, 300, 400, 500};
    private static final int[] WEIGHT_FONTS = {
            R.font.outfit_extralight, R.font.outfit_light, R.font.outfit_regular, R.font.outfit_medium};

    private Tokens tokens = Defaults.tokens();

    private Drawable pillBackground;
    private ColorStateList pillText;
    private ColorStateList pillTextDim;
    private Drawable inputRowBackground;
    private Drawable tileBackground;
    private Drawable panelBackground;
    private Drawable statusDot;

    private static final int[] FOCUSED = new int[]{android.R.attr.state_focused};
    private static final int[] ACTIVATED = new int[]{android.R.attr.state_activated};
    /** Reorder button while reorder mode is on: not themed, it matches MainActivity.REORDER_ACCENT. */
    private static final int REORDER_FILL = 0x596EC1FF, REORDER_STROKE = 0xFF6EC1FF;
    private static final int[] NOT_FOCUSED = new int[]{};

    public Skin(Context ctx) {
        this.ctx = ctx;
        density = ctx.getResources().getDisplayMetrics().density;
        build();
    }

    /** The tokens this skin is currently drawing with. */
    public Tokens tokens() {
        return tokens;
    }

    /** Rebuilds every shared drawable. Call once per theme change, not per frame. */
    public Skin retheme(Tokens t) {
        tokens = t;
        build();
        return this;
    }

    private void build() {
        float pillRadius = dp(tokens.pillRadius);
        float rowRadius = dp(tokens.inputRowRadius);

        StateListDrawable pill = new StateListDrawable();
        pill.addState(FOCUSED, solid(tokens.pillFocusFill, pillRadius));
        GradientDrawable pillActive = new GradientDrawable();
        pillActive.setColor(REORDER_FILL);
        pillActive.setCornerRadius(pillRadius);
        pillActive.setStroke(Math.max(1, Math.round(dp(1.5f))), REORDER_STROKE);
        pill.addState(ACTIVATED, pillActive);
        GradientDrawable pillRest = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{tokens.pillFillTop, tokens.pillFillBottom});
        pillRest.setCornerRadius(pillRadius);
        pillRest.setStroke(Math.max(1, Math.round(dp(tokens.pillStrokeWidth))), tokens.pillStroke);
        pill.addState(NOT_FOCUSED, pillRest);
        pillBackground = pill;

        pillText = states(tokens.textPillFocus, tokens.textPill);
        pillTextDim = states(tokens.textPillFocusDim, tokens.textPillDim);

        StateListDrawable row = new StateListDrawable();
        row.addState(FOCUSED, solid(tokens.inputRowFocus, rowRadius));
        row.addState(NOT_FOCUSED, new ColorDrawable(Color.TRANSPARENT));
        inputRowBackground = row;

        tileBackground = solid(tokens.tileSurface, dp(tokens.tileRadius));

        GradientDrawable panel = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{tokens.panelFillTop, tokens.panelFillBottom});
        panel.setCornerRadius(dp(tokens.panelRadius));
        panel.setStroke(Math.max(1, Math.round(dp(1f))), tokens.panelStroke);
        panelBackground = panel;

        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(tokens.statusDot);
        statusDot = dot;
    }

    // ---- appliers --------------------------------------------------------------------------------

    /** The window: background, clock block, hero name and the three pills. */
    public void applyScreen(Activity activity, View root) {
        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        ColorDrawable windowBackground = new ColorDrawable(tokens.background);
        activity.getWindow().setBackgroundDrawable(windowBackground);
        decor.setBackground(windowBackground);
        root.setBackground(windowBackground);

        text(root, R.id.heroName, tokens.textHero, tokens.hero);
        text(root, R.id.greeting, tokens.textGreeting, tokens.greeting);
        text(root, R.id.clock, tokens.textClock, tokens.clock);
        text(root, R.id.ampm, tokens.textAmPm, tokens.amPm);
        text(root, R.id.date, tokens.textDate, tokens.date);
        text(root, R.id.shamsiDate, tokens.textDate, tokens.date);
        text(root, R.id.stats, tokens.textDate, tokens.date);

        View divider = root.findViewById(R.id.clockDivider);
        if (divider != null) divider.setBackgroundColor(tokens.clockDivider);

        for (int id : new int[]{R.id.btnInputs, R.id.btnWifi, R.id.btnReorder, R.id.btnSettings}) {
            applyPill(root.findViewById(id));
        }
    }

    public void applyPill(TextView pill) {
        if (pill == null) return;
        pill.setBackground(own(pillBackground));
        pill.setTextColor(pillText);
        applyType(pill, tokens.pill);
        pill.setCompoundDrawableTintList(pillText);
        ViewGroup.LayoutParams lp = pill.getLayoutParams();
        int height = Math.round(dp(tokens.pillHeight));
        if (lp.height != height) {
            lp.height = height;
            pill.setLayoutParams(lp);
        }
        // An icon-only pill (TopButtonIcon) has a fixed width: keep it a circle at the themed height.
        if (lp.width > 0 && lp.width != height) {
            lp.width = height;
            pill.setLayoutParams(lp);
        }
    }

    /** One tile: the card, the label over a banner-less icon, and the name underneath. */
    public void applyTile(View itemView) {
        View card = itemView.findViewById(R.id.card);
        if (card != null) {
            card.setBackground(own(tileBackground));
            card.setClipToOutline(true);
        }
        text(itemView, R.id.label, tokens.textTileLabel, tokens.tileLabel);
        text(itemView, R.id.name, tokens.textTileName, tokens.tileName);
    }

    /** One row of the Inputs menu. Focus follows from the parent via duplicateParentState in XML. */
    /** A Skin from the stored theme, for screens that run outside MainActivity (quick settings). */
    public static Skin forContext(Context ctx) {
        boolean night = (ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        ThemeStore store = new ThemeStore(ctx, new ThemeCatalog(ctx));
        return new Skin(ctx).retheme(store.current(night));
    }

    /** The theme's text colour at a given alpha (0-255): secondary text and hairlines that read on any variant. */
    public int ink(int alpha) {
        return (tokens.textPill & 0x00FFFFFF) | (alpha << 24);
    }

    /** Module cards prompt: panel, text and the two buttons. */
    public void applyCardPrompt(View panel) {
        panel.setBackground(panelBackground());
        text(panel, R.id.title, tokens.textPill, new Tokens.TypeSpec(22f, 500, -0.01f));
        text(panel, R.id.body, ink(0xD9), new Tokens.TypeSpec(15f, 400, 0f));
        text(panel, R.id.names, ink(0xD9), new Tokens.TypeSpec(15f, 400, 0f));
        text(panel, R.id.hint, ink(0x99), new Tokens.TypeSpec(13f, 400, 0f));
        text(panel, R.id.identity, ink(0x80), new Tokens.TypeSpec(12f, 400, 0.03f));
        applyPill(panel.findViewById(R.id.btnKeep));
        applyPill(panel.findViewById(R.id.btnShow));
    }

    /** Module cards list: panel and its heading. */
    public void applyCardsPanel(View panel) {
        panel.setBackground(panelBackground());
        text(panel, R.id.heading, tokens.textPillDim, new Tokens.TypeSpec(11f, 500, 0.18f));
    }

    /** One row of the module cards list. */
    public void applyCardRow(View row) {
        row.setBackground(own(inputRowBackground));
        statesText(row, R.id.title, pillText, tokens.inputRowTitle);
        statesText(row, R.id.state, pillText, tokens.inputRowStatus);
        statesText(row, R.id.app, pillTextDim, tokens.inputRowStatus);
        statesText(row, R.id.pkg, pillTextDim, tokens.inputRowStatus);
        statesText(row, R.id.digest, pillTextDim, tokens.inputRowStatus);
    }

    private void statesText(View parent, int id, ColorStateList colours, Tokens.TypeSpec type) {
        TextView v = parent.findViewById(id);
        if (v == null) return;
        v.setTextColor(colours);
        applyType(v, type);
    }

    public void applyInputRow(View row) {
        row.setBackground(own(inputRowBackground));
        TextView title = row.findViewById(R.id.title);
        title.setTextColor(pillText);
        applyType(title, tokens.inputRowTitle);
        ImageView icon = row.findViewById(R.id.icon);
        if (icon != null) icon.setImageTintList(pillText);
        TextView status = row.findViewById(R.id.status);
        status.setTextColor(pillTextDim);
        applyType(status, tokens.inputRowStatus);
        View dot = row.findViewById(R.id.dot);
        if (dot != null) dot.setBackground(own(statusDot));
    }

    /**
     * One row of the theme picker: the Inputs row's look, with the committed choice marked by a small
     * square. A drawn square rather than a check glyph, because Outfit may not carry the codepoint and
     * a missing glyph on a TV is a tofu box.
     *
     * @param row       an inflated {@code item_input} row
     * @param committed whether this row is the choice currently saved
     */
    public void applyPickerRow(View row, boolean committed) {
        applyInputRow(row);
        row.findViewById(R.id.icon).setVisibility(View.GONE);
        row.findViewById(R.id.status).setVisibility(View.GONE);
        View mark = row.findViewById(R.id.dot);
        GradientDrawable square = new GradientDrawable();
        square.setColor(0xFFFFFFFF);                 // only a shape: the tint supplies the colour, following the row's focus state
        square.setCornerRadius(dp(2f));
        mark.setBackground(square);
        mark.setBackgroundTintList(pillText);
        mark.setVisibility(committed ? View.VISIBLE : View.INVISIBLE);
    }

    // ---- pieces other classes ask for -------------------------------------------------------------

    public ColorStateList pillText() {
        return pillText;
    }

    /** The frosted panel behind the Inputs menu and the theme picker. */
    /** A private copy: two panels can be on screen at once (a menu over the library), and views must not share bounds. */
    public Drawable panelBackground() {
        return own(panelBackground);
    }

    public int background() {
        return tokens.background;
    }

    public int separator() {
        return tokens.separator;
    }

    public int ambientCore() {
        return tokens.ambientCore;
    }

    public int ambientEdge() {
        return tokens.ambientEdge;
    }

    public int ambientFallback() {
        return tokens.ambientFallback;
    }

    public float dp(float value) {
        return value * density;
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private void text(View parent, int id, int colour, Tokens.TypeSpec type) {
        TextView v = parent.findViewById(id);
        if (v == null) return;
        v.setTextColor(colour);
        applyType(v, type);
    }

    /**
     * Size, weight and tracking. The weight goes through {@code fontVariationSettings} so the
     * variable font renders a real weight rather than a synthesised bold.
     */
    /**
     * The typeface for a weight. Only the weights in WEIGHTS exist as font families, so a theme asking
     * for another (600, 700) gets the nearest one. Add a res/font family and an entry above to widen it.
     */
    private Typeface typeface(int weight) {
        int best = 0;
        for (int i = 1; i < WEIGHTS.length; i++) {
            if (Math.abs(WEIGHTS[i] - weight) < Math.abs(WEIGHTS[best] - weight)) best = i;
        }
        Typeface t = typefaces.get(WEIGHTS[best]);
        if (t == null) {
            t = ctx.getResources().getFont(WEIGHT_FONTS[best]);
            typefaces.put(WEIGHTS[best], t);
        }
        return t;
    }

    public void applyType(TextView v, Tokens.TypeSpec spec) {
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, spec.sizeSp);
        v.setTypeface(typeface(spec.weight));
        v.setLetterSpacing(spec.tracking);
    }

    /**
     * A private copy of a shared drawable. A Drawable holds one set of bounds and one state, so
     * handing the same instance to several views makes them overwrite each other's size and focus.
     */
    private static Drawable own(Drawable shared) {
        return shared.getConstantState().newDrawable().mutate();
    }

    private static Drawable solid(int colour, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(colour);
        d.setCornerRadius(radius);
        return d;
    }

    private static ColorStateList states(int focused, int normal) {
        return new ColorStateList(new int[][]{FOCUSED, NOT_FOCUSED}, new int[]{focused, normal});
    }
}
