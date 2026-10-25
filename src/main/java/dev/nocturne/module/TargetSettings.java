package dev.nocturne.module;

import dev.nocturne.setting.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

/** Shared "what do I target / how do I colour it" settings used by Tracers, ESP, Nametags, Hitboxes. */
public class TargetSettings {
    public final BoolSetting players, hostile, passive, items;
    public final ModeSetting colorMode;
    public final ColorSetting custom;

    public TargetSettings(Module m, boolean p, boolean h, boolean pa, boolean i) {
        players = m.add(new BoolSetting("Players", p));
        hostile = m.add(new BoolSetting("Hostile mobs", h));
        passive = m.add(new BoolSetting("Passive mobs", pa));
        items = m.add(new BoolSetting("Items", i));
        colorMode = m.add(new ModeSetting("Color", "Type", "Type", "Health", "Distance", "Custom"));
        custom = m.add(new ColorSetting("Custom color", 0.0f));
        custom.showIf(() -> colorMode.is("Custom"));
    }

    public boolean test(Entity e) {
        if (e == MinecraftClient.getInstance().player || e.isRemoved()) return false;
        if (e instanceof PlayerEntity) return players.get();
        if (e instanceof Monster) return hostile.get();
        if (e instanceof MobEntity) return passive.get();
        if (e instanceof ItemEntity) return items.get();
        return false;
    }

    /** @return 0xRRGGBB */
    public int color(Entity e, double dist, double range) {
        switch (colorMode.get()) {
            case "Health" -> {
                if (e instanceof LivingEntity le) {
                    float r = MathHelper.clamp(le.getHealth() / le.getMaxHealth(), 0f, 1f);
                    return MathHelper.hsvToRgb(r * 0.33f, 0.85f, 1f);
                }
            }
            case "Distance" -> {
                float r = (float) MathHelper.clamp(dist / range, 0, 1);
                return MathHelper.hsvToRgb(r * 0.33f, 0.85f, 1f);
            }
            case "Custom" -> { return custom.rgb() & 0xFFFFFF; }
            default -> {}
        }
        if (e instanceof PlayerEntity) return 0xFF4D6D;
        if (e instanceof Monster) return 0xFF9F1C;
        if (e instanceof MobEntity) return 0x57E389;
        return 0x4CC9F0;
    }
}
