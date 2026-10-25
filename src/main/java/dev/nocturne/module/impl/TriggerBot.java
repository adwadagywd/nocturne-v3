package dev.nocturne.module.impl;

import dev.nocturne.module.*;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import dev.nocturne.util.Combat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;

public class TriggerBot extends Module {
    private final BoolSetting onlyCrits = add(new BoolSetting("Only crits", false));
    private final NumSetting minCooldown = add(new NumSetting("Min cooldown", 0.92, 0.5, 1.0, 0.01));
    private final NumSetting range = add(new NumSetting("Range", 3.0, 2.0, 6.0, 0.1));
    private final BoolSetting weaponOnly = add(new BoolSetting("Weapon only", true));
    private final BoolSetting holdClick = add(new BoolSetting("Only while clicking", false));
    private final BoolSetting pauseUsing = add(new BoolSetting("Not while using item", true));
    private final TargetSettings targets = new TargetSettings(this, true, true, false, false);

    public TriggerBot() {
        super("TriggerBot", "Attacks automatically when your crosshair is on a target", Category.COMBAT);
    }

    @Override
    public void onTick(MinecraftClient mc) {
        PlayerEntity p = mc.player;
        if (p == null || mc.currentScreen != null || mc.interactionManager == null || Combat.blocked()) return;
        if (!(mc.crosshairTarget instanceof EntityHitResult hit)) return;

        Entity e = hit.getEntity();
        if (!(e instanceof LivingEntity le) || !le.isAlive() || !targets.test(e)) return;
        if (p.distanceTo(e) > range.get()) return;
        if (weaponOnly.get() && !Combat.isWeapon(p.getMainHandStack())) return;
        if (pauseUsing.get() && p.isUsingItem()) return;
        if (holdClick.get() && !mc.options.attackKey.isPressed()) return;
        if (p.getAttackCooldownProgress(0.5f) < minCooldown.get()) return;
        if (onlyCrits.get() && !Combat.canCrit(p)) return; // tip: AutoSprint keeps you sprinting => no crits

        mc.interactionManager.attackEntity(p, e);
        p.swingHand(Hand.MAIN_HAND);
    }
}
