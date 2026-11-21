package org.dsh.whalepet;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

/** 蓝黑渐变风格的半环功能菜单。 */
public class MenuView extends View {
    private final PetService host;
    private final String[] labels = {"聊天", "设置", "换肤", "隐藏", "收起"};
    private final float[] cx = new float[5];
    private final float[] cy = new float[5];
    private float r = 1f;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int downIdx = -1;

    public MenuView(Context c, PetService host) {
        super(c);
        this.host = host;
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(1.6f);
        text.setColor(0xFFDCE6FF);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
    }

    void reset() { downIdx = -1; invalidate(); }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float ccx = w * 0.36f, ccy = h * 0.50f;
        r = Math.min(w, h) * 0.118f;
        float dist = Math.min(w, h) * 0.315f;
        for (int i = 0; i < 5; i++) {
            double a = Math.toRadians(-90 + i * 45);
            cx[i] = ccx + (float) (Math.cos(a) * dist);
            cy[i] = ccy + (float) (Math.sin(a) * dist);
        }
        text.setTextSize(r * 0.50f);
    }

    @Override
    protected void onDraw(Canvas c) {
        Paint.FontMetrics fm = text.getFontMetrics();
        float baseline = -(fm.ascent + fm.descent) / 2f;
        for (int i = 0; i < 5; i++) {
            boolean down = i == downIdx;
            fill.setShader(new RadialGradient(cx[i], cy[i] - r * 0.4f, r * 1.7f,
                    down ? 0xFF415CBF : 0xFF243258, 0xFF0A0F1E, Shader.TileMode.CLAMP));
            c.drawCircle(cx[i], cy[i], r, fill);
            fill.setShader(null);

            ring.setShader(new LinearGradient(cx[i] - r, cy[i] - r, cx[i] + r, cy[i] + r,
                    0xFF6B8BFF, 0xFF16213F, Shader.TileMode.CLAMP));
            c.drawCircle(cx[i], cy[i], r, ring);
            ring.setShader(null);

            c.drawText(labels[i], cx[i], cy[i] + baseline, text);
        }
    }

    private int hit(float x, float y) {
        for (int i = 0; i < 5; i++) {
            float dx = x - cx[i], dy = y - cy[i];
            if (dx * dx + dy * dy <= r * r * 1.4f) return i;
        }
        return -1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downIdx = hit(x, y);
                invalidate();
                return downIdx >= 0;
            case MotionEvent.ACTION_UP: {
                int idx = hit(x, y);
                if (idx >= 0 && idx == downIdx) fire(idx);
                downIdx = -1;
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                downIdx = -1;
                invalidate();
                return true;
        }
        return super.onTouchEvent(e);
    }

    private void fire(int i) {
        switch (i) {
            case 0: host.showChat(); break;
            case 1: host.showSettings(); break;
            case 2: host.cycleSkin(); break;
            case 3: host.hidePet(); break;
            default: host.closePanel(); break;
        }
    }
}
