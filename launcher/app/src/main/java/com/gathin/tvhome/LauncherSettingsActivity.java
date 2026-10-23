package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
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
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView followThemeChoice;
    private TextView themeChoice;
    private TextView sortChoice, vpnChoice, bannerChoice;
    private final List<Runnable> toggleRefreshers = new ArrayList<>();
    private int columns;
    private int rowsBelowDock;
    private int dockOpacity;
    private boolean animations;
    private boolean advancedEffects;

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
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            changed();
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        vpnChoice = findViewById(R.id.vpnChoice);
        vpnChoice.setOnClickListener(v -> pickVpnApps());
        findViewById(R.id.hiddenChoice).setOnClickListener(v -> pickHiddenApps());
        findViewById(R.id.resetChoice).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.reset_defaults)
                .setMessage(R.string.reset_defaults_hint)
                .setPositiveButton(R.string.reset_defaults, (d, w) -> {
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
                })
                .setNegativeButton(android.R.string.cancel, null).show());
        bannerChoice = findViewById(R.id.bannerChoice);
        bannerChoice.setOnClickListener(v -> {
            choices.edit().putInt(Themes.BANNER_STYLE_KEY, (Themes.bannerStyle(this) + 1) % 3).apply();
            changed();
            refresh();
        });
        LinearLayout toggles = findViewById(R.id.togglesContainer);
        addToggle(toggles, "show_all_apps", R.string.opt_show_all_apps, R.string.d_show_all, config.showAllApps);
        addToggle(toggles, "long_press_move", R.string.opt_long_press_move, R.string.d_long_press, config.longPressMove);
        addToggle(toggles, "always_show_names", R.string.opt_names, R.string.d_names, config.alwaysShowNames);
        LinearLayout buttons = findViewById(R.id.buttonsContainer);
        addToggle(buttons, "show_inputs_pill", R.string.opt_inputs, R.string.d_inputs, config.showInputsPill);
        addToggle(buttons, "show_network_pill", R.string.opt_wifi, R.string.d_wifi, config.showNetworkPill);
        sortChoice = findViewById(R.id.sortChoice);
        sortChoice.setOnClickListener(v -> {
            choices.edit().putBoolean(Themes.SORT_RECENT_KEY, !Themes.sortRecent(this)).apply();
            changed();
            refresh();
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
                    .remove("always_show_names").remove("show_inputs_pill").remove("show_network_pill").commit();
            changed();
            recreate();   // rows and toggles take the new theme's defaults
        });
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove(Config.VPN_APP_KEY).remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                    .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY)
                    .remove(Config.DOCK_OPACITY_KEY).remove("show_all_apps").remove("long_press_move")
                    .remove("always_show_names").remove("show_inputs_pill").remove("show_network_pill").commit();
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
        new AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == images.size()) {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, "none").apply();
                    } else {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY,
                                images.get(which).getName()).apply();
                    }
                    changed();
                    Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .setNeutralButton(R.string.wallpaper_theme_default, (dialog, which) -> {
                    choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
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
        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        advancedChoice.setText(getString(R.string.advanced_effects_state,
                getString(advancedEffects ? R.string.on : R.string.off)));
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);
        String wallpaperCurrent = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        wallpaperChoice.setText(getString(R.string.wallpaper_state,
                "none".equals(wallpaperCurrent) ? getString(R.string.wallpaper_none)
                        : wallpaperCurrent != null ? wallpaperCurrent
                        : getString(R.string.wallpaper_theme_default)));
        followThemeChoice.setSelected(!hasOnTvChoices());
        int style = Themes.bannerStyle(this);
        bannerChoice.setText(getString(R.string.banner_state, getString(style == Themes.BANNER_STRETCH
                ? R.string.banner_stretch : style == Themes.BANNER_GRADIENT ? R.string.banner_gradient : R.string.banner_off)));
        for (Runnable r : toggleRefreshers) r.run();
        String vpns = choices.getString(Config.VPN_APP_KEY, null);
        if (vpns == null) vpns = Config.load(this).vpnApp;
        List<String> names = new ArrayList<>();
        for (String pkg : vpns.split(",")) if (!pkg.trim().isEmpty()) names.add(AppActions.label(this, pkg.trim()));
        vpnChoice.setText(getString(R.string.vpn_state, names.isEmpty() ? getString(R.string.vpn_none)
                : android.text.TextUtils.join(", ", names)));
        sortChoice.setText(getString(R.string.sort_state,
                getString(Themes.sortRecent(this) ? R.string.sort_recent : R.string.sort_manual)));
        File dir = Config.dir(this);
        boolean pushed = dir != null && new File(dir, Config.FILE_NAME).isFile();
        String name = getString(Themes.label(Themes.chosen(choices, FireOs.is(this))));
        themeChoice.setText(pushed ? getString(R.string.theme_state_file)
                : getString(R.string.theme_state, name));
    }

    private boolean hasOnTvChoices() {
        return choices.contains(Config.VPN_APP_KEY) || choices.contains("columns") || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains("animations") || choices.contains("advanced_effects")
                || choices.contains(Config.WALLPAPER_CHOICE_KEY) || choices.contains(Config.DOCK_OPACITY_KEY);
    }

    /** An on/off row saved as a boolean choice that Config reads under the same key. */
    private void addToggle(LinearLayout parent, String key, int label, int desc, boolean theme) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lineLp.topMargin = (int) (10 * d);
        TextView row = new TextView(this);
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackgroundResource(R.drawable.settings_choice_bg);
        row.setTextColor(0xFFFFFFFF);
        row.setTextSize(18);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding((int) (18 * d), 0, (int) (18 * d), 0);
        row.setMinHeight((int) (52 * d));
        line.addView(row, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 5));
        TextView hint = new TextView(this);
        hint.setText(desc);
        hint.setTextColor(0xFFACB8C1);
        hint.setTextSize(14);
        hint.setPadding((int) (24 * d), 0, 0, 0);
        line.addView(hint, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 4));
        Runnable show = () -> row.setText(getString(R.string.opt_state, getString(label),
                getString(choices.getBoolean(key, theme) ? R.string.on : R.string.off)));
        row.setOnClickListener(v -> {
            choices.edit().putBoolean(key, !choices.getBoolean(key, theme)).apply();
            changed();
            show.run();
        });
        toggleRefreshers.add(show);
        parent.addView(line, lineLp);
        show.run();
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
        Set<String> current = new HashSet<>(Arrays.asList((saved != null ? saved : Config.load(this).vpnApp).split("\\s*,\\s*")));
        CharSequence[] labels = new CharSequence[apps.size()];
        boolean[] on = new boolean[apps.size()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = apps.get(i)[1];
            on[i] = current.contains(apps.get(i)[0]);
        }
        new AlertDialog.Builder(this).setTitle(R.string.vpn_apps)
                .setMultiChoiceItems(labels, on, (d, which, checked) -> on[which] = checked)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    List<String> picked = new ArrayList<>();
                    for (int i = 0; i < on.length; i++) if (on[i]) picked.add(apps.get(i)[0]);
                    choices.edit().putString(Config.VPN_APP_KEY, android.text.TextUtils.join(",", picked)).apply();
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null).show();
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
        new AlertDialog.Builder(this).setTitle(R.string.hidden_apps)
                .setMultiChoiceItems(labels, on, (d, which, checked) -> on[which] = checked)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    boolean any = false;
                    for (int i = 0; i < on.length; i++) {
                        if (on[i] == was[i]) continue;
                        boolean saved = LibraryApps.save(this, apps.get(i)[0], !on[i]);
                        if (!saved) Toast.makeText(this, R.string.library_save_failed, Toast.LENGTH_LONG).show();
                        any |= saved;
                    }
                    if (any) changed();
                })
                .setNegativeButton(android.R.string.cancel, null).show();
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
