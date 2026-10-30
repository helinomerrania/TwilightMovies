package com.twilightcafe.movies.pointer;

import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;

import com.twilightcafe.movies.ui.Palette;
import com.twilightcafe.movies.ui.Screen;
import com.twilightcafe.movies.web.PageScripts;
import com.twilightcafe.movies.web.PageTarget;
import com.twilightcafe.movies.web.PageView;

/**
 * The mouse pointer, for websites without a TV interface of their own (menu → Mouse pointer, kept for each
 * website): the arrows move it, faster the longer they're held; OK taps where it is; pushing against the edge
 * of the screen scrolls what's under it (scripts/scroll.js). It hides after a while.
 */
public final class Pointer {
    private static final long MOVE_DELAY_MS = 170;
    private static final float SPEED_MIN_DP = 260f;   // when an arrow is first held (dp a second)
    private static final float SPEED_MAX_DP = 1100f;  // top speed, reached after about a second
    private static final float NUDGE_DP = 7f;         // a single short press

    private final Screen screen;
    private final CursorView cursor;
    private final Touch touch;
    private final PageTarget target;
    private final PageView page;
    private final PageScripts scripts;
    private boolean enabled;
    private int dirX;
    private int dirY;
    private boolean moving;
    private long moveStart;
    private long lastTick;
    private float pendingX;
    private float pendingY;
    private boolean scrollPosted;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (dirX == 0 && dirY == 0) {
                moving = false;
                return;
            }
            long now = SystemClock.uptimeMillis();
            float dt = Math.min(0.05f, (now - lastTick) / 1000f);
            lastTick = now;
            long held = now - moveStart;
            if (held > MOVE_DELAY_MS) {
                float t = Math.min(1f, (held - MOVE_DELAY_MS) / 1100f);
                float speed = (SPEED_MIN_DP + (SPEED_MAX_DP - SPEED_MIN_DP) * t * t) * screen.views.density();
                float diagonal = dirX != 0 && dirY != 0 ? 0.7071f : 1f;
                move(dirX * speed * dt * diagonal, dirY * speed * dt * diagonal);
            }
            cursor.postOnAnimation(this);
        }
    };
    private final Runnable flush = new Runnable() {
        @Override
        public void run() {
            scrollPosted = false;
            flushScroll();
        }
    };
    private final Runnable autoHide = new Runnable() {
        @Override
        public void run() {
            if (dirX == 0 && dirY == 0) cursor.setPointerVisible(false);
        }
    };

    public Pointer(Screen s, PageTarget target, PageView page, PageScripts scripts) {
        this.screen = s;
        this.target = target;
        this.page = page;
        this.scripts = scripts;
        this.cursor = new CursorView(s.activity, Palette.ACCENT);
        this.touch = new Touch(s, target);
    }

    /** The pointer's picture, over everything. */
    public View view() {
        return cursor;
    }

    /** Whether the website shown uses the pointer (the arrows then move it). */
    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean on) {
        enabled = on;
        if (!on) hide();
    }

    public boolean visible() {
        return cursor.isPointerVisible();
    }

    public float x() {
        return cursor.tipX();
    }

    public float y() {
        return cursor.tipY();
    }

    /** An arrow went down: the pointer starts moving that way. */
    public void start(int keyCode) {
        int dx = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0;
        int dy = keyCode == KeyEvent.KEYCODE_DPAD_UP ? -1 : keyCode == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : 0;
        if (dx != 0) dirX = dx;
        if (dy != 0) dirY = dy;
        screen.main.removeCallbacks(autoHide);
        cursor.setPointerVisible(true);
        if (moving) return;
        moving = true;
        moveStart = SystemClock.uptimeMillis();
        lastTick = moveStart;
        nudge(dx, dy);
        cursor.postOnAnimation(tick);
    }

    /** An arrow came up: that way stops (and, once none is held, the page learns where the pointer is). */
    public void end(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && dirX < 0) dirX = 0;
        else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && dirX > 0) dirX = 0;
        else if (keyCode == KeyEvent.KEYCODE_DPAD_UP && dirY < 0) dirY = 0;
        else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && dirY > 0) dirY = 0;
        if (dirX != 0 || dirY != 0) return;
        moving = false;
        cursor.removeCallbacks(tick);
        flushScroll();
        touch.hover(x(), y(), true);
        show();
    }

    /** Stops at once (something else takes the screen). */
    public void stop() {
        dirX = 0;
        dirY = 0;
        moving = false;
        cursor.removeCallbacks(tick);
        flushScroll();
    }

    /** Shown, and hidden again after a while (sooner over a film). */
    public void show() {
        cursor.setPointerVisible(true);
        screen.main.removeCallbacks(autoHide);
        screen.main.postDelayed(autoHide, target.fullscreen() ? 3000 : 15000);
    }

    public void hide() {
        cursor.setPointerVisible(false);
    }

    /** A tap at x, y, with a ring at the tip. */
    public void tap(float x, float y) {
        touch.tap(x, y);
        cursor.pulse();
    }

    /** Scrolls what's under the pointer by fy of the screen (page up and down). */
    public void scrollHere(float fy) {
        scrollAt(x(), y(), 0f, fy);
    }

    /** Scrolls what's in the middle of the screen (the channel keys, without the pointer). */
    public void scrollMiddle(float fy) {
        scrollAt(screen.root.getWidth() / 2f, screen.root.getHeight() / 2f, 0f, fy);
    }

    private void nudge(int dx, int dy) {
        float w = screen.root.getWidth();
        float h = screen.root.getHeight();
        float x = x();
        float y = y();
        boolean atEdge = (dx < 0 && x <= 0.5f) || (dx > 0 && x >= w - 1.5f) || (dy < 0 && y <= 0.5f) || (dy > 0 && y >= h - 1.5f);
        if (atEdge) {
            if (!target.fullscreen()) scrollAt(x, y, dx * 0.15f, dy * 0.15f);
            return;
        }
        float n = NUDGE_DP * screen.views.density();
        move(dx * n, dy * n);
    }

    private void move(float dx, float dy) {
        float w = screen.root.getWidth();
        float h = screen.root.getHeight();
        if (w <= 0 || h <= 0) return;
        float x = x() + dx;
        float y = y() + dy;
        float overX = x < 0 ? x : x > w - 1 ? x - (w - 1) : 0f;
        float overY = y < 0 ? y : y > h - 1 ? y - (h - 1) : 0f;
        x = Math.max(0, Math.min(w - 1, x));
        y = Math.max(0, Math.min(h - 1, y));
        cursor.moveTo(x, y);
        touch.hover(x, y, false);
        if (target.fullscreen() || (overX == 0f && overY == 0f)) return;
        pendingX += overX;   // pushing against the edge of the screen scrolls the page
        pendingY += overY;
        if (!scrollPosted) {
            scrollPosted = true;
            screen.main.postDelayed(flush, 50);
        }
    }

    private void flushScroll() {
        screen.main.removeCallbacks(flush);
        scrollPosted = false;
        if (pendingX == 0f && pendingY == 0f) return;
        float fx = pendingX / Math.max(1, screen.root.getWidth());
        float fy = pendingY / Math.max(1, screen.root.getHeight());
        pendingX = 0f;
        pendingY = 0f;
        scrollAt(x(), y(), fx, fy);
    }

    private void scrollAt(float x, float y, float fx, float fy) {
        float w = Math.max(1, screen.root.getWidth());
        float h = Math.max(1, screen.root.getHeight());
        page.run(scripts.call(PageScripts.SCROLL, x / w, y / h, fx, fy));
    }
}
