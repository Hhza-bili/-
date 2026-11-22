package org.dsh.whalepet;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;

/** 剪贴板历史面板，点条目可复制回去。 */
public class ClipView extends LinearLayout {
    private final ArrayList<String> clips;
    private final PetService host;
    private final LinearLayout list;

    public ClipView(Context c, ArrayList<String> clips, PetService host) {
        super(c);
        this.clips = clips;
        this.host = host;
        setOrientation(VERTICAL);
        setBackgroundColor(0xF20D1020);
        int p = pd(c, 10);
        setPadding(p, p, p, p);

        LinearLayout head = new LinearLayout(c);
        head.setOrientation(HORIZONTAL);
        TextView t = new TextView(c);
        t.setText("剪贴板记录");
        t.setTextColor(0xFFB8CBFF);
        t.setTextSize(16);
        head.addView(t, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        Button close = new Button(c);
        close.setText("×");
        close.setTextSize(20);
        close.setBackgroundColor(Color.TRANSPARENT);
        close.setTextColor(0xFFCCD6FF);
        close.setOnClickListener(v -> host.closePanel());
        head.addView(close, new LayoutParams(pd(c, 44), pd(c, 40)));
        addView(head);

        ScrollView sc = new ScrollView(c);
        list = new LinearLayout(c);
        list.setOrientation(VERTICAL);
        sc.addView(list);
        addView(sc, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
    }

    public void refresh() {
        list.removeAllViews();
        if (clips.isEmpty()) {
            TextView e = new TextView(getContext());
            e.setText("还没有记录，去别的 App 复制点东西试试～");
            e.setTextColor(0xFF6E7AA8);
            e.setTextSize(13);
            list.addView(e);
            return;
        }
        for (int i = 0; i < clips.size(); i++) {
            final String s = clips.get(i);
            TextView tv = new TextView(getContext());
            tv.setText(s);
            tv.setTextColor(0xFFE6EDFF);
            tv.setTextSize(13);
            tv.setPadding(pd(getContext(), 10), pd(getContext(), 10),
                    pd(getContext(), 10), pd(getContext(), 10));
            tv.setBackgroundColor(0xFF151B2E);
            tv.setMaxLines(3);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            lp.topMargin = pd(getContext(), 6);
            tv.setLayoutParams(lp);
            tv.setOnClickListener(v -> {
                try {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager)
                            getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("whale", s));
                    host.buzz(12);
                    host.closePanel();
                } catch (Exception ignored) {
                }
            });
            list.addView(tv);
        }
    }

    private static int pd(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
