package com.gathin.tvhome;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/** Other apps' Watch Next rows (TvProvider) as Continue watching items; needs READ_TV_LISTINGS. */
final class WatchNext {
    static final String PERMISSION = "android.permission.READ_TV_LISTINGS";
    /** watch_next_type 3 is the watchlist, not a resume point (0 continue, 1 next episode, 2 new). */
    private static final int WATCHLIST = 3;

    private WatchNext() { }

    static boolean missingPermission(Context ctx) {
        return ctx.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED;
    }

    /** Runs onChange on the main thread when any app edits Watch Next; run the result to stop. */
    static Runnable observe(Context ctx, Runnable onChange) {
        ContentResolver resolver = ctx.getApplicationContext().getContentResolver();
        ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override public void onChange(boolean selfChange) { onChange.run(); }
        };
        resolver.registerContentObserver(TvContract.WatchNextPrograms.CONTENT_URI, true, observer);
        return () -> resolver.unregisterContentObserver(observer);
    }

    /** Blocking, off the main thread. Empty without the permission or a TV provider. */
    static List<ResumePick.Item> read(Context ctx) {
        List<ResumePick.Item> items = new ArrayList<>();
        if (missingPermission(ctx)) return items;
        String[] columns = {
                TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME, TvContract.WatchNextPrograms.COLUMN_TITLE,
                TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE, TvContract.WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER,
                TvContract.WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER, TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI,
                TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_URI, TvContract.WatchNextPrograms.COLUMN_INTENT_URI,
                TvContract.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE, TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS,
                TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS, TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS};
        // No selection: without ACCESS_ALL_EPG_DATA a selection throws SecurityException, so filter in code.
        try (Cursor c = ctx.getContentResolver().query(TvContract.WatchNextPrograms.CONTENT_URI, columns, null, null, null)) {
            while (c != null && c.moveToNext()) {
                if (c.getInt(8) == WATCHLIST || c.getString(0) == null || blank(c.getString(1))) continue;
                if (ctx.getPackageManager().getLaunchIntentForPackage(c.getString(0)) == null) continue;
                String image = !blank(c.getString(6)) ? c.getString(6) : c.getString(5);
                items.add(new ResumePick.Item(c.getString(0), c.getString(1).trim(), ResumePick.subtitle(c.getString(2), c.getString(3), c.getString(4)),
                        blank(image) ? null : image, blank(c.getString(7)) ? null : c.getString(7),
                        c.getLong(9), ResumePick.progress(c.getLong(10), c.getLong(11)), c.getInt(8)));
            }
        } catch (RuntimeException e) {
            // Provider missing (Fire OS) or refused: the row stays empty.
        }
        return items;
    }

    /** The row's intent: URI, scoped to the publishing app; null when malformed or aimed at another package. */
    static Intent intent(String uri, String pkg) {
        if (blank(uri)) return null;
        try {
            Intent intent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME);
            if (intent.getPackage() == null) intent.setPackage(pkg);
            if (!pkg.equals(intent.getPackage())) return null;
            intent.setSelector(null);
            return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private static boolean blank(String s) {
        return ResumePick.blank(s);
    }
}
