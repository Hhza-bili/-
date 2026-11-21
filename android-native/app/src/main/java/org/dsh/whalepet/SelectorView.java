package org.dsh.whalepet;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

/** 滑动选择器：中间项放大 + 流动液态玻璃背景。 */
public class SelectorView extends View {
    private final PetService host;
    private final String[] labels = {"聊天", "设置", "换肤", "隐藏", "收起"};
    private final Paint glass = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ball = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float offset = 0f;
    private float lastX = 0f;
    private boolean dragging = false;
    private long born = 0;
    private float spacing = 1f;
    private ValueAnimator anim;

    public SelectorView(Context c, PetService host) {
        super(c);
        this.host = host;
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1.4f);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        text.setColor(0xFFE6EDFF);
        born = SystemClock.uptimeMillis();
    }

    public void reset() {
        offset = 0f;
        born = SystemClock.uptimeMillis();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        spacing = w / 5.2f;
        text.setTextSize(h * 0.075f);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        float t = (SystemClock.uptimeMillis() - born) / 1000f;

        // —— 液态玻璃底 ——
        glass.setShader(new LinearGradient(0, 0, w, h,
                new int[]{0xE60A0F1E, 0xC8111A33, 0xE60A0F1E},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawRoundRect(0, 0, w, h, h * 0.42f, h * 0.42f, glass);
        glass.setShader(null);

        // —— 缓慢游动的光斑 ——
        for (int i = 0; i < 3; i++) {
            float bx = w * 0.5f + (float) Math.sin(t * 0.55f + i * 2.2f) * w * 0.34f;
            float by = h * 0.5f + (float) Math.cos(t * 0.8f + i * 1.9f) * h * 0.24f;
            float br = h * (0.85f + 0.15f * (float) Math.sin(t * 0.9f + i));
            blob.setShader(new RadialGradient(bx, by, br,
                    new int[]{0x3A3D6BFF, 0x00000000}, null, Shader.TileMode.CLAMP));
            c.drawCircle(bx, by, br, blob);
        }
        blob.setShader(null);

        // —— 选项 ——
        float cx = w / 2f, cy = h * 0.5f;
        Paint.FontMetrics fm = text.getFontMetrics();
        float baseline = -(fm.ascent + fm.descent) / 2f;

        for (int i = 0; i < labels.length; i++) {
            float d = i - offset;
            float ad = Math.abs(d);
            if (ad > 2.3f) continue;
            float scale = 1.34f - Math.min(ad, 2f) * 0.27f;
            float x = cx + d * spacing;
            float r = h * 0.20f * scale;
            int alpha = (int) (255 * (1f - Math.min(ad, 2f) * 0.3f));

            if (ad < 0.55f) {
                halo.setColor(0x555B8CFF);
                c.drawCircle(x, cy, r * 1.55f, halo);
            }

            ball.setShader(new RadialGradient(x, cy - r * 0.35f, r * 1.45f,
                    new int[]{0xFF2E427D, 0xFF0C1224}, null, Shader.TileMode.CLAMP));
            ball.setAlpha(alpha);
            c.drawCircle(x, cy, r, ball);
            ball.setShader(null);
            ball.setAlpha(255);

            rim.setShader(new LinearGradient(x - r, cy - r, x + r, cy + r,
                    new int[]{0xFF7C9BFF, 0xFF16213F}, null, Shader.TileMode.CLAMP));
            rim.setAlpha(alpha);
            c.drawCircle(x, cy, r, rim);
            rim.setShader(null);
            rim.setAlpha(255);

            text.setTextSize(r * 0.62f);
            text.setAlpha(alpha);
            c.drawText(labels[i], x, cy + baseline, text);
        }
        text.setAlpha(255);
        postInvalidateOnAnimation();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (anim != null) anim.cancel();
                lastX = e.getX();
                dragging = true;
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = e.getX() - lastX;
                lastX = e.getX();
                offset -= dx / spacing;
                offset = Math.max(-0.4f, Math.min(labels.length - 0.6f, offset));
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                dragging = false;
                int target = Math.round(offset);
                target = Math.max(0, Math.min(labels.length - 1, target));
                animTo(target);
                return true;
            }
        }
        return super.onTouchEvent(e);
    }

    private void animTo(final int target) {
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(offset, target);
        anim.setDuration(230);
        anim.addUpdateListener(a -> {
            offset = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                host.onSelectorPick(target);
            }
        });
        anim.start();
    }
}
