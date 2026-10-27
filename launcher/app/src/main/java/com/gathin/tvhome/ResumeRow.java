package com.gathin.tvhome;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntPredicate;

/** The Continue watching shelf under the dock: one card per Watch Next item, picture, title, episode, progress. */
final class ResumeRow extends LinearLayout {
    /** Height the shelf takes, in dp: heading, picture, two text lines and the focus margin. */
    static final int ROOM_DP = 176;
    private static final int CARD_W_DP = 176, CARD_H_DP = 99;
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(4 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap b) { return b.getByteCount(); }
    };
    private static final ExecutorService LOADER = Executors.newFixedThreadPool(2);

    private final LinearLayout cards;
    private final float density;
    private final TextView heading;
    /** Up/Down from a card; Left/Right at the ends are swallowed here. Returns true when handled. */
    private IntPredicate keys = code -> false;
    private OnPick onPick = item -> { };
    private OnPick onHide = item -> { };

    interface OnPick { void pick(ResumePick.Item item); }

    ResumeRow(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);
        heading = new TextView(context);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        heading.setLetterSpacing(0.12f);
        heading.setAllCaps(true);
        heading.setTextColor(0xB3FFFFFF);
        heading.setPadding(dp(24), 0, dp(24), dp(6));
        heading.setText(R.string.resume_title);
        addView(heading);
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(OVER_SCROLL_NEVER);
        cards = new LinearLayout(context);
        cards.setOrientation(HORIZONTAL);
        cards.setClipChildren(false);
        cards.setPadding(dp(18), dp(6), dp(18), dp(10));
        scroll.addView(cards, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    void setHandlers(IntPredicate keys, OnPick onPick, OnPick onHide) {
        this.keys = keys;
        this.onPick = onPick;
        this.onHide = onHide;
    }

    boolean hasItems() { return cards.getChildCount() > 0; }

    boolean focusFirst() { return hasItems() && cards.getChildAt(0).requestFocus(); }

    boolean holdsFocus(View v) { return v != null && v.getParent() == cards; }

    void setItems(List<ResumePick.Item> items) {
        cards.removeAllViews();
        for (ResumePick.Item item : items) cards.addView(card(item));
        setVisibility(items.isEmpty() ? GONE : VISIBLE);
    }

    private View card(ResumePick.Item item) {
        Context c = getContext();
        LinearLayout column = new LinearLayout(c);
        column.setOrientation(VERTICAL);
        column.setFocusable(true);
        column.setClickable(true);
        column.setClipChildren(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(CARD_W_DP), LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(12));
        column.setLayoutParams(lp);

        FrameLayout picture = new FrameLayout(c);
        picture.setBackgroundColor(0x33FFFFFF);
        picture.setClipToOutline(true);
        picture.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(8)); }
        });
        ImageView image = new ImageView(c);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        picture.addView(image, new FrameLayout.LayoutParams(-1, -1));
        if (item.progress >= 0f) {
            FrameLayout track = new FrameLayout(c);
            track.setBackgroundColor(0x66000000);
            View bar = new View(c);
            bar.setBackgroundColor(0xFFFFFFFF);
            track.addView(bar, new FrameLayout.LayoutParams(Math.max(dp(2), Math.round(dp(CARD_W_DP) * item.progress)), -1));
            picture.addView(track, new FrameLayout.LayoutParams(-1, dp(3), Gravity.BOTTOM));
        }
        // The focus ring sits over the picture, so the card's text does not move.
        picture.setForeground(ring());
        column.addView(picture, new LinearLayout.LayoutParams(dp(CARD_W_DP), dp(CARD_H_DP)));

        column.addView(text(item.title, 12, 0xFFFFFFFF), new LinearLayout.LayoutParams(-1, -2));
        if (item.subtitle != null) column.addView(text(item.subtitle, 11, 0xB3FFFFFF), new LinearLayout.LayoutParams(-1, -2));

        column.setOnFocusChangeListener((v, focused) -> {
            picture.setSelected(focused);
            v.animate().scaleX(focused ? 1.05f : 1f).scaleY(focused ? 1.05f : 1f).setDuration(140).start();
        });
        column.setOnClickListener(v -> onPick.pick(item));
        column.setOnLongClickListener(v -> { onHide.pick(item); return true; });
        column.setOnKeyListener((v, code, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            int i = cards.indexOfChild(v);
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) return i == 0;
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) return i == cards.getChildCount() - 1;
            return (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) && keys.test(code);
        });
        if (item.type == ResumePick.APP) appPicture(item.pkg, image);
        else load(item.image, image);
        return column;
    }

    /** A recently opened app: its banner, or its icon centred on the card. */
    private void appPicture(String pkg, ImageView into) {
        android.content.pm.PackageManager pm = getContext().getPackageManager();
        try {
            android.graphics.drawable.Drawable d = pm.getApplicationBanner(pkg);
            if (d == null) {
                d = pm.getApplicationIcon(pkg);
                into.setScaleType(ImageView.ScaleType.FIT_CENTER);
                into.setPadding(dp(28), dp(14), dp(28), dp(14));
            }
            into.setImageDrawable(d);
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
            // Uninstalled since the list was built: the plain card stays.
        }
    }

    private StateListDrawable ring() {
        GradientDrawable on = new GradientDrawable();
        on.setColor(Color.TRANSPARENT);
        on.setStroke(dp(3), 0xFFFFFFFF);
        on.setCornerRadius(dp(8));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_selected}, on);
        states.addState(new int[]{}, new GradientDrawable());
        return states;
    }

    private TextView text(String value, int sp, int color) {
        TextView t = new TextView(getContext());
        t.setText(value);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(dp(2), dp(4), dp(2), 0);
        return t;
    }

    private int dp(int v) { return Math.round(v * density); }

    private void load(String uri, ImageView into) {
        if (uri == null) return;
        Bitmap cached = CACHE.get(uri);
        if (cached != null) { into.setImageBitmap(cached); return; }
        final int width = dp(CARD_W_DP) * 2;
        final ContentResolver resolver = getContext().getContentResolver();
        LOADER.execute(() -> {
            Bitmap b = decode(resolver, uri, width);
            if (b == null) return;
            CACHE.put(uri, b);
            into.post(() -> { if (into.isAttachedToWindow()) into.setImageBitmap(b); });
        });
    }

    /** Remote or content:// picture, at most about twice the card's width. Null on any failure. */
    private static Bitmap decode(ContentResolver resolver, String uri, int width) {
        try (InputStream in = open(resolver, uri)) {
            if (in == null) return null;
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n, total = 0;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > 3 * 1024 * 1024) return null;
                bytes.write(buf, 0, n);
            }
            byte[] data = bytes.toByteArray();
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            if (o.outWidth <= 0) return null;
            o.inJustDecodeBounds = false;
            o.inSampleSize = Math.max(1, Integer.highestOneBit(Math.max(1, o.outWidth / width)));
            return BitmapFactory.decodeByteArray(data, 0, data.length, o);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    private static InputStream open(ContentResolver resolver, String uri) throws Exception {
        if (uri.startsWith("http://") || uri.startsWith("https://")) {
            HttpURLConnection c = (HttpURLConnection) new URL(uri).openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(8000);
            return c.getInputStream();
        }
        return uri.startsWith("content:") || uri.startsWith("android.resource:") ? resolver.openInputStream(Uri.parse(uri)) : null;
    }
}
