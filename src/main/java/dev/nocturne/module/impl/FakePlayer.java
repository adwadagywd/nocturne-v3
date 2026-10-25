package dev.nocturne.module.impl;

import com.mojang.authlib.GameProfile;
import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import dev.nocturne.util.Combat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/**
 * Client-side training dummy: a player with totems that takes knockback, pops and dies like a real one.
 * Nothing is sent to the server - hits on it are simulated locally (see ClientPlayerInteractionManagerMixin).
 */
public class FakePlayer extends Module {
    public static FakePlayer INSTANCE;
    private static int nextId = -9000;

    private final ModeSetting name = add(new ModeSetting("Name", "Dummy", "Dummy", "Steve", "Notch", "Alex"));
    private final NumSetting health = add(new NumSetting("Health", 20, 2, 20, 1));
    private final NumSetting totems = add(new NumSetting("Totems", 5, 0, 10, 1));
    private final BoolSetting regen = add(new BoolSetting("Regen", true));
    private final BoolSetting knockback = add(new BoolSetting("Knockback", true));
    private final BoolSetting copyGear = add(new BoolSetting("Copy gear", true));
    private final BoolSetting respawn = add(new BoolSetting("Respawn", true));
    private final BoolSetting chatLog = add(new BoolSetting("Chat log", true));

    private OtherClientPlayerEntity fake;
    private int totemsLeft, pops, absTicks, regenTimer, deadTicks;
    private boolean dead;
    private Vec3d vel = Vec3d.ZERO;

    public FakePlayer() {
        super("FakePlayer", "Spawns a client-side dummy with totems to test your combat modules on", Category.COMBAT);
        INSTANCE = this;
    }

    @Override public boolean persistEnabled() { return false; }

    public static boolean isFake(Entity e) {
        return INSTANCE != null && INSTANCE.fake != null && INSTANCE.fake == e;
    }

    // ---------------- lifecycle ----------------
    @Override protected void onEnable() { pops = 0; spawn(); }

    @Override
    protected void onDisable() {
        remove();
    }

    private void remove() {
        if (fake != null && mc.world != null && fake.getWorld() == mc.world)
            mc.world.removeEntity(fake.getId(), Entity.RemovalReason.DISCARDED);
        fake = null;
        dead = false;
    }

    private void spawn() {
        PlayerEntity p = mc.player;
        if (p == null || mc.world == null) return;
        fake = new OtherClientPlayerEntity(mc.world, new GameProfile(UUID.randomUUID(), name.get()));
        fake.setId(nextId--);
        Vec3d fwd = Vec3d.fromPolar(0f, p.getYaw()).multiply(2.5);
        fake.refreshPositionAndAngles(p.getX() + fwd.x, p.getY(), p.getZ() + fwd.z, p.getYaw() + 180f, 0f);
        fake.setHeadYaw(p.getYaw() + 180f);
        fake.setBodyYaw(p.getYaw() + 180f);
        if (copyGear.get()) {
            for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND})
                fake.equipStack(s, p.getEquippedStack(s).copy());
        }
        fake.setHealth(health.getFloat());
        totemsLeft = totems.getInt();
        absTicks = 0; regenTimer = 0; deadTicks = 0; dead = false; vel = Vec3d.ZERO;
        updateOffhand();
        mc.world.addEntity(fake);
    }

    private void updateOffhand() {
        if (fake != null) fake.equipStack(EquipmentSlot.OFFHAND,
                totemsLeft > 0 ? new ItemStack(Items.TOTEM_OF_UNDYING) : ItemStack.EMPTY);
    }

    private void log(String s) {
        if (chatLog.get() && mc.player != null) mc.player.sendMessage(Text.literal("[FakePlayer] " + s), false);
    }

    // ---------------- combat simulation ----------------
    public void hit(PlayerEntity p, Entity target) {
        float cd = p.getAttackCooldownProgress(0.5f);
        p.resetLastAttackedTicks();
        if (fake == null || target != fake || dead || fake.hurtTime > 0) return;

        float dmg = (float) p.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);
        dmg *= 0.2f + cd * cd * 0.8f;
        if (cd > 0.9f && Combat.canCrit(p)) dmg *= 1.5f;

        if (knockback.get()) {
            double dx = fake.getX() - p.getX(), dz = fake.getZ() - p.getZ();
            double len = Math.max(1.0E-4, Math.sqrt(dx * dx + dz * dz));
            double k = p.isSprinting() ? 0.9 : 0.4;
            vel = new Vec3d(dx / len * k, fake.isOnGround() ? 0.36 : vel.y, dz / len * k);
        }

        float abs = fake.getAbsorptionAmount();
        if (abs > 0f) {
            float a = Math.min(abs, dmg);
            fake.setAbsorptionAmount(abs - a);
            dmg -= a;
        }
        fake.handleStatus((byte) 2); // hurt animation + sound
        fake.hurtTime = fake.maxHurtTime = 10;

        float h = fake.getHealth() - dmg;
        if (h > 0f) fake.setHealth(h);
        else if (totemsLeft > 0) pop();
        else die();
    }

    private void pop() {
        totemsLeft--;
        pops++;
        fake.setHealth(1f);
        fake.setAbsorptionAmount(8f);
        absTicks = 100;
        mc.particleManager.addEmitter(fake, ParticleTypes.TOTEM_OF_UNDYING, 30);
        mc.world.playSound(fake.getX(), fake.getY(), fake.getZ(), SoundEvents.ITEM_TOTEM_USE,
                fake.getSoundCategory(), 1f, 1f, false);
        updateOffhand();
        log("popped a totem (" + totemsLeft + " left, " + pops + " pops)");
    }

    private void die() {
        fake.setHealth(0f);
        fake.handleStatus((byte) 3);
        dead = true;
        deadTicks = 0;
        log("died after " + pops + " pops");
    }

    // ---------------- per tick ----------------
    @Override
    public void onTick(MinecraftClient mc) {
        if (fake == null) return;
        if (mc.world == null || fake.getWorld() != mc.world) { fake = null; setEnabled(false); return; }

        if (dead) {
            if (++deadTicks >= 30) {
                remove();
                if (respawn.get()) spawn(); else setEnabled(false);
            }
            return;
        }

        // simple physics so knockback + gravity work for a client-only entity
        fake.move(MovementType.SELF, vel);
        boolean g = fake.isOnGround();
        vel = new Vec3d(vel.x * (g ? 0.6 : 0.91), g ? -0.08 : (vel.y - 0.08) * 0.98, vel.z * (g ? 0.6 : 0.91));

        if (absTicks > 0 && --absTicks == 0) fake.setAbsorptionAmount(0f);
        if (regen.get() && fake.getHealth() < health.getFloat() && ++regenTimer >= 25) {
            regenTimer = 0;
            fake.setHealth(Math.min(health.getFloat(), fake.getHealth() + 1f));
        }
    }
}
