package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Locale;

/**
 * A new wallpaper every day from an online source, downloaded once per day when Home opens. The picture is
 * saved as wallpapers/daily-YYYY-MM-DD.jpg and the wallpaper choice "daily" always points at the newest one.
 */
final class DailyWallpaper {
    static final String SOURCE_KEY = "daily_source", STAMP_KEY = "daily_stamp", CHOICE = "daily";
    static final int OFF = 0, PICSUM = 1, BING = 2;
    private static final int MAX_BYTES = 9 * 1024 * 1024;
    private static volatile int running = -1;

    private DailyWallpaper() {}

    interface Done {
        /** error is null on success (changed tells whether a new picture was saved). */
        void finished(boolean changed, String error);
    }

    static int source(SharedPreferences prefs) {
        int v = prefs.getInt(SOURCE_KEY, OFF);
        return v >= OFF && v <= BING ? v : OFF;
    }

    /** The newest downloaded picture's file name, or null when there is none. */
    static String latestName(File dir) {
        File[] files = dir == null ? null : new File(dir, "wallpapers").listFiles();
        String best = null;
        if (files != null) {
            for (File f : files) {
                String n = f.getName();
                if (n.startsWith("daily-") && n.endsWith(".jpg") && f.length() > 0 && (best == null || n.compareTo(best) > 0)) best = n;
            }
        }
        return best;
    }

    /** True while today's picture from the chosen source is already saved. */
    static boolean current(Context ctx, SharedPreferences prefs) {
        String stamp = source(prefs) + ":" + LocalDate.now();
        return stamp.equals(prefs.getString(STAMP_KEY, "")) && latestName(Config.dir(ctx)) != null;
    }

    /** Downloads today's picture if it is not there yet; done runs on the main thread. Does nothing when off. */
    static void refresh(Context ctx, boolean force, Done done) {
        Context app = ctx.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        int source = source(prefs);
        if (source == OFF || running == source || (!force && current(app, prefs))) return;
        running = source;
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            boolean changed = false;
            String error = null;
            try {
                File dir = Config.dir(app);
                if (dir == null) throw new IOException("no storage");
                File wallpapers = new File(dir, "wallpapers");
                if (!wallpapers.isDirectory() && !wallpapers.mkdirs()) throw new IOException("no storage");
                String today = LocalDate.now().toString();
                String url = source == PICSUM ? "https://picsum.photos/seed/" + today + "/1920/1080.jpg" : bingUrl();
                byte[] data = download(url);
                if (data.length < 10_000) throw new IOException("empty picture");
                File target = new File(wallpapers, "daily-" + today + ".jpg"), tmp = new File(wallpapers, "part-" + source);
                try (FileOutputStream out = new FileOutputStream(tmp)) {
                    out.write(data);
                }
                synchronized (DailyWallpaper.class) {
                    if (source(prefs) != source) {
                        tmp.delete();
                        throw new IOException("source changed");
                    }
                    if (!tmp.renameTo(target)) {
                        target.delete();
                        if (!tmp.renameTo(target)) throw new IOException("cannot save");
                    }
                    File[] old = wallpapers.listFiles();
                    if (old != null) for (File f : old) {
                        if (f.getName().startsWith("daily-") && !f.getName().equals(target.getName())) f.delete();
                    }
                    prefs.edit().putString(STAMP_KEY, source + ":" + today).putString(Config.WALLPAPER_CHOICE_KEY, CHOICE)
                            .putLong(Config.SETTINGS_REVISION_KEY, prefs.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
                }
                changed = true;
            } catch (Exception e) {
                error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            }
            final boolean c = changed;
            final String err = error;
            if (running == source) running = -1;
            if (done != null) main.post(() -> done.finished(c, err));
        }, "daily-wallpaper").start();
    }

    private static String bingUrl() throws Exception {
        String market = Locale.getDefault().getLanguage().equals("it") ? "it-IT" : "en-US";
        String json = new String(download("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=" + market),
                StandardCharsets.UTF_8);
        String path = new JSONObject(json).getJSONArray("images").getJSONObject(0).getString("url");
        return path.startsWith("http") ? path : "https://www.bing.com" + path;
    }

    private static byte[] download(String address) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(15_000);
        c.setReadTimeout(20_000);
        c.setInstanceFollowRedirects(true);
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > MAX_BYTES) throw new IOException("too large");
                }
                return out.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }
}
