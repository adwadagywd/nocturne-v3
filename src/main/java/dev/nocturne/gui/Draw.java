package dev.nocturne.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Tiny 2D toolkit: global alpha, rounded rects, gradients, shadows, colour maths. */
public final class Draw {
    public static float alpha = 1f;

    private Draw() {}

    public static int col(int argb) {
        int a = (int) (((argb >>> 24) & 255) * alpha);
        return (Math.max(0, Math.min(255, a)) << 24) | (argb & 0xFFFFFF);
    }

    /** multiply the alpha channel of an ARGB colour */
    public static int scaleA(int argb, float f) {
        int a = (int) (((argb >>> 24) & 255) * Math.max(0f, Math.min(1f, f)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    public static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int out = 0;
        for (int s = 24; s >= 0; s -= 8) {
            int ca = (a >>> s) & 255, cb = (b >>> s) & 255;
            out |= ((int) (ca + (cb - ca) * t) & 255) << s;
        }
        return out;
    }

    public static void rect(DrawContext ctx, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        int c = col(color);
        if ((c >>> 24) == 0) return;
        ctx.fill(x, y, x + w, y + h, c);
    }

    public static void round(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        int c = col(color);
        if ((c >>> 24) == 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) { ctx.fill(x, y, x + w, y + h, c); return; }
        ctx.fill(x, y + r, x + w, y + h - r, c);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            ctx.fill(x + inset, y + i, x + w - inset, y + i + 1, c);
            ctx.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, c);
        }
    }

    public static void hgrad(DrawContext ctx, int x, int y, int w, int h, int c1, int c2) {
        for (int i = 0; i < w; i++) rect(ctx, x + i, y, 1, h, lerp(c1, c2, i / (float) Math.max(1, w - 1)));
    }

    public static void shadow(DrawContext ctx, int x, int y, int w, int h, int r) {
        for (int i = 8; i >= 1; i--) round(ctx, x - i, y - i, w + 2 * i, h + 2 * i, r + i, 0x06000000);
    }

    public static void text(DrawContext ctx, TextRenderer tr, String s, int x, int y, int color, boolean shadow) {
        int c = col(color);
        if ((c >>> 24) < 8) return; // vanilla draws near-transparent text fully opaque, so skip it
        ctx.drawText(tr, s, x, y, c, shadow);
    }

    public static void bold(DrawContext ctx, TextRenderer tr, String s, int x, int y, int color, boolean shadow) {
        int c = col(color);
        if ((c >>> 24) < 8) return;
        ctx.drawText(tr, Text.literal(s).formatted(Formatting.BOLD), x, y, c, shadow);
    }

    public static int boldWidth(TextRenderer tr, String s) {
        return tr.getWidth(Text.literal(s).formatted(Formatting.BOLD));
    }
}
