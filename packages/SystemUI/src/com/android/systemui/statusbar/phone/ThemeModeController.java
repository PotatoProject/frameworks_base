/*
 * Copyright (C) 2018 The Android Open Source Project
 * Copyright (C) 2019 ArrowOS
 * Copyright (C) 2020 POSP
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.systemui.statusbar.phone;
import static android.os.UserHandle.USER_SYSTEM;
import android.app.UiModeManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.VisibleForTesting;
import java.lang.Runnable;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
/**
 * Preference controller to allow users to choose an overlay from a list for a given category.
 * The chosen overlay is enabled along with its Ext overlays belonging to the same category.
 * A default option is also exposed that disables all overlays in the given category.
 */
public class ThemeModeController extends BroadcastReceiver {
    private static final String TAG = "CustomOverlayCategoryPC";
    @VisibleForTesting
    static final String PACKAGE_DEVICE_DEFAULT = "package_device_default";
    /* Define system target packages here */
    private static final List<String> SYSTEM_TARGET_PACKAGES = Arrays.asList
    (
        "android",
        "com.android.settings",
        "com.android.systemui",
        "com.google.android.inputmethod.latin"
    );
    /* Define custom app target packages here */
    private static final List<String> CUSTOM_APP_TARGET_PACKAGES = Arrays.asList();
    private static final Uri SETTING_URI = Settings.System.getUriFor(
        Settings.System.COLOR_BUCKET_OVERLAY);

    private static final String OVERLAY_CATEGORY = "android.theme.customization.custom_overlays";
    private static final Comparator<OverlayInfo> OVERLAY_INFO_COMPARATOR =
            Comparator.comparingInt(a -> a.priority);
    private final IOverlayManager mOverlayManager;
    private final PackageManager mPackageManager;
    private final Handler mHandler;
    private CustomSettingsObserver mCustomSettingsObserver;
    private String mCurrentTheme = PACKAGE_DEVICE_DEFAULT;
    private Context mContext;
    private ContentResolver resolver;
    public ThemeModeController(Context context, Handler handler) {
        mOverlayManager = IOverlayManager.Stub
                .asInterface(ServiceManager.getService(Context.OVERLAY_SERVICE));
        mPackageManager = context.getPackageManager();
        mHandler = handler;
        mContext = context;
        resolver = mContext.getContentResolver();
        mCustomSettingsObserver = new CustomSettingsObserver(mHandler);
	    mCustomSettingsObserver.observe();
        updateState();
    }
    @Override
    public void onReceive(Context context, Intent intent) {
        updateState();
    }
    private boolean setOverlay(String packageName) {
        final String currentPackageName = getOverlayInfos().stream()
                .filter(info -> info.isEnabled())
                .map(info -> info.packageName)
                .findFirst()
                .orElse(PACKAGE_DEVICE_DEFAULT);

        if(mCurrentTheme != currentPackageName) {
            if(currentPackageName != null)
                // This shouldn't happen, fixing
                mCurrentTheme = currentPackageName;
        }
        if (mCurrentTheme.equals(packageName) &&
                mCurrentTheme.equals(currentPackageName)) {
            // Already set.
            return true;
        }

        Handler handler = new Handler(mContext.getMainLooper());
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (PACKAGE_DEVICE_DEFAULT.equals(packageName)) {
                    handleOverlays(mCurrentTheme, false);
                } else {
                    // first disable all the current enabled overlays and their extensions
                    handleOverlays(mCurrentTheme, false);
                    // enable all the selected overlays and their extensions
                    handleOverlays(packageName, true);
                }
            }
        };
        handler.post(runnable);
        return true; // Assume success
    }
    private Boolean handleOverlays(String currentPackageName, Boolean state) {
        mCurrentTheme = currentPackageName;
        try {
            for (OverlayInfo overlay : getOverlayInfos()) {
                if (mCurrentTheme.equals(overlay.packageName)
                        || (mCurrentTheme + "Ext").equals(overlay.packageName)) {
                    mOverlayManager.setEnabled(overlay.packageName, state, USER_SYSTEM);
                }
            }
        } catch (RemoteException re) {
            Log.w(TAG, "Error handling overlays.", re);
            return false;
        }
        return true;
    }
    public void updateState() {
        final List<String> pkgs = new ArrayList<>();
        for (OverlayInfo overlayInfo : getOverlayInfos()) {
            if (!overlayInfo.packageName.endsWith("Ext")) {
                pkgs.add(overlayInfo.packageName);
                if (overlayInfo.isEnabled()) {
                    mCurrentTheme = pkgs.get(pkgs.size() - 1);
                }
            }
        }
    }
    private List<OverlayInfo> getOverlayInfos() {
        final List<OverlayInfo> filteredInfos = new ArrayList<>();
        List<OverlayInfo> overlayInfos = new ArrayList<>();
        Stream.of(SYSTEM_TARGET_PACKAGES.stream(), CUSTOM_APP_TARGET_PACKAGES.stream())
              .flatMap(target -> target)
              .forEach(targetPackageName -> {
                  try {
                      overlayInfos.addAll(mOverlayManager
                          .getOverlayInfosForTarget(targetPackageName, USER_SYSTEM));
                  } catch (RemoteException re) {
                      throw re.rethrowFromSystemServer();
                  }
             }
        );
        for (OverlayInfo overlay : overlayInfos) {
            if (OVERLAY_CATEGORY.equals(overlay.category)) {
                filteredInfos.add(overlay);
            }
        }
        filteredInfos.sort(OVERLAY_INFO_COMPARATOR);
        return filteredInfos;
    }
    private class CustomSettingsObserver extends ContentObserver {
        CustomSettingsObserver(Handler handler) {
            super(handler);
        }
        void observe() {
	        resolver.registerContentObserver(SETTING_URI,
                    false, this, UserHandle.USER_ALL);
        }
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (SETTING_URI.equals(uri)) {
                String newValue = Settings.System.getString(resolver, Settings.System.COLOR_BUCKET_OVERLAY);
                if(newValue == null || newValue == PACKAGE_DEVICE_DEFAULT) {
                    setOverlay(PACKAGE_DEVICE_DEFAULT);
                } else {
                    setOverlay(newValue);
                }
            }
        }
    }
}
