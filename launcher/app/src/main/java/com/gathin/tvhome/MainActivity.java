package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LayoutAnimationController;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * tvOS-style home for a leanback set: a pinned dock, an app grid above it, and one glass highlight
 * that travels between tiles.
 *
 * <p>The screen has exactly three focus zones stacked top to bottom — pills, grid, dock — and every
 * directional key moves one step within them or crosses to the nearest neighbour. Nothing is left
 * to the framework's spatial focus search, because that is what put focus on the right-hand pills
 * when you pressed Up from a tile on the far left.
 */
public class MainActivity extends Activity implements AppAdapter.TileListener {


    /** Tiles per row in the grid (res/values/config.xml, columns). The dock holds dock_apps. */
    static int COLUMNS = 4;
    private static final String PREFS = "tvhome";
    private static final String KEY_LIGHT = "light_mode";
    /** Set across the theme-switch recreate so the Settings menu comes straight back. */
    private static boolean reopenSettings;

    private static final long MINUTE_MS = 60_000L;
    private static final long GLASS_MS = 170L;
    private String liveTvPkg;

    private AppAdapter dockAdapter;
    private AppAdapter gridAdapter;
    private RecyclerView grid;
    private RecyclerView dock;
    private FrameLayout root;
    private GlassView glass;
    private View ambient;
    private View gridFadeTop;
    private View gridFadeBottom;
    private View scrimLeft;
    private View scrimBottom;
    private TextView heroName;
    private GradientDrawable ambientGlow;
    private int ambientColor = 0;
    private boolean heroShown;
    private boolean gridShown;
    private int lastDockPos;
    private int lastGridPos;
    private int lastHeroMode = -1;
    private int lastHeroPos = -1;
    private float density;

    private ValueAnimator glassAnim;
    private float glassTargetX;
    private float glassTargetY;
    private boolean glassShown;
    /** The tile the highlight belongs to, so a relayout can put it back where it should be. */
    private View focusedCard;

    private final View[] pills = new View[3];

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            updateGreeting();
            updateNetStatus();
            handler.postDelayed(this, MINUTE_MS);
        }
    };

    /**
     * Pins the night/notnight resource qualifier to the user's saved choice (ignoring the system's),
     * so values/ (dark) or values-notnight/ (light) colours are picked for the whole activity.
     */
    @Override
    protected void attachBaseContext(Context base) {
        boolean light = base.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_LIGHT, false);
        Configuration c = new Configuration(base.getResources().getConfiguration());
        c.uiMode = (c.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (light ? Configuration.UI_MODE_NIGHT_NO : Configuration.UI_MODE_NIGHT_YES);
        super.attachBaseContext(base.createConfigurationContext(c));
    }

    /** The theme's background colour at the given alpha (0-255), for scrims and fades. */
    private int bg(int alpha) {
        return getColor(R.color.bg) & 0x00FFFFFF | alpha << 24;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        density = getResources().getDisplayMetrics().density;
        COLUMNS = Math.max(1, getResources().getInteger(R.integer.columns));
        liveTvPkg = getString(R.string.live_tv_package).trim();

        root = findViewById(R.id.root);
        glass = findViewById(R.id.glass);
        ambient = findViewById(R.id.ambient);
        scrimLeft = findViewById(R.id.scrimLeft);
        scrimBottom = findViewById(R.id.scrimBottom);
        heroName = findViewById(R.id.heroName);

        // Scrims: a horizontal one so type stays legible over artwork, a vertical one so the dock is
        // seated on black instead of floating on top of a photograph.
        scrimLeft.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                scrimLeft.setBackground(new GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        new int[]{bg(0xC6), bg(0x73), bg(0x1A), bg(0x00)})));
        scrimBottom.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                scrimBottom.setBackground(new GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[]{bg(0x00), bg(0x73), bg(0xD9)})));

        // Edge fades for the grid, so a half-scrolled row dissolves instead of being sliced. Kept to a
