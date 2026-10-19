package com.gathin.tvhome.theme;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Remembers which theme the user picked.
 *
 * <p>Three keys, and the distinction between the first two is the whole point:
 * {@code theme_id} says <i>which</i> theme, {@code follow_system} says whether it should track the
 * TV's day/night setting or stay on the variant that was on screen when it was chosen. Encoding
 * "system" as a magic theme id would make those two ideas impossible to tell apart.
 *
 * <p>A fresh install is {@link ThemeCatalog#DEFAULT_ID} following the system, which is exactly how
 * the launcher behaved before themes existed.
 *
 * <p>Reads are validated against the catalog, so a preference left behind by an uninstalled theme,
 * or corrupted by any means, resolves to the default rather than to nothing.
 *
 * <p><b>Not unit tested, deliberately.</b> This is {@link SharedPreferences} plumbing around
 * {@link VariantResolver}, which holds the rules worth testing. Putting an interface in front of it
 * would be more code than the logic it guards.
 */
public final class ThemeStore {

    private static final String PREFS = "tvhome.prefs";
    private static final String KEY_THEME = "theme_id";
    private static final String KEY_FOLLOW_SYSTEM = "follow_system";
    private static final String KEY_PINNED_VARIANT = "pinned_variant";

    private final SharedPreferences prefs;
    private final ThemeCatalog catalog;

    public ThemeStore(Context ctx, ThemeCatalog catalog) {
        this.catalog = catalog;
        this.prefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The chosen theme's id, falling back to the default if it is no longer installed. */
    public String themeId() {
        String id = prefs.getString(KEY_THEME, ThemeCatalog.DEFAULT_ID);
        return catalog.byId(id) != null ? id : ThemeCatalog.DEFAULT_ID;
    }

    public boolean followSystem() {
        return prefs.getBoolean(KEY_FOLLOW_SYSTEM, true);
    }

    /** The variant an explicit choice pinned, or null when following the system. */
    public String pinnedVariant() {
        return prefs.getString(KEY_PINNED_VARIANT, null);
    }

    public void select(String themeId, boolean followSystem, String pinnedVariant) {
        prefs.edit()
                .putString(KEY_THEME, catalog.byId(themeId) != null ? themeId : ThemeCatalog.DEFAULT_ID)
                .putBoolean(KEY_FOLLOW_SYSTEM, followSystem)
                .putString(KEY_PINNED_VARIANT, pinnedVariant)
                .apply();
    }

    /** The tokens to draw with right now. */
    public Tokens current(boolean isNight) {
        return tokensFor(themeId(), followSystem(), pinnedVariant(), isNight);
    }

    /** The tokens a given choice would produce, used to preview without committing. */
    public Tokens tokensFor(String themeId, boolean followSystem, String pinnedVariant, boolean isNight) {
        String variant = VariantResolver.resolve(followSystem, pinnedVariant, isNight);
        Theme theme = catalog.byId(themeId);
        if (theme == null) theme = catalog.byId(ThemeCatalog.DEFAULT_ID);
        return theme == null ? Defaults.tokens() : theme.forVariant(variant);
    }
}
