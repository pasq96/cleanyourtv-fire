package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Which rows the Continue watching shelf shows, and how they read. No Android classes. */
final class ResumePick {
    /** Settings keys (launcher_settings) and the choices behind them. */
    static final String KEY_ROW = "resume_row", KEY_AGE = "resume_age", KEY_PER_APP = "resume_per_app",
            KEY_SUGGESTED = "resume_suggested", KEY_APPS = "resume_apps", KEY_HIDDEN = "resume_hidden";
    /** Days a row may be old (0: no limit) and rows kept per app (0: no limit); the settings store the index. */
    static final int[] AGES = {7, 30, 90, 0}, PER_APP = {1, 3, 5, 0};
    static final int DEFAULT_AGE = 1, DEFAULT_PER_APP = 1;
    static final int LIMIT = 10, SUGGESTED_LIMIT = 10, SUGGESTED_PER_APP = 3, APPS_LIMIT = 8;
    /** Item types besides watch_next_type 0 continue, 1 next episode, 2 new. */
    static final int SUGGESTED = 10, APP = 11;
    private static final long DAY_MS = 24L * 3600 * 1000;
    private static final float DONE = 0.95f;

    static final class Item {
        final String pkg, title, subtitle, image, intent;
        final long engaged;
        final float progress;   // 0..1, -1 unknown
        final int type;

        Item(String pkg, String title, String subtitle, String image, String intent, long engaged, float progress, int type) {
            this.pkg = pkg; this.title = title; this.subtitle = subtitle; this.image = image;
            this.intent = intent; this.engaged = engaged; this.progress = progress; this.type = type;
        }

        /** What "hide" remembers: the app and the title, so a new episode of a hidden show stays hidden. */
        String key() { return pkg + '\n' + title; }
    }

    private ResumePick() { }

    /** Newest first, no stale, finished or hidden rows, at most perApp per package, at most LIMIT. */
    static List<Item> pick(List<Item> all, long now, int maxAgeDays, int perApp, Set<String> hidden) {
        List<Item> sorted = new ArrayList<>(all);
        sorted.sort(Comparator.comparingLong((Item i) -> i.engaged).reversed());
        List<Item> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Map<String, Integer> perPackage = new LinkedHashMap<>();
        for (Item i : sorted) {
            if (hidden.contains(i.key()) || !seen.add(i.key())) continue;
            if (maxAgeDays > 0 && i.engaged > 0 && now - i.engaged > maxAgeDays * DAY_MS) continue;
            if (i.type == 0 && i.progress >= DONE) continue;
            int n = perPackage.getOrDefault(i.pkg, 0);
            if (perApp > 0 && n >= perApp) continue;
            perPackage.put(i.pkg, n + 1);
            out.add(i);
            if (out.size() == LIMIT) break;
        }
        return out;
    }

    /** Preview-channel programs: heaviest first, apps taking turns so one app does not fill the shelf. */
    static List<Item> suggest(List<Item> all, Set<String> hidden, Set<String> already) {
        List<Item> sorted = new ArrayList<>(all);
        sorted.sort(Comparator.comparingLong((Item i) -> i.engaged).reversed());
        Map<String, List<Item>> byApp = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>(already);
        for (Item i : sorted) {
            if (hidden.contains(i.key()) || !seen.add(i.key())) continue;
            List<Item> list = byApp.computeIfAbsent(i.pkg, k -> new ArrayList<>());
            if (list.size() < SUGGESTED_PER_APP) list.add(i);
        }
        List<Item> out = new ArrayList<>();
        for (int round = 0; round < SUGGESTED_PER_APP && out.size() < SUGGESTED_LIMIT; round++) {
            for (List<Item> list : byApp.values()) {
                if (round < list.size() && out.size() < SUGGESTED_LIMIT) out.add(list.get(round));
            }
        }
        return out;
    }

    /** "S1E2 Episode title", or whichever parts exist; null when none. */
    static String subtitle(String episodeTitle, String season, String episode) {
        StringBuilder sb = new StringBuilder();
        if (!blank(season)) sb.append('S').append(season.trim());
        if (!blank(episode)) sb.append('E').append(episode.trim());
        if (!blank(episodeTitle)) sb.append(sb.length() > 0 ? " " : "").append(episodeTitle.trim());
        return sb.length() == 0 ? null : sb.toString();
    }

    static float progress(long position, long duration) {
        return duration <= 0 || position <= 0 ? -1f : Math.min(1f, (float) position / duration);
    }

    static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
