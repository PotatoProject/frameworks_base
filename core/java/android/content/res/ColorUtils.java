package android.content.res;

import com.android.internal.graphics.ColorUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

public class ColorUtils {
    private static final String TAG = "ColorUtils";

    public static boolean isQsColorValid(int color) {
        float hsl[] = new float[3];
        ColorUtils.colorToHSL(color, hsl);
        return hsl[2] >= QS_MIN_LIGHTNESS && hsl[2] <= QS_MAX_LIGHTNESS;
    }

    public static int getValidQsColor(int color) {
        if (isQsColorValid(color)) return color;
        float hsl[] = new float[3];
        ColorUtils.colorToHSL(color, hsl);
        // We want to use the lightness at 0.75 of valid range
        hsl[2] = ((QS_MIN_LIGHTNESS * 1) + (QS_MAX_LIGHTNESS * 3)) / 4;
        return ColorUtils.HSLToColor(hsl);
    }

    public static int genRandomQsColor() {
        Random r = new Random();
        float hsl[] = new float[3];
        hsl[0] = r.nextInt(360);
        hsl[1] = r.nextFloat();
        hsl[2] = r.nextFloat() * 0.6f;
        return ColorUtils.HSLToColor(hsl);
    }

}
