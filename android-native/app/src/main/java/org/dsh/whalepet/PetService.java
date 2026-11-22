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
    static final int D_W = 190;
    static final int D_H = 244;
    static final int D_MENU = 300;
    static final int D_CHAT_H = 430;

    private WindowManager wm;
    private FrameLayout root;
    private WindowManager.LayoutParams lp;
    private PetView petView;
    private MenuView menuView;
    private SelectorView selectorView;
    private ClipView clipView;
    private final java.util.ArrayList<String> clips = new java.util.ArrayList<>();
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private ChatView chatView;
    private SpriteSheet[] sheets;
    private SpriteSheet[][] skins;
    private int skin = 0;
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
        setupClipboard();
        handler.postDelayed(this::pollBalance, 2500);
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                pollBalance();
                handler.postDelayed(this, 60000);
            }
        }, 60000);
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
            SpriteSheet[] pixel = new SpriteSheet[names.length];
            for (int i = 0; i < names.length; i++) {
                pixel[i] = SpriteSheet.load(getAssets(), meta.getJSONObject(names[i]));
            }
            JSONObject w = new JSONObject();
            w.put("file", "whale_sheet.png");
            w.put("cols", 2);
            w.put("rows", 1);
            w.put("count", 2);
            w.put("frame_w", 220);
            w.put("frame_h", 124);
            w.put("cell_w", 224);
            w.put("cell_h", 128);
            w.put("pad", 2);
            w.put("interval", 0.9);
            SpriteSheet whale = SpriteSheet.load(getAssets(), w);
            skins = new SpriteSheet[][]{pixel, new SpriteSheet[]{whale, whale, whale, whale}};
            skin = 0;
            sheets = skins[0];
        } catch (Exception e) {
            sheets = null;
            skins = null;
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
        int w = dp(D_W), hgt = dp(D_H);
        lp = new WindowManager.LayoutParams(
                w, hgt,
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = screenW - w - dp(8);
        lp.y = screenH / 2;

        root = new FrameLayout(this);
        petView = new PetView(this, sheets, this);
        root.addView(petView, new FrameLayout.LayoutParams(w, hgt));
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
        float petOldX = lp.x + lp.width / 2f;
        float petOldY = lp.y + lp.height * 0.62f;
        lp.width = s;
        lp.height = s;
        lp.x = Math.round(petOldX - s * 0.70f);
        lp.y = Math.round(petOldY - s * 0.52f);
        lp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        try { wm.updateViewLayout(root, lp); } catch (Exception ignored) {}

        root.removeAllViews();
        if (menuView == null) menuView = new MenuView(this, this);
        root.addView(menuView, new FrameLayout.LayoutParams(s, s));
        menuView.play();
    }

    void menuHover(float rx, float ry) {
        if (menuView == null) return;
        menuView.hover(rx - lp.x, ry - lp.y);
    }

    void menuPick(float rx, float ry) {
        if (menuView == null) return;
        menuView.pick(rx - lp.x, ry - lp.y);
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
        int w = dp(D_W), hgt = dp(D_H);
        lp.width = w;
        lp.height = hgt;
        lp.x = screenW - w - dp(8);
        lp.y = screenH / 2;
        lp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        lp.softInputMode = 0;
        try { wm.updateViewLayout(root, lp); } catch (Exception ignored) {}

        root.removeAllViews();
        if (petView == null) petView = new PetView(this, sheets, this);
        root.addView(petView, new FrameLayout.LayoutParams(w, hgt));
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

    void pollBalance() {
        final String key = getKey();
        if (key.isEmpty() || !balanceOn()) return;
        new Thread(() -> {
            try {
                final String text = Balance.fetch(key);
                handler.post(() -> {
                    if (petView != null) petView.setBubble(text, 6000);
                });
            } catch (Exception ignored) {
            }
        }).start();
    }

    private void setupClipboard() {
        try {
            final android.content.ClipboardManager cm =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm == null) return;
            cm.addPrimaryClipChangedListener(() -> {
                if (!clipOn()) return;
                String s = null;
                try {
                    android.content.ClipData d = cm.getPrimaryClip();
                    if (d != null && d.getItemCount() > 0) {
                        CharSequence cs = d.getItemAt(0).coerceToText(this);
                        if (cs != null) s = cs.toString().trim();
                    }
                } catch (Exception ignored) {
                }
                final String text = s;
                handler.post(() -> {
                    if (text == null || text.isEmpty()) {
                        if (petView != null) petView.setBubble("剪贴板有新内容（后台读不到）", 3000);
                        return;
                    }
                    clips.add(0, text);
                    while (clips.size() > 30) clips.remove(clips.size() - 1);
                    String show = text.length() > 16 ? text.substring(0, 16) + "…" : text;
                    if (petView != null) petView.setBubble("\ud83d\udccb " + show, 5200);
                });
            });
        } catch (Exception ignored) {
        }
    }

    boolean balanceOn() {
        return getSharedPreferences("pet", MODE_PRIVATE).getBoolean("balance", true);
    }

    boolean clipOn() {
        return getSharedPreferences("pet", MODE_PRIVATE).getBoolean("clip", true);
    }

    void showClips() {
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
        if (clipView == null) clipView = new ClipView(this, clips, this);
        root.addView(clipView, new FrameLayout.LayoutParams(w, h));
        clipView.refresh();
    }

    boolean blurOn() {
        return getSharedPreferences("pet", MODE_PRIVATE).getBoolean("blur", true);
    }

    boolean fadeOn() {
        return getSharedPreferences("pet", MODE_PRIVATE).getBoolean("fade", true);
    }

    void cycleSkin() {
        if (skins == null) return;
        skin = (skin + 1) % skins.length;
        sheets = skins[skin];
        closePanel();
        if (petView != null) petView.setSheets(sheets);
        buzz(20);
    }

    void newChat() {
        if (chatView != null) chatView.reset();
        showChat();
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
