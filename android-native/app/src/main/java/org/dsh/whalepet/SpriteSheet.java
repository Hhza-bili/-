package org.dsh.whalepet;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;

import org.json.JSONObject;

import java.io.InputStream;

/** 一张精灵表 + 帧取景。 */
public class SpriteSheet {
    public Bitmap sheet;
    public int cols, rows, count, fw, fh, cellW, cellH, pad;
    public float interval;
    private final Rect src = new Rect();
    public final Rect dst = new Rect();

    public static SpriteSheet load(AssetManager am, JSONObject meta) throws Exception {
        SpriteSheet s = new SpriteSheet();
        s.cols = meta.getInt("cols");
        s.rows = meta.getInt("rows");
        s.count = meta.getInt("count");
        s.fw = meta.getInt("frame_w");
        s.fh = meta.getInt("frame_h");
        s.cellW = meta.optInt("cell_w", s.fw);
        s.cellH = meta.optInt("cell_h", s.fh);
        s.pad = meta.optInt("pad", 0);
        s.interval = (float) meta.optDouble("interval", 0.08);
        try (InputStream in = am.open(meta.getString("file"))) {
            s.sheet = BitmapFactory.decodeStream(in);
        }
        return s;
    }

    public Rect rect(int frame) {
        int f = Math.max(0, Math.min(frame, count - 1));
        int r = f / cols, c = f % cols;
        int x = c * cellW + pad, y = r * cellH + pad;
        src.set(x, y, x + fw, y + fh);
        return src;
    }

    /** 按 view 尺寸等比居中。 */
    public void fitInto(int vw, int vh) {
        float scale = Math.min(vw / (float) fw, vh / (float) fh);
        int w = Math.round(fw * scale), h = Math.round(fh * scale);
        int l = (vw - w) / 2, t = (vh - h) / 2;
        dst.set(l, t, l + w, t + h);
    }
}
