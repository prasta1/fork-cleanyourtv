package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import com.gathin.tvhome.theme.Defaults;
import com.gathin.tvhome.theme.Tokens;

/**
 * A translucent glass slab: soft sheen fill, bright top specular, crisp rim and a faint outer glow.
 * One instance floats over the grid and is moved with spring animations between tiles.
 */
public class GlassView extends View {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specular = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;

    private Tokens tokens = Defaults.tokens();
    private float radius;
    /** Accent ARGB from a festival or reorder mode; 0 means "use the theme's glass". */
    private int accent;

    public GlassView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        rim.setStyle(Paint.Style.STROKE);
        glow.setStyle(Paint.Style.STROKE);
        // Cache the slab as a texture. The spring only changes translation, so the GPU can
        // move it without replaying clipPath and the glow on every frame. That path is
        // software-rendered on the MT5895 and is what makes the highlight crawl.
        setLayerType(LAYER_TYPE_HARDWARE, null);
        retheme(tokens);
    }

    /**
     * Repaints with a new theme. Safe to call at any time, including before the view has a size —
     * the gradients are rebuilt when it does.
     */
    void retheme(Tokens t) {
        tokens = t;
        radius = t.glassRadius * density;
        rim.setStrokeWidth(t.glassStroke * density);
        rebuildGradients();
        invalidate();
    }

    /** Tints the rim and the outer glow; 0 restores the theme's own glass. */
    void setAccent(int argb) {
        accent = argb;
        invalidate();
    }

    void setRadius(float px) {
        radius = px;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuildGradients();
    }

    /** The two vertical gradients are sized from the view, so they follow both a resize and a retheme. */
    private void rebuildGradients() {
        int h = getHeight();
        if (h <= 0) return;
        fill.setShader(new LinearGradient(0, 0, 0, h,
                tokens.glassFillTop, tokens.glassFillBottom, Shader.TileMode.CLAMP));
        // The specular fades to transparent in its own hue, not to a fixed white.
        specular.setShader(new LinearGradient(0, 0, 0, h * 0.45f,
                tokens.glassSpecular, tokens.glassSpecular & 0x00FFFFFF, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float inset = tokens.glassInset * density;   // room for the glow
        rect.set(inset, inset, getWidth() - inset, getHeight() - inset);

        // Outer glow: three widening, fading strokes. The hardware layer caches the result, so
        // they cost one draw, not one per frame of the spring. An accent (festival, reorder mode)
        // overrides the theme: a coloured glow needs more alpha than the white one to read as light.
        int glowAlpha = tokens.glassGlow >>> 24;
        int glowRgb = tokens.glassGlow & 0x00FFFFFF;
        int rimColor = tokens.glassRim;
        if (accent != 0) {
            glowRgb = accent & 0xFFFFFF;
            glowAlpha = 0x70;
            // The rim is the accent lifted towards white, so the edge still reads as glass, not paint.
            int r = (((glowRgb >> 16) & 0xFF) + 255 * 2) / 3, g = (((glowRgb >> 8) & 0xFF) + 255 * 2) / 3,
                    b = ((glowRgb & 0xFF) + 255 * 2) / 3;
            rimColor = 0xE6000000 | (r << 16) | (g << 8) | b;
        }
        for (int i = 1; i <= 3; i++) {
            glow.setStrokeWidth(i * 2f * density);
            glow.setColor(((glowAlpha / i) << 24) | glowRgb);
            float o = i * density;
            canvas.drawRoundRect(rect.left - o, rect.top - o, rect.right + o, rect.bottom + o, radius + o, radius + o, glow);
        }

        canvas.drawRoundRect(rect, radius, radius, fill);

        // Sheen across the top 45%: clipRect stays on the GPU (clipPath did not on the MT5895) and
        // the round rect under it keeps the top corners rounded with a flat bottom edge.
        canvas.save();
        canvas.clipRect(rect.left, rect.top, rect.right, rect.top + rect.height() * 0.45f);
        canvas.drawRoundRect(rect, radius, radius, specular);
        canvas.restore();

        rim.setColor(rimColor);
        canvas.drawRoundRect(rect, radius, radius, rim);
    }
}
