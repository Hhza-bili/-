package org.dsh.whalepet;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

/** 宠物本体：逐帧动画 + 果冻弹簧 + 长按菜单 + 拖拽模糊 + 闲置半透明。 */
public class PetView extends View {
    private static final long LONG_PRESS_MS = 460;
    private static final long IDLE_FADE_MS = 8000;

    private final PetService host;
    private SpriteSheet[] sheets;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final BlurMaskFilter blur = new BlurMaskFilter(9f, BlurMaskFilter.Blur.NORMAL);
    private final Handler h = new Handler(Looper.getMainLooper());

    private int cur = 0, frame = 0;
    private long nextFrameAt = 0;

    private float squash = 0f, squashV = 0f, tilt = 0f;
    private float downX, downY, lastX, lastY;
    private long downAt = 0, lastTouchAt = 0, lastTap = 0;
    private boolean dragging = false, longFired = false;

    private final Runnable longPress = new Runnable() {
        @Override public void run() {
            if (dragging) return;
            longFired = true;
            squashV += 3.6f;
            host.buzz(30);
            host.showMenu();
        }
    };

    public PetView(Context c, SpriteSheet[] sheets, PetService host) {
        super(c);
        this.sheets = sheets;
        this.host = host;
        setLayerType(LAYER_TYPE_HARDWARE, null);
        lastTouchAt = SystemClock.uptimeMillis();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (sheets == null) return;
        long now = SystemClock.uptimeMillis();
        SpriteSheet s = sheets[cur];
        if (now >= nextFrameAt) {
            nextFrameAt = now + (long) (s.interval * 1000f);
            frame = (frame + 1) % s.count;
        }

        // 果冻弹簧：欠阻尼，会多荡两下再停
        squashV += (-330f * squash - 14f * squashV) * 0.016f;
        squash += squashV * 0.016f;
        if (squash > 0.5f) squash = 0.5f;
        if (squash < -0.42f) squash = -0.42f;

        boolean moving = dragging || Math.abs(squash) > 0.004f || Math.abs(squashV) > 0.01f;

        if (dragging && host.blurOn()) {
            if (paint.getMaskFilter() == null) {
                paint.setMaskFilter(blur);
                setLayerType(LAYER_TYPE_SOFTWARE, null);
            }
        } else if (paint.getMaskFilter() != null) {
            paint.setMaskFilter(null);
            setLayerType(LAYER_TYPE_HARDWARE, null);
        }

        canvas.save();
        canvas.translate(getWidth() / 2f, getHeight() / 2f);
        canvas.rotate(tilt);
        canvas.scale(1f + squash * 0.28f, 1f - squash * 0.34f);
        canvas.translate(-getWidth() / 2f, -getHeight() / 2f);
        s.fitInto(getWidth(), getHeight());
        canvas.drawBitmap(s.sheet, s.rect(frame), s.dst, paint);
        canvas.restore();

        // 闲置自动半透明
        float want = 1f;
        if (host.fadeOn() && !dragging && now - lastTouchAt > IDLE_FADE_MS) want = 0.38f;
        if (Math.abs(getAlpha() - want) > 0.01f) setAlpha(want);

        if (moving) postInvalidateOnAnimation();
        else postInvalidateDelayed((long) (s.interval * 1000f));
    }

    public void setSheets(SpriteSheet[] s) {
        if (s != null) {
            sheets = s;
            cur = 0;
            frame = 0;
            squashV += 2.2f;
            invalidate();
        }
    }

    /** 换一套动画（idle / happy / drag / squash）。 */
    public void cycle() {
        if (sheets == null) return;
        cur = (cur + 1) % sheets.length;
        frame = 0;
        squashV += 4f;
        host.buzz(16);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = e.getRawX();
                downY = lastY = e.getRawY();
                downAt = SystemClock.uptimeMillis();
                lastTouchAt = downAt;
                dragging = false;
                longFired = false;
                squashV += 2.8f;
                h.postDelayed(longPress, LONG_PRESS_MS);
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = e.getRawX() - lastX;
                float dy = e.getRawY() - lastY;
                if (!dragging && Math.hypot(e.getRawX() - downX, e.getRawY() - downY) > 16) {
                    dragging = true;
                    h.removeCallbacks(longPress);
                }
                if (dragging) {
                    host.moveWindow(Math.round(dx), Math.round(dy));
                    tilt = Math.max(-14f, Math.min(14f, -dx * 1.6f));
                    if (squash > -0.06f) squash = -0.06f;   // 拖动时略微拉长
                }
                lastX = e.getRawX();
                lastY = e.getRawY();
                lastTouchAt = SystemClock.uptimeMillis();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                h.removeCallbacks(longPress);
                long dt = SystemClock.uptimeMillis() - downAt;
                if (!dragging && !longFired && dt < 300) {
                    long now = SystemClock.uptimeMillis();
                    if (now - lastTap < 300) {
                        lastTap = 0;
                        squashV += 3.2f;
                        host.buzz(18);
                    } else {
                        lastTap = now;
                        host.buzz(10);
                    }
                }
                if (dragging) squashV -= 2.4f;   // 松手回弹
                tilt = 0f;
                lastTouchAt = SystemClock.uptimeMillis();
                return true;
            }
        }
        return super.onTouchEvent(e);
    }
}
