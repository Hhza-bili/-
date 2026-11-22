package org.dsh.whalepet;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.animation.OvershootInterpolator;

/** 围绕宠物的弧形气泡菜单（问一句 / 新对话 / 设置 / 换肤 / 隐藏）。 */
public class MenuView extends View {
    private static final String[] LABELS = {"问一句", "剪贴板", "设置", "换肤", "隐藏"};
    private static final int[] ANGLES = {108, 144, 180, 216, 252};

    private final PetService host;
    private final float[] bx = new float[5];
    private final float[] by = new float[5];
    private float petX, petY, btnR, ringR;
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int downIdx = -1;
    private float anim = 0f;
    private final float[] sc = new float[5];
    private final float[] scv = new float[5];
    private long lastFrame = 0;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable autoClose = new Runnable() {
        @Override public void run() { host.closePanel(); }
    };

    private void armAutoClose() {
        h.removeCallbacks(autoClose);
        h.postDelayed(autoClose, 5000);
    }
    private ValueAnimator va;

    public MenuView(Context c, PetService host) {
        super(c);
        this.host = host;
        for (int i = 0; i < 5; i++) sc[i] = 1f;
        icon.setStyle(Paint.Style.STROKE);
        icon.setStrokeCap(Paint.Cap.ROUND);
        label.setTextAlign(Paint.Align.CENTER);
        label.setFakeBoldText(true);
    }

    /** 弹簧式缩放：选中 1.30x，左右相邻 1.12x，其余微缩。 */
    private void stepScales() {
        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        for (int i = 0; i < 5; i++) {
            float target = 1f;
            if (downIdx >= 0) {
                int d = Math.abs(i - downIdx);
                target = d == 0 ? 1.30f : (d == 1 ? 1.12f : 0.93f);
            }
            scv[i] += (target - sc[i]) * 72f * dt - scv[i] * 11f * dt;
            sc[i] += scv[i] * dt;
        }
    }

    public void play() {
        armAutoClose();
        if (va != null) va.cancel();
        anim = 0f;
        va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(320);
        va.setInterpolator(new OvershootInterpolator(1.5f));
        va.addUpdateListener(a -> {
            anim = Math.min(1.12f, (float) a.getAnimatedValue());
            invalidate();
        });
        va.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        petX = w * 0.70f;
        petY = h * 0.52f;
        ringR = Math.min(w, h) * 0.30f;
        btnR = Math.min(w, h) * 0.090f;
        for (int i = 0; i < 5; i++) {
            double a = Math.toRadians(ANGLES[i]);
            bx[i] = petX + (float) (Math.cos(a) * ringR);
            by[i] = petY + (float) (Math.sin(a) * ringR);
        }
        label.setTextSize(btnR * 0.60f);
    }

    private float curX(int i) { return petX + (bx[i] - petX) * anim; }
    private float curY(int i) { return petY + (by[i] - petY) * anim; }

    @Override
    protected void onDraw(Canvas c) {
        stepScales();
        float k = anim;
        float fade = Math.min(1f, k * 1.8f);
        for (int i = 0; i < 5; i++) {
            float x = curX(i), y = curY(i);
            float r = btnR * sc[i] * (0.72f + 0.28f * Math.min(1f, k));
            boolean on = i == downIdx;

            bg.setColor(on ? 0xFF4D6BFE : 0xE62B3245);
            c.drawCircle(x, y, r, bg);
            icon.setColor(on ? 0xFFFFFFFF : 0xFFE6EDFF);
            icon.setStrokeWidth(Math.max(2f, r * 0.11f));
            icon.setAlpha((int) (255 * fade));
            drawIcon(c, i, x, y, r);

            double a = Math.toRadians(ANGLES[i]);
            float lx = x + (float) (Math.cos(a) * (r + label.getTextSize() * 1.7f));
            float ly = y + (float) (Math.sin(a) * (r + label.getTextSize() * 1.7f));
            Paint.FontMetrics fm = label.getFontMetrics();
            label.setColor(on ? 0xFF7C9BFF : 0xFFB7C4E6);
            label.setAlpha((int) (255 * fade));
            c.drawText(LABELS[i], lx, ly - (fm.ascent + fm.descent) / 2f, label);
        }
        icon.setAlpha(255);
        label.setAlpha(255);
        postInvalidateOnAnimation();
    }

