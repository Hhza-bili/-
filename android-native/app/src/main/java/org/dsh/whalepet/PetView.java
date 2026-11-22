package org.dsh.whalepet;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

/** 宠物：动画 / 果冻 / 长按滑动选菜单 / 头顶气泡。 */
public class PetView extends View {
    private static final long LONG_PRESS_MS = 460;
    private static final long IDLE_FADE_MS = 8000;

    private final PetService host;
    private final SpriteSheet[] sheets = new SpriteSheet[4];
    private int sheetCount;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final BlurMaskFilter blur = new BlurMaskFilter(9f, BlurMaskFilter.Blur.NORMAL);
    private final Handler h = new Handler(Looper.getMainLooper());

    private final Paint bubbleBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubbleTx = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubbleTri = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bubbleRect = new RectF();
    private String bubbleText = null;
    private long bubbleUntil = 0;

    private int cur = 0, frame = 0;
    private long nextFrameAt = 0;
    private float squash = 0f, squashV = 0f, tilt = 0f;
    private float downX, downY, lastX, lastY;
    private long downAt = 0, lastTouchAt = 0, lastTap = 0;
    private boolean dragging = false, longFired = false, menuMode = false;
    private boolean menuLayout = false;

    private final Runnable longPress = new Runnable() {
        @Override public void run() {
            if (dragging) return;
            longFired = true;
            menuMode = true;
            squashV += 3.6f;
            host.buzz(30);
            host.showMenu();
        }
    };

    public PetView(Context c, SpriteSheet[] src, PetService host) {
        super(c);
        this.host = host;
        setSheets(src);
        setLayerType(LAYER_TYPE_HARDWARE, null);
        lastTouchAt = SystemClock.uptimeMillis();
        bubbleBg.setColor(0xF2FFFFFF);
        bubbleTx.setColor(0xFF1B2A55);
        bubbleTx.setTextAlign(Paint.Align.CENTER);
        bubbleTx.setFakeBoldText(true);
        bubbleTri.setColor(0xF2FFFFFF);
    }

    public void setSheets(SpriteSheet[] src) {
        if (src == null) return;
        sheetCount = src.length;
        for (int i = 0; i < src.length && i < sheets.length; i++) sheets[i] = src[i];
        cur = 0;
        frame = 0;
        squashV += 2.2f;
        invalidate();
    }

    /** 菜单展开时宠物换到弧形菜单的中心位置。 */
    public void setMenuLayout(boolean on) {
        menuLayout = on;
        invalidate();
    }

    /** 头顶气泡：余额 / 剪贴板内容。 */
    public void setBubble(String text, long holdMs) {
        bubbleText = text;
        bubbleUntil = SystemClock.uptimeMillis() + holdMs;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (sheetCount == 0) return;
        long now = SystemClock.uptimeMillis();
        SpriteSheet s = sheets[Math.min(cur, sheetCount - 1)];
        if (now >= nextFrameAt) {
            nextFrameAt = now + (long) (s.interval * 1000f);
            frame = (frame + 1) % s.count;
        }

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

        int w = getWidth(), hgt = getHeight();
        int l, t, bw, bh;
        if (menuLayout) {
            float size = Math.min(w, hgt) * 0.40f;
            float cx = w * 0.70f, cy = hgt * 0.52f;
            float scale = size / Math.max(s.fw, s.fh);
            bw = Math.round(s.fw * scale);
            bh = Math.round(s.fh * scale);
            l = Math.round(cx - bw / 2f);
            t = Math.round(cy - bh / 2f);
        } else {
            int areaH = (int) (hgt * 0.76f);
            int top = hgt - areaH;
            float scale = Math.min(w / (float) s.fw, areaH / (float) s.fh);
            bw = Math.round(s.fw * scale);
            bh = Math.round(s.fh * scale);
            l = (w - bw) / 2;
            t = top + (areaH - bh) / 2;
        }
        s.dst.set(l, t, l + bw, t + bh);

        canvas.save();
        canvas.translate(l + bw / 2f, t + bh / 2f);
        canvas.rotate(tilt);
        canvas.scale(1f + squash * 0.28f, 1f - squash * 0.34f);
        canvas.translate(-(l + bw / 2f), -(t + bh / 2f));
        canvas.drawBitmap(s.sheet, s.rect(frame), s.dst, paint);
        canvas.restore();

        if (!menuLayout) drawBubble(canvas, now);

        float want = 1f;
        if (host.fadeOn() && !dragging && now - lastTouchAt > IDLE_FADE_MS) want = 0.38f;
        if (Math.abs(getAlpha() - want) > 0.01f) setAlpha(want);

        if (moving || (bubbleText != null && now < bubbleUntil)) postInvalidateOnAnimation();
        else postInvalidateDelayed((long) (s.interval * 1000f));
    }

    private void drawBubble(Canvas c, long now) {
        if (bubbleText == null || now > bubbleUntil) return;
        String txt = bubbleText.length() > 18 ? bubbleText.substring(0, 18) + "…" : bubbleText;
        bubbleTx.setTextSize(getHeight() * 0.055f);
        float tw = bubbleTx.measureText(txt);
        float padH = getWidth() * 0.06f;
        float padV = getHeight() * 0.022f;
        float bw = Math.min(getWidth() * 0.94f, tw + padH * 2);
        float bh = getHeight() * 0.11f;
        float cx = getWidth() / 2f;
        float by = getHeight() * 0.045f;

        bubbleRect.set(cx - bw / 2, by, cx + bw / 2, by + bh);
        c.drawRoundRect(bubbleRect, bh / 2, bh / 2, bubbleBg);
        // 小尖角
        float tipX = cx, tipY = by + bh;
        android.graphics.Path p = new android.graphics.Path();
        p.moveTo(tipX - bh * 0.22f, tipY - 1);
        p.lineTo(tipX, tipY + bh * 0.42f);
        p.lineTo(tipX + bh * 0.22f, tipY - 1);
        p.close();
        c.drawPath(p, bubbleTri);

        Paint.FontMetrics fm = bubbleTx.getFontMetrics();
        c.drawText(txt, cx, by + bh / 2 - (fm.ascent + fm.descent) / 2, bubbleTx);
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
                menuMode = false;
                squashV += 2.8f;
                h.postDelayed(longPress, LONG_PRESS_MS);
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (menuMode) {
                    host.menuHover(e.getRawX(), e.getRawY());
                    return true;
                }
                float dx = e.getRawX() - lastX;
                float dy = e.getRawY() - lastY;
                if (!dragging && Math.hypot(e.getRawX() - downX, e.getRawY() - downY) > 16) {
                    dragging = true;
                    h.removeCallbacks(longPress);
                }
                if (dragging) {
                    host.moveWindow(Math.round(dx), Math.round(dy));
                    tilt = Math.max(-14f, Math.min(14f, -dx * 1.6f));
                    if (squash > -0.06f) squash = -0.06f;
                }
                lastX = e.getRawX();
                lastY = e.getRawY();
                lastTouchAt = SystemClock.uptimeMillis();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                h.removeCallbacks(longPress);
                if (menuMode) {
                    menuMode = false;
                    if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                        host.menuPick(e.getRawX(), e.getRawY());
                    }
                    return true;
                }
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
                if (dragging) squashV -= 2.4f;
                tilt = 0f;
                lastTouchAt = SystemClock.uptimeMillis();
                return true;
            }
        }
        return super.onTouchEvent(e);
    }

    public void cycle() {
        if (sheetCount == 0) return;
        cur = (cur + 1) % sheetCount;
        frame = 0;
        squashV += 4f;
        host.buzz(16);
    }
}
