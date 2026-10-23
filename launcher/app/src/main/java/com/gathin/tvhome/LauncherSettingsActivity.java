package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    static final String EXTRA_REORDER = "com.gathin.tvhome.extra.REORDER_FROM_SETTINGS";
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[5];
    private final TextView[] rowChoices = new TextView[7];
    private static final int[] DOCK_OPACITIES = {100, 70, 50, 30};
    private final TextView[] dockChoices = new TextView[DOCK_OPACITIES.length];
    private TextView wallpaperChoice;
    private View advancedBlock;
    private TextView followThemeChoice;
    private TextView themeChoice;
    private TextView vpnChoice, wifiNameChoice;
    private final List<Runnable> toggleRefreshers = new ArrayList<>();
    private int columns;
    private int rowsBelowDock;
    private int dockOpacity;
    private boolean animations;
    private boolean advancedEffects;
    /** VPN apps from the theme, used while no on-TV choice exists (Config is read once, not on every refresh). */
    private String themeVpnApp;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        View reorderChoice = findViewById(R.id.reorderChoice);
        ((View) reorderChoice.getParent()).setVisibility(config.launcherSettingsButton ? View.VISIBLE : View.GONE);
        reorderChoice.setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_REORDER, true));
            finish();
        });
        themeVpnApp = config.vpnApp;
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        dockOpacity = config.dockOpacity;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7, R.id.columns8};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                changed();
                refresh();
            });
        }
        int[] rowIds = {R.id.rows0, R.id.rows1, R.id.rows2, R.id.rows3, R.id.rows4, R.id.rows5, R.id.rows6};
        for (int i = 0; i < rowIds.length; i++) {
            final int value = i;
            rowChoices[i] = findViewById(rowIds[i]);
            rowChoices[i].setOnClickListener(v -> {
                if (rowsBelowDock == value) return;
                rowsBelowDock = value;
                choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, value).apply();
                changed();
                refresh();
            });
        }
        int[] dockIds = {R.id.dock100, R.id.dock70, R.id.dock50, R.id.dock30};
        for (int i = 0; i < dockIds.length; i++) {
            final int value = DOCK_OPACITIES[i];
            dockChoices[i] = findViewById(dockIds[i]);
            dockChoices[i].setOnClickListener(v -> {
                if (dockOpacity == value) return;
                dockOpacity = value;
                choices.edit().putInt(Config.DOCK_OPACITY_KEY, value).apply();
                changed();
                refresh();
            });
        }
        // Keep the numeric choices increasing in the Hebrew reading direction. Reorder the
        // actual views so D-pad focus follows their positions without changing the saved values.
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(columnChoices);
            reverseRow(rowChoices);
            reverseRow(dockChoices);
        }
        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperChoice.setOnClickListener(v -> showWallpaperPicker());
        vpnChoice = findViewById(R.id.vpnChoice);
        vpnChoice.setOnClickListener(v -> pickVpnApps());
        findViewById(R.id.hiddenChoice).setOnClickListener(v -> pickHiddenApps());
        findViewById(R.id.resetChoice).setOnClickListener(v -> GlassDialog.confirm(this,
                getString(R.string.reset_defaults), getString(R.string.reset_defaults_hint), R.string.reset_defaults, () -> {
                    // Back to the built-in setup: no VPN app, theme and layout choices cleared (hidden apps stay as they are).
                    // Keep the change counter and theme stamp: Home must still see a change, and the stamp must not look new.
                    long revision = choices.getLong(Config.SETTINGS_REVISION_KEY, 0);
                    String stamp = choices.getString(Config.THEME_SIGNATURE_KEY, null);
                    SharedPreferences.Editor wipe = choices.edit().clear().putString(Config.VPN_APP_KEY, "")
                            .putLong(Config.SETTINGS_REVISION_KEY, revision);
                    if (stamp != null) wipe.putString(Config.THEME_SIGNATURE_KEY, stamp);
                    wipe.commit();
                    changed();
                    recreate();
                }));
        findViewById(R.id.weatherKeyChoice).setOnClickListener(v -> askText(Weather.KEY_API, R.string.weather_key));
        findViewById(R.id.weatherCityChoice).setOnClickListener(v -> askText(Weather.KEY_CITY, R.string.weather_city));
        wifiNameChoice = findViewById(R.id.wifiNameChoice);
        wifiNameChoice.setOnClickListener(v -> {
            if (locationGranted()) {
                if (!WifiName.enableLocation(this)) Toast.makeText(this, R.string.wifi_name_cmd, Toast.LENGTH_LONG).show();
                else Toast.makeText(this, R.string.wifi_name_loc, Toast.LENGTH_LONG).show();
                changed();
                refresh();
                return;
            }
            try {
                requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 78);
            } catch (RuntimeException e) {
                Toast.makeText(this, R.string.wifi_name_adb, Toast.LENGTH_LONG).show();
            }
        });
        LinearLayout toggles = findViewById(R.id.togglesContainer);
        addToggle(toggles, "show_all_apps", R.string.opt_show_all_apps, R.string.d_show_all, config.showAllApps);
        addToggle(toggles, "long_press_move", R.string.opt_long_press_move, R.string.d_long_press, config.longPressMove);
        addToggle(toggles, "always_show_names", R.string.opt_names, 0, config.alwaysShowNames);
        LinearLayout buttons = findViewById(R.id.buttonsContainer);
        addToggle(buttons, "show_inputs_pill", R.string.opt_inputs, 0, config.showInputsPill);
        addToggle(buttons, "show_network_pill", R.string.opt_wifi, 0, config.showNetworkPill);
        addToggle(buttons, Pills.SHOW_POWER_KEY, R.string.opt_power, 0, true);
        addPillStyles((LinearLayout) findViewById(R.id.pillsContainer), config);
        addSegmented(findViewById(R.id.segLift), R.string.dock_position, R.string.d_dock_height,
                new int[]{R.string.dock_pos_0, R.string.dock_pos_1, R.string.dock_pos_2, R.string.dock_pos_3, R.string.dock_pos_4},
                () -> Math.min(4, choices.getInt("dock_lift", 1)), i -> choices.edit().putInt("dock_lift", i).apply());
        addSegmented(findViewById(R.id.segBanner), R.string.lbl_banner, R.string.d_banner,
                new int[]{R.string.banner_off, R.string.banner_stretch, R.string.banner_gradient},
                () -> Themes.bannerStyle(this), i -> choices.edit().putInt(Themes.BANNER_STYLE_KEY, i).apply());
        addSegmented(findViewById(R.id.segAnimations), R.string.lbl_animations, 0, new int[]{R.string.on, R.string.off},
                () -> animations ? 0 : 1, i -> {
                    animations = i == 0;
                    if (!animations) advancedEffects = false;
                    choices.edit().putBoolean("animations", animations).putBoolean("advanced_effects", advancedEffects).apply();
                });
        advancedBlock = addSegmented(findViewById(R.id.segAdvanced), R.string.advanced_effects, R.string.d_advanced,
                new int[]{R.string.on, R.string.off}, () -> advancedEffects ? 0 : 1, i -> {
                    if (!animations || (i == 0) == advancedEffects) return;
                    if (i == 1) {
                        setAdvanced(false);
                    } else {
                        GlassDialog.confirm(this, getString(R.string.advanced_effects), getString(R.string.advanced_effects_warning),
                                R.string.enable, () -> setAdvanced(true));
                    }
                });
        addSegmented(findViewById(R.id.segSort), R.string.lbl_sort, 0, new int[]{R.string.sort_manual, R.string.sort_recent},
                () -> Themes.sortRecent(this) ? 1 : 0, i -> choices.edit().putBoolean(Themes.SORT_RECENT_KEY, i == 1).apply());
        addSegmented(findViewById(R.id.segUnits), R.string.lbl_units, 0, new int[]{R.string.weather_celsius, R.string.weather_fahrenheit},
                () -> Weather.fahrenheit(choices) ? 1 : 0, i -> {
                    choices.edit().putString(Weather.KEY_UNITS, i == 1 ? "f" : "c").apply();
                    Weather.forget(this);
                });
        addSegmented(findViewById(R.id.segLanguage), R.string.lbl_lang, 0,
                new int[]{R.string.lang_system, R.string.lang_en, R.string.lang_it},
                () -> Arrays.asList(Lang.IDS).indexOf(Lang.chosen(this)), i -> {
                    if (Lang.IDS[i].equals(Lang.chosen(this))) return;
                    choices.edit().putString(Lang.KEY, Lang.IDS[i]).commit();
                    changed();
                    recreate();
                });
        findViewById(R.id.backupChoice).setOnClickListener(v -> {
            File f = Backup.file(this);
            Toast.makeText(this, Backup.export(this) ? getString(R.string.backup_done, f.getAbsolutePath())
                    : getString(R.string.backup_failed), Toast.LENGTH_LONG).show();
        });
        findViewById(R.id.restoreChoice).setOnClickListener(v -> {
            boolean ok = Backup.restore(this);
            Toast.makeText(this, ok ? R.string.restore_done : R.string.restore_failed, Toast.LENGTH_LONG).show();
            if (ok) { setResult(RESULT_OK); recreate(); }
        });
        themeChoice = findViewById(R.id.themeChoice);
        themeChoice.setOnClickListener(v -> {
            File pushedDir = Config.dir(this);
            if (pushedDir != null && new File(pushedDir, Config.FILE_NAME).isFile()) {
                Toast.makeText(this, R.string.theme_state_file, Toast.LENGTH_LONG).show();
                return;
            }
            String next = Themes.next(Themes.chosen(choices, FireOs.is(this)));
            // The new theme's layout applies: drop on-TV choices made over the old one.
            choices.edit().putString(Themes.KEY, next).remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY)
                    .remove("animations").remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY)
                    .remove(Config.DOCK_OPACITY_KEY).remove("show_all_apps").remove("long_press_move")
                    .remove("always_show_names").remove("show_inputs_pill").remove("show_network_pill").remove("dock_lift").commit();
            changed();
            recreate();   // rows and toggles take the new theme's defaults
        });
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove(Config.VPN_APP_KEY).remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                    .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY)
                    .remove(Config.DOCK_OPACITY_KEY).remove("show_all_apps").remove("long_press_move")
                    .remove("always_show_names").remove("show_inputs_pill").remove("show_network_pill").remove("dock_lift").commit();
            changed();
            recreate();
        });
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v -> {
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
            open(new Intent(Settings.ACTION_HOME_SETTINGS));
        });
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
        refresh();
        if (config.launcherSettingsButton) reorderChoice.requestFocus();
        else columnChoices[Math.max(0, Math.min(columns - 4, columnChoices.length - 1))].requestFocus();
    }

    private boolean locationGranted() {
        return checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != 78) return;
        if (!locationGranted()) Toast.makeText(this, R.string.wifi_name_adb, Toast.LENGTH_LONG).show();
        changed();
        refresh();
    }

    private void reverseRow(TextView[] views) {
        LinearLayout row = (LinearLayout) views[0].getParent();
        row.removeAllViews();
        for (int i = views.length - 1; i >= 0; i--) row.addView(views[i]);
    }

    private void showWallpaperPicker() {
        File dir = Config.dir(this);
        List<File> images = new java.util.ArrayList<>(WallpaperRotation.images(
                dir == null ? null : new File(dir, "wallpapers")));
        // A theme with a single wallpaper.jpg offers that picture too (Config maps the choice back to it).
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        String[] labels = new String[images.size() + 1];
        for (int i = 0; i < images.size(); i++) labels[i] = images.get(i).getName();
        labels[images.size()] = getString(R.string.wallpaper_none);
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int checked = "none".equals(current) ? images.size()
                : indexOf(images, current);
        boolean[] on = new boolean[labels.length];
        if (checked >= 0) on[checked] = true;
        new GlassDialog(this, getString(R.string.wallpaper_label)).width(560)
                .list(labels, on, false, which -> {
                    if (which == images.size()) {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, "none").apply();
                    } else {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, images.get(which).getName()).apply();
                    }
                    changed();
                    Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .button(android.R.string.cancel, null)
                .button(R.string.wallpaper_theme_default, () -> {
                    choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
                    changed();
                    refresh();
                })
                .show();
    }

    private static int indexOf(List<File> images, String name) {
        if (name == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    /**
     * A choice was saved. Home rebuilds when it next resumes because the change count moved, whether these
     * settings are left with Back or with the remote's Home button (which cancels the activity result).
     */
    private void changed() {
        setResult(RESULT_OK);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY,
                choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        changed();
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        for (int i = 0; i < rowChoices.length; i++) {
            rowChoices[i].setSelected(rowsBelowDock == i);
        }
        for (int i = 0; i < dockChoices.length; i++) {
            dockChoices[i].setSelected(dockOpacity == DOCK_OPACITIES[i]);
        }
        advancedBlock.setAlpha(animations ? 1f : 0.45f);
        String wallpaperCurrent = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        wallpaperChoice.setText(getString(R.string.wallpaper_state,
                "none".equals(wallpaperCurrent) ? getString(R.string.wallpaper_none)
                        : wallpaperCurrent != null ? wallpaperCurrent
                        : getString(R.string.wallpaper_theme_default)));
        followThemeChoice.setSelected(!hasOnTvChoices());
        String wkey = choices.getString(Weather.KEY_API, "").trim(), wcity = choices.getString(Weather.KEY_CITY, "").trim();
        ((TextView) findViewById(R.id.weatherKeyChoice)).setText(getString(R.string.weather_key_state,
                wkey.isEmpty() ? getString(R.string.weather_unset) : getString(R.string.weather_set)));
        ((TextView) findViewById(R.id.weatherCityChoice)).setText(getString(R.string.weather_city_state,
                wcity.isEmpty() ? getString(R.string.weather_unset) : wcity));
        wifiNameChoice.setText(getString(R.string.wifi_name_state,
                getString(!locationGranted() ? R.string.wifi_name_ask : !WifiName.locationOn(this) ? R.string.wifi_name_off
                        : WifiName.read(this) != null ? R.string.wifi_name_ok : R.string.wifi_name_hidden)));
        for (Runnable r : toggleRefreshers) r.run();
        String vpns = choices.getString(Config.VPN_APP_KEY, null);
        if (vpns == null) vpns = themeVpnApp;
        List<String> names = new ArrayList<>();
        for (String pkg : vpns.split(",")) if (!pkg.trim().isEmpty()) names.add(AppActions.label(this, pkg.trim()));
        vpnChoice.setText(getString(R.string.vpn_state, names.isEmpty() ? getString(R.string.vpn_none)
                : android.text.TextUtils.join(", ", names)));
        File dir = Config.dir(this);
        boolean pushed = dir != null && new File(dir, Config.FILE_NAME).isFile();
        String name = getString(Themes.label(Themes.chosen(choices, FireOs.is(this))));
        themeChoice.setText(pushed ? getString(R.string.theme_state_file)
                : getString(R.string.theme_state, name));
    }

    private boolean hasOnTvChoices() {
        return choices.contains(Config.VPN_APP_KEY) || choices.contains("columns") || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains("animations") || choices.contains("advanced_effects")
                || choices.contains(Config.WALLPAPER_CHOICE_KEY) || choices.contains(Config.DOCK_OPACITY_KEY)
                || choices.contains("dock_lift");
    }

    /** A one-line text entry (API key, place) saved as a choice; the weather cache is dropped so it refreshes. */
    private void askText(String key, int title) {
        GlassDialog.ask(this, title, choices.getString(key, ""), value -> {
            choices.edit().putString(key, value).apply();
            Weather.forget(this);
            changed();
            refresh();
        });
    }

    /**
     * One setting as a label and a capsule of options (a note under the label only when needed). A tap saves the
     * option through set, then everything refreshes. Returns the block, so a caller can dim it.
     */
    private View addSegmented(LinearLayout parent, int label, int note, int[] options,
                              java.util.function.IntSupplier current, java.util.function.IntConsumer set) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.HORIZONTAL);
        block.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams blockLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blockLp.topMargin = (int) (12 * d);
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this, null, 0, R.style.SettingsLabel);
        name.setText(label);
        text.addView(name);
        if (note != 0) {
            TextView hint = new TextView(this, null, 0, R.style.SettingsNote);
            hint.setText(note);
            text.addView(hint);
        }
        block.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout capsule = new LinearLayout(this, null, 0, R.style.SettingsSegments);
        final TextView[] segs = new TextView[options.length];
        for (int i = 0; i < options.length; i++) {
            final int index = i;
            segs[i] = new TextView(this, null, 0, R.style.SettingsSeg);
            String option = getString(options[i]);
            segs[i].setText(option.substring(0, 1).toUpperCase() + option.substring(1));
            segs[i].setOnClickListener(v -> {
                if (current.getAsInt() == index) return;
                set.accept(index);
                changed();
                refresh();
            });
            capsule.addView(segs[i], new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (int) (44 * d)));
        }
        block.addView(capsule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        toggleRefreshers.add(() -> {
            for (int i = 0; i < segs.length; i++) segs[i].setSelected(current.getAsInt() == i);
        });
        parent.addView(block, blockLp);
        return block;
    }

    /** One setting per top pill: icon with label, or icon only, independently of the others. */
    private void addPillStyles(LinearLayout parent, Config config) {
        int[] names = {R.string.pill_inputs, R.string.pill_wifi, R.string.pill_vpn, R.string.pill_launcher,
                R.string.pill_settings, R.string.pill_power};
        for (int i = 0; i < Pills.IDS.length; i++) {
            final String id = Pills.IDS[i];
            addSegmented(parent, names[i], 0, new int[]{R.string.pill_full, R.string.pill_compact},
                    () -> Pills.compact(choices, config, id) ? 1 : 0, v -> Pills.set(choices, id, v == 1));
        }
    }

    /** An on/off setting saved as a boolean choice that Config reads under the same key; desc 0 means no note. */
    private void addToggle(LinearLayout parent, String key, int label, int desc, boolean theme) {
        addSegmented(parent, label, desc, new int[]{R.string.on, R.string.off},
                () -> choices.getBoolean(key, theme) ? 0 : 1, i -> choices.edit().putBoolean(key, i == 0).apply());
    }

    /** Launchable apps that are not hidden by the system, sorted by name: package -> label. */
    private List<String[]> launchableApps(boolean vpnOnly) {
        PackageManager pm = getPackageManager();
        List<ResolveInfo> found = new ArrayList<>();
        if (vpnOnly) {
            for (ResolveInfo ri : pm.queryIntentServices(new Intent("android.net.VpnService"), 0)) found.add(ri);
        } else {
            found.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
            found.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
        }
        Set<String> seen = new HashSet<>();
        List<String[]> out = new ArrayList<>();
        for (ResolveInfo ri : found) {
            String pkg = ri.activityInfo != null ? ri.activityInfo.packageName : ri.serviceInfo.packageName;
            if (pkg.equals(getPackageName()) || Arrays.asList(FireOs.HIDDEN).contains(pkg) || !seen.add(pkg)) continue;
            out.add(new String[]{pkg, AppActions.label(this, pkg)});
        }
        Collections.sort(out, (a, b) -> a[1].compareToIgnoreCase(b[1]));
        return out;
    }

    private void pickVpnApps() {
        List<String[]> apps = launchableApps(true);
        if (apps.isEmpty()) {
            Toast.makeText(this, R.string.vpn_no_apps, Toast.LENGTH_LONG).show();
            return;
        }
        String saved = choices.getString(Config.VPN_APP_KEY, null);
        Set<String> current = new HashSet<>(Arrays.asList((saved != null ? saved : themeVpnApp).split("\\s*,\\s*")));
        CharSequence[] labels = new CharSequence[apps.size()];
        boolean[] on = new boolean[apps.size()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = apps.get(i)[1];
            on[i] = current.contains(apps.get(i)[0]);
        }
        new GlassDialog(this, getString(R.string.vpn_apps)).width(560)
                .list(labels, on, true, null)
                .button(android.R.string.cancel, null)
                .button(android.R.string.ok, () -> {
                    List<String> picked = new ArrayList<>();
                    for (int i = 0; i < on.length; i++) if (on[i]) picked.add(apps.get(i)[0]);
                    choices.edit().putString(Config.VPN_APP_KEY, android.text.TextUtils.join(",", picked)).apply();
                    changed();
                    refresh();
                })
                .show();
    }

    /** Every launchable app with a tick when it is hidden from Home; OK applies the changes. */
    private void pickHiddenApps() {
        List<String[]> apps = launchableApps(false);
        Set<String> hidden = LibraryApps.loadRemoved(this);
        CharSequence[] labels = new CharSequence[apps.size()];
        boolean[] on = new boolean[apps.size()], was = new boolean[apps.size()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = apps.get(i)[1];
            on[i] = was[i] = hidden.contains(apps.get(i)[0]);
        }
        new GlassDialog(this, getString(R.string.hidden_apps)).width(560)
                .list(labels, on, true, null)
                .button(android.R.string.cancel, null)
                .button(android.R.string.ok, () -> {
                    boolean any = false;
                    for (int i = 0; i < on.length; i++) {
                        if (on[i] == was[i]) continue;
                        boolean saved = LibraryApps.save(this, apps.get(i)[0], !on[i]);
                        if (!saved) Toast.makeText(this, R.string.library_save_failed, Toast.LENGTH_LONG).show();
                        any |= saved;
                    }
                    if (any) changed();
                })
                .show();
    }

    private void open(Intent intent) {
        try {
            FireOs.opening(intent);
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
