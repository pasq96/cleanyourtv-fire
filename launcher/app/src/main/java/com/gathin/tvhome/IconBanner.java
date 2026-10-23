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

    /** @param style a Themes.BANNER_* value: stretched icon, gradient (default here), blurred or glass backdrop. */
    static Drawable make(Resources res, Drawable icon, int style) {
        if (icon == null) return null;
        if (style == Themes.BANNER_BLUR || style == Themes.BANNER_GLASS) return backdrop(res, icon, style == Themes.BANNER_GLASS);
        if (style == Themes.BANNER_STRETCH) {
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

    /**
     * Blurred: the icon blown up and softened fills the tile, the sharp rounded icon sits on it. Glass: the icon's
     * colour as a frosted pane with a light top sheen and a hairline edge, the icon centred on it.
     */
    private static Drawable backdrop(Resources res, Drawable icon, boolean glass) {
        Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Bitmap src = render(icon, 96);
        int base = edgeColor(src);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setFilterBitmap(true);
        if (glass) {
            paint.setShader(new LinearGradient(0, 0, 0, H, shade(base, 1.5f), shade(base, 0.85f), Shader.TileMode.CLAMP));
            c.drawRect(0, 0, W, H, paint);
            paint.setShader(new LinearGradient(0, 0, 0, H * 0.55f, 0x4DFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, W, H * 0.55f, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f);
            paint.setColor(0x66FFFFFF);
            c.drawRect(1.5f, 1.5f, W - 1.5f, H - 1.5f, paint);
            paint.setStyle(Paint.Style.FILL);
        } else {
            Bitmap tiny = Bitmap.createScaledBitmap(src, 10, 10, true);   // down then up: a cheap, even blur
            c.drawBitmap(tiny, null, new RectF(-W * 0.1f, (H - W * 1.2f) / 2f, W * 1.1f, (H + W * 1.2f) / 2f), paint);
            c.drawColor(0x66000000);
        }
        int size = (int) (H * (glass ? 0.56f : PLAIN_ICON)), left = (W - size) / 2, top = (H - size) / 2;
        float radius = size * 0.22f;
        RectF box = new RectF(left, top, left + size, top + size);
        Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadow.setColor(base);
        shadow.setShadowLayer(16f, 0f, 7f, 0x80000000);
        c.drawRoundRect(box, radius, radius, shadow);
        Path clip = new Path();
        clip.addRoundRect(box, radius, radius, Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        drawCentered(c, icon, size);
        c.restore();
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
