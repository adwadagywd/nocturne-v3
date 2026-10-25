package dev.nocturne.module;

import dev.nocturne.module.impl.*;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;

import java.util.ArrayList;
import java.util.List;

public final class ModuleManager {
    public static final List<Module> ALL = new ArrayList<>();
    public static HudModule HUD;

    public static void init() {
        HUD = new HudModule();
        // combat
        ALL.add(new Hitboxes());
        ALL.add(new AimAssist());
        ALL.add(new TriggerBot());
        ALL.add(new KillAura());
        ALL.add(new FakePlayer());
        // render
        ALL.add(new Tracers());
        ALL.add(new Esp());
        ALL.add(new Nametags());
        ALL.add(new XRay());
        ALL.add(new Fullbright());
        // movement
        ALL.add(new AutoSprint());
        ALL.add(new Freecam());
        // client
        ALL.add(new TargetHud());
        ALL.add(HUD);
        HUD.setEnabled(true);
    }

    /** called every frame: toggles modules whose bound key was just pressed */
    public static void pollBinds() {
        MinecraftClient mc = MinecraftClient.getInstance();
        long h = mc.getWindow().getHandle();
        for (Module m : ALL) {
            if (m.key < 0) { m.keyDown = false; continue; }
            boolean down = InputUtil.isKeyPressed(h, m.key);
            if (down && !m.keyDown && mc.currentScreen == null) m.toggle();
            m.keyDown = down;
        }
    }

    public static List<Module> byCategory(Category c) {
        List<Module> out = new ArrayList<>();
        for (Module m : ALL) if (m.category == c) out.add(m);
        return out;
    }
}
