package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Which Watch Next rows the Continue watching shelf shows, and how they read. No Android classes. */
final class ResumePick {
    static final int LIMIT = 10;
    private static final long MAX_AGE_MS = 45L * 24 * 3600 * 1000;
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
    }

    /** Newest first, no stale or finished rows, one per package and title, at most LIMIT. */
    static List<Item> pick(List<Item> all, long now) {
        List<Item> sorted = new ArrayList<>(all);
        sorted.sort(Comparator.comparingLong((Item i) -> i.engaged).reversed());
        List<Item> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Item i : sorted) {
            if (i.engaged > 0 && now - i.engaged > MAX_AGE_MS) continue;
            if (i.type == 0 && i.progress >= DONE) continue;
            if (!seen.add(i.pkg + '\n' + i.title)) continue;
            out.add(i);
            if (out.size() == LIMIT) break;
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

    private ResumePick() { }
}
