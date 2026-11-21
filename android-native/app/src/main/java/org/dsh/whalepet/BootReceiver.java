package org.dsh.whalepet;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) return;
        if (!c.getSharedPreferences("pet", Context.MODE_PRIVATE).getBoolean("autostart", true)) return;
        Intent svc = new Intent(c, PetService.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(svc);
        else c.startService(svc);
    }
}
