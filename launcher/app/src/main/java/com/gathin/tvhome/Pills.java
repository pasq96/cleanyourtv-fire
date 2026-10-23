package com.gathin.tvhome;

import android.content.SharedPreferences;

/** Top pills and their look: full (icon and label) or compact (icon only), chosen per pill on the TV. */
final class Pills {
    static final String[] IDS = {"inputs", "wifi", "vpn", "launcher", "settings", "power"};
    static final String SHOW_POWER_KEY = "show_power_pill";

    private Pills() {}

    /** The saved choice, else what the theme already did: only the power pill starts compact. */
    static boolean compact(SharedPreferences choices, Config cfg, String id) {
        String saved = choices.getString("pill_style_" + id, null);
        if (saved != null) return saved.equals("compact");
        switch (id) {
            case "inputs": return cfg.iconOnlyInputs;
            case "wifi":
            case "settings": return cfg.iconOnlyButtons;
            case "power": return true;
            default: return false;
        }
    }

    static void set(SharedPreferences choices, String id, boolean compact) {
        choices.edit().putString("pill_style_" + id, compact ? "compact" : "full").apply();
    }
}
