package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Settings, tile order and hidden apps as one backup.json next to the theme file, to survive a reinstall. */
final class Backup {
    static final String NAME = "backup.json";
    private static final String[] FILES = {Order.FILE_NAME, "library_apps.txt", "library_home_removed.txt"};
    /** Per-install state: the change counter and the theme-file stamp must not travel. */
    private static final String[] LOCAL_KEYS = {Config.SETTINGS_REVISION_KEY, Config.THEME_SIGNATURE_KEY};

    private Backup() {}

    static File file(Context ctx) {
        File dir = Config.dir(ctx);
        return dir == null ? null : new File(dir, NAME);
    }

    static boolean export(Context ctx) {
        File out = file(ctx);
        if (out == null) return false;
        try {
            JSONObject root = new JSONObject(), prefs = new JSONObject(), files = new JSONObject();
            SharedPreferences sp = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
            for (Map.Entry<String, ?> e : sp.getAll().entrySet()) if (!local(e.getKey())) prefs.put(e.getKey(), e.getValue());
            for (String name : FILES) {
                String text = read(new File(ctx.getFilesDir(), name));
                if (text != null) files.put(name, text);
            }
            root.put("version", 1).put("settings", prefs).put("files", files);
            out.getParentFile().mkdirs();
            try (FileOutputStream os = new FileOutputStream(out)) {
                os.write(root.toString(1).getBytes(StandardCharsets.UTF_8));
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static boolean restore(Context ctx) {
        File in = file(ctx);
        String text = in == null ? null : read(in);
        if (text == null) return false;
        try {
            JSONObject root = new JSONObject(text);
            JSONObject prefs = root.getJSONObject("settings"), files = root.getJSONObject("files");
            SharedPreferences sp = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor ed = sp.edit();
            for (String key : sp.getAll().keySet()) if (!local(key)) ed.remove(key);
            java.util.Iterator<String> keys = prefs.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object v = prefs.get(key);
                if (local(key)) continue;
                if (v instanceof Boolean) ed.putBoolean(key, (Boolean) v);
                else if (v instanceof Number) ed.putInt(key, ((Number) v).intValue());
                else if (v instanceof String) ed.putString(key, (String) v);
            }
            ed.putLong(Config.SETTINGS_REVISION_KEY, sp.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).commit();
            for (String name : FILES) {
                File target = new File(ctx.getFilesDir(), name);
                if (!files.has(name)) { target.delete(); continue; }
                try (FileOutputStream os = new FileOutputStream(target)) {
                    os.write(files.getString(name).getBytes(StandardCharsets.UTF_8));
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean local(String key) {
        for (String k : LOCAL_KEYS) if (k.equals(key)) return true;
        return false;
    }

    private static String read(File f) {
        if (!f.isFile() || f.length() > 256 * 1024) return null;
        try (FileInputStream is = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int n = 0, r;
            while (n < b.length && (r = is.read(b, n, b.length - n)) > 0) n += r;
            return new String(b, 0, n, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