    private void drawIcon(Canvas c, int i, float x, float y, float r) {
        switch (i) {
            case 0:
                rect.set(x - r * 0.42f, y - r * 0.36f, x + r * 0.42f, y + r * 0.20f);
                c.drawRoundRect(rect, r * 0.22f, r * 0.22f, icon);
                c.drawLine(x - r * 0.10f, y + r * 0.20f, x - r * 0.22f, y + r * 0.50f, icon);
                break;
            case 1:
                rect.set(x - r * 0.36f, y - r * 0.36f, x + r * 0.36f, y + r * 0.36f);
                c.drawRoundRect(rect, r * 0.16f, r * 0.16f, icon);
                c.drawLine(x - r * 0.17f, y, x + r * 0.17f, y, icon);
                c.drawLine(x, y - r * 0.17f, x, y + r * 0.17f, icon);
                break;
            case 2:
                c.drawCircle(x, y, r * 0.28f, icon);
                for (int k = 0; k < 6; k++) {
                    double a = Math.toRadians(k * 60);
                    c.drawLine(x + (float) (Math.cos(a) * r * 0.32f), y + (float) (Math.sin(a) * r * 0.32f),
                               x + (float) (Math.cos(a) * r * 0.48f), y + (float) (Math.sin(a) * r * 0.48f), icon);
                }
                break;
            case 3:
                c.drawCircle(x - r * 0.15f, y, r * 0.24f, icon);
                c.drawCircle(x + r * 0.19f, y, r * 0.24f, icon);
                break;
            default:
                c.drawLine(x - r * 0.26f, y - r * 0.26f, x + r * 0.26f, y + r * 0.26f, icon);
                c.drawLine(x + r * 0.26f, y - r * 0.26f, x - r * 0.26f, y + r * 0.26f, icon);
                break;
        }
    }

    /** 手指滑过时高亮跟手。 */
    public void hover(float x, float y) {
        armAutoClose();
        int idx = hit(x, y);
        if (idx != downIdx) {
            downIdx = idx;
            invalidate();
        }
    }

    /** 松手时执行当前高亮项。 */
    public void pick(float x, float y) {
        h.removeCallbacks(autoClose);
        int idx = hit(x, y);
        if (idx >= 0) {
            host.buzz(14);
            fire(idx);
        } else {
            host.closePanel();   // 没选中任何项 -> 收起
        }
        downIdx = -1;
        invalidate();
    }

    private int hit(float x, float y) {
        for (int i = 0; i < 5; i++) {
            float dx = x - curX(i), dy = y - curY(i);
            float r = btnR * 1.35f;
            if (dx * dx + dy * dy <= r * r) return i;
        }
        return -1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (anim < 0.8f) return false;
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downIdx = hit(x, y);
                invalidate();
                return downIdx >= 0;
            case MotionEvent.ACTION_MOVE:
                downIdx = hit(x, y);
                invalidate();
                return true;
            case MotionEvent.ACTION_UP: {
                int idx = hit(x, y);
                if (idx >= 0 && idx == downIdx) {
                    host.buzz(12);
                    fire(idx);
                }
                downIdx = -1;
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                h.removeCallbacks(autoClose);
                downIdx = -1;
                invalidate();
                return true;
        }
        return super.onTouchEvent(e);
    }

    private void fire(int i) {
        switch (i) {
            case 0: host.showChat(); break;
            case 1: host.showClips(); break;
            case 2: host.showSettings(); break;
            case 3: host.cycleSkin(); break;
            default: host.hidePet(); break;
        }
    }
}
