package org.dsh.whalepet;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** 持有悬浮窗，负责在宠物 / 菜单 / 对话三种形态间切换。 */
public class PetService extends Service {
    static final int D_SMALL = 190;
    static final int D_MENU = 300;
    static final int D_CHAT_H = 430;

    private WindowManager wm;
    private FrameLayout root;
    private WindowManager.LayoutParams lp;
    private PetView petView;
    private MenuView menuView;
    private SelectorView selectorView;
    private ChatView chatView;
    private SpriteSheet[] sheets;
    private int screenW, screenH;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;
        startForeground(1, buildNotify());
        loadSheets();
        addPet();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (root == null) addPet();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (root != null && root.getParent() != null) {
            try { wm.removeView(root); } catch (Exception ignored) {}
        }
        root = null;
        super.onDestroy();
    }

    // ------------------------------------------------------------------
    int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private Notification buildNotify() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel("pet", "桌宠", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, "pet") : new Notification.Builder(this);
        return b.setContentTitle("鲸鱼娘在桌面上")
                .setContentText("点这里可以重新召唤")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void loadSheets() {
        try {
            JSONObject meta = new JSONObject(readAsset("anim_meta.json"));
            String[] names = {"idle", "happy", "drag", "squash"};
            sheets = new SpriteSheet[names.length];
            for (int i = 0; i < names.length; i++) {
                sheets[i] = SpriteSheet.load(getAssets(), meta.getJSONObject(names[i]));
            }
        } catch (Exception e) {
            sheets = null;
        }
    }

    private String readAsset(String name) throws Exception {
        InputStream in = getAssets().open(name);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return new String(out.toByteArray(), "UTF-8");
    }

    private void addPet() {
        int size = dp(D_SMALL);
        lp = new WindowManager.LayoutParams(
                size, size,
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = screenW - size - dp(8);
        lp.y = screenH / 2;

        root = new FrameLayout(this);
        petView = new PetView(this, sheets, this);
        root.addView(petView, new FrameLayout.LayoutParams(size, size));
        try {
            wm.addView(root, lp);
        } catch (Exception ignored) {}
    }

    // ------------------------------------------------------------------
    void moveWindow(int dx, int dy) {
        if (root == null) return;
        lp.x += dx;
        lp.y += dy;
        lp.x = Math.max(-dp(30), Math.min(lp.x, screenW - dp(60)));
        lp.y = Math.max(-dp(30), Math.min(lp.y, screenH - dp(60)));
        try { wm.updateViewLayout(root, lp); } catch (Exception ignored) {}
    }

    void showMenu() {
        if (root == null) return;
        int s = dp(D_MENU);
        lp.width = s;
        lp.height = s;
        lp.x = Math.max(dp(4), Math.min(lp.x - (s - dp(D_SMALL)) / 2, screenW - s - dp(4)));
        lp.y = Math.max(dp(4), Math.min(screenH / 2 - s / 2, screenH - s - dp(4)));
        try { wm.updateViewLayout(root, lp); } catch (Exception ignored) {}

        root.removeAllViews();
        if (menuView == null) menuView = new MenuView(this, this);
        root.addView(menuView, new FrameLayout.LayoutParams(s, s));
        menuView.reset();
    }

    void showSelector() {
        if (root == null) return;
        int w = screenW;
        int h = dp(200);
        lp.width = w;
        lp.height = h;
        lp.x = 0;
        lp.y = screenH - h - dp(36);
        lp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        try { wm.updateViewLayout(root, lp); } catch (Exception ignored) {}
        root.removeAllViews();
        if (selectorView == null) selectorView = new SelectorView(this, this);
        selectorView.reset();
        root.addView(selectorView, new FrameLayout.LayoutParams(w, h));
    }

    void onSelectorPick(int i) {
        switch (i) {
            case 0: showChat(); break;
            case 1: showSettings(); break;
            case 2: cycleSkin(); break;
            case 3: hidePet(); break;
            default: closePanel(); break;
        }
    }

    void showChat() {
        if (root == null) return;
        int w = screenW;
        int h = dp(D_CHAT_H);
        lp.width = w;
        lp.height = h;
        lp.x = 0;
        lp.y = screenH - h;
        lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        try { wm.updateViewLayout(root, lp); } catch (Exception ignored) {}

        root.removeAllViews();
        if (chatView == null) chatView = new ChatView(this, this);
        root.addView(chatView, new FrameLayout.LayoutParams(w, h));
        chatView.onOpen();
    }

    void closePanel() {
        if (root == null) return;
        int size = dp(D_SMALL);
        lp.width = size;
        lp.height = size;
        lp.x = screenW - size - dp(8);
        lp.y = screenH / 2;
        lp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        lp.softInputMode = 0;
        try { wm.updateViewLayout(root, lp); } catch (Exception ignored) {}

        root.removeAllViews();
        if (petView == null) petView = new PetView(this, sheets, this);
        root.addView(petView, new FrameLayout.LayoutParams(size, size));
    }

    void showSettings() {
        final android.widget.EditText et = new android.widget.EditText(this);
        et.setText(getKey());
        et.setHint("sk-...");
        et.setSingleLine(true);
        android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(this)
                .setTitle("DeepSeek API Key")
                .setView(et)
                .setPositiveButton("保存", (d, w) -> {
                    setKey(et.getText().toString().trim());
                    closePanel();
                })
                .setNegativeButton("取消", null)
                .create();
        try {
            dlg.getWindow().setType(Build.VERSION.SDK_INT >= 26
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE);
        } catch (Exception ignored) {}
        dlg.show();
    }

    void buzz(int ms) {
        try {
            android.os.Vibrator v = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null) v.vibrate(ms);
        } catch (Exception ignored) {}
    }

    boolean blurOn() {
        return getSharedPreferences("pet", MODE_PRIVATE).getBoolean("blur", true);
    }

    boolean fadeOn() {
        return getSharedPreferences("pet", MODE_PRIVATE).getBoolean("fade", true);
    }

    void cycleSkin() {
        closePanel();
        if (petView != null) petView.cycle();
    }

    void hidePet() {
        if (root != null) {
            try { wm.removeView(root); } catch (Exception ignored) {}
            root = null;
        }
        stopSelf();
    }

    String getKey() {
        return getSharedPreferences("pet", MODE_PRIVATE).getString("key", "");
    }

    void setKey(String k) {
        getSharedPreferences("pet", MODE_PRIVATE).edit().putString("key", k).apply();
    }
}
