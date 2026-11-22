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
import android.view.MotionEvent;
import android.view.View;
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
    private SettingsView settingsView;
    private final java.util.ArrayList<String> clips = new java.util.ArrayList<>();
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private ChatView chatView;
    private SpriteSheet[] sheets;
    private SpriteSheet[][] skins;
    private int skin = 0;
    private int screenW, screenH;

    private static PetService instance;
    private boolean menuActive = false;

    @Override public IBinder onBind(Intent i) { return null; }

    static void onClipCaptured(final String text) {
        final PetService p = instance;
        if (p == null || text == null) return;
        p.handler.post(new Runnable() {
            @Override public void run() { p.handleClip(text); }
        });
    }

    private void handleClip(String text) {
        if (!clipOn()) return;
        if (clips.isEmpty() || !clips.get(0).equals(text)) {
            clips.add(0, text);
            while (clips.size() > 30) clips.remove(clips.size() - 1);
        }
        String show = text.length() > 16 ? text.substring(0, 16) + "\u2026" : text;
        if (petView != null) petView.setBubbleTemp("\ud83d\udccb " + show, 5200);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;
        startForeground(1, buildNotify());
        loadSheets();
        addPet();
        setupClipboard();
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                pollClip();
                handler.postDelayed(this, 1500);
            }
        }, 1200);
        handler.postDelayed(this::pollBalance, 2500);
        if (getKey().isEmpty()) {
            handler.postDelayed(() -> {
                if (petView != null) petView.setBubbleBase("\u83dc\u5355\u00b7\u8bbe\u7f6e \u91cc\u586b Key");
            }, 1800);
        }
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
        instance = null;
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
        int w = Math.round(dp(D_W) * petScale()), hgt = Math.round(dp(D_H) * petScale());
        lp = new WindowManager.LayoutParams(
                w, hgt,
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                clipFocusOn()
                        ? (WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                           | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
                        : (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                           | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                           | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS),
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = screenW - w - dp(8);
        lp.y = screenH / 2;

        // 菜单展开时把整窗事件直接转发给菜单，绕过触摸目标丢失的问题
        root = new FrameLayout(this) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                if (menuActive && menuView != null && menuView.getParent() == this) {
                    return menuView.dispatchTouchEvent(ev);
                }
                return super.dispatchTouchEvent(ev);
            }
        };
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

        // 宠物 View 必须留在窗口里，否则长按后的滑动事件会全部丢失
        if (petView == null) {
            petView = new PetView(this, sheets, this);
            root.addView(petView, new FrameLayout.LayoutParams(s, s));
        } else {
            if (petView.getParent() != root) root.addView(petView);
            petView.setLayoutParams(new FrameLayout.LayoutParams(s, s));
        }
        petView.setMenuLayout(true);
        petView.bringToFront();

        if (menuView == null) menuView = new MenuView(this, this);
        if (menuView.getParent() != root) {
            root.addView(menuView, new FrameLayout.LayoutParams(s, s));
        } else {
            menuView.setLayoutParams(new FrameLayout.LayoutParams(s, s));
            menuView.bringToFront();
        }
        menuActive = true;
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
        int w = Math.round(dp(D_W) * petScale()), hgt = Math.round(dp(D_H) * petScale());
        lp.width = w;
        lp.height = hgt;
        lp.x = screenW - w - dp(8);
        lp.y = screenH / 2;
        if (!clipFocusOn()) lp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        else lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        lp.softInputMode = 0;
        try { wm.updateViewLayout(root, lp); } catch (Exception ignored) {}

        menuActive = false;
        for (View v : new View[]{menuView, chatView, clipView, settingsView}) {
            if (v != null && v.getParent() == root) root.removeView(v);
        }
        if (petView == null) petView = new PetView(this, sheets, this);
        if (petView.getParent() != root) {
            root.addView(petView, new FrameLayout.LayoutParams(w, hgt));
        } else {
            petView.setLayoutParams(new FrameLayout.LayoutParams(w, hgt));
        }
        petView.setMenuLayout(false);
        petView.bringToFront();
    }

    void showSettings() {
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
        for (View v : new View[]{menuView, chatView, clipView, petView}) {
            if (v != null && v.getParent() == root) root.removeView(v);
        }
        if (settingsView == null) settingsView = new SettingsView(this, this);
        root.removeView(settingsView);
        root.addView(settingsView, new FrameLayout.LayoutParams(w, h));
    }

    // ------------------------------------------------------------------
    float petScale() {
        return getSharedPreferences("pet", MODE_PRIVATE).getFloat("scale", 1f);
    }

    void setPetScale(float s) {
        s = Math.max(0.6f, Math.min(1.4f, s));
        getSharedPreferences("pet", MODE_PRIVATE).edit().putFloat("scale", s).apply();
        applyPetSize();
    }

    void applyPetSize() {
        if (root == null) return;
        if (menuView != null && menuView.getParent() == root) return;
        if (chatView != null && chatView.getParent() == root) return;
        if (settingsView != null && settingsView.getParent() == root) return;
        if (clipView != null && clipView.getParent() == root) return;
        int w = Math.round(dp(D_W) * petScale());
        int hgt = Math.round(dp(D_H) * petScale());
        lp.width = w;
        lp.height = hgt;
        lp.x = screenW - w - dp(8);
        lp.y = screenH / 2;
        try { wm.updateViewLayout(root, lp); } catch (Exception ignored) {}
        if (petView != null) petView.setLayoutParams(new FrameLayout.LayoutParams(w, hgt));
    }

    void updateKey() {
        pollBalance();
    }

    void refreshToggles() {
        if (!balanceOn() && petView != null) petView.setBubbleBase(null);
        else pollBalance();
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
                    if (petView != null) petView.setBubbleBase(text);
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
                        if (petView != null) petView.setBubbleTemp("\u6709\u65b0\u526a\u5207\uff08\u70b9\u9762\u677f\u540c\u6b65\uff09", 4000);
                        return;
                    }
                    clips.add(0, text);
                    while (clips.size() > 30) clips.remove(clips.size() - 1);
                    String show = text.length() > 16 ? text.substring(0, 16) + "…" : text;
                    if (petView != null) petView.setBubbleTemp("\ud83d\udccb " + show, 5200);
                });
            });
        } catch (Exception ignored) {
        }
    }

    boolean clipFocusOn() {
        return getSharedPreferences("pet", MODE_PRIVATE).getBoolean("clipfocus", true);
    }

    private String readClip() {
        try {
            android.content.ClipboardManager cm =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) return null;
            android.content.ClipData d = cm.getPrimaryClip();
            if (d == null || d.getItemCount() == 0) return null;
            CharSequence cs = d.getItemAt(0).coerceToText(this);
            if (cs == null) return null;
            String s = cs.toString().trim();
            return s.isEmpty() ? null : s;
        } catch (Exception e) {
            return null;
        }
    }

    private String lastClip = null;

    private void pollClip() {
        if (!clipOn()) return;
        String s = readClip();
        if (s == null) return;
        if (s.equals(lastClip)) return;
        lastClip = s;
        handleClip(s);
    }

    boolean balanceOn() {
        return getSharedPreferences("pet", MODE_PRIVATE).getBoolean("balance", true);
    }

    boolean clipOn() {
        return getSharedPreferences("pet", MODE_PRIVATE).getBoolean("clip", true);
    }

    /** 面板打开时顺手读一次当前剪贴板（前台才能读）。 */
    private void grabClip() {
        try {
            android.content.ClipboardManager cm =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) return;
            android.content.ClipData d = cm.getPrimaryClip();
            if (d == null || d.getItemCount() == 0) return;
            CharSequence cs = d.getItemAt(0).coerceToText(this);
            if (cs == null) return;
            String s = cs.toString().trim();
            if (s.isEmpty()) return;
            if (clips.isEmpty() || !clips.get(0).equals(s)) {
                clips.add(0, s);
                while (clips.size() > 30) clips.remove(clips.size() - 1);
            }
        } catch (Exception ignored) {
        }
    }

    void showClips() {
        if (root == null) return;
        grabClip();
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
