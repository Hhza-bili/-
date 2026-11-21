package org.dsh.whalepet;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/** 悬浮对话面板，直接在当前悬浮窗里聊天。 */
public class ChatView extends LinearLayout {
    private final PetService host;
    private final LinearLayout msgs;
    private final ScrollView scroll;
    private final EditText input;
    private final JSONArray history = new JSONArray();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean busy = false;

    public ChatView(Context c, PetService host) {
        super(c);
        this.host = host;
        setOrientation(VERTICAL);
        setBackgroundColor(0xF20D1020);
        int pad = pd(c, 10);
        setPadding(pad, pad, pad, pad);

        LinearLayout head = new LinearLayout(c);
        head.setOrientation(HORIZONTAL);
        TextView title = new TextView(c);
        title.setText("鲸鱼娘");
        title.setTextColor(0xFFB8CBFF);
        title.setTextSize(16);
        head.addView(title, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        Button close = new Button(c);
        close.setText("×");
        close.setTextSize(20);
        close.setBackgroundColor(Color.TRANSPARENT);
        close.setTextColor(0xFFCCD6FF);
        close.setOnClickListener(v -> host.closePanel());
        head.addView(close, new LayoutParams(pd(c, 44), pd(c, 40)));
        addView(head);

        scroll = new ScrollView(c);
        msgs = new LinearLayout(c);
        msgs.setOrientation(VERTICAL);
        scroll.addView(msgs);
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        input = new EditText(c);
        input.setHint("和本鲸说点什么…");
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0xFF6E7AA8);
        input.setTextSize(14);
        input.setSingleLine(true);
        input.setBackgroundColor(0xFF1A2036);
        input.setPadding(pd(c, 10), pd(c, 8), pd(c, 10), pd(c, 8));
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setOnEditorActionListener((v, actionId, event) -> { send(); return true; });
        row.addView(input, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        Button send = new Button(c);
        send.setText("发送");
        send.setTextSize(13);
        send.setBackgroundColor(0xFF3D5AFE);
        send.setTextColor(Color.WHITE);
        send.setOnClickListener(v -> send());
        LinearLayout.LayoutParams sp = new LayoutParams(pd(c, 64), pd(c, 42));
        sp.leftMargin = pd(c, 8);
        row.addView(send, sp);
        addView(row);

        bubble("呜，本鲸在的！有什么想聊的？", false);
    }

    public void onOpen() {
        input.requestFocus();
        postDelayed(() -> {
            try {
                InputMethodManager imm = (InputMethodManager)
                        getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            } catch (Exception ignored) {}
        }, 250);
    }

    private void send() {
        String text = input.getText().toString().trim();
        if (text.isEmpty() || busy) return;
        String key = host.getKey();
        if (key.isEmpty()) {
            bubble("还没配置 API Key，点菜单里的「设置」填一下。", false);
            return;
        }
        input.setText("");
        bubble(text, true);
        try {
            JSONObject m = new JSONObject();
            m.put("role", "user");
            m.put("content", text);
            history.put(m);
        } catch (Exception ignored) {}

        busy = true;
        final TextView pending = bubble("…", false);
        final JSONArray snap = new JSONArray();
        int from = Math.max(0, history.length() - 12);
        for (int i = from; i < history.length(); i++) {
            JSONObject o = history.optJSONObject(i);
            if (o != null) snap.put(o);
        }
        new Thread(() -> {
            try {
                String reply = DeepSeek.chat(key, snap);
                ui.post(() -> done(pending, reply));
            } catch (Exception e) {
                ui.post(() -> fail(pending, e.getMessage()));
            }
        }).start();
    }

    private void done(TextView pending, String reply) {
        busy = false;
        pending.setText(reply);
        try {
            JSONObject m = new JSONObject();
            m.put("role", "assistant");
            m.put("content", reply);
            history.put(m);
        } catch (Exception ignored) {}
    }

    private void fail(TextView pending, String err) {
        busy = false;
        String s = err == null ? "未知错误" : err;
        if (s.length() > 110) s = s.substring(0, 110) + "…";
        pending.setText("呜…出错了：" + s);
    }

    private TextView bubble(String text, boolean mine) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextSize(14);
        tv.setTextColor(mine ? 0xFF9FB6FF : 0xFFF2F4FF);
        int p = pd(getContext(), 9);
        tv.setPadding(p, p, p, p);
        tv.setBackgroundColor(mine ? 0xFF1B2650 : 0xFF171C2E);
        tv.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.78f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.gravity = mine ? Gravity.END : Gravity.START;
        lp.topMargin = pd(getContext(), 4);
        msgs.addView(tv, lp);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        return tv;
    }

    private static int pd(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
