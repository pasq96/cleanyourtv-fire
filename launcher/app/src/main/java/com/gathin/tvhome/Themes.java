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
    static final String[] IDS = {"fire", "glass", "aurora", "clean", "stock"};

    static final String BANNER_STYLE_KEY = "banner_style";
    static final int BANNER_OFF = 0, BANNER_STRETCH = 1, BANNER_GRADIENT = 2, BANNER_BLUR = 3, BANNER_GLASS = 4,
            BANNER_SOLID = 5, BANNER_FRAME = 6;

    /** How tiles of apps without a TV banner are drawn: icon stretched to fill, icon on a gradient, or the plain icon. */
    static int bannerStyle(Context ctx) {
        int v = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).getInt(BANNER_STYLE_KEY, BANNER_STRETCH);
        return v >= BANNER_OFF && v <= BANNER_FRAME ? v : BANNER_STRETCH;
    }

    static final String SORT_RECENT_KEY = "sort_recent";

    private Themes() {}

    /** Tiles after the dock ordered by last opened instead of the saved manual order (Launcher settings). */
    static boolean sortRecent(Context ctx) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).getBoolean(SORT_RECENT_KEY, false);
    }

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
            case "glass": return R.string.theme_glass;
            case "aurora": return R.string.theme_aurora;
            case "clean": return R.string.theme_clean;
            default: return R.string.theme_stock;
        }
    }

    private static int raw(String id) {
        switch (id) {
            case "fire": return R.raw.theme_fire;
            case "glass": return R.raw.theme_glass;
            case "aurora": return R.raw.theme_aurora;
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
