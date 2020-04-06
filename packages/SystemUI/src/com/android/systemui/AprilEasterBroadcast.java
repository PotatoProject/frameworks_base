package com.android.systemui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.om.IOverlayManager;
import android.graphics.Color;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.Settings.Secure;
import android.provider.Settings.System;
import android.util.Log;
import android.widget.Toast;

import java.util.Calendar;
import java.util.Date;

/** @hide */
public class AprilEasterBroadcast extends BroadcastReceiver {

    private static final String TAG = "AprilEasterBroadcast";
    private IOverlayManager mOverlayManager;

    @Override
    public void onReceive(Context context, Intent intent) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        if (cal.get(Calendar.MONTH) != 3 || cal.get(Calendar.DAY_OF_MONTH) != 1) {
            Secure.putInt(context.getContentResolver(), "disco_mode_triggered", 0);
            return;
        }
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "");
        wl.acquire();
        Toast.makeText(context, "🌈", Toast.LENGTH_LONG).show();
        mOverlayManager = IOverlayManager.Stub.asInterface(
                ServiceManager.getService(Context.OVERLAY_SERVICE));
        if (Secure.getInt(context.getContentResolver(), "disco_mode_triggered", 0) != 1) {
            try {
                Secure.putInt(context.getContentResolver(), "disco_mode_triggered", 1);
                mOverlayManager.reloadAssets("android", UserHandle.USER_CURRENT);
                mOverlayManager.reloadAssets("com.android.systemui", UserHandle.USER_CURRENT);
                mOverlayManager.reloadAssets("com.android.settings", UserHandle.USER_CURRENT);
            } catch (RemoteException e) {
                Log.e(TAG, "🌈");
            }
        }
        System.putInt(context.getContentResolver(), System.QS_PANEL_BG_COLOR, Color.BLACK);
        wl.release();
    }
}
