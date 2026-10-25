package dev.nocturne.module.impl;

import dev.nocturne.gui.Draw;
import dev.nocturne.gui.Theme;
import dev.nocturne.module.*;
import dev.nocturne.module.Module;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;

import java.util.Locale;

/** Small animated card with the health of whatever you are looking at. */
public class TargetHud extends Module {
    private LivingEntity last;
    private float alpha, healthAnim;
    private long lastNs;

    public TargetHud() { super("TargetHUD", "Shows health and armor of the entity you look at", Category.CLIENT); }

    @Override
    public void onRenderHud(DrawContext ctx) {
        long now = System.nanoTime();
        float dt = lastNs == 0 ? 0.016f : Math.min(0.05f, (now - lastNs) / 1.0e9f);
        lastNs = now;

        Entity t = mc.targetedEntity;
        LivingEntity cur = (t instanceof LivingEntity le && le.isAlive()) ? le : null;
        if (cur != null) {
            if (cur != last) { last = cur; healthAnim = cur.getHealth(); }
        }
        alpha += ((cur != null ? 1f : 0f) - alpha) * (1f - (float) Math.exp(-12f * dt));
        if (last == null || alpha < 0.03f) return;

        float hp = last.getHealth(), max = Math.max(1f, last.getMaxHealth());
        healthAnim += (hp - healthAnim) * (1f - (float) Math.exp(-8f * dt));
        float ratio = MathHelper.clamp(healthAnim / max, 0f, 1f);

        TextRenderer tr = mc.textRenderer;
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        int w = 128, h = 38;
        int x = sw / 2 + 22 + Math.round((1f - alpha) * 10f), y = sh / 2 + 12;

        Draw.alpha = alpha;
        Draw.shadow(ctx, x, y, w, h, 6);
        Draw.round(ctx, x, y, w, h, 6, 0xE60B0B0F);
        Draw.round(ctx, x, y + 6, 2, h - 12, 1, Theme.accent());
        Draw.bold(ctx, tr, tr.trimToWidth(last.getName().getString(), w - 50), x + 9, y + 6, Theme.TEXT, false);
        String hpText = String.format(Locale.ROOT, "%.1f", hp);
        Draw.text(ctx, tr, hpText, x + w - 8 - tr.getWidth(hpText), y + 6, Theme.SOFT, false);

        int bx = x + 9, bw = w - 18, by = y + 19;
        Draw.round(ctx, bx, by, bw, 5, 2, Theme.TRACK);
        Draw.round(ctx, bx, by, Math.max(4, Math.round(bw * ratio)), 5, 2,
                0xFF000000 | MathHelper.hsvToRgb(ratio * 0.33f, 0.75f, 1f));
        Draw.text(ctx, tr, "Armor " + last.getArmor(), bx, y + 27, Theme.DIM, false);
        String d = String.format(Locale.ROOT, "%.1fm", mc.player.distanceTo(last));
        Draw.text(ctx, tr, d, x + w - 8 - tr.getWidth(d), y + 27, Theme.DIM, false);
        Draw.alpha = 1f;
    }
}
