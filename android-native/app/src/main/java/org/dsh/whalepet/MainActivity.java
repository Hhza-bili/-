package org.dsh.whalepet;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** 设置页：权限、Key、开关、召唤 / 隐藏。 */
public class MainActivity extends Activity {
    private LinearLayout box;
    private SharedPreferences sp;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("pet", MODE_PRIVATE);
        build();
    }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void build() {
        ScrollView sc = new ScrollView(this);
        sc.setBackgroundColor(0xFF080B14);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(22);
        box.setPadding(p, p, p, p);
        sc.addView(box);
        setContentView(sc);

        TextView title = new TextView(this);
        title.setText("鲸鱼娘");
        title.setTextColor(0xFFEAF0FF);
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(title);

        TextView sub = new TextView(this);
        sub.setText("嘉诚编程社高二四班组期末作业 · Android 桌宠");
        sub.setTextColor(0xFF5D6B94);
        sub.setTextSize(12);
        sub.setPadding(0, dp(6), 0, dp(20));
        box.addView(sub);

        // 权限状态
        LinearLayout perm = new LinearLayout(this);
        perm.setOrientation(LinearLayout.HORIZONTAL);
        perm.setGravity(Gravity.CENTER_VERTICAL);
        TextView pt = new TextView(this);
        pt.setText("悬浮窗权限");
        pt.setTextColor(0xFFC8D4FF);
        pt.setTextSize(15);
        perm.addView(pt, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView pv = new TextView(this);
        pv.setText(hasOverlay() ? "已开启" : "未开启 · 点此授权");
        pv.setTextColor(hasOverlay() ? 0xFF4ADE80 : 0xFFFF8A65);
        pv.setTextSize(13);
        pv.setOnClickListener(v -> openOverlaySetting());
        perm.addView(pv);
        box.addView(perm);

        gap();

        box.addView(label("DeepSeek API Key"));
        EditText key = new EditText(this);
        key.setHint("sk-...");
        key.setText(sp.getString("key", ""));
        key.setTextColor(0xFFEAF0FF);
        key.setHintTextColor(0xFF4A5578);
        key.setBackgroundColor(0xFF111726);
        key.setPadding(dp(14), dp(12), dp(14), dp(12));
        key.setSingleLine(true);
        box.addView(key);
        gap();

        Button save = button("保存 Key", 0xFF3D5AFE);
        save.setOnClickListener(v -> {
            sp.edit().putString("key", key.getText().toString().trim()).apply();
            toast("Key 已保存");
        });
        box.addView(save);

        gap();
        addSwitch("开机自启动", "autostart", true);
        addSwitch("拖拽动态模糊", "blur", true);
        addSwitch("闲置自动半透明", "fade", true);

        gap();
        Button show = button("召唤桌宠", 0xFF2E3F9E);
        show.setOnClickListener(v -> {
            if (!hasOverlay()) { openOverlaySetting(); return; }
            Intent i = new Intent(this, PetService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
            toast("桌宠已召唤，长按它打开功能栏");
            finish();
        });
        box.addView(show);

        gap();
        Button hide = button("隐藏桌宠", 0xFF1B2236);
        hide.setOnClickListener(v -> {
            stopService(new Intent(this, PetService.class));
            toast("已隐藏");
        });
        box.addView(hide);

        TextView foot = new TextView(this);
        foot.setText("v2.1  ·  长按桌宠呼出功能栏");
        foot.setTextColor(0xFF3A4363);
        foot.setTextSize(11);
        foot.setPadding(0, dp(24), 0, 0);
        box.addView(foot);
    }

    private void gap() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(14)));
        box.addView(v);
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(0xFF8FA0CC);
        t.setTextSize(13);
        t.setPadding(0, 0, 0, dp(8));
        return t;
    }

    private Button button(String s, int color) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(0xFFFFFFFF);
        b.setTextSize(15);
        b.setBackgroundColor(color);
        b.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
        b.setLayoutParams(lp);
        return b;
    }

    private void addSwitch(String text, String key, boolean def) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xFFC8D4FF);
        t.setTextSize(15);
        row.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Switch sw = new Switch(this);
        sw.setChecked(sp.getBoolean(key, def));
        sw.setOnCheckedChangeListener((b, v) -> sp.edit().putBoolean(key, v).apply());
        row.addView(sw);
        box.addView(row);
    }

    private boolean hasOverlay() {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
    }

    private void openOverlaySetting() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            toast("请到系统设置里手动授权");
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }
}
