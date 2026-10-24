package com.gathin.tvhome;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Remote-friendly glass dialogs: a centred panel over a dimmed screen, glass rows and pill buttons. */
final class GlassDialog {
    private final Context ctx;
    private final Dialog dialog;
    private final LinearLayout panel;
    private final LinearLayout buttons;
    private final float d;
    private int widthDp = 560;
    private boolean tall;
    private View focusFirst;
    private EditText input;

    GlassDialog(Context ctx, CharSequence title) {
        this.ctx = ctx;
        d = ctx.getResources().getDisplayMetrics().density;
        dialog = new Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar);
        panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundResource(R.drawable.dialog_glass);
        int pad = (int) (30 * d);
        panel.setPadding(pad, (int) (26 * d), pad, (int) (24 * d));
        TextView head = new TextView(ctx);
        head.setText(title);
        head.setTextColor(0xFFFFFFFF);
        head.setTextSize(24);
        head.setPadding((int) (6 * d), 0, 0, (int) (10 * d));
        panel.addView(head);
        buttons = new LinearLayout(ctx);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
    }

    GlassDialog width(int dp) {
        widthDp = dp;
        return this;
    }

    GlassDialog message(CharSequence text) {
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setTextColor(0xFFC4D0DB);
        t.setTextSize(17);
        t.setPadding((int) (6 * d), (int) (4 * d), (int) (6 * d), (int) (8 * d));
        panel.addView(t);
        return this;
    }

    /** One-line text entry; read it with text() from a button action. */
    GlassDialog input(String initial) {
        input = new EditText(ctx);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setText(initial);
        input.setSelection(initial.length());
        input.setTextColor(0xFFFFFFFF);
        input.setTextSize(20);
        input.setBackgroundResource(R.drawable.settings_segments_bg);
        int p = (int) (16 * d);
        input.setPadding(p, p, p, p);
        panel.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        focusFirst = input;
        // The remote's Down leaves the field for the buttons (the keyboard would otherwise keep every arrow key).
        input.setOnKeyListener((v, code, e) -> {
            if (code != android.view.KeyEvent.KEYCODE_DPAD_DOWN || buttons.getChildCount() == 0) return false;
            if (e.getAction() == android.view.KeyEvent.ACTION_DOWN) focusButtons();
            return true;
        });
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        input.setOnEditorActionListener((v, action, e) -> {
            focusButtons();
            return true;
        });
        return this;
    }

    /** Hides the keyboard and focuses the last button (OK) of a dialog with a text field. */
    private void focusButtons() {
        InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && input != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        View ok = buttons.getChildAt(buttons.getChildCount() - 1);
        if (ok != null) ok.requestFocus();
    }

    String text() {
        return input == null ? "" : input.getText().toString().trim();
    }

    /** Rows with a tick when checked. multi: each tap toggles; otherwise a tap picks the row and closes. */
    GlassDialog list(CharSequence[] labels, boolean[] checked, boolean multi, IntConsumer onPick) {
        tall = labels.length > 6;
        LinearLayout rows = new LinearLayout(ctx);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(0, 0, 0, (int) (4 * d));
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setFocusable(true);
            row.setClickable(true);
            row.setBackgroundResource(R.drawable.settings_choice_bg);
            row.setPadding((int) (20 * d), 0, (int) (20 * d), 0);
            row.setMinimumHeight((int) (50 * d));
            row.setSelected(checked[i]);
            TextView name = tint(new TextView(ctx));
            name.setText(labels[i]);
            name.setTextSize(18);
            row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView tick = tint(new TextView(ctx));
            tick.setText("✓");
            tick.setTextSize(20);
            tick.setVisibility(checked[i] ? View.VISIBLE : View.INVISIBLE);
            row.addView(tick);
            row.setOnClickListener(v -> {
                if (multi) {
                    checked[index] = !checked[index];
                    row.setSelected(checked[index]);
                    tick.setVisibility(checked[index] ? View.VISIBLE : View.INVISIBLE);
                } else {
                    dialog.dismiss();
                }
                if (onPick != null) onPick.accept(index);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = (int) (8 * d);
            rows.addView(row, lp);
            if (focusFirst == null || checked[i] && !multi) focusFirst = row;
        }
        ScrollView scroll = new ScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(rows);
        panel.addView(scroll, tall ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1)
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return this;
    }

    /** A pill button; the dialog closes after the action unless keepOpen. The first button added is focused when nothing else is. */
    GlassDialog button(int label, Runnable action) {
        TextView b = tint(new TextView(ctx));
        b.setText(label);
        b.setTextSize(18);
        b.setGravity(Gravity.CENTER);
        b.setFocusable(true);
        b.setClickable(true);
        b.setBackgroundResource(R.drawable.settings_choice_bg);
        b.setMinHeight((int) (50 * d));
        b.setPadding((int) (18 * d), 0, (int) (18 * d), 0);
        b.setOnClickListener(v -> {
            dialog.dismiss();
            if (action != null) action.run();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        if (buttons.getChildCount() > 0) lp.setMarginStart((int) (12 * d));
        buttons.addView(b, lp);
        b.setOnKeyListener((v, code, e) -> {
            if (input == null || code != android.view.KeyEvent.KEYCODE_DPAD_UP) return false;
            if (e.getAction() == android.view.KeyEvent.ACTION_DOWN) input.requestFocus();
            return true;
        });
        if (focusFirst == null) focusFirst = b;
        return this;
    }

    private TextView tint(TextView t) {
        t.setTextColor(ctx.getResources().getColorStateList(R.drawable.pill_text, ctx.getTheme()));
        t.setDuplicateParentStateEnabled(true);
        return t;
    }

    void show() {
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.topMargin = (int) (16 * d);
        panel.addView(buttons, bl);
        FrameLayout root = new FrameLayout(ctx);
        root.setBackgroundColor(0xAA050A14);
        root.setOnClickListener(v -> dialog.dismiss());
        int screenH = ctx.getResources().getDisplayMetrics().heightPixels;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) (widthDp * d),
                tall ? (int) (screenH * 0.86f) : ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        panel.setClickable(true);
        root.addView(panel, lp);
        dialog.setContentView(root);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        }
        dialog.show();
        if (focusFirst != null) focusFirst.requestFocus();
        if (input != null) {
            InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            input.post(() -> {
                if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            });
        }
    }

    static void confirm(Context c, CharSequence title, CharSequence message, int ok, Runnable onOk) {
        new GlassDialog(c, title).width(520).message(message).button(android.R.string.cancel, null).button(ok, onOk).show();
    }

    static void ask(Context c, int title, String initial, Consumer<String> onOk) {
        GlassDialog g = new GlassDialog(c, c.getString(title)).width(520).input(initial);
        g.button(android.R.string.cancel, null).button(android.R.string.ok, () -> onOk.accept(g.text())).show();
    }
}
