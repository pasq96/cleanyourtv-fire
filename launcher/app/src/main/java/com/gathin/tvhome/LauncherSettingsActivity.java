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
    private GlassScrollView scroll;
    private FocusGlass glass;
    private TextView vpnChoice;
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
        scroll = findViewById(R.id.settingsScroll);
        glass = new FocusGlass(this);
        ((android.widget.FrameLayout) findViewById(R.id.settingsRoot)).addView(glass,
                new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scroll.setOnScrollChangeListener((v, x, y, ox, oy) -> glass.snap());
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener(
                (oldFocus, now) -> glass.follow(now != null && scroll.findFocus() == now ? now : null, glideMillis()));
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
        addSegmented(findViewById(R.id.segWifiName), R.string.lbl_wifi_name, 0, new int[]{R.string.wifi_hidden, R.string.wifi_shown},
                () -> choices.getBoolean("wifi_show_name", true) ? 1 : 0, i -> {
                    choices.edit().putBoolean("wifi_show_name", i == 1).apply();
                    if (i == 1) checkWifiName();
                });
        addSegmented(findViewById(R.id.segDaily), R.string.lbl_daily, R.string.d_daily,
                new int[]{R.string.daily_off, R.string.daily_picsum, R.string.daily_bing},
                () -> DailyWallpaper.source(choices), i -> {
                    SharedPreferences.Editor e = choices.edit().putInt(DailyWallpaper.SOURCE_KEY, i).remove(DailyWallpaper.STAMP_KEY);
                    if (i == DailyWallpaper.OFF) {
                        if (DailyWallpaper.CHOICE.equals(choices.getString(Config.WALLPAPER_CHOICE_KEY, null))) e.remove(Config.WALLPAPER_CHOICE_KEY);
                        e.apply();
                        return;
                    }
                    e.apply();
                    DailyWallpaper.refresh(this, true, (ok, error) -> {
                        if (isDestroyed()) return;
                        if (error != null) Toast.makeText(this, getString(R.string.daily_failed, error), Toast.LENGTH_LONG).show();
                        else { Toast.makeText(this, R.string.daily_ok, Toast.LENGTH_SHORT).show(); setResult(RESULT_OK); refresh(); }
                    });
                });
        LinearLayout toggles = findViewById(R.id.togglesContainer);
        addToggle(toggles, "show_all_apps", R.string.opt_show_all_apps, R.string.d_show_all, config.showAllApps);
        addToggle(toggles, "long_press_move", R.string.opt_long_press_move, R.string.d_long_press, config.longPressMove);
        addToggle(toggles, "always_show_names", R.string.opt_names, R.string.d_names, config.alwaysShowNames);
        LinearLayout buttons = findViewById(R.id.buttonsContainer);
        addToggle(buttons, "show_inputs_pill", R.string.opt_inputs, R.string.d_inputs, config.showInputsPill);
        addToggle(buttons, "show_network_pill", R.string.opt_wifi, 0, config.showNetworkPill);
        addToggle(buttons, Pills.SHOW_POWER_KEY, R.string.opt_power, R.string.d_power, true);
        addPillStyles((LinearLayout) findViewById(R.id.pillsContainer), config);
        addSegmented(findViewById(R.id.segLift), R.string.dock_position, R.string.d_dock_height,
                new int[]{R.string.dock_pos_0, R.string.dock_pos_1, R.string.dock_pos_2, R.string.dock_pos_3},
                () -> Math.min(3, choices.getInt("dock_lift", 1)), i -> choices.edit().putInt("dock_lift", i).apply());
        addPicker(findViewById(R.id.segTheme), R.string.theme_title, R.string.d_theme, themeNames(false), themeNames(true),
                () -> Arrays.asList(Themes.IDS).indexOf(Themes.chosen(choices, FireOs.is(this))), i -> {
                    // The new theme's layout applies: drop on-TV choices made over the old one.
                    choices.edit().putString(Themes.KEY, Themes.IDS[i]).remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY)
                            .remove("animations").remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY)
                            .remove(Config.DOCK_OPACITY_KEY).remove("show_all_apps").remove("long_press_move")
                            .remove("always_show_names").remove("show_inputs_pill").remove("show_network_pill").remove("dock_lift").remove(Config.GLASS_STYLE_KEY)
                            .remove(DailyWallpaper.SOURCE_KEY).commit();
                    changed();
                    recreate();   // rows and toggles take the new theme's defaults
                }, () -> themePushed() ? R.string.theme_state_file : 0);
        addSegmented(findViewById(R.id.segWallOpacity), R.string.lbl_wall_opacity, 0, new CharSequence[]{"0%", "25%", "50%", "75%", "100%"},
                () -> Math.max(0, Math.min(4, Math.round(choices.getInt(Config.WALLPAPER_OPACITY_KEY, 100) / 25f))),
                i -> choices.edit().putInt(Config.WALLPAPER_OPACITY_KEY, i * 25).apply());
        addPicker(findViewById(R.id.segBanner), R.string.lbl_banner, R.string.d_banner,
                new int[]{R.string.banner_off, R.string.banner_stretch, R.string.banner_gradient, R.string.banner_blur, R.string.banner_glass,
                        R.string.banner_solid, R.string.banner_frame},
                () -> Themes.bannerStyle(this), i -> choices.edit().putInt(Themes.BANNER_STYLE_KEY, i).apply());
        addSegmented(findViewById(R.id.segAnimations), R.string.lbl_animations, R.string.d_animations, new int[]{R.string.on, R.string.off},
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
        addPicker(findViewById(R.id.segGlass), R.string.lbl_glass, R.string.d_glass,
                new int[]{R.string.glass_glass, R.string.glass_soft, R.string.glass_rim, R.string.glass_halo, R.string.glass_neon},
                () -> Arrays.asList(Config.GLASS_STYLES).indexOf(choices.contains(Config.GLASS_STYLE_KEY)
                        ? Config.glassStyle(choices.getString(Config.GLASS_STYLE_KEY, null)) : config.glassStyle),
                i -> choices.edit().putString(Config.GLASS_STYLE_KEY, Config.GLASS_STYLES[i]).apply());
        addSegmented(findViewById(R.id.segGlide), R.string.lbl_glide, 0, new int[]{R.string.glide_off, R.string.glide_short, R.string.glide_normal},
                () -> Math.max(0, Math.min(2, choices.getInt("glide_speed", 1))), i -> choices.edit().putInt("glide_speed", i).apply());
        addSegmented(findViewById(R.id.segResume), R.string.lbl_resume, R.string.d_resume, new int[]{R.string.on, R.string.off},
                () -> choices.getBoolean(ResumePick.KEY_ROW, true) ? 0 : 1, i -> choices.edit().putBoolean(ResumePick.KEY_ROW, i == 0).apply());
        addSegmented(findViewById(R.id.segResumeAge), R.string.lbl_resume_age, 0,
                new int[]{R.string.resume_age_7, R.string.resume_age_30, R.string.resume_age_90, R.string.resume_age_all},
                () -> Math.max(0, Math.min(3, choices.getInt(ResumePick.KEY_AGE, ResumePick.DEFAULT_AGE))),
                i -> choices.edit().putInt(ResumePick.KEY_AGE, i).apply());
        addSegmented(findViewById(R.id.segResumePerApp), R.string.lbl_resume_per_app, 0,
                new CharSequence[]{"1", "3", "5", getString(R.string.resume_all)},
                () -> Math.max(0, Math.min(3, choices.getInt(ResumePick.KEY_PER_APP, ResumePick.DEFAULT_PER_APP))),
                i -> choices.edit().putInt(ResumePick.KEY_PER_APP, i).apply());
        addSegmented(findViewById(R.id.segResumeSuggested), R.string.lbl_resume_suggested, R.string.d_resume_suggested,
                new int[]{R.string.on, R.string.off},
                () -> choices.getBoolean(ResumePick.KEY_SUGGESTED, false) ? 0 : 1, i -> choices.edit().putBoolean(ResumePick.KEY_SUGGESTED, i == 0).apply());
        addSegmented(findViewById(R.id.segResumeApps), R.string.lbl_resume_apps, R.string.d_resume_apps, new int[]{R.string.on, R.string.off},
                () -> choices.getBoolean(ResumePick.KEY_APPS, true) ? 0 : 1, i -> choices.edit().putBoolean(ResumePick.KEY_APPS, i == 0).apply());
        TextView unhide = findViewById(R.id.resumeUnhide);
        Runnable unhideLabel = () -> unhide.setText(getString(R.string.resume_unhide, ResumeSource.hidden(choices).size()));
        unhideLabel.run();
        unhide.setOnClickListener(v -> {
            boolean any = !ResumeSource.hidden(choices).isEmpty();
            choices.edit().remove(ResumePick.KEY_HIDDEN).apply();
            unhideLabel.run();
            if (any) Toast.makeText(this, R.string.resume_unhidden, Toast.LENGTH_SHORT).show();
        });
        addSegmented(findViewById(R.id.segSort), R.string.lbl_sort, R.string.d_sort, new int[]{R.string.sort_manual, R.string.sort_recent},
                () -> Themes.sortRecent(this) ? 1 : 0, i -> choices.edit().putBoolean(Themes.SORT_RECENT_KEY, i == 1).apply());
        addSegmented(findViewById(R.id.segUnits), R.string.lbl_units, 0, new int[]{R.string.weather_celsius, R.string.weather_fahrenheit},
                () -> Weather.fahrenheit(choices) ? 1 : 0, i -> {
                    choices.edit().putString(Weather.KEY_UNITS, i == 1 ? "f" : "c").apply();
                    Weather.forget(this);
                });
        addSegmented(findViewById(R.id.segLanguage), R.string.lbl_lang, R.string.d_lang,
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
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove(Config.VPN_APP_KEY).remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                    .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY)
                    .remove(Config.DOCK_OPACITY_KEY).remove("show_all_apps").remove("long_press_move")
                    .remove("always_show_names").remove("show_inputs_pill").remove("show_network_pill").remove("dock_lift").remove(Config.GLASS_STYLE_KEY)
                    .remove(DailyWallpaper.SOURCE_KEY).commit();
            changed();
            recreate();
        });
        if (FireOs.is(this)) ((TextView) findViewById(R.id.homeNote)).setText(R.string.home_fire_note);
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v -> {
            if (FireOs.is(this)) {
                Toast.makeText(this, R.string.home_fire_note, Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
            open(new Intent(Settings.ACTION_HOME_SETTINGS));
        });
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
        refresh();
        ArrayList<View> order = new ArrayList<>();
        findViewById(android.R.id.content).addFocusables(order, View.FOCUS_DOWN);
        for (View v : order) {
            if (v.getVisibility() == View.VISIBLE) { v.requestFocus(); break; }
        }
        scroll.post(() -> glass.follow(getCurrentFocus(), 0));
    }

    /**
     * D-pad navigation by rows: Up and Down go to the next or previous row (a capsule of options, or a single
     * button) and land on the option nearest to where the focus was; Left and Right stay inside the row. The stock
     * focus search lost rows whose options sit beside a wide neighbour, so some rows could not be reached.
     */
    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        int code = event.getKeyCode();
        boolean vertical = code == android.view.KeyEvent.KEYCODE_DPAD_UP || code == android.view.KeyEvent.KEYCODE_DPAD_DOWN;
        boolean horizontal = code == android.view.KeyEvent.KEYCODE_DPAD_LEFT || code == android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
        View focused = getCurrentFocus();
        if (!(vertical || horizontal) || focused == null) return super.dispatchKeyEvent(event);
        if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) return true;
        ArrayList<View> all = new ArrayList<>();
        findViewById(android.R.id.content).addFocusables(all, View.FOCUS_DOWN);
        if (horizontal) {
            boolean right = code == android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
            float from = centerX(focused);
            View next = null;
            float gap = Float.MAX_VALUE;
            for (View v : all) {
                if (v == focused || v.getParent() != focused.getParent() || v.getVisibility() != View.VISIBLE) continue;
                float delta = right ? centerX(v) - from : from - centerX(v);
                if (delta > 1 && delta < gap) { gap = delta; next = v; }
            }
            if (next != null) next.requestFocus();
            return true;
        }
        List<android.view.ViewParent> groups = new ArrayList<>();
        for (View v : all) if (v.getVisibility() == View.VISIBLE && !groups.contains(v.getParent())) groups.add(v.getParent());
        int at = groups.indexOf(focused.getParent());
        int to = at + (code == android.view.KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1);
        if (at < 0 || to < 0 || to >= groups.size()) return true;
        int[] loc = new int[2];
        focused.getLocationOnScreen(loc);
        float cx = loc[0] + focused.getWidth() / 2f;
        View best = null;
        float bestDistance = Float.MAX_VALUE;
        for (View v : all) {
            if (v.getParent() != groups.get(to) || v.getVisibility() != View.VISIBLE) continue;
            v.getLocationOnScreen(loc);
            float distance = Math.abs(loc[0] + v.getWidth() / 2f - cx);
            if (distance < bestDistance) { bestDistance = distance; best = v; }
        }
        if (best != null) {
            best.requestFocus();
            reveal(best, to == 0);
        }
        return true;
    }

    private static float centerX(View v) {
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        return loc[0] + v.getWidth() / 2f;
    }

    /** Scrolls smoothly so the focused row shows with the heading and note above it; the first row goes back to the very top. */
    private void reveal(View v, boolean toTop) {
        if (toTop) {
            scroll.smoothScrollTo(0, 0);
            return;
        }
        int[] a = new int[2], b = new int[2];
        v.getLocationOnScreen(a);
        scroll.getLocationOnScreen(b);
        int margin = (int) (110 * getResources().getDisplayMetrics().density);
        int top = a[1] - b[1], bottom = top + v.getHeight(), height = scroll.getHeight();
        if (top < margin) scroll.smoothScrollBy(0, top - margin);
        else if (bottom > height - margin) scroll.smoothScrollBy(0, bottom - (height - margin));
    }

    private static int widest(CharSequence[] texts) {
        int n = 0;
        for (CharSequence t : texts) n = Math.max(n, t.length());
        return n;
    }

    /** Length of the focus glide in these settings: none, short or normal (also none while animations are off). */
    private int glideMillis() {
        return !animations ? 0 : new int[]{0, 200, 340}[Math.max(0, Math.min(2, choices.getInt("glide_speed", 1)))];
    }

    private boolean themePushed() {
        File dir = Config.dir(this);
        return dir != null && new File(dir, Config.FILE_NAME).isFile();
    }

    /** Theme names: the full line, or just the name before its parentheses for the compact value. */
    private CharSequence[] themeNames(boolean shortForm) {
        CharSequence[] out = new CharSequence[Themes.IDS.length];
        for (int i = 0; i < out.length; i++) {
            String n = getString(Themes.label(Themes.IDS[i]));
            out[i] = shortForm ? n.replaceFirst("\\s*\\(.*$", "") : n;
        }
        return out;
    }

    private boolean locationGranted() {
        return checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /** "Mostrato" was chosen: say briefly what stops the name from appearing, or fix what can be fixed. */
    private void checkWifiName() {
        if (!locationGranted()) {
            try {
                requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 78);
            } catch (RuntimeException e) {
                Toast.makeText(this, R.string.wifi_problem_perm, Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (!WifiName.enableLocation(this)) Toast.makeText(this, R.string.wifi_problem_loc, Toast.LENGTH_LONG).show();
        else if (WifiName.read(this) == null) Toast.makeText(this, R.string.wifi_problem_hidden, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != 78) return;
        if (!locationGranted()) Toast.makeText(this, R.string.wifi_problem_perm, Toast.LENGTH_LONG).show();
        else checkWifiName();
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
        images.removeIf(f -> f.getName().startsWith("daily-"));
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
                    choices.edit().putInt(DailyWallpaper.SOURCE_KEY, DailyWallpaper.OFF).apply();   // a manual pick ends the daily one
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
                    choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).putInt(DailyWallpaper.SOURCE_KEY, DailyWallpaper.OFF).apply();
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
        if (DailyWallpaper.CHOICE.equals(wallpaperCurrent)) wallpaperCurrent = getString(R.string.lbl_daily);
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
        for (Runnable r : toggleRefreshers) r.run();
        String vpns = choices.getString(Config.VPN_APP_KEY, null);
        if (vpns == null) vpns = themeVpnApp;
        List<String> names = new ArrayList<>();
        for (String pkg : vpns.split(",")) if (!pkg.trim().isEmpty()) names.add(AppActions.label(this, pkg.trim()));
        vpnChoice.setText(getString(R.string.vpn_state, names.isEmpty() ? getString(R.string.vpn_none)
                : android.text.TextUtils.join(", ", names)));
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
        return addSegmented(parent, label, note, texts(options), current, set);
    }

    private CharSequence[] texts(int[] ids) {
        CharSequence[] out = new CharSequence[ids.length];
        for (int i = 0; i < ids.length; i++) out[i] = getString(ids[i]);
        return out;
    }

    private View addSegmented(LinearLayout parent, int label, int note, CharSequence[] options,
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
            String option = options[i].toString();
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

    /** One setting with many options: the label and the current choice, which opens a glass list. */
    private void addPicker(LinearLayout parent, int label, int note, int[] options,
                           java.util.function.IntSupplier current, java.util.function.IntConsumer set) {
        CharSequence[] names = texts(options);
        addPicker(parent, label, note, names, names, current, set, null);
    }

    /** full: the list rows; shown: the short value on the capsule; lock returns a message id while the choice is fixed elsewhere, else 0. */
    private void addPicker(LinearLayout parent, int label, int note, CharSequence[] options, CharSequence[] shown,
                           java.util.function.IntSupplier current, java.util.function.IntConsumer set, java.util.function.IntSupplier lock) {
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
        TextView value = new TextView(this, null, 0, R.style.SettingsSeg);
        value.setSelected(true);
        value.setOnClickListener(v -> {
            int locked = lock == null ? 0 : lock.getAsInt();
            if (locked != 0) {
                Toast.makeText(this, locked, Toast.LENGTH_LONG).show();
                return;
            }
            CharSequence[] labels = new CharSequence[options.length];
            boolean[] on = new boolean[options.length];
            for (int i = 0; i < options.length; i++) {
                String o = options[i].toString();
                labels[i] = o.substring(0, 1).toUpperCase() + o.substring(1);
                on[i] = i == current.getAsInt();
            }
            new GlassDialog(this, getString(label)).width(widest(options) > 14 ? 620 : 480)
                    .list(labels, on, false, which -> {
                        if (which == current.getAsInt()) return;
                        set.accept(which);
                        changed();
                        refresh();
                    })
                    .button(android.R.string.cancel, null)
                    .show();
        });
        capsule.addView(value, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (int) (44 * d)));
        block.addView(capsule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        toggleRefreshers.add(() -> {
            String o = (lock != null && lock.getAsInt() != 0) ? "config.json"
                    : shown[Math.max(0, Math.min(shown.length - 1, current.getAsInt()))].toString();
            value.setText(o.substring(0, 1).toUpperCase() + o.substring(1) + "  \u25BE");
        });
        parent.addView(block, blockLp);
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
