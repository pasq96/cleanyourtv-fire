package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import java.util.Locale;

/** The language picked in Launcher settings ("system" follows the TV), applied to every launcher screen. */
final class Lang {
    static final String KEY = "language";
    static final String[] IDS = {"system", "en", "it"};

    private Lang() {}

    static String chosen(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        String id = p.getString(KEY, IDS[0]);
        for (String known : IDS) if (known.equals(id)) return id;
        return IDS[0];
    }

    static String next(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return IDS[(i + 1) % IDS.length];
        return IDS[0];
    }

    static Context wrap(Context base) {
        String id = chosen(base);
        if (id.equals(IDS[0])) return base;
        Configuration c = new Configuration(base.getResources().getConfiguration());
        c.setLocale(new Locale(id));
        return base.createConfigurationContext(c);
    }
}
