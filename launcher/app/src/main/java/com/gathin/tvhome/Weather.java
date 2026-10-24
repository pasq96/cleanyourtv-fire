package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Current conditions from OpenWeatherMap, with the API key and place set in Launcher settings. Cached, refreshed every 30 min. */
final class Weather {
    static final String KEY_API = "weather_key", KEY_CITY = "weather_city", KEY_UNITS = "weather_units";
    private static final String CACHE = "weather_cache";
    private static final long MAX_AGE_MS = 30L * 60 * 1000;

    /** What the header shows: place, whole degrees Celsius, and the OpenWeather condition id and day/night flag for the icon. */
    static final class Now {
        final String city;
        final int temp, id;
        final boolean night;
        final long at;

        Now(String city, int temp, int id, boolean night, long at) {
            this.city = city;
            this.temp = temp;
            this.id = id;
            this.night = night;
            this.at = at;
        }
    }

    interface Listener { void onWeather(Now now); }

    private Weather() {}

    /** The place as typed may end in a tag such as "Poggiomarino (NA),IT": the tag is shown, not searched. */
    static String suffix(String place) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\s*(\\([^)]*\\))").matcher(place);
        return m.find() ? " " + m.group(1) : "";
    }

    static String query(String place) {
        return place.replaceAll("\\s*\\([^)]*\\)", "").replaceAll("\\s*,\\s*", ",");
    }

    /** "Comune di Poggiomarino" reads as "Poggiomarino". */
    static String shortName(String name) {
        return name.replaceFirst("(?i)^(comune di|municipality of|city of)\\s+", "").trim();
    }

    private static String tidy(String city, String place) {
        city = shortName(city);
        return city.contains("(") ? city : city + suffix(place);
    }

    /** Fahrenheit when the choice says so; Celsius otherwise. */
    static boolean fahrenheit(SharedPreferences p) {
        return "f".equals(p.getString(KEY_UNITS, "c"));
    }

    static boolean configured(Context ctx) {
        SharedPreferences p = prefs(ctx);
        return !p.getString(KEY_API, "").trim().isEmpty() && !p.getString(KEY_CITY, "").trim().isEmpty();
    }

    static void forget(Context ctx) {
        ctx.getSharedPreferences(CACHE, Context.MODE_PRIVATE).edit().clear().apply();
    }

    /** The cached reading (any age), or null. */
    static Now cached(Context ctx) {
        SharedPreferences c = ctx.getSharedPreferences(CACHE, Context.MODE_PRIVATE);
        if (!c.contains("temp")) return null;
        String place = prefs(ctx).getString(KEY_CITY, "").trim();
        if (!place.equals(c.getString("place", ""))) return null;   // the place was changed since
        return new Now(tidy(c.getString("city", place), place), c.getInt("temp", 0), c.getInt("id", 800), c.getBoolean("night", false),
                c.getLong("at", 0));
    }

    static boolean fresh(Now now) {
        return now != null && System.currentTimeMillis() - now.at < MAX_AGE_MS;
    }

    /** Fetches on a background thread and calls back there; a failure leaves the cache as it was. */
    static void fetch(Context ctx, Listener listener) {
        final Context app = ctx.getApplicationContext();
        final SharedPreferences p = prefs(app);
        final String key = p.getString(KEY_API, "").trim(), place = p.getString(KEY_CITY, "").trim(), query = query(place);
        final String lang = Lang.chosen(app).equals("system") ? java.util.Locale.getDefault().getLanguage() : Lang.chosen(app);
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL("https://api.openweathermap.org/data/2.5/weather?q=" + URLEncoder.encode(query, "UTF-8")
                        + "&units=" + (fahrenheit(p) ? "imperial" : "metric") + "&lang=" + URLEncoder.encode(lang, "UTF-8") + "&appid=" + URLEncoder.encode(key, "UTF-8"));
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                if (conn.getResponseCode() != 200) return;
                JSONObject json;
                try (InputStream in = conn.getInputStream()) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buf = new byte[2048];
                    int n;
                    while ((n = in.read(buf)) > 0 && out.size() < 64 * 1024) out.write(buf, 0, n);
                    json = new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
                }
                JSONObject w = json.getJSONArray("weather").getJSONObject(0);
                Now now = new Now(shortName(json.optString("name", query)) + suffix(place), (int) Math.round(json.getJSONObject("main").getDouble("temp")),
                        w.optInt("id", 800), w.optString("icon", "").endsWith("n"), System.currentTimeMillis());
                app.getSharedPreferences(CACHE, Context.MODE_PRIVATE).edit().putString("place", place).putString("city", now.city)
                        .putInt("temp", now.temp).putInt("id", now.id).putBoolean("night", now.night).putLong("at", now.at).apply();
                listener.onWeather(now);
            } catch (Exception ignored) {
                // Offline or a wrong key: keep showing the last reading.
            } finally {
                if (conn != null) conn.disconnect();
            }
        }, "weather").start();
    }

    /** Drawable for the condition: OpenWeather ids 2xx storm, 3xx and 5xx rain, 6xx snow, 7xx fog, 800 clear, 80x clouds. */
    static int icon(int id, boolean night) {
        if (id >= 200 && id < 300) return R.drawable.ic_weather_storm;
        if (id >= 300 && id < 600) return R.drawable.ic_weather_rain;
        if (id >= 600 && id < 700) return R.drawable.ic_weather_snow;
        if (id >= 700 && id < 800) return R.drawable.ic_weather_fog;
        if (id == 800) return night ? R.drawable.ic_weather_moon : R.drawable.ic_weather_sun;
        return R.drawable.ic_weather_cloud;
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
    }
}