// single smooth ramp: a band of flat black reads as a masked bar drawn over the content.
        gridFadeTop = findViewById(R.id.gridFadeTop);
        gridFadeBottom = findViewById(R.id.gridFadeBottom);
        gridFadeTop.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{bg(0xE6), bg(0x73), bg(0x00)}));
        gridFadeBottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{bg(0xE6), bg(0x73), bg(0x00)}));

        ambientGlow = new GradientDrawable();
        ambientGlow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ambientGlow.setGradientCenter(0.30f, 0.34f);
        ambient.setBackground(ambientGlow);
        ambient.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                ambientGlow.setGradientRadius((r - l) * 0.44f));

        grid = findViewById(R.id.grid);
        grid.setLayoutManager(new GridLayoutManager(this, COLUMNS));
        grid.setHasFixedSize(true);
        grid.setItemAnimator(null);
        grid.setPadding(0, (int) (6 * density), 0, (int) (14 * density));
        grid.setLayoutAnimation(new LayoutAnimationController(
                AnimationUtils.loadAnimation(this, R.anim.tile_enter), 0.06f));
        gridAdapter = new AppAdapter(this, this, AppAdapter.MODE_GRID);
        grid.setAdapter(gridAdapter);

        dock = findViewById(R.id.dock);
        dockAdapter = new AppAdapter(this, this, AppAdapter.MODE_DOCK);
        setDockLayoutManager();
        dock.setAdapter(dockAdapter);

        // Re-centre the dock when its width settles.
        dock.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> centerDock());

        // The grid starts folded away. It cannot take focus until it is opened.
        grid.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

        RecyclerView.OnScrollListener glue = new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                nudgeGlass(dy);
            }
        };
        grid.addOnScrollListener(glue);
        dock.addOnScrollListener(glue);

        TextView greeting = findViewById(R.id.greeting);
        updateGreeting();

        pills[0] = findViewById(R.id.btnInputs);
        pills[1] = findViewById(R.id.btnWifi);
        pills[2] = findViewById(R.id.btnSettings);
        pills[0].setOnClickListener(v -> showInputs());
        pills[1].setOnClickListener(v -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        pills[2].setOnClickListener(v -> showSettings());
        if (reopenSettings) {
            reopenSettings = false;
            root.post(() -> {
                pills[2].requestFocus();
                showSettings();
            });
        }

        // Re-place the highlight immediately before every draw while it is visible.
        //
        // The tile does not sit still: the dock's centring padding, the RecyclerView's layout, the
        // tile's own scale animation and any scroll all move it, each at a different moment. Placing
        // the glass once on focus and then catching up with post() callbacks is a race — the measured
        // offset changed between builds (11px, 14px, 20px) because the correction kept landing
        // between moves rather than after them. Doing it per frame costs one translation and cannot
        // fall behind.
        getWindow().getDecorView().getViewTreeObserver().addOnPreDrawListener(() -> {
            if (glassShown && focusedCard != null && focusedCard.isFocused()) {
                placeGlass(focusedCard);
                glass.setTranslationX(glassTargetX);
                glass.setTranslationY(glassTargetY);
            }
            return true;
        });

        // Every pill handles every direction itself, so the framework never runs a spatial search
        // that could land focus on the wrong side of the screen.
        View.OnKeyListener pillKeys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            int i = pillIndex(v);
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (i > 0) pills[i - 1].requestFocus();
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (i < pills.length - 1) pills[i + 1].requestFocus();
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                focusGrid(0, i);
            } else if (keyCode != KeyEvent.KEYCODE_DPAD_UP) {
                return false;
            }
            return true;
        };
        for (View pill : pills) pill.setOnKeyListener(pillKeys);

        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((oldF, newF) -> {
            boolean onTile = newF != null && newF.getId() == R.id.card;
            if (!onTile) hideGlass();
        });

        // Fill both lists and hand focus to the dock. The dock is the floor of the screen, so it is
        // where the screen opens; the grid only appears when something asks for it.
        gridAdapter.reload();
        dockAdapter.reload();
        if (!dockAdapter.isEmpty()) focusDock(lastDockPos);
        else if (!gridAdapter.isEmpty()) focusGrid(0, 0);
        else pills[0].requestFocus();

        updateNetStatus();
    }

    private int pillIndex(View v) {
        for (int i = 0; i < pills.length; i++) if (pills[i] == v) return i;
        return 0;
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(minuteTick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(minuteTick);
    }

    @Override
    public void onBackPressed() {
        // Home screen: Back goes nowhere.
    }

    // ---- focus zones ----------------------------------------------------------------------------

    /** The dock is the floor of the screen, so Down from it has nowhere to go. */
    @Override
    public boolean onTileVerticalKey(int mode, int position, int keyCode) {
        int col = position % COLUMNS;
        if (mode == AppAdapter.MODE_DOCK) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (gridAdapter.isEmpty()) {
                    pills[Math.min(col, pills.length - 1)].requestFocus();
                } else {
                    focusGrid((gridAdapter.getItemCount() - 1) / COLUMNS, col);
                }
            }
            return true;
        }

        int row = position / COLUMNS;
        int lastRow = (gridAdapter.getItemCount() - 1) / COLUMNS;
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (row == 0) pills[Math.min(col, pills.length - 1)].requestFocus();
            else focusGrid(row - 1, col);
        } else {
            if (row >= lastRow) focusDock(col);
            else focusGrid(row + 1, col);
        }
        return true;
    }

    /** Left and right clamp at the ends. The shelf stops where it stops; it does not wrap. */
    @Override
    public boolean onTileHorizontalKey(int mode, int position, int keyCode) {
        AppAdapter a = mode == AppAdapter.MODE_DOCK ? dockAdapter : gridAdapter;
        if (a.isEmpty()) return true;
        int next = position + (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
        next = Math.max(0, Math.min(next, a.getItemCount() - 1));
        if (mode == AppAdapter.MODE_DOCK) focusDock(next);
        else focusGrid(position / COLUMNS, next);
        return true;
    }

    private void focusDock(int column) {
        if (dockAdapter.isEmpty()) {
            pills[0].requestFocus();
            return;
        }
        setGridVisible(false);
        // Collapse the grid's scroll before it folds away. It stays laid out while hidden, and a
        // grid parked at its last row leaves a large empty box under the hero, which shows through.
        grid.scrollToPosition(0);
        int pos = Math.max(0, Math.min(column, dockAdapter.getItemCount() - 1));
        lastDockPos = pos;
        focusInto(dock, pos);
    }

    private void focusGrid(int row, int column) {
        if (gridAdapter.isEmpty()) {
            focusDock(column);
            return;
        }
        setGridVisible(true);
        int count = gridAdapter.getItemCount();
        int rowStart = Math.max(0, Math.min(row, (count - 1) / COLUMNS)) * COLUMNS;
        int pos = Math.max(0, Math.min(rowStart + Math.min(column, COLUMNS - 1), count - 1));
        lastGridPos = pos;
        focusInto(grid, pos);
    }

    /** Focus a tile now if it is laid out, otherwise scroll to it and focus when the list settles. */
    private void focusInto(final RecyclerView rv, final int pos) {
        View card = cardAt(rv, pos);
        if (card != null) {
            card.requestFocus();
            return;
        }
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            private boolean done;

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView r, int newState) {
                if (done || newState != RecyclerView.SCROLL_STATE_IDLE) return;
                done = true;
                r.removeOnScrollListener(this);
                View target = cardAt(r, pos);
                if (target == null && r.getChildCount() > 0) target = r.getChildAt(0).findViewById(R.id.card);
                if (target != null) target.requestFocus();
            }
        });
        rv.smoothScrollToPosition(pos);
        retryFocus(rv, pos, 0);
    }

    private View cardAt(RecyclerView rv, int pos) {
        RecyclerView.ViewHolder vh = rv.findViewHolderForAdapterPosition(pos);
        return vh != null ? vh.itemView.findViewById(R.id.card) : null;
    }

    /**
     * Takes focus once the holder exists.
     *
     * <p>The scroll listener above only fires when the list actually enters a scroll state. A list
     * that is already showing the target never does, so on first launch -- where the dock starts at
     * position 0 and the dock is what gets focus -- the callback never arrives and nothing takes
     * focus at all. Poll for a few frames instead, then give up rather than spin forever.
     */
    private void retryFocus(final RecyclerView rv, final int pos, final int attempt) {
        if (attempt >= 8) return;
        rv.post(() -> {
            View card = cardAt(rv, pos);
            if (card != null) {
                if (!card.isFocused()) card.requestFocus();
            } else {
                retryFocus(rv, pos, attempt + 1);
            }
        });
    }

    /**
 * The dock is a horizontal list of wrap-content tiles; GridLayoutManager with one span per tile
 * would stretch each child to a full cell and stack them. Spacing comes from SpacingDecoration, not
 * from item margins.
 */
    private void setDockLayoutManager() {
        dock.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        dock.setItemAnimator(null);
    }

    /**
     * Centres the tiles in the shelf.
     *
     * <p>Tiles are wrap_content with no margins, so the gap comes from the list's item spacing.
     */
    private void centerDock() {
        dock.post(() -> {
            int gap = Math.round(20 * density);
            if (!(dock.getItemDecorationCount() > 0
                    && dock.getItemDecorationAt(0) instanceof SpacingDecoration)) {
                dock.addItemDecoration(new SpacingDecoration(gap));
            }
            int cell = Math.round(AppAdapter.TILE_W_DP * density) + gap;
            int content = dockAdapter.getItemCount() * cell - gap;
            int w = dock.getWidth();
            if (w <= 0 || content <= 0) return;
            int side = Math.max(0, (w - content) / 2);
            if (dock.getPaddingLeft() == side && dock.getPaddingRight() == side) return;
            dock.setPadding(side, 0, side, 0);
        });
    }

    private void setGridVisible(boolean show) {
        if (gridShown == show) return;
        gridShown = show;
        grid.setDescendantFocusability(show ? ViewGroup.FOCUS_AFTER_DESCENDANTS : ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        long dur = show ? 300 : 190;
        grid.animate().cancel();
        gridFadeTop.animate().cancel();
        gridFadeBottom.animate().cancel();
        grid.animate()
                .alpha(show ? 1f : 0f)
                .translationY(show ? 0f : 16 * density)
                .setDuration(dur)
                .setInterpolator(new DecelerateInterpolator(1.3f))
                .start();
        for (View fade : new View[]{gridFadeTop, gridFadeBottom}) {
            fade.animate().alpha(show ? 1f : 0f).setDuration(dur).start();
        }
    }

    // ---- glass highlight ------------------------------------------------------------------------

    @Override
    public void onTileFocused(int mode, int position, View card) {
        if (mode == AppAdapter.MODE_DOCK) lastDockPos = position;
        else lastGridPos = position;
        focusedCard = card;
        showHero(mode, position);
        moveGlass(card);
        // The tile's bounds and the glass's own resize both settle a frame later. Re-place after
        // that, so the highlight is measured against the real tile rather than the frame it was
        // first laid out at.
        root.post(this::syncGlass);
    }

    /**
     * The focused tile itself moved. Following it here is what keeps the highlight locked on: scroll
     * and padding changes move tiles without resizing their list, so a listener on the list or its
     * holder root never sees them happen.
     */
    @Override
    public void onTileLaidOut(View card) {
        if (card != focusedCard) return;
        syncGlass();
    }

    /** Re-places the highlight on the focused tile without animating, after a layout change. */
    private void syncGlass() {
        if (!glassShown || focusedCard == null) return;
        // focusedCard is cached from onTileFocused and can go stale when a tile is recycled or focus
        // moves without that callback firing. Trust the view tree's actual focus instead.
        View live = getCurrentFocus();
        if (live != null && live.getId() == R.id.card) focusedCard = live;
        if (focusedCard == null || !focusedCard.isFocused()) return;
        if (focusedCard.getWidth() <= 0) return;      // not laid out yet; a later pass will catch it
        placeGlass(focusedCard);
        glass.setTranslationX(glassTargetX);
        glass.setTranslationY(glassTargetY);
        // The tile's scale animation runs for a few frames after focus. Re-place across those frames
        // so the glass tracks the tile as it settles rather than being pinned to its pre-scale box.
        focusedCard.postOnAnimation(this::syncGlassIfSettling);
    }

    /** Re-places the glass while the focused tile's scale is still moving, then stops. */
    private void syncGlassIfSettling() {
        if (!glassShown || focusedCard == null) return;
        float scale = focusedCard.getScaleX();
        float settled = focusedCard.isFocused() ? AppAdapter.FOCUS_SCALE : 1f;
        placeGlass(focusedCard);
        glass.setTranslationX(glassTargetX);
        glass.setTranslationY(glassTargetY);
        if (Math.abs(scale - settled) > 0.001f) focusedCard.postOnAnimation(this::syncGlassIfSettling);
    }

    /**
     * Resolves the highlight's target box for a tile, in window space.
     *
     * <p>Both axes must use the same scale. {@code getLocationInWindow} reports where the tile
     * paints, which already folds in the focus scale, but {@code getWidth}/{@code getHeight} report
     * the unscaled layout size. Deriving the centre from the painted edge and the unscaled half-size
     * double-counts the scale and leaves the highlight sitting left of and above the tile by half the
     * scale delta — about 17px at focus. Both terms below use the same live scale value.
     */
    private void placeGlass(View card) {
        int[] loc = new int[2];
        card.getLocationInWindow(loc);
        float scale = card.getScaleX() > 0f ? card.getScaleX() : 1f;
        // The highlight stands pad outside the tile on every side, plus room for the glow's own reach so
        // its outer strokes are never clipped by the view bounds. GlassView insets its drawn rect by
        // that glow room, so what it paints as the slab is tile*scale + 2*pad.
        float pad = 6f * density;
        float w = card.getWidth() * scale + 2 * pad;
        float h = card.getHeight() * scale + 2 * pad;
        ViewGroup.LayoutParams lp = glass.getLayoutParams();
        if (lp.width != (int) w || lp.height != (int) h) {
            lp.width = (int) w;
            lp.height = (int) h;
            glass.setLayoutParams(lp);
            // GlassView insets its drawn rect by the same pad, so the slab it paints is the tile's
            // scaled box and this radius is that box's radius.
            glass.setRadius(AppAdapter.RADIUS_DP * density * scale);
        }
        // getLocationInWindow returns the PAINTED left edge, which already includes the focus scale
        // (scaling about the centre pulls the left edge inward), while getWidth() returns the
        // UNSCALED width. Adding an unscaled half-width to a scaled left edge double-counts the
        // scale and puts the highlight about 17px left of the tile. Use the scaled half-width.
        float halfW = card.getWidth() * scale / 2f;
        float halfH = card.getHeight() * scale / 2f;
        glassTargetX = loc[0] + halfW - w / 2f;
        glassTargetY = loc[1] + halfH - h / 2f;
    }

    /**
     * One animator, one clock, one personality. The highlight used to spring while the tile beneath
     * it overshot on a separate curve, so it always arrived trailing; now it runs slightly ahead
     * and they land together.
     */
    private void moveGlass(View card) {
        placeGlass(card);
        if (!glassShown) {
            if (glassAnim != null) glassAnim.cancel();
            glass.setTranslationX(glassTargetX);
            glass.setTranslationY(glassTargetY);
            glass.animate().alpha(1f).setDuration(150).start();
            glassShown = true;
            return;
        }
        final float sx = glass.getTranslationX();
        final float sy = glass.getTranslationY();
        final float dx = glassTargetX - sx;
        final float dy = glassTargetY - sy;
        if (glassAnim != null) glassAnim.cancel();
        glassAnim = ValueAnimator.ofFloat(0f, 1f);
        glassAnim.setDuration(GLASS_MS);
        glassAnim.setInterpolator(new DecelerateInterpolator(1.8f));
        glassAnim.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            glass.setTranslationX(sx + dx * t);
            glass.setTranslationY(sy + dy * t);
        });
        glassAnim.start();
    }

    /**
     * Keeps the highlight glued to its tile through a scroll. Re-resolves both axes from the tile
     * rather than offsetting by the scroll delta: a horizontal list moves in X, and accumulating
     * deltas drifts when a scroll is interrupted or reversed.
     */
    private void nudgeGlass(int dy) {
        if (!glassShown) return;
        syncGlass();
    }

    private void hideGlass() {
        if (!glassShown) return;
        glassShown = false;
        if (glassAnim != null) glassAnim.cancel();
        glass.animate().alpha(0f).setDuration(150).start();
    }

    // ---- hero stage -----------------------------------------------------------------------------

    /**
     * The focused app's name, and an ambient glow in a colour sampled from its banner. The banner
     * image itself is deliberately not drawn: every leanback banner is a logo on a flat field, so
     * filling the screen with one puts lettering behind the name that reads as a rendering fault.
     */
    private void showHero(int mode, int position) {
        AppAdapter a = mode == AppAdapter.MODE_DOCK ? dockAdapter : gridAdapter;
        String label = a.labelAt(position);
        int color = ambientTint(a.ambientColorAt(position));

        if (!heroShown) {
            heroName.setText(label);
            heroName.setAlpha(0f);
            heroName.animate().alpha(1f).setDuration(240).start();
            applyAmbient(color);
            ambient.animate().alpha(1f).setDuration(520).start();
            heroShown = true;
            lastHeroMode = mode;
            lastHeroPos = position;
            return;
        }

        if (mode != lastHeroMode || position != lastHeroPos) {
            heroName.animate().alpha(0f).translationY(-5 * density).setDuration(110).withEndAction(() -> {
                heroName.setText(label);
                heroName.setTranslationY(5 * density);
                heroName.animate().alpha(1f).translationY(0f).setDuration(190).start();
            }).start();
            if (color != ambientColor) {
                ambient.animate().alpha(0.2f).setDuration(180).withEndAction(() -> {
                    applyAmbient(color);
                    ambient.animate().alpha(1f).setDuration(440).start();
                }).start();
            }
            lastHeroMode = mode;
            lastHeroPos = position;
        }
    }

    /**
     * The banner colour, knocked back. The full-strength pixel value turns a red Netflix banner
     * into a red screen and swallows the tile it is meant to be lighting.
     */
    private static int ambientTint(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 0.78f);
        hsv[2] = Math.min(1f, hsv[2] * 0.92f);
        return Color.HSVToColor(hsv);
    }

    private void applyAmbient(int color) {
        ambientColor = color;
        int c = color & 0x00FFFFFF;
        ambientGlow.setColors(new int[]{0x26000000 | c, 0x10000000 | c, 0x00000000 | c});
    }

    // ---- top bar --------------------------------------------------------------------------------

    private void updateGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int res;
        if (hour < 5) res = R.string.good_night;
        else if (hour < 12) res = R.string.good_morning;
        else if (hour < 17) res = R.string.good_afternoon;
        else if (hour < 22) res = R.string.good_evening;
        else res = R.string.good_night;
        ((TextView) findViewById(R.id.greeting)).setText(res);
    }

    /**
     * The network name is a readout beside the pills, not the label on one. Keeping it out of the
     * pill means the pill never changes width, and the label never reads like an action.
     */
    private void updateNetStatus() {
        TextView status = findViewById(R.id.netStatus);
        String text = "";
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            Network net = cm != null ? cm.getActiveNetwork() : null;
            NetworkCapabilities caps = net != null ? cm.getNetworkCapabilities(net) : null;
            if (caps == null) {
                text = getString(R.string.offline);
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                text = getString(R.string.ethernet);
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
                String ssid = info != null ? info.getSSID() : null;
                if (ssid != null) {
                    ssid = ssid.replace("\"", "").trim();
                    if (!ssid.isEmpty() && !ssid.contains("unknown") && !ssid.equals("0x")) text = ssid;
                }
                if (text.isEmpty()) text = getString(R.string.wifi);
            }
        } catch (Exception ignored) {
        }
        if (status != null && !status.getText().toString().contentEquals(text)) status.setText(text);
    }

    private void open(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Not available on this TV", Toast.LENGTH_SHORT).show();
        }
    }

    /** Dropdown menu under the Inputs pill: HDMI ports (with connection state), AV and Live TV. */
    private void showInputs() {
        View anchor = pills[0];
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);

        int width = Math.max(anchor.getWidth(), (int) (150 * density));
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));   // needed for Back to dismiss
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);

        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        boolean sawNonHdmi = false;
        if (tim != null) {
            List<TvInputInfo> inputs = new ArrayList<>(tim.getTvInputList());
            Collections.sort(inputs, (a, b) -> {
                boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
                if (ha != hb) return ha ? -1 : 1;
                return String.valueOf(a.loadLabel(this)).compareToIgnoreCase(String.valueOf(b.loadLabel(this)));
            });
            for (TvInputInfo info : inputs) {
                if (!info.isPassthroughInput()) continue;
                CharSequence l = info.loadLabel(this);
                String label = l == null ? info.getId() : l.toString();
                boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
                if (hdmi && !label.toUpperCase().contains("HDMI")) label = "HDMI " + label;
                final String id = info.getId();
                int state = tim.getInputState(id);
                if (!hdmi && rows.getChildCount() > 0 && !sawNonHdmi) addSeparator(rows);
                if (!hdmi) sawNonHdmi = true;
                addInputRow(rows, popup, hdmi ? R.drawable.ic_input : R.drawable.ic_av, label, state,
                        () -> open(new Intent(Intent.ACTION_VIEW).setDataAndType(
                                TvContract.buildChannelUriForPassthroughInput(id), "vnd.android.cursor.item/channel")));
            }
        }
        Intent liveTv = liveTvPkg.isEmpty() ? null : getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPkg);
        if (liveTv != null) {
            if (rows.getChildCount() > 0) addSeparator(rows);
            addInputRow(rows, popup, R.drawable.ic_livetv, getString(R.string.live_tv), -1, () -> open(liveTv));
        }
        if (rows.getChildCount() == 0) {
            Toast.makeText(this, R.string.no_inputs, Toast.LENGTH_SHORT).show();
            return;
        }

        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);
        popup.showAsDropDown(anchor, 0, (int) (10 * density), Gravity.START);
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        rows.getChildAt(0).requestFocus();
    }

    /** Dropdown under the Settings pill: the light/dark toggle, then the system Settings app. */
    private void showSettings() {
        View anchor = pills[2];
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);

        int width = Math.max(anchor.getWidth(), (int) (210 * density));
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));   // needed for Back to dismiss
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean light = prefs.getBoolean(KEY_LIGHT, false);
        View toggle = addInputRow(rows, popup, R.drawable.ic_theme, getString(R.string.light_mode), -1, () -> {
            prefs.edit().putBoolean(KEY_LIGHT, !light).apply();
            reopenSettings = true;
            recreate();   // re-runs attachBaseContext, which picks the other colour set
        });
        TextView state = toggle.findViewById(R.id.status);
        state.setText(light ? R.string.on : R.string.off);
        state.setVisibility(View.VISIBLE);
        addSeparator(rows);
        addInputRow(rows, popup, R.drawable.ic_settings, getString(R.string.system_settings), -1,
                () -> open(new Intent(Settings.ACTION_SETTINGS)));

        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);
        popup.showAsDropDown(anchor, 0, (int) (10 * density), Gravity.END);
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new DecelerateInterpolator(1.6f)).start();
        rows.getChildAt(0).requestFocus();
    }

    private void addSeparator(LinearLayout rows) {
        View line = new View(this);
        line.setBackgroundColor(getColor(R.color.separator));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) density);
        int m = (int) (6 * density), h = (int) (12 * density);
        lp.setMargins(h, m, h, m);
        rows.addView(line, lp);
    }

    private View addInputRow(LinearLayout rows, PopupWindow popup, int iconRes, String title, int state, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        TextView status = row.findViewById(R.id.status);
        View dot = row.findViewById(R.id.dot);
        // This TV reports "standby" for every port whether or not something is plugged in, so only
        // definite states are shown.
        if (state == TvInputManager.INPUT_STATE_CONNECTED) {
            status.setText(R.string.connected);
            dot.setVisibility(View.VISIBLE);
        } else if (state == TvInputManager.INPUT_STATE_DISCONNECTED) {
            status.setText(R.string.not_connected);
            dot.setVisibility(View.GONE);
        } else {
            status.setVisibility(View.GONE);
            dot.setVisibility(View.GONE);
        }
        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        rows.addView(row);
        return row;
    }
}