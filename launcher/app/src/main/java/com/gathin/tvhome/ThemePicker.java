package com.gathin.tvhome;

import android.app.Activity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gathin.tvhome.theme.Skin;
import com.gathin.tvhome.theme.Theme;
import com.gathin.tvhome.theme.ThemeCatalog;
import com.gathin.tvhome.theme.ThemeStore;
import com.gathin.tvhome.theme.Tokens;
import com.gathin.tvhome.theme.VariantResolver;

import java.util.ArrayList;
import java.util.List;

/**
 * The theme picker: an overlay inside the home screen, not an activity, so the screen behind it keeps
 * running and can be re-themed live. Moving focus down the list previews the row under it; OK saves
 * it; Back restores what was on screen when the picker opened.
 *
 * <p>The first row is "System", which keeps the saved theme but follows the TV's day/night setting.
 * Every other row pins the variant that was on screen when the picker opened (see {@link ThemeStore}).
 */
final class ThemePicker {

    /** What the picker needs from the screen it sits on. */
    interface Host {
        /** Repaints the whole screen with these tokens. Used for preview, commit and revert alike. */
        void applyTokens(Tokens t);

        boolean isNight();

        /** The picker has gone, by a commit or by Back. The host gives the D-pad back to the screen. */
        void onPickerClosed();
    }

    private final Activity activity;
    private final FrameLayout root;
    private final Skin skin;
    private final ThemeCatalog catalog;
    private final ThemeStore store;
    private final Host host;

    private View overlay;
    private LinearLayout panel;
    /** One entry per row: a theme id, or null for the System row. */
    private final List<String> ids = new ArrayList<>();
    private Tokens openedWith;       // restored on Back
    private String variant;          // the variant on screen at open; an explicit choice pins it
    private String committedId;
    private boolean committedFollowsSystem;

    ThemePicker(Activity activity, FrameLayout root, Skin skin, ThemeCatalog catalog, ThemeStore store,
                Host host) {
        this.activity = activity;
        this.root = root;
        this.skin = skin;
        this.catalog = catalog;
        this.store = store;
        this.host = host;
    }

    boolean isOpen() {
        return overlay != null;
    }

    void open() {
        if (isOpen()) return;
        openedWith = store.current(host.isNight());
        committedId = store.themeId();
        committedFollowsSystem = store.followSystem();
        variant = VariantResolver.resolve(committedFollowsSystem, store.pinnedVariant(), host.isNight());

        overlay = LayoutInflater.from(activity).inflate(R.layout.picker_themes, root, false);
        panel = overlay.findViewById(R.id.panel);

        ids.clear();
        ids.add(null);
        for (Theme t : catalog.all()) ids.add(t.id);
        for (int i = 0; i < ids.size(); i++) panel.addView(newRow(i));

        root.addView(overlay);
        restyle();
        panel.getChildAt(committedIndex()).requestFocus();
    }

    /** Back: undo any preview and close. */
    void cancel() {
        if (!isOpen()) return;
        host.applyTokens(openedWith);
        close();
    }

    private View newRow(int index) {
        final String id = ids.get(index);
        View row = LayoutInflater.from(activity).inflate(R.layout.item_input, panel, false);
        String label = id == null ? activity.getString(R.string.theme_system) : catalog.byId(id).label;
        ((TextView) row.findViewById(R.id.title)).setText(label);

        row.setOnFocusChangeListener((v, focused) -> {
            if (!focused) return;
            host.applyTokens(tokensFor(id));
            restyle();
        });
        row.setOnClickListener(v -> commit(id));
        // The D-pad must not leave the panel, so every edge is swallowed here rather than left to
        // Android's focus search, which would jump to the pills or to a tile behind the panel.
        final boolean first = index == 0, last = index == ids.size() - 1;
        row.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    return first;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    return last;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    return true;
                default:
                    return false;
            }
        });
        return row;
    }

    /** What the screen would look like if this row were chosen. */
    private Tokens tokensFor(String id) {
        return id == null
                ? store.tokensFor(committedId, true, null, host.isNight())
                : store.tokensFor(id, false, variant, host.isNight());
    }

    private void commit(String id) {
        if (id == null) store.select(committedId, true, null);
        else store.select(id, false, variant);
        host.applyTokens(tokensFor(id));
        close();
    }

    private void close() {
        root.removeView(overlay);
        overlay = null;
        panel = null;
        host.onPickerClosed();
    }

    /** The row that is the saved choice: System if following the system, else the saved theme. */
    private int committedIndex() {
        if (committedFollowsSystem) return 0;
        int i = ids.indexOf(committedId);
        return i < 0 ? 0 : i;
    }

    /** Repaints the picker in the theme now on screen, since the preview changes it under the user. */
    private void restyle() {
        overlay.findViewById(R.id.scrim).setBackgroundColor(skin.tokens().pickerScrim);
        panel.setBackground(skin.panelBackground());
        for (int i = 0; i < panel.getChildCount(); i++) {
            skin.applyPickerRow(panel.getChildAt(i), i == committedIndex());
        }
    }
}
