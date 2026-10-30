package com.twilightcafe.movies.pointer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

/**
 * Transparent overlay that draws a mouse-style pointer. It never takes focus
 * and never consumes touches, so everything underneath keeps working.
 */
final class CursorView extends View {

    private final Path arrow = new Path();
    private final Path shadow = new Path();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    private float x = -1f;
    private float y = -1f;
    private boolean visible;
    private long pulseAt;

    CursorView(Context context, int accent) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        setFocusable(false);
        setClickable(false);
        setWillNotDraw(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

        // Classic arrow pointer, tip at (0,0).
        float u = density * 1.35f;
        float[] p = {0f, 0f, 0f, 21f, 5f, 16.5f, 8.5f, 24.5f, 12f, 23f, 8.5f, 15.5f, 15f, 15.5f};
        arrow.moveTo(p[0] * u, p[1] * u);
        for (int i = 2; i < p.length; i += 2) {
            arrow.lineTo(p[i] * u, p[i + 1] * u);
        }
        arrow.close();
        shadow.set(arrow);
        shadow.offset(1.5f * density, 2f * density);

        fill.setStyle(Paint.Style.FILL);
        fill.setColor(Color.WHITE);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setColor(0xFF111111);
        stroke.setStrokeWidth(1.6f * density);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(0x66000000);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(3f * density);
        ring.setColor(accent);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (x < 0 || y < 0 || x >= w || y >= h) {
            x = w / 2f;
            y = h / 2f;
        }
    }

    float tipX() {
        return x < 0 ? getWidth() / 2f : x;
    }

    float tipY() {
        return y < 0 ? getHeight() / 2f : y;
    }

    boolean isPointerVisible() {
        return visible;
    }

    void setPointerVisible(boolean v) {
        if (visible != v) {
            visible = v;
            invalidate();
        }
    }

    void moveTo(float nx, float ny) {
        x = nx;
        y = ny;
        if (visible) invalidate();
    }

    /** Short ring animation at the tip, as click feedback. */
    void pulse() {
        pulseAt = SystemClock.uptimeMillis();
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    protected void onDraw(Canvas c) {
        if (!visible) return;
        float px = tipX();
        float py = tipY();
        long age = SystemClock.uptimeMillis() - pulseAt;
        if (age >= 0 && age < 300) {
            float t = age / 300f;
            ring.setAlpha((int) (255 * (1f - t)));
            c.drawCircle(px, py, (8f + 22f * t) * density, ring);
            postInvalidateOnAnimation();
        }
        c.save();
        c.translate(px, py);
        c.drawPath(shadow, shadowPaint);
        c.drawPath(arrow, fill);
        c.drawPath(arrow, stroke);
        c.restore();
    }
}
