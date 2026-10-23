package com.gathin.tvhome;

/** Lock, power off and restart. Apps may not do these directly: the accessibility service drives the system power menu. */
final class PowerActions {
    private PowerActions() {}

    /** which: HomeKeyService.POWER_LOCK, POWER_OFF or POWER_RESTART. False when the service is off. */
    static boolean run(int which) {
        return HomeKeyService.power(which);
    }
}
