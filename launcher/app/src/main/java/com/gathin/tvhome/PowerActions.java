package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.os.PowerManager;
import android.os.SystemClock;

/**
 * Power off and restart. An ordinary app is not allowed to do either, so each tries the direct call and
 * falls back to the system power menu through the accessibility service (HomeKeyService), if it is on.
 */
final class PowerActions {
    private PowerActions() {}

    /** True when something was triggered: the reboot itself, or the system power menu. */
    static boolean restart(Context ctx) {
        try {
            ((PowerManager) ctx.getSystemService(Context.POWER_SERVICE)).reboot(null);
            return true;
        } catch (Throwable denied) {
            return HomeKeyService.global(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG);
        }
    }

    /** Fire TV has no true off: this is standby, from the direct call or the service's lock-screen action. */
    static boolean off(Context ctx) {
        try {
            PowerManager.class.getMethod("goToSleep", long.class)
                    .invoke(ctx.getSystemService(Context.POWER_SERVICE), SystemClock.uptimeMillis());
            return true;
        } catch (Throwable denied) {
            return HomeKeyService.global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                    || HomeKeyService.global(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG);
        }
    }
}
