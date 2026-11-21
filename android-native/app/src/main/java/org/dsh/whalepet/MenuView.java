package org.dsh.whalepet;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/** 宠物旁边的半环形功能菜单。 */
public class MenuView extends View {
    private final PetService host;
    private final String[] labels = {"问一句", "聊天", "设置", "收起"};
    private final float[] cx = new float[4];
    private final float[] cy = new float[4];
    private float radius = 1f;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int downIdx = -1;

    public MenuView(Context c, PetService host) {
        super(c);
        this.host = host;
        ring.setColor(0xFF4D6BFE);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(2.5f);
        text.setColor(Color.WHITE);
        text.setTextAlign(Paint.Align.CENTER);
    }

    void reset() { downIdx = -1; invalidate(); }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float ccx = w * 0.40f, ccy = h * 0.50f;
        radius = Math.min(w, h) * 0.135f;
        float dist = Math.min(w, h) * 0.31f;
        for (int i = 0; i < 4; i++) {
            double a = Math.toRadians(-78 + i * 52);
            cx[i] = ccx + (float) (Math.cos(a) * dist);
            cy[i] = ccy + (float) (Math.sin(a) * dist);
        }
        text.setTextSize(radius * 0.55f);
    }

    @Override
    protected void onDraw(Canvas c) {
        Paint.FontMetrics fm = text.getFontMetrics();
        float baseline = -(fm.ascent + fm.descent) / 2f;
        for (int i = 0; i < 4; i++) {
            fill.setColor(i == downIdx ? 0xF03B4FA8 : 0xF01A2247);
            c.drawCircle(cx[i], cy[i], radius, fill);
            c.drawCircle(cx[i], cy[i], radius, ring);
            c.drawText(labels[i], cx[i], cy[i] + baseline, text);
        }
    }

    private int hit(float x, float y) {
        for (int i = 0; i < 4; i++) {
            float dx = x - cx[i], dy = y - cy[i];
            if (dx * dx + dy * dy <= radius * radius * 1.35f) return i;
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
            case 0:
            case 1:
                host.showChat();
                break;
            case 2:
                host.showSettings();
                break;
            default:
                host.closePanel();
                break;
        }
    }
}
