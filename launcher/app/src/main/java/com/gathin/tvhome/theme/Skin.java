package com.gathin.tvhome.theme;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
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

    private Tokens tokens = Defaults.tokens();

    private Drawable pillBackground;
    private ColorStateList pillText;
    private ColorStateList pillTextDim;
    private Drawable inputRowBackground;
    private Drawable tileBackground;
    private Drawable panelBackground;
    private Drawable statusDot;

    private static final int[] FOCUSED = new int[]{android.R.attr.state_focused};
    private static final int[] NOT_FOCUSED = new int[]{};

    public Skin(Context ctx) {
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
        GradientDrawable pillRest = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{tokens.pillFillTop, tokens.pillFillBottom});
        pillRest.setCornerRadius(pillRadius);
        pillRest.setStroke(Math.max(1, Math.round(dp(1f))), tokens.pillStroke);
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

        View divider = root.findViewById(R.id.clockDivider);
        if (divider != null) divider.setBackgroundColor(tokens.clockDivider);

        for (int id : new int[]{R.id.btnInputs, R.id.btnWifi, R.id.btnSettings}) {
            applyPill(root.findViewById(id));
        }
    }

    private void applyPill(TextView pill) {
        if (pill == null) return;
        pill.setBackground(pillBackground);
        pill.setTextColor(pillText);
        applyType(pill, tokens.pill);
        pill.setCompoundDrawableTintList(pillText);
        ViewGroup.LayoutParams lp = pill.getLayoutParams();
        int height = Math.round(dp(tokens.pillHeight));
        if (lp.height != height) {
            lp.height = height;
            pill.setLayoutParams(lp);
        }
    }

    /** One tile: the card, the label over a banner-less icon, and the name underneath. */
    public void applyTile(View itemView) {
        View card = itemView.findViewById(R.id.card);
        if (card != null) {
            card.setBackground(tileBackground);
            card.setClipToOutline(true);
        }
        text(itemView, R.id.label, tokens.textTileLabel, tokens.tileLabel);
        text(itemView, R.id.name, tokens.textTileName, tokens.tileName);
    }

    /** One row of the Inputs menu. Focus follows from the parent via duplicateParentState in XML. */
    public void applyInputRow(View row) {
        row.setBackground(inputRowBackground);
        TextView title = row.findViewById(R.id.title);
        title.setTextColor(pillText);
        applyType(title, tokens.pill);
        ImageView icon = row.findViewById(R.id.icon);
        if (icon != null) icon.setImageTintList(pillText);
        TextView status = row.findViewById(R.id.status);
        status.setTextColor(pillTextDim);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, tokens.pill.sizeSp * 0.75f);
        View dot = row.findViewById(R.id.dot);
        if (dot != null) dot.setBackground(statusDot);
    }

    // ---- pieces other classes ask for -------------------------------------------------------------

    public ColorStateList pillText() {
        return pillText;
    }

    /** The frosted panel behind the Inputs menu and the theme picker. */
    public Drawable panelBackground() {
        return panelBackground;
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
    public void applyType(TextView v, Tokens.TypeSpec spec) {
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, spec.sizeSp);
        v.setFontVariationSettings("'wght' " + spec.weight);
        v.setLetterSpacing(spec.tracking);
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
