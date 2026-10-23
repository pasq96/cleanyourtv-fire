package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Fire OS: Amazon's launcher stays the protected system home (pm disable-user and set-home-activity are refused),
 * so HomeKeyService covers its home window with this launcher. Detected at runtime, no build flag.
 */
final class FireOs {
    static final String LAUNCHER = "com.amazon.tv.launcher";
    /** Opening Fire OS Settings passes through Amazon's home window; the redirect holds off this long. */
    static final long SETTINGS_QUIET_MS = 10_000;
    /** One redirect per Home press: Amazon's home can report several window changes while it comes up. */
    static final long DEBOUNCE_MS = 600;
    /** LOOP_MAX redirects within LOOP_WINDOW_MS: this launcher isn't staying up (crash, OOM), so Amazon's home is
     *  left alone for LOOP_BACKOFF_MS instead of flickering between the two. */
    static final int LOOP_MAX = 4;
    static final long LOOP_WINDOW_MS = 8_000, LOOP_BACKOFF_MS = 60_000;
    /** Amazon entries that would only show as tiles of the home being covered, or of Settings (Gear button). */
    static final String[] HIDDEN = {LAUNCHER, "com.amazon.tv.settings.v2"};

    private static volatile long settingsOpenedAt = -SETTINGS_QUIET_MS;

    private FireOs() {}

    static boolean is(Context context) {
        return context.getPackageManager().hasSystemFeature("amazon.hardware.fire_tv");
    }

    /** The home-screen classes among the stock launcher's HOME activities: Settings (same package) never,
     *  and the HomeActivity ones only when there are any (Fire OS 7/8: ui.HomeActivity_vNext). */
    static Set<String> homeClasses(Collection<String> declared) {
        Set<String> all = new HashSet<>(), home = new HashSet<>();
        for (String cls : declared) {
            if (cls == null || cls.contains("Settings")) continue;
            all.add(cls);
            if (cls.contains("HomeActivity")) home.add(cls);
        }
        return home.isEmpty() ? all : home;
    }

    /** Call before starting {@code intent}: a Settings screen opened from here starts the quiet window. */
    static void opening(Intent intent) {
        if (intent == null) return;
        ComponentName c = intent.getComponent();
        String pkg = c != null ? c.getPackageName() : intent.getPackage();
        String action = intent.getAction();
        if (LAUNCHER.equals(pkg) || (action != null && action.startsWith("android.settings."))) {
            settingsOpenedAt = SystemClock.uptimeMillis();
        }
    }

    static boolean settingsQuiet(long now) {
        return now - settingsOpenedAt < SETTINGS_QUIET_MS;
    }

    /** Debounce plus loop back-off for the redirect; one per service, so no locking. */
    static final class Guard {
        private final long[] recent = new long[LOOP_MAX];
        private int count;
        private long last = -DEBOUNCE_MS, pausedUntil;

        boolean allow(long now) {
            if (now < pausedUntil || now - last < DEBOUNCE_MS) return false;
            int slot = count % LOOP_MAX;
            if (count >= LOOP_MAX && now - recent[slot] < LOOP_WINDOW_MS) {
                pausedUntil = now + LOOP_BACKOFF_MS;
                count = 0;
                return false;
            }
            recent[slot] = last = now;
            count++;
            return true;
        }

        boolean paused(long now) {
            return now < pausedUntil;
        }
    }
}
