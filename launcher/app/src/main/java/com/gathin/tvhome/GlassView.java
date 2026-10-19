package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
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
    private final Path clip = new Path();
    private final float density;

    private Tokens tokens = Defaults.tokens();
    private float radius;

    public GlassView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        rim.setStyle(Paint.Style.STROKE);
        glow.setStyle(Paint.Style.STROKE);
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

        // Outer glow: three widening, fading strokes.
        int glowAlpha = tokens.glassGlow >>> 24;
        int glowRgb = tokens.glassGlow & 0x00FFFFFF;
        for (int i = 1; i <= 3; i++) {
            glow.setStrokeWidth(i * 2f * density);
            glow.setColor(((glowAlpha / i) << 24) | glowRgb);
            float o = i * density;
            canvas.drawRoundRect(rect.left - o, rect.top - o, rect.right + o, rect.bottom + o, radius + o, radius + o, glow);
        }

        canvas.drawRoundRect(rect, radius, radius, fill);

        clip.reset();
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);
        canvas.drawRect(rect.left, rect.top, rect.right, rect.top + rect.height() * 0.45f, specular);
        canvas.restore();

        rim.setColor(tokens.glassRim);
        canvas.drawRoundRect(rect, radius, radius, rim);
    }
}
