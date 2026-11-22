package org.dsh.whalepet;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/** 悬浮窗里的设置面板：Key、人物大小、开关。 */
public class SettingsView extends LinearLayout {
    private final PetService host;
    private final SharedPreferences sp;

    public SettingsView(Context c, PetService host) {
        super(c);
        this.host = host;
        this.sp = c.getSharedPreferences("pet", Context.MODE_PRIVATE);
        setOrientation(VERTICAL);
        setBackgroundColor(0xF20B0F1C);
        int p = pd(c, 14);
        setPadding(p, p, p, p);

        LinearLayout head = new LinearLayout(c);
        head.setOrientation(HORIZONTAL);
        TextView title = new TextView(c);
        title.setText("设置");
        title.setTextColor(0xFFB8CBFF);
        title.setTextSize(17);
        head.addView(title, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        Button close = new Button(c);
        close.setText("×");
        close.setTextSize(20);
        close.setBackgroundColor(Color.TRANSPARENT);
        close.setTextColor(0xFFCCD6FF);
        close.setOnClickListener(v -> host.closePanel());
        head.addView(close, new LayoutParams(pd(c, 44), pd(c, 40)));
        addView(head);

        ScrollView sc = new ScrollView(c);
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(VERTICAL);
        sc.addView(box);
        addView(sc, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        box.addView(label("DeepSeek API Key"));
        EditText key = new EditText(c);
        key.setHint("sk-... 留空则用免费接口");
        key.setText(sp.getString("key", ""));
        key.setTextColor(0xFFEAF0FF);
        key.setHintTextColor(0xFF4A5578);
        key.setBackgroundColor(0xFF141B2E);
        key.setPadding(pd(c, 12), pd(c, 10), pd(c, 12), pd(c, 10));
        key.setSingleLine(true);
        box.addView(key);
        Button save = button(c, "保存 Key", 0xFF3D5AFE);
        save.setOnClickListener(v -> {
            sp.edit().putString("key", key.getText().toString().trim()).apply();
            host.updateKey();
            toast(c, "已保存");
        });
        box.addView(save);

        box.addView(gap(c, 14));
        final TextView sizeLabel = label("人物大小  " + Math.round(host.petScale() * 100) + "%");
        box.addView(sizeLabel);
        SeekBar sb = new SeekBar(c);
        sb.setMax(80);                       // 0.6x ~ 1.4x
        sb.setProgress(Math.round((host.petScale() - 0.6f) / 0.8f * 80));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean fromUser) {
                float scale = 0.6f + v / 80f * 0.8f;
                sizeLabel.setText("人物大小  " + Math.round(scale * 100) + "%");
                host.setPetScale(scale);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        box.addView(sb);

        box.addView(gap(c, 10));
        box.addView(toggle(c, "头顶余额气泡", "balance", true));
        box.addView(toggle(c, "剪贴板记录", "clip", true));
        box.addView(toggle(c, "拖拽动态模糊", "blur", true));
        box.addView(toggle(c, "闲置自动半透明", "fade", true));
        box.addView(toggle(c, "开机自启动", "autostart", true));

        box.addView(gap(c, 12));
        Button sync = button(c, "立即同步剪贴板", 0xFF2E3F9E);
        sync.setOnClickListener(v -> host.showClips());
        box.addView(sync);

        TextView foot = new TextView(c);
        foot.setText("Android 10+ 后台读不到剪贴板，后台复制的内容需要点这里同步。");
        foot.setTextColor(0xFF4A5578);
        foot.setTextSize(11);
        foot.setPadding(0, pd(c, 10), 0, 0);
        box.addView(foot);
    }

    private LinearLayout toggle(Context c, String text, String key, boolean def) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(HORIZONTAL);
        row.setPadding(0, pd(c, 6), 0, pd(c, 6));
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(0xFFC8D4FF);
        t.setTextSize(14);
        row.addView(t, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        Switch sw = new Switch(c);
        sw.setChecked(sp.getBoolean(key, def));
        sw.setOnCheckedChangeListener((b, v) -> {
            sp.edit().putBoolean(key, v).apply();
            host.refreshToggles();
        });
        row.addView(sw);
        return row;
    }

    private TextView label(String s) {
        TextView t = new TextView(getContext());
        t.setText(s);
        t.setTextColor(0xFF8FA0CC);
        t.setTextSize(12);
        t.setPadding(0, 0, 0, pd(getContext(), 6));
        return t;
    }

    private Button button(Context c, String s, int color) {
        Button b = new Button(c);
        b.setText(s);
        b.setTextColor(0xFFFFFFFF);
        b.setBackgroundColor(color);
        b.setAllCaps(false);
        b.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, pd(c, 42)));
        return b;
    }

    private android.view.View gap(Context c, float h) {
        android.view.View v = new android.view.View(c);
        v.setLayoutParams(new LayoutParams(1, pd(c, h)));
        return v;
    }

    private void toast(Context c, String s) {
        android.widget.Toast.makeText(c, s, android.widget.Toast.LENGTH_SHORT).show();
    }

    private static int pd(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
