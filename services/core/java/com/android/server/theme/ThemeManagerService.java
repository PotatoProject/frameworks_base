package com.android.server.theme;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.IActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.ThemeUtils;
import android.content.res.ThemeUtils.Target;
import android.content.theme.IThemeManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.util.AtomicFile;
import android.util.Log;

import com.android.internal.util.ConcurrentUtils;
import com.android.server.SystemServerInitThreadPool;
import com.android.server.SystemService;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

public class ThemeManagerService extends SystemService {
    private static final String TAG = "ThemeManagerService";

    static final boolean DEBUG = false;

    private final AtomicFile mCurrentConfig, mConfig;
    private final Object mLock = new Object();
    private List<Target> mCurrentTheme = new ArrayList<>();

    private Future<?> mInitCompleteSignal;
    private Context mContext;
    private List<String> mCurrentThemeList = new ArrayList<>();

    public ThemeManagerService(Context context) {
        super(context);
        mContext = context;
        mCurrentConfig =
                new AtomicFile(new File(Environment.getDataSystemDirectory(), "loaded-config.json"), "potate");
        mConfig =
                new AtomicFile(new File(Environment.getDataSystemDirectory(), "theme-config.json"), "potate");
        mInitCompleteSignal = SystemServerInitThreadPool.get().submit(() -> {
            publishBinderService(Context.THEMER_SERVICE, mService);
            publishLocalService(ThemeManagerService.class, this);
        }, "Init ThemeManagerService");
    }

    private final IBinder mService = new IThemeManager.Stub() {

        @SuppressLint("NewApi")
        @Override
        public boolean checkTheme(String packageName) {
            PackageManager packageManager = mContext.getPackageManager();
            Resources res;
            try {
                res = packageManager.getResourcesForApplication(packageName);
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "Failed to register " + packageName + ": " + e.getMessage());
                return false;
            }
            return getAllThemes().contains(packageName) &&
                    res.getIdentifier("theme_config", "raw", packageName) != 0;
        }

        @Override
        public void enableTheme(String packageName) {
            if (!mCurrentThemeList.contains(packageName) || mContext == null)
                return;
            PackageManager packageManager = mContext.getPackageManager();
            Resources res;
            removeConfig();
            try {
                res = packageManager.getResourcesForApplication(packageName);
                int resId = res.getIdentifier("theme_config", "raw", packageName);
                InputStream inputStream = res.openRawResource(resId);
                mCurrentTheme = ThemeUtils.readJsonThemeStream(inputStream, packageName, mContext);
            } catch (PackageManager.NameNotFoundException | IOException e) {
                Log.e(TAG, "Failed to enable " + packageName + ": " + e.getMessage());
                e.printStackTrace();
                return;
            }
            updateConfig();
            updateAssets();
            SystemProperties.set("persist.sys.theme.name", packageName);
        }

        @Override
        public String getCurrentTheme() {
            return SystemProperties.get("persist.sys.theme.name");
        }

        @Override
        public List<String> getAllThemes() {
            return ThemeManagerService.this.getAllThemes(UserHandle.USER_ALL);
        }

        @Override
        public void reloadAssets() {
            updateAssets();
        }

        @Override
        public int getMaskedColor(String packageName, String resourceName, int defaultValue) {
            if (mCurrentTheme == null)
                return defaultValue;
            for (Target target: mCurrentTheme) {
                if (target.targetName.equals(packageName)) {
                    int newRes = target.color.get(resourceName);
                    if (newRes <= 0)
                        return defaultValue;
                    return newRes;
                }
            }
            return defaultValue;
        }
    };

    @Override
    public void onStart() {
        // Intentionally empty
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == PHASE_SYSTEM_SERVICES_READY && mInitCompleteSignal != null) {
            ConcurrentUtils.waitForFutureNoInterrupt(mInitCompleteSignal,
                    "Wait for ThemeManagerService init");
            mInitCompleteSignal = null;
            getAllThemes(UserHandle.USER_SYSTEM);
            loadConfig();
            updateAssets();
        }
    }

    private List<String> getAllThemes(int uid) {
        PackageManager packageManager = mContext.getPackageManager();
        List<String> packageList = new ArrayList<>();

        if (packageManager != null) {
            List<PackageInfo> packages = packageManager.getInstalledPackages(0);
            Bundle meta = null;
            if (packages != null) {
                for (PackageInfo pkg: packages) {
                    try {
                        meta = packageManager.getApplicationInfo(pkg.packageName,
                                PackageManager.GET_META_DATA).metaData;
                    } catch (PackageManager.NameNotFoundException e) {
                        e.printStackTrace();
                    }
                    if (meta != null && meta.getBoolean("pte_compatible"))
                        packageList.add(pkg.packageName);
                }
            }
        }
        synchronized (mLock) {
            mCurrentThemeList = packageList;
        }
        return packageList;
    }

    private void updateConfig() {
        synchronized (mLock) {
            FileOutputStream outputStream = null;
            try {
                outputStream = mCurrentConfig.startWrite();
                ThemeUtils.writeJsonThemeStream(outputStream, mCurrentTheme);
                mCurrentConfig.finishWrite(outputStream);
            } catch (IOException e) {
                mCurrentConfig.failWrite(outputStream);
                e.printStackTrace();
            }
        }
    }

    private void loadConfig() {
        synchronized (mLock) {
            FileInputStream inputStream;
            if (mCurrentConfig.exists()) {
                try {
                    inputStream = mCurrentConfig.openRead();
                    mCurrentTheme = ThemeUtils.readJsonThemeStream(inputStream,
                            "android", mContext);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void removeConfig() {
        synchronized (mLock) {
            if (mCurrentConfig.exists()) {
                mCurrentConfig.delete();
            }
        }
    }

    private void updateAssets() {
        if (mCurrentTheme == null)
            return;
        for (Target target : mCurrentTheme)
            updateAssets(target.targetName);
    }

    private void updateAssets(final String targetPackageName) {
        updateAssets(Collections.singletonList(targetPackageName));
        final Intent intent = new Intent(Intent.ACTION_OVERLAY_CHANGED,
                Uri.fromParts("package", targetPackageName, null));
        intent.setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);

        try {
            ActivityManager.getService().broadcastIntent(null, intent, null,
                    null, 0, null, null, null,
                    android.app.AppOpsManager.OP_NONE, null, false, false,
                    UserHandle.USER_ALL);
        } catch (RemoteException e) {
            // Intentionally left empty.
        }
    }

    private void updateAssets(List<String> targetPackageNames) {
        final IActivityManager am = ActivityManager.getService();
        try {
            am.scheduleApplicationInfoChanged(targetPackageNames, UserHandle.USER_ALL);
        } catch (RemoteException e) {
            // Intentionally left empty.
        }
    }
}
