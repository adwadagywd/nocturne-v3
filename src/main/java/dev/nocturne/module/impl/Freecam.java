package dev.nocturne.module.impl;

import dev.nocturne.module.*;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class Freecam extends Module {
    public static Freecam INSTANCE;

    private final NumSetting speed = add(new NumSetting("Speed", 14, 2, 60, 1));
    private final NumSetting boost = add(new NumSetting("Sprint boost", 2.5, 1, 6, 0.5));

    private OtherClientPlayerEntity cam;
    private PlayerEntity owner;
    private float cyaw, cpitch;
    private double cx, cy, cz;
    private long last;

    public Freecam() {
        super("Freecam", "Fly the camera around while your body stays put", Category.MOVEMENT);
        INSTANCE = this;
    }

    @Override public boolean persistEnabled() { return false; }

    @Override
    protected void onEnable() {
        if (mc.player == null || mc.world == null) return;
        owner = mc.player;
        cam = new OtherClientPlayerEntity(mc.world, mc.player.getGameProfile());
        cx = mc.player.getX(); cy = mc.player.getY(); cz = mc.player.getZ();
        cyaw = mc.player.getYaw();
        cpitch = mc.player.getPitch();
        last = 0;
        place();
        mc.setCameraEntity(cam);
    }

    @Override
    protected void onDisable() {
        if (cam != null && mc.player != null && mc.getCameraEntity() == cam) mc.setCameraEntity(mc.player);
        cam = null;
        owner = null;
        KeyBinding.updatePressedStates(); // give the movement keys back
    }

    private void place() {
        cam.setPosition(cx, cy, cz);
        cam.prevX = cx; cam.prevY = cy; cam.prevZ = cz;
        cam.setYaw(cyaw); cam.setPitch(cpitch);
        cam.prevYaw = cyaw; cam.prevPitch = cpitch;
    }

    /** mouse input (called from the Entity.changeLookDirection mixin): turns the camera, the real player keeps its rotation */
    public boolean captureLook(Entity self, double dx, double dy) {
        if (cam == null || self != mc.player) return false;
        cyaw += (float) dx * 0.15f;
        cpitch = MathHelper.clamp(cpitch + (float) dy * 0.15f, -90f, 90f);
        return true;
    }

    /** stop the real player from reacting to keys while we fly the camera */
    @Override
    public void onPreTick(MinecraftClient mc) {
        if (cam == null) return;
        GameOptions o = mc.options;
        for (KeyBinding k : new KeyBinding[]{o.forwardKey, o.backKey, o.leftKey, o.rightKey,
                o.jumpKey, o.sneakKey, o.sprintKey, o.attackKey, o.useKey, o.pickItemKey}) k.setPressed(false);
    }

    private boolean down(KeyBinding k) {
        return InputUtil.isKeyPressed(mc.getWindow().getHandle(), KeyBindingHelper.getBoundKeyOf(k).getCode());
    }

    /** called at the very start of Camera.update (CameraMixin), so this frame already renders from the new position */
    public void step(Entity focused) {
        if (cam == null || focused != cam) return;
        long now = System.nanoTime();
        float dt = last == 0 ? 0.016f : Math.min(0.1f, (now - last) / 1.0e9f);
        last = now;

        if (mc.currentScreen == null) {
            GameOptions o = mc.options;
            Vec3d look = Vec3d.fromPolar(cpitch, cyaw);
            double yr = Math.toRadians(cyaw);
            Vec3d right = new Vec3d(-Math.cos(yr), 0, -Math.sin(yr));
            Vec3d mv = Vec3d.ZERO;
            if (down(o.forwardKey)) mv = mv.add(look);
            if (down(o.backKey)) mv = mv.subtract(look);
            if (down(o.rightKey)) mv = mv.add(right);
            if (down(o.leftKey)) mv = mv.subtract(right);
            if (down(o.jumpKey)) mv = mv.add(0, 1, 0);
            if (down(o.sneakKey)) mv = mv.subtract(0, 1, 0);
            if (mv.lengthSquared() > 1.0E-6) {
                double sp = speed.get() * (down(o.sprintKey) ? boost.get() : 1.0) * dt;
                mv = mv.normalize().multiply(sp);
                cx += mv.x; cy += mv.y; cz += mv.z;
            }
        }
        place();
    }

    /** safety net: respawn / dimension change / leaving the world must never leave the player stuck */
    @Override
    public void onFrame() {
        if (cam == null) return;
        if (mc.player == null || mc.world == null || mc.player != owner
                || cam.getWorld() != mc.world || mc.getCameraEntity() != cam) setEnabled(false);
    }
}
