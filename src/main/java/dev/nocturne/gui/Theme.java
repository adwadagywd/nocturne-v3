package dev.nocturne.gui;

import dev.nocturne.module.ModuleManager;

/** Tweak the look of the whole client from here. */
public final class Theme {
    public static final int BG = 0xEC0B0B0F, HEADER = 0xF2121218, ROW_HOVER = 0xFF1C1C25;
    public static final int TEXT = 0xFFEDEDF2, DIM = 0xFF7C7C8A, SOFT = 0xFFBDBDC9;
    public static final int TRACK = 0xFF24242D, KNOB = 0xFFFFFFFF;

    public static int accent() {
        return ModuleManager.HUD == null ? 0xFFB06CFF : ModuleManager.HUD.color(0);
    }
    public static int accent2() { return Draw.lerp(accent(), 0xFFFFFFFF, 0.45f); }
    public static int accentA(int alpha) { return (accent() & 0xFFFFFF) | (alpha << 24); }
}
