package com.gathin.tvhome.theme;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The themes installed on this TV, read from {@code assets/themes/*.xml}.
 *
 * <p>Parsed once and cached: the files are a few kilobytes each and the picker needs every label,
 * so there is nothing to gain from laziness, and a broken file must not be re-read on every focus
 * change while previewing.
 *
 * <p>Never returns empty. If no file parses, or if the reference theme is missing from assets, a
 * theme built from {@link Defaults} is prepended — the launcher has to be able to show a picker
 * even when its theme files are all broken.
 */
public final class ThemeCatalog {

    private static final String TAG = "ThemeCatalog";
    private static final String DIR = "themes";

    /** The theme every install starts on, and the one users are told to copy. */
    public static final String DEFAULT_ID = "tvos-black";

    private final Context ctx;
    private List<Theme> themes;

    public ThemeCatalog(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    /** Every installed theme, sorted by label, always at least one entry. */
    public synchronized List<Theme> all() {
        if (themes != null) return themes;

        List<Theme> found = new ArrayList<>();
        String[] names = null;
        try {
            names = ctx.getAssets().list(DIR);
        } catch (IOException e) {
            Log.w(TAG, "cannot list assets/" + DIR, e);
        }
        if (names != null) {
            for (String name : names) {
                if (!name.endsWith(".xml")) continue;
                Theme t = read(name);
                if (t != null) found.add(t);
                else Log.w(TAG, "skipping unreadable theme " + name);
            }
        }
        Collections.sort(found, new Comparator<Theme>() {
            @Override
            public int compare(Theme a, Theme b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });

        if (byIdIn(found, DEFAULT_ID) == null) {
            found.add(0, fallbackTheme());
            Log.i(TAG, "no usable '" + DEFAULT_ID + "' in assets; offering the built-in defaults");
        }
        themes = Collections.unmodifiableList(found);
        return themes;
    }

    /** The theme with this id, or null if it is not installed. */
    public Theme byId(String id) {
        return id == null ? null : byIdIn(all(), id);
    }

    private Theme read(String name) {
        try (InputStream in = ctx.getAssets().open(DIR + "/" + name)) {
            return ThemeParser.parse(in, name, msg -> Log.w(TAG, name + ": " + msg));
        } catch (IOException e) {
            Log.w(TAG, "cannot open theme " + name, e);
            return null;
        }
    }

    private static Theme byIdIn(List<Theme> list, String id) {
        for (Theme t : list) {
            if (t.id.equals(id)) return t;
        }
        return null;
    }

    /** All-defaults theme, both variants. Reachable by hand if the real files are all broken. */
    private static Theme fallbackTheme() {
        return new Theme(DEFAULT_ID, "tvOS Black", Defaults.tokens(), Defaults.tokens());
    }
}
