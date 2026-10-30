package com.twilightcafe.movies.pointer;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;

import com.twilightcafe.movies.ui.Screen;
import com.twilightcafe.movies.web.PageTarget;

/**
 * What the pointer does to the page: hovering, like a mouse (for menus that open under it), and taps, like a
 * finger (so every kind of page reacts), at a point of the screen.
 */
final class Touch {
    private final Screen screen;
    private final PageTarget target;
    private long lastHoverAt;

    Touch(Screen screen, PageTarget target) {
        this.screen = screen;
        this.target = target;
    }

    /** A mouse over x, y (at most 30 times a second, unless force). */
    void hover(float x, float y, boolean force) {
        long now = SystemClock.uptimeMillis();
        if (!force && now - lastHoverAt < 32) return;
        lastHoverAt = now;
        View t = target.view();
        if (t == null) return;
        float[] p = toTarget(t, x, y);
        MotionEvent ev = event(now, now, MotionEvent.ACTION_HOVER_MOVE, p, MotionEvent.TOOL_TYPE_MOUSE, InputDevice.SOURCE_MOUSE);
        t.dispatchGenericMotionEvent(ev);
        ev.recycle();
    }

    /** A finger down at x, y, and up again 40 ms later. */
    void tap(float x, float y) {
        final View t = target.view();
        if (t == null) return;
        final float[] p = toTarget(t, x, y);
        final long downTime = SystemClock.uptimeMillis();
        MotionEvent down = event(downTime, downTime, MotionEvent.ACTION_DOWN, p, MotionEvent.TOOL_TYPE_FINGER, InputDevice.SOURCE_TOUCHSCREEN);
        t.dispatchTouchEvent(down);
        down.recycle();
        screen.main.postDelayed(new Runnable() {
            @Override
            public void run() {
                MotionEvent up = event(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, p,
                        MotionEvent.TOOL_TYPE_FINGER, InputDevice.SOURCE_TOUCHSCREEN);
                t.dispatchTouchEvent(up);
                up.recycle();
            }
        }, 40);
    }

    private static MotionEvent event(long downTime, long eventTime, int action, float[] p, int toolType, int source) {
        MotionEvent.PointerProperties props = new MotionEvent.PointerProperties();
        props.id = 0;
        props.toolType = toolType;
        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        coords.x = p[0];
        coords.y = p[1];
        coords.pressure = 1f;
        coords.size = 1f;
        return MotionEvent.obtain(downTime, eventTime, action, 1, new MotionEvent.PointerProperties[]{props},
                new MotionEvent.PointerCoords[]{coords}, 0, 0, 1f, 1f, 0, 0, source, 0);
    }

    /** x, y on the screen, as a point in the view t. */
    private float[] toTarget(View t, float x, float y) {
        int[] a = new int[2];
        int[] b = new int[2];
        screen.root.getLocationInWindow(a);
        t.getLocationInWindow(b);
        return new float[]{x + a[0] - b[0], y + a[1] - b[1]};
    }
}
