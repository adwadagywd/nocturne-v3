package dev.nocturne.util;

/** Server-side ("silent") rotation. While active, the movement packet is sent with this yaw/pitch instead of the camera's. */
public final class RotationManager {
    private RotationManager() {}

    public static float yaw, pitch;
    private static boolean active;

    public static void set(float y, float p) { yaw = y; pitch = p; active = true; }
    public static void clear() { active = false; }
    public static boolean active() { return active; }
}
