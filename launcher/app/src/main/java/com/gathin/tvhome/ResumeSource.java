package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds the Continue watching shelf from the sources the launcher settings leave on. Blocking: off the main thread. */
final class ResumeSource {
    private ResumeSource() { }

    static Set<String> hidden(SharedPreferences prefs) {
        return new HashSet<>(prefs.getStringSet(ResumePick.KEY_HIDDEN, Collections.emptySet()));
    }

    static List<ResumePick.Item> load(Context ctx, SharedPreferences prefs) {
        int age = ResumePick.AGES[index(prefs, ResumePick.KEY_AGE, ResumePick.DEFAULT_AGE, ResumePick.AGES.length)];
        int perApp = ResumePick.PER_APP[index(prefs, ResumePick.KEY_PER_APP, ResumePick.DEFAULT_PER_APP, ResumePick.PER_APP.length)];
        Set<String> hidden = hidden(prefs);
        List<ResumePick.Item> items = new ArrayList<>();
        if (!WatchNext.missingPermission(ctx)) {
            items.addAll(ResumePick.pick(WatchNext.read(ctx), System.currentTimeMillis(), age, perApp, hidden));
            if (prefs.getBoolean(ResumePick.KEY_SUGGESTED, false)) {
                Set<String> have = new HashSet<>();
                for (ResumePick.Item i : items) have.add(i.key());
                items.addAll(ResumePick.suggest(WatchNext.readSuggested(ctx), hidden, have));
            }
        }
        if (items.isEmpty() && prefs.getBoolean(ResumePick.KEY_APPS, true)) items = recentApps(ctx, hidden);
        return items;
    }

    private static int index(SharedPreferences prefs, String key, int fallback, int size) {
        return Math.max(0, Math.min(size - 1, prefs.getInt(key, fallback)));
    }

    /** What Home opened last, for a TV without Watch Next rows (Fire OS) or with nothing in them. */
    private static List<ResumePick.Item> recentApps(Context ctx, Set<String> hidden) {
        PackageManager pm = ctx.getPackageManager();
        List<ResumePick.Item> out = new ArrayList<>();
        for (Map.Entry<String, Long> e : LastOpened.recent(ctx)) {
            String pkg = e.getKey();
            if (pkg.equals(ctx.getPackageName()) || pm.getLaunchIntentForPackage(pkg) == null) continue;
            String label;
            try {
                label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
            } catch (PackageManager.NameNotFoundException gone) {
                continue;
            }
            ResumePick.Item item = new ResumePick.Item(pkg, label, LastOpened.describe(ctx, e.getValue()), null, null,
                    e.getValue(), -1f, ResumePick.APP);
            if (hidden.contains(item.key())) continue;
            out.add(item);
            if (out.size() == ResumePick.APPS_LIMIT) break;
        }
        return out;
    }
}
