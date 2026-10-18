package com.gathin.tvhome;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * App tiles, in two roles. {@link #MODE_DOCK} is the pinned shelf and holds only the favourites in
 * dock order; {@link #MODE_GRID} holds everything else, alphabetically. Both render the same tile
 * at the same size, so a pinned app and a listed app are the same object.
 */
public class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {

    static final int MODE_DOCK = 0;
    static final int MODE_GRID = 1;

    interface TileListener {
        /** The tile at {@code position} in {@code mode} took focus. */
        void onTileFocused(int mode, int position, View card);

        /** D-pad Up/Down on a tile. Return true when handled. */
        boolean onTileVerticalKey(int mode, int position, int keyCode);

        /** D-pad Left/Right on a tile. Return true when handled. */
        boolean onTileHorizontalKey(int mode, int position, int keyCode);

        /** The focused tile's own bounds moved. Anything tracking it must follow. */
        void onTileLaidOut(View card);
    }

    static final class Entry {
        final String label;
        final ComponentName component;
        final String pkg;
        final Drawable banner;
        final Drawable icon;
        int ambient;
        int plate;

        Entry(String label, ComponentName component, Drawable banner, Drawable icon) {
            this.label = label;
            this.component = component;
            this.pkg = component.getPackageName();
            this.banner = banner;
            this.icon = icon;
        }
    }

    static final float FOCUS_SCALE = 1.10f;
    static final float RADIUS_DP = 16f;
    /** Tile width in dp. Also used to centre the dock, so it must match item_app.xml. */
    static final float TILE_W_DP = 180f;

    private final Context ctx;
    private final TileListener listener;
    private final int mode;
    /** Dock order (res/values/config.xml, dock_apps). */
    private final List<String> favourites;
    /** Never shown as tiles (res/values/config.xml, hidden_apps). */
    private final Set<String> hidden;
    private final List<Entry> items = new ArrayList<>();

    AppAdapter(Context ctx, TileListener listener, int mode) {
        this.ctx = ctx;
        this.listener = listener;
        this.mode = mode;
        this.favourites = Arrays.asList(ctx.getResources().getStringArray(R.array.dock_apps));
        this.hidden = new HashSet<>(Arrays.asList(ctx.getResources().getStringArray(R.array.hidden_apps)));
    }

    void reload() {
        PackageManager pm = ctx.getPackageManager();
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(probe, 0);
        List<Entry> fresh = new ArrayList<>();
        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName()) || hidden.contains(pkg)) continue;
            Drawable banner = ri.activityInfo.loadBanner(pm);
            if (banner == null) banner = ri.activityInfo.applicationInfo.loadBanner(pm);
            Drawable icon = ri.loadIcon(pm);
            String label = String.valueOf(ri.loadLabel(pm));
            fresh.add(new Entry(label, new ComponentName(pkg, ri.activityInfo.name), banner, icon));
        }

        boolean dock = mode == MODE_DOCK;
        Collections.sort(fresh, (a, b) -> {
            int fa = favourites.indexOf(a.pkg), fb = favourites.indexOf(b.pkg);
            boolean inA = fa >= 0, inB = fb >= 0;
            if (inA || inB) {
                if (!inA) return dock ? 1 : -1;
                if (!inB) return dock ? -1 : 1;
                return dock ? Integer.compare(fa, fb) : 0;   // grid sorts alphabetically below
            }
            return a.label.compareToIgnoreCase(b.label);
        });

        items.clear();
        for (Entry e : fresh) {
            boolean isFavourite = favourites.contains(e.pkg);
            if (isFavourite == dock) items.add(e);   // dock keeps favourites, grid keeps the rest
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        if (mode == MODE_DOCK) {
            // wrap_content, and no margins. A wrap_content item measures to the card width PLUS its
            // margins, so margins made the item wider than the card it contains; the card's
            // center_horizontal gravity then offset the card inside it, and the item and the card
            // stopped sharing a coordinate space. The glass is placed from the card's window
            // position, so it drifted off the painted tile by that offset. Spacing between dock tiles
            // comes from the list's own layout, not from item margins.
            ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            v.setLayoutParams(lp);
        }
        return new Holder(v, listener, mode);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Entry e = items.get(position);
        if (e.banner != null) {
            h.banner.setImageDrawable(e.banner);
            h.banner.setVisibility(View.VISIBLE);
            h.icon.setVisibility(View.GONE);
            h.card.setBackgroundResource(R.drawable.tile_bg);
        } else {
            // No banner: a plate tinted from the app's own colour, so a grid of mixed apps still
            // reads as one surface instead of ragged dark holes between bright banners.
            h.banner.setImageDrawable(null);
            h.banner.setVisibility(View.GONE);
            h.icon.setImageDrawable(e.icon);
            h.icon.setVisibility(View.VISIBLE);
            h.card.setBackground(plateDrawable(e));
        }
        h.card.setOnClickListener(v -> launch(e));
        h.card.setOnLongClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + e.pkg));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                ctx.startActivity(i);
            } catch (ActivityNotFoundException ignored) {
            }
            return true;
        });
    }

    /** The dark, hue-tinted plate behind a banner-less app's icon. Cached per entry. */
    private Drawable plateDrawable(Entry e) {
        GradientDrawable plate = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{shade(ambientColorAt(indexOf(e)), 0.62f, 0.34f),
                        shade(ambientColorAt(indexOf(e)), 0.72f, 0.15f)});
        plate.setCornerRadius(dp(RADIUS_DP));
        plate.setStroke(Math.max(1, (int) dp(1)), shade(ambientColorAt(indexOf(e)), 0.5f, 0.34f));
        return plate;
    }

    private int indexOf(Entry e) {
        return items.indexOf(e);
    }

    private int dp(float v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }

    /** Keeps the hue, drops the value: a colour derived from an app, not the app's actual colour. */
    private static int shade(int color, float satMul, float val) {
        float[] hsv = new float[3];
        Color.colorToHSV(color & 0x00FFFFFF | 0xFF000000, hsv);
        hsv[1] = Math.min(1f, hsv[1] * satMul);
        hsv[2] = val;
        return Color.HSVToColor(hsv);
    }

    private void launch(Entry e) {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setComponent(e.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            ctx.startActivity(i);
        } catch (Exception ex) {
            Toast.makeText(ctx, "Can't open " + e.label, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    boolean isEmpty() {
        return items.isEmpty();
    }

    String labelAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).label : "";
    }

    

    /** A vivid colour sampled from the app's banner (or icon), cached per entry. */
    int ambientColorAt(int position) {
        if (position < 0 || position >= items.size()) return 0xFF4A4A52;
        Entry e = items.get(position);
        if (e.ambient == 0) e.ambient = vividColor(e.banner != null ? e.banner : e.icon);
        return e.ambient;
    }

    private static int vividColor(Drawable live) {
        if (live == null) return 0xFF4A4A52;
        // Draw a private copy: resizing the drawable an ImageView is showing would blank that tile.
        Drawable.ConstantState cs = live.getConstantState();
        Drawable d = cs != null ? cs.newDrawable().mutate() : live;
        int n = 12;
        Bitmap bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Rect saved = new Rect(d.getBounds());
        d.setBounds(0, 0, n, n);
        d.draw(c);
        d.setBounds(saved);
        float[] hsv = new float[3];
        int best = 0xFF4A4A52;
        float bestScore = -1f;
        long r = 0, g = 0, b = 0;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int p = bmp.getPixel(x, y);
                r += Color.red(p);
                g += Color.green(p);
                b += Color.blue(p);
                Color.colorToHSV(p, hsv);
                float score = hsv[1] * Math.min(1f, hsv[2] * 1.4f);   // saturated and not too dark
                if (score > bestScore) {
                    bestScore = score;
                    best = p;
                }
            }
        }
        bmp.recycle();
        if (bestScore < 0.25f) {   // nearly monochrome banner (e.g. white): use its average, lifted
            int avg = Color.rgb((int) (r / (n * n)), (int) (g / (n * n)), (int) (b / (n * n)));
            Color.colorToHSV(avg, hsv);
            hsv[2] = Math.max(hsv[2], 0.55f);
            return Color.HSVToColor(hsv);
        }
        Color.colorToHSV(best, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.1f);
        hsv[2] = Math.max(0.5f, hsv[2]);
        return Color.HSVToColor(hsv);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final View card;
        final ImageView banner;
        final ImageView icon;

        Holder(View v, TileListener listener, int mode) {
            super(v);
            card = v.findViewById(R.id.card);
            banner = v.findViewById(R.id.banner);
            icon = v.findViewById(R.id.icon);

            final float density = v.getResources().getDisplayMetrics().density;
            final float radius = density * RADIUS_DP;
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                }
            });
            card.setClipToOutline(true);

            card.setOnKeyListener((view, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                int pos = getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION || listener == null) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    return listener.onTileVerticalKey(mode, pos, keyCode);
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    return listener.onTileHorizontalKey(mode, pos, keyCode);
                }
                return false;
            });

            card.setOnFocusChangeListener((view, focused) -> {
                float s = focused ? FOCUS_SCALE : 1.0f;
                view.animate().scaleX(s).scaleY(s)
                        .setDuration(focused ? 220 : 170)
                        .setInterpolator(new OvershootInterpolator(focused ? 1.2f : 0f))
                        .start();
                int pos = getBindingAdapterPosition();
                if (focused && listener != null && pos != RecyclerView.NO_POSITION) {
                    listener.onTileFocused(mode, pos, view);
                }
            });

            // A tile moves whenever its RecyclerView scrolls, re-lays out, or the dock's centring
            // padding changes. None of those change the list's own bounds, so a listener on the list
            // never hears about them and the highlight drifts off the tile it belongs to.
            card.addOnLayoutChangeListener((changed, l, t, r, b, ol, ot, or, ob) -> {
                if (listener != null && changed.isFocused()) listener.onTileLaidOut(changed);
            });
        }
    }
}