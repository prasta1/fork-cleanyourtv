package com.gathin.tvhome.theme;

/**
 * Decides which variant of a theme is on screen.
 *
 * <p>Exists as its own class, with no Android imports, so the rules can be tested directly. The
 * distinction matters:
 *
 * <ul>
 *   <li><b>System</b> ({@code followSystem}) follows the TV's day/night setting, so the launcher
 *       changes with the rest of the system.
 *   <li><b>An explicit choice</b> pins the variant that was on screen when it was made. Choosing a
 *       theme while the TV is dark leaves it dark after the system switches to light — which is
 *       what makes "System" a real choice rather than a duplicate of picking any theme.
 * </ul>
 *
 * <p>A pinned variant that is missing or unrecognised falls back to following the system rather
 * than to a guess, so a corrupted preference degrades to sensible behaviour.
 */
public final class VariantResolver {

    public static final String DARK = "dark";
    public static final String LIGHT = "light";

    private VariantResolver() {
    }

    public static String resolve(boolean followSystem, String pinnedVariant, boolean isNight) {
        if (followSystem) return forNight(isNight);
        if (DARK.equals(pinnedVariant)) return DARK;
        if (LIGHT.equals(pinnedVariant)) return LIGHT;
        return forNight(isNight);
    }

    public static String forNight(boolean isNight) {
        return isNight ? DARK : LIGHT;
    }
}
