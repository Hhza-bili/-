package org.dsh.whalepet;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

/** 宠物本体：逐帧动画 + 压扁弹簧 + 拖动 / 双击。 */
public class PetView extends View {
    private final PetService host;
    private final SpriteSheet[] sheets;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private int cur = 0, frame = 0;
    private long nextFrameAt = 0;
    private float squash = 0f, squashV = 0f, tilt = 0f;
    private float downX, downY, lastX, lastY;
    private long downAt = 0, lastTap = 0;
    private boolean dragging = false;

    public PetView(Context c, SpriteSheet[] sheets, PetService host) {
        super(c);
        this.sheets = sheets;
        this.host = host;
        setLayerType(LAYER_TYPE_HARDWARE, null);
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

        squashV += (-340f * squash - 16f * squashV) * 0.016f;
        squash += squashV * 0.016f;
        if (squash > 0.55f) squash = 0.55f;
        if (squash < -0.45f) squash = -0.45f;

        canvas.save();
        canvas.translate(getWidth() / 2f, getHeight() / 2f);
        canvas.rotate(tilt);
        canvas.scale(1f + squash * 0.26f, 1f - squash * 0.34f);
        canvas.translate(-getWidth() / 2f, -getHeight() / 2f);
        s.fitInto(getWidth(), getHeight());
        canvas.drawBitmap(s.sheet, s.rect(frame), s.dst, paint);
        canvas.restore();

        postInvalidateOnAnimation();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = e.getRawX();
                downY = lastY = e.getRawY();
                downAt = SystemClock.uptimeMillis();
                dragging = false;
                squashV += 2.4f;
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = e.getRawX() - lastX;
                float dy = e.getRawY() - lastY;
                if (Math.abs(e.getRawX() - downX) + Math.abs(e.getRawY() - downY) > 14) {
                    dragging = true;
                }
                if (dragging) {
                    host.moveWindow(Math.round(dx), Math.round(dy));
                    tilt = Math.max(-16f, Math.min(16f, -dx * 2f));
                }
                lastX = e.getRawX();
                lastY = e.getRawY();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                long dt = SystemClock.uptimeMillis() - downAt;
                if (!dragging && dt < 320) {
                    long now = SystemClock.uptimeMillis();
                    if (now - lastTap < 320) {
                        lastTap = 0;
                        host.showMenu();
                    } else {
                        lastTap = now;
                        host.buzz(14);
                    }
                }
                tilt = 0f;
                return true;
            }
        }
        return super.onTouchEvent(e);
    }
}
