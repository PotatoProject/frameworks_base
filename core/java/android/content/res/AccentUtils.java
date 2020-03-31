package android.content.res;

import android.graphics.Color;
import android.os.SystemProperties;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;

/** @hide */
public class AccentUtils {
    private static final String TAG = "AccentUtils";

    private static final String ACCENT_DARK_PROP = "persist.sys.theme.accent_dark";
    private static final String ACCENT_LIGHT_PROP = "persist.sys.theme.accent_light";
    private static final String ACCENT_DISCO_PROP = "persist.sys.theme.accent_disco";

    public static boolean isResourceDarkAccent(String resName) {
        return resName.contains("accent_device_default_dark");
    }

    public static boolean isResourceLightAccent(String resName) {
        return resName.contains("accent_device_default_light");
    }

    public static int getDarkAccentColor(int defaultColor) {
        return getAccentColor(defaultColor, ACCENT_DARK_PROP);
    }

    public static int getLightAccentColor(int defaultColor) {
        return getAccentColor(defaultColor, ACCENT_LIGHT_PROP);
    }

    private static int getAccentColor(int defaultColor, String property) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        if ((cal.get(Calendar.MONTH) == 3 && cal.get(Calendar.DAY_OF_MONTH) == 1) ||
                        "1".equals(SystemProperties.get(ACCENT_DISCO_PROP, "0"))) {
            return ColorUtils.genRandomAccentColor(property == ACCENT_DARK_PROP);
        }
        try {
            String colorValue = SystemProperties.get(property, "-1");
            return "-1".equals(colorValue)
                    ? defaultColor
                    : Color.parseColor("#" + colorValue);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set accent: " + e.getMessage() +
                    "\nSetting default: " + defaultColor);
            return defaultColor;
        }
    }
}
