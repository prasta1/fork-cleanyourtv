package com.gathin.tvhome.theme;

/**
 * One theme file, with both variants already resolved over {@link Defaults}.
 *
 * <p>Consumers never see a missing variant or a default that was never applied; by the time a
 * {@code Theme} exists, {@link #dark} and {@link #light} are complete token sets.
 */
public final class Theme {

    public final String id;
    public final String label;
    public final Tokens dark;
    public final Tokens light;

    public Theme(String id, String label, Tokens dark, Tokens light) {
        this.id = id;
        this.label = label;
        this.dark = dark;
        this.light = light;
    }

    /** The tokens for a variant name, falling back to dark for anything unrecognised. */
    public Tokens forVariant(String variant) {
        return "light".equals(variant) ? light : dark;
    }
}
