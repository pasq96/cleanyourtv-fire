package com.gathin.tvhome;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

/**
 * A 16:9 banner made from an app's icon, for apps with no TV banner (phone apps, Amazon Photos): an adaptive icon's
 * background layer fills the tile under its foreground; any other icon sits on its own edge colour.
 */
final class IconBanner {
    static final int W = 320, H = 180;
    /** Adaptive foreground at 1.08 H puts its 72/108 safe zone at 0.72 H; a plain icon takes 0.6 H. */
    private static final float ADAPTIVE_FG = 1.08f, PLAIN_ICON = 0.6f;

    private IconBanner() {}

    /** @param stretch the whole icon scaled to fill the 16:9 tile (adaptive layers included); else icon on a gradient. */
    static Drawable make(Resources res, Drawable icon, boolean stretch) {
        if (icon == null) return null;
        if (stretch) {
            Bitmap filled = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
            Canvas fc = new Canvas(filled);
            fc.drawColor(edgeColor(render(icon, 96)));   // under a see-through icon
            icon.setBounds(0, 0, W, H);
            icon.draw(fc);
            return new BitmapDrawable(res, filled);
        }
        Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        if (icon instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable a = (AdaptiveIconDrawable) icon;
            Drawable bg = a.getBackground(), fg = a.getForeground();
            if (bg != null) {
                bg.setBounds(0, 0, W, W);   // square layer, cropped to the middle band
                c.save();
                c.translate(0, (H - W) / 2f);
                bg.draw(c);
                c.restore();
            } else c.drawColor(Color.DKGRAY);
            if (fg != null) drawCentered(c, fg, (int) (H * ADAPTIVE_FG));
        } else {
            Bitmap src = render(icon, 96);
            // Clean backdrop: a soft vertical gradient of the icon's edge colour, lighter at the top, like a TV banner.
            int base = edgeColor(src);
            Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
            bg.setShader(new LinearGradient(0, 0, 0, H, shade(base, 1.25f), shade(base, 0.72f), Shader.TileMode.CLAMP));
            c.drawRect(0, 0, W, H, bg);
            // The icon on a soft shadow, rounded like the rest of the TV UI.
            int size = (int) (H * PLAIN_ICON), left = (W - size) / 2, top = (H - size) / 2;
            float radius = size * 0.22f;
            RectF box = new RectF(left, top, left + size, top + size);
            Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
            shadow.setColor(base);
            shadow.setShadowLayer(14f, 0f, 6f, 0x73000000);
            c.drawRoundRect(box, radius, radius, shadow);
            Path clip = new Path();
            clip.addRoundRect(box, radius, radius, Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            drawCentered(c, icon, size);
            c.restore();
        }
        return new BitmapDrawable(res, out);
    }

    /** The colour scaled brighter (>1, towards white) or darker (<1). */
    static int shade(int argb, float k) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        if (k > 1f) {
            float t = k - 1f;
            r += (int) ((255 - r) * t); g += (int) ((255 - g) * t); b += (int) ((255 - b) * t);
        } else {
            r *= k; g *= k; b *= k;
        }
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static void drawCentered(Canvas c, Drawable d, int size) {
        int left = (W - size) / 2, top = (H - size) / 2;
        d.setBounds(left, top, left + size, top + size);
        d.draw(c);
    }

    private static Bitmap render(Drawable d, int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, size, size);
        d.draw(c);
        return b;
    }

    /** Average opaque colour along the icon's border, darkened so white text and glow stay readable; dark grey if see-through. */
    static int edgeColor(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[2 * (w + h)];
        int n = 0;
        for (int x = 0; x < w; x++) { px[n++] = b.getPixel(x, 0); px[n++] = b.getPixel(x, h - 1); }
        for (int y = 0; y < h; y++) { px[n++] = b.getPixel(0, y); px[n++] = b.getPixel(w - 1, y); }
        return average(px, n);
    }

    static int average(int[] px, int n) {
        long r = 0, g = 0, bl = 0;
        int count = 0;
        for (int i = 0; i < n; i++) {
            int p = px[i];
            if ((p >>> 24) < 200) continue;
            r += (p >> 16) & 0xFF; g += (p >> 8) & 0xFF; bl += p & 0xFF;
            count++;
        }
        if (count < n / 4) return 0xFF2A2D33;
        float k = 0.8f;
        return 0xFF000000 | ((int) (r / count * k) << 16) | ((int) (g / count * k) << 8) | (int) (bl / count * k);
    }
}
