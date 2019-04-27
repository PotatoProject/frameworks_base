package android.content.res;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.util.JsonReader;
import android.util.JsonWriter;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressLint("NewApi")
public class ThemeUtils {
    public static class Target {
        Target(String targetName, Map<String, Integer> color) {
            this.targetName = targetName;
            this.color = color;
        }
        public String targetName;
        public Map<String, Integer> color;
    }

    // Deserialization Methods
    public static List<Target> readJsonThemeStream(InputStream in, String packageName, Context ctx) throws IOException {
        try (JsonReader reader = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return readTargetsArray(reader, packageName, ctx);
        }
    }

    public static List<Target> readTargetsArray(JsonReader reader, String packageName, Context ctx) throws IOException {
        List<Target> targets = new ArrayList<>();

        reader.beginArray();
        while (reader.hasNext()) {
            targets.add(readTarget(reader, packageName, ctx));
        }
        reader.endArray();
        return targets;
    }

    public static Target readTarget(JsonReader reader, String packageName, Context ctx) throws IOException {
        String targetName = "";
        Map<String,Integer> colorMap = new HashMap<>();
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            switch (name) {
                case "target":
                    targetName = reader.nextString();
                    break;
                case "color":
                    colorMap = readJsonMap(reader, packageName, ctx);
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();
        return new Target(targetName, colorMap);
    }

    public static HashMap<String,Integer> readJsonMap(JsonReader reader, String packageName, Context ctx) throws IOException {
        HashMap<String, Integer> retMap = new HashMap<>();
        reader.beginObject();
        while (reader.hasNext())
            retMap.put(reader.nextName(), resolveColor(reader.nextString(), packageName, ctx));
        reader.endObject();
        return retMap;
    }

    public static int resolveColorRef(String resStr, String packageName, Context ctx) {
        String targetPackage = packageName;
        String resName = resStr;
        PackageManager pm = ctx.getPackageManager();
        if (resStr.contains("*"))
            targetPackage = resStr.substring(resStr.indexOf('*') + 1, resStr.indexOf('/'));
        resName = resStr.substring(resStr.indexOf('/') + 1);
        Resources res;
        try {
            res = pm.getResourcesForApplication(targetPackage);
            int resId = res.getIdentifier(resName, "color", targetPackage);
            if (resId <= 0) {
                return 0;
            }
            return res.getColor(resId);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public static int resolveColor(String resStr, String packageName, Context ctx) {
        if (resStr.charAt(0) == '#')
            return Color.parseColor(resStr);
        if (resStr.charAt(0) == '@') {
            return resolveColorRef(resStr, packageName, ctx);
        }
        return 0;
    }


    // Serialization Methods
    public static void writeJsonThemeStream(OutputStream out, List<Target> targets) throws IOException {
        JsonWriter writer = new JsonWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        writer.setIndent("  ");
        writeTargetsArray(writer, targets);
        writer.close();
    }

    public static void writeTargetsArray(JsonWriter writer, List<Target> targets) throws IOException {
        writer.beginArray();
        for (Target target : targets) {
            writeTarget(writer, target);
        }
        writer.endArray();
    }

    public static void writeTarget(JsonWriter writer, Target target) throws IOException {
        writer.beginObject();
        writer.name("target").value(target.targetName);
        if (target.color != null) {
            writer.name("color");
            writeColorMap(writer, target.color);
        }
        writer.endObject();
    }


    public static void writeColorMap(JsonWriter writer, Map<String, Integer> color) throws IOException {
        writer.beginObject();
        for (Map.Entry<String, Integer> colorEntry : color.entrySet()) {
            writer.name(colorEntry.getKey()).value(colorEntry.getValue());
        }
        writer.endObject();
    }
}