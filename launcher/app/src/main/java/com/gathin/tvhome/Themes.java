package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Themes shipped inside the APK, so no adb or script is needed. A config.json pushed to the TV still wins over them;
 * otherwise the one picked in Launcher settings applies (Fire OS starts on "fire").
 */
final class Themes {
    static final String KEY = "theme_choice";
    static final String[] IDS = {"fire", "clean", "stock"};

    private Themes() {}

    static String defaultId(boolean fireOs) {
        return fireOs ? IDS[0] : IDS[IDS.length - 1];
    }

    static String chosen(SharedPreferences prefs, boolean fireOs) {
        String id = prefs.getString(KEY, null);
        for (String known : IDS) if (known.equals(id)) return id;
        return defaultId(fireOs);
    }

    static String next(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return IDS[(i + 1) % IDS.length];
        return IDS[0];
    }

    static int label(String id) {
        switch (id) {
            case "fire": return R.string.theme_fire;
            case "clean": return R.string.theme_clean;
            default: return R.string.theme_stock;
        }
    }

    private static int raw(String id) {
        switch (id) {
            case "fire": return R.raw.theme_fire;
            case "clean": return R.raw.theme_clean;
            default: return R.raw.theme_stock;
        }
    }

    /** The bundled theme's overlay, or null if it can't be read. */
    static JSONObject load(Context ctx, String id) {
        try (InputStream in = ctx.getResources().openRawResource(raw(id))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[2048];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }
}
