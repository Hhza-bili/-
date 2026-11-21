package org.dsh.whalepet;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

/** 只负责拿悬浮窗权限，然后把 Service 拉起来。 */
public class MainActivity extends Activity {
    private static final int REQ = 1001;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            startActivityForResult(new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())), REQ);
        } else {
            boot();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ) {
            if (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)) boot();
            finish();
        }
    }

    private void boot() {
        Intent i = new Intent(this, PetService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
        finish();
    }
}
