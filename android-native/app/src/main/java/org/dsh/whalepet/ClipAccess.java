package org.dsh.whalepet;

import android.accessibilityservice.AccessibilityService;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.view.accessibility.AccessibilityEvent;

/** 尽力尝试在后台捕获剪贴板（Android 10+ 多数机型仍会被拦，失败则静默）。 */
public class ClipAccess extends AccessibilityService {
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && type != AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
            return;
        }
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) return;
            ClipData d = cm.getPrimaryClip();
            if (d == null || d.getItemCount() == 0) return;
            CharSequence cs = d.getItemAt(0).coerceToText(this);
            if (cs == null) return;
            String s = cs.toString().trim();
            if (s.isEmpty()) return;
            PetService.onClipCaptured(s);
        } catch (Exception ignored) {
            // 被系统拦截时直接放弃
        }
    }

    @Override
    public void onInterrupt() {
    }
}
