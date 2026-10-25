package dev.nocturne.module.impl;

import dev.nocturne.gui.Draw;
import dev.nocturne.gui.Theme;
import dev.nocturne.module.*;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class HudModule extends Module {
    public final ColorSetting accent = add(new ColorSetting("Accent", 0.76f));
    public final BoolSetting rainbow = add(new BoolSetting("Rainbow", false));
    private final BoolSetting watermark = add(new BoolSetting("Watermark", true));
    private final BoolSetting list = add(new BoolSetting("Module list", true));
    /** Auto = detect; switch to CPU/GPU if ESP or tracers look offset */
    public final ModeSetting matrixMode = add(new ModeSetting("Matrix", "Auto", "Auto", "CPU", "GPU"));

    public HudModule() {
        super("HUD", "Watermark, module list and theme colour", Category.CLIENT);
        accent.showIf(() -> !rainbow.get());
    }

    public int color(int index) {
        if (!rainbow.get()) return accent.rgb();
        float h = ((System.currentTimeMillis() % 5000L) / 5000f + index * 0.04f) % 1f;
        return 0xFF000000 | MathHelper.hsvToRgb(h, 0.7f, 1f);
    }

    @Override
    public void onRenderHud(DrawContext ctx) {
        Draw.alpha = 1f;
        TextRenderer tr = mc.textRenderer;
        if (watermark.get()) {
            String a = "NOCTURNE", b = mc.getCurrentFps() + " fps";
            int w = Draw.boldWidth(tr, a) + tr.getWidth(b) + 30;
            Draw.shadow(ctx, 6, 6, w, 17, 5);
            Draw.round(ctx, 6, 6, w, 17, 5, 0xD00B0B0F);
            Draw.round(ctx, 12, 12, 5, 5, 2, color(0));
            Draw.bold(ctx, tr, a, 21, 10, color(0), false);
            Draw.rect(ctx, 21 + Draw.boldWidth(tr, a) + 5, 10, 1, 8, 0xFF2A2A34);
            Draw.text(ctx, tr, b, 21 + Draw.boldWidth(tr, a) + 11, 10, 0xFF9A9AA6, false);
        }
        if (list.get()) {
            List<Module> on = new ArrayList<>();
            for (Module m : ModuleManager.ALL) if (m.isEnabled() && m != this) on.add(m);
            on.sort(Comparator.comparingInt((Module m) -> -tr.getWidth(m.name)));
            int sw = mc.getWindow().getScaledWidth();
            int y = 6, i = 0;
            for (Module m : on) {
                int w = tr.getWidth(m.name) + 12;
                int x = sw - w - 6;
                Draw.round(ctx, x, y, w, 13, 4, 0xC00B0B0F);
                Draw.round(ctx, sw - 9, y + 3, 2, 7, 1, color(i));
                Draw.text(ctx, tr, m.name, x + 5, y + 3, color(i), false);
                y += 15; i++;
            }
        }
    }
}
