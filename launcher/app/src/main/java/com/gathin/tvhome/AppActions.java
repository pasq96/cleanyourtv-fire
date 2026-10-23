package com.gathin.tvhome;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;

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

    /** Installed by the user (not part of the system image), so Uninstall can remove it. */
    static boolean removable(Context context, String pkg) {
        try {
            ApplicationInfo ai = context.getPackageManager().getApplicationInfo(pkg, 0);
            return (ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0;
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
