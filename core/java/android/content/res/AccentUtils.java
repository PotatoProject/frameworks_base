package android.content.res;

import android.annotation.Nullable;
import android.app.ActivityThread;
import android.content.Context;
import android.content.res.ColorUtils;
import android.graphics.Color;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;

/** @hide */
public final class AccentUtils {
    private AccentUtils() {}

    private static final String TAG = "AccentUtils";

    private static final String ACCENT_DARK_SETTING = "accent_dark";
    private static final String ACCENT_LIGHT_SETTING = "accent_light";
    private static final String ACCENT_DISCO_SETTING = "accent_disco";

    public static boolean isResourceDarkAccent(@Nullable String resName) {
        return resName == null
                ? false
                : resName.contains("accent_device_default_dark");
    }

    public static boolean isResourceLightAccent(@Nullable String resName) {
        return resName == null
                ? false
                : resName.contains("accent_device_default_light");
    }

    public static int getDarkAccentColor(int defaultColor) {
        return getAccentColor(defaultColor, ACCENT_DARK_SETTING);
    }

    public static int getLightAccentColor(int defaultColor) {
        return getAccentColor(defaultColor, ACCENT_LIGHT_SETTING);
    }

    private static int getAccentColor(int defaultColor, String setting) {
        final Context context = ActivityThread.currentApplication();
        try {
            if (Settings.Secure.getInt(context.getContentResolver(), ACCENT_DISCO_SETTING, 0) == 1) {
                return ColorUtils.genRandomAccentColor(setting == ACCENT_DARK_SETTING);
            }
            String colorValue = Settings.Secure.getString(context.getContentResolver(), setting);
            return (colorValue == null || "-1".equals(colorValue))
                    ? defaultColor
                    : Color.parseColor("#" + colorValue);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set accent: " + e.getMessage() +
                    "\nSetting default: " + defaultColor);
            return defaultColor;
        }
    }
}
