package com.gathin.tvhome;

import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.provider.Settings;

/** Reads the connected network name. Android hides it unless the location permission is granted and location is on. */
final class WifiName {
    private static long deniedAt = -60_000L;

    private WifiName() {}

    static boolean permission(Context c) {
        return c.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean locationOn(Context c) {
        LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        return lm != null && lm.isLocationEnabled();
    }

    /** Turns system location on when this app may write secure settings (pm grant WRITE_SECURE_SETTINGS); true when it is on. */
    static boolean enableLocation(Context c) {
        if (locationOn(c)) return true;
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - deniedAt < 60_000L) return false;   // the network label asks often: do not retry a refusal every time
        try {
            Settings.Secure.putInt(c.getContentResolver(), "location_mode", 3);
        } catch (Throwable denied) {
            deniedAt = now;
            return false;
        }
        return locationOn(c);
    }

    /** The name without quotes, or null while Android keeps it hidden. */
    static String read(Context c) {
        try {
            WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
            String ssid = info != null ? info.getSSID() : null;
            if (ssid == null) return null;
            ssid = ssid.replace("\"", "").trim();
            return ssid.isEmpty() || ssid.contains("unknown") || ssid.equals("0x") ? null : ssid;
        } catch (Exception e) {
            return null;
        }
    }
}
