package com.gathin.tvhome;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;

import java.util.Arrays;

/**
 * Tile menu actions on another app. A normal app can't force-stop, clear data or clear cache of another one
 * (signature permissions): Force stop ends its background processes, App info is the system page with those buttons.
 */
final class AppActions {
    private AppActions() {}

    /** Ends the app's processes; with Home in front it is in the background, so this closes it (not its foreground services). */
    static void stop(Context context, String pkg) {
        ActivityManager am = context.getSystemService(ActivityManager.class);
        String label = label(context, pkg);
        try {
            am.killBackgroundProcesses(pkg);
            Toast.makeText(context, context.getString(R.string.tile_stopped, label), Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            Toast.makeText(context, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    static Intent info(String pkg) {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg));
    }

    /** The system uninstall confirmation; nothing is removed without it. */
    static Intent uninstall(String pkg) {
        return new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg));
    }

    /** A system app that was never updated: Android keeps its App info page and Uninstall out of reach. */
    static boolean pureSystem(Context context, String pkg) {
        try {
            int f = context.getPackageManager().getApplicationInfo(pkg, 0).flags;
            return (f & ApplicationInfo.FLAG_SYSTEM) != 0 && (f & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** The App info page exists and the app is not a bare system one (those pages refuse data and cache actions). */
    static boolean hasInfo(Context context, String pkg) {
        return !pureSystem(context, pkg) && info(pkg).resolveActivity(context.getPackageManager()) != null;
    }

    /** A system app the user may still turn off from its App info page: not Android itself, nor Amazon's or Google's core. */
    static boolean disablable(Context context, String pkg) {
        if (!pureSystem(context, pkg) || info(pkg).resolveActivity(context.getPackageManager()) == null) return false;
        if (pkg.equals("android") || pkg.startsWith("com.android.") || pkg.startsWith("com.amazon.tv.")
                || pkg.startsWith("com.amazon.firetv.") || pkg.equals("com.google.android.gms")) return false;
        return !Arrays.asList(FireOs.HIDDEN).contains(pkg);
    }

    /** Not a bare system app, so Uninstall (or removing its updates) can work. */
    static boolean removable(Context context, String pkg) {
        try {
            context.getPackageManager().getApplicationInfo(pkg, 0);
            return !pureSystem(context, pkg);   // an updated system app can at least drop its updates
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    static String label(Context context, String pkg) {
        PackageManager pm = context.getPackageManager();
        try {
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }
}
