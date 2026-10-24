package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/**
 * A glass highlight that flows from the previously focused view to the new one: it glides with a slight
 * overshoot and stretches in the direction of travel, like a drop of liquid. It follows its target while the page scrolls.
 */
final class FocusGlass extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG), halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF(), start = new RectF();
    private final float d;
    private View target;
    private ValueAnimator anim;

    FocusGlass(Context ctx) {
        super(ctx);
        d = ctx.getResources().getDisplayMetrics().density;
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(2 * d);
        edge.setColor(0x99FFFFFF);
        halo.setStyle(Paint.Style.STROKE);
        halo.setStrokeWidth(7 * d);
        halo.setColor(0x24BFE6FF);
    }

    /** Moves to the view (gliding for millis, none when 0 or when there is nothing to come from); null hides the glass. */
    void follow(View to, int millis) {
        if (to == null) {
            if (anim != null) anim.cancel();
            target = null;
            setVisibility(INVISIBLE);
            return;
        }
        boolean fresh = target == null || getVisibility() != VISIBLE;
        if (anim != null) anim.cancel();
        start.set(rect);
        target = to;
        setVisibility(VISIBLE);
        if (fresh || millis <= 0) {
            snap();
            return;
        }
        final float[] last = new float[1];
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(millis);
        anim.setInterpolator(new OvershootInterpolator(1.0f));
        anim.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue(), f = a.getAnimatedFraction();
            RectF end = bounds(target);
            if (end == null) return;
            rect.set(lerp(start.left, end.left, t), lerp(start.top, end.top, t), lerp(start.right, end.right, t), lerp(start.bottom, end.bottom, t));
            // Stretch along the travel, strongest in the middle of the move, then settle.
            float stretch = 1f + 0.10f * (float) Math.sin(Math.PI * f), cx = rect.centerX(), cy = rect.centerY();
            boolean horizontal = Math.abs(end.centerX() - start.centerX()) > Math.abs(end.centerY() - start.centerY());
            float w = rect.width() * (horizontal ? stretch : 1f), h = rect.height() * (horizontal ? 1f : 1f + (stretch - 1f) * 0.6f);
            rect.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
            invalidate();
        });
        anim.start();
    }

    /** Puts the glass on its target right now (after a scroll or a layout change). */
    void snap() {
        if (target == null || (anim != null && anim.isRunning())) return;
        RectF b = bounds(target);
        if (b == null) return;
        rect.set(b);
        invalidate();
    }

    private RectF bounds(View v) {
        if (v == null || v.getWidth() == 0) return null;
        int[] a = new int[2], b = new int[2];
        v.getLocationOnScreen(a);
        getLocationOnScreen(b);
        float pad = 3 * d;
        return new RectF(a[0] - b[0] - pad, a[1] - b[1] - pad, a[0] - b[0] + v.getWidth() + pad, a[1] - b[1] + v.getHeight() + pad);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    @Override
    protected void onDraw(Canvas c) {
        if (rect.width() < 2 || rect.height() < 2) return;
        float r = Math.min(rect.height() / 2f, 26 * d);
        fill.setShader(new LinearGradient(0, rect.top, 0, rect.bottom, 0x40FFFFFF, 0x12FFFFFF, Shader.TileMode.CLAMP));
        c.drawRoundRect(rect, r, r, fill);
        c.drawRoundRect(rect, r, r, halo);
        c.drawRoundRect(rect, r, r, edge);
    }
}
