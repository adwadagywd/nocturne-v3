package dev.nocturne.util;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.nocturne.module.ModuleManager;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class RenderUtil {
    public static final Matrix4f view = new Matrix4f(), proj = new Matrix4f(), viewProj = new Matrix4f(), invViewProj = new Matrix4f();
    public static final Matrix4f IDENTITY = new Matrix4f();
    public static Vec3d cam = Vec3d.ZERO, forward = new Vec3d(0, 0, 1), up = new Vec3d(0, 1, 0);
    public static float tickDelta;
    public static boolean valid;
    /** true  = we rotate vertices ourselves (GPU modelview is identity)
     *  false = the GPU modelview already contains the camera rotation, so we must NOT rotate again */
    public static boolean cpuView = true;

    private RenderUtil() {}

    public static void capture(WorldRenderContext c) {
        view.set(c.positionMatrix());
        proj.set(c.projectionMatrix());
        viewProj.set(proj).mul(view);
        invViewProj.set(viewProj).invert();
        Camera camera = c.camera();
        cam = camera.getPos();
        forward = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
        up = Vec3d.fromPolar(camera.getPitch() - 90f, camera.getYaw());
        tickDelta = c.tickCounter().getTickDelta(true);

        String mode = ModuleManager.HUD == null ? "Auto" : ModuleManager.HUD.matrixMode.get();
        switch (mode) {
            case "CPU" -> cpuView = true;
            case "GPU" -> cpuView = false;
            default -> cpuView = RenderSystem.getModelViewMatrix().equals(IDENTITY, 1.0E-4f);
        }
        valid = true;
    }

    // ---- 3D drawing (camera-relative, no depth test => visible through walls) ----
    public static void setup(float lineWidth) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.lineWidth(lineWidth);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
    }

    public static void restore() {
        RenderSystem.lineWidth(1f);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    public static BufferBuilder begin(VertexFormat.DrawMode mode) {
        return Tessellator.getInstance().begin(mode, VertexFormats.POSITION_COLOR);
    }

    public static void finish(BufferBuilder b) {
        BuiltBuffer built = b.endNullable();
        if (built != null) BufferRenderer.drawWithGlobalProgram(built);
    }

    private static void v(BufferBuilder b, double x, double y, double z, int c) {
        Matrix4f m = cpuView ? view : IDENTITY;
        b.vertex(m, (float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z))
                .color((c >> 16) & 255, (c >> 8) & 255, c & 255, (c >>> 24) & 255);
    }

    public static void line(BufferBuilder b, Vec3d a, Vec3d c, int argb) {
        v(b, a.x, a.y, a.z, argb);
        v(b, c.x, c.y, c.z, argb);
    }

    private static void l(BufferBuilder b, double x1, double y1, double z1, double x2, double y2, double z2, int c) {
        v(b, x1, y1, z1, c);
        v(b, x2, y2, z2, c);
    }

    public static void boxOutline(BufferBuilder b, Box bx, int c) {
        double x1 = bx.minX, y1 = bx.minY, z1 = bx.minZ, x2 = bx.maxX, y2 = bx.maxY, z2 = bx.maxZ;
        l(b, x1, y1, z1, x2, y1, z1, c); l(b, x2, y1, z1, x2, y1, z2, c);
        l(b, x2, y1, z2, x1, y1, z2, c); l(b, x1, y1, z2, x1, y1, z1, c);
        l(b, x1, y2, z1, x2, y2, z1, c); l(b, x2, y2, z1, x2, y2, z2, c);
        l(b, x2, y2, z2, x1, y2, z2, c); l(b, x1, y2, z2, x1, y2, z1, c);
        l(b, x1, y1, z1, x1, y2, z1, c); l(b, x2, y1, z1, x2, y2, z1, c);
        l(b, x2, y1, z2, x2, y2, z2, c); l(b, x1, y1, z2, x1, y2, z2, c);
    }

    private static void q(BufferBuilder b, int c, double... p) {
        for (int i = 0; i < 12; i += 3) v(b, p[i], p[i + 1], p[i + 2], c);
    }

    public static void boxFill(BufferBuilder b, Box bx, int c) {
        double x1 = bx.minX, y1 = bx.minY, z1 = bx.minZ, x2 = bx.maxX, y2 = bx.maxY, z2 = bx.maxZ;
        q(b, c, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2);
        q(b, c, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1);
        q(b, c, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1);
        q(b, c, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2);
        q(b, c, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1);
        q(b, c, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2);
    }

    public static Box lerpedBox(Entity e) {
        Vec3d d = e.getLerpedPos(tickDelta).subtract(e.getPos());
        return e.getBoundingBox().offset(d);
    }

    public static int argb(int rgb, float alpha) {
        int a = (int) (Math.max(0f, Math.min(1f, alpha)) * 255f);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    /** World position -> scaled GUI coordinates, or null when behind the camera. */
    public static Vec3d project(Vec3d w, int sw, int sh) {
        if (!valid) return null;
        Vector4f p = new Vector4f((float) (w.x - cam.x), (float) (w.y - cam.y), (float) (w.z - cam.z), 1f);
        viewProj.transform(p);
        if (p.w <= 0.01f) return null;
        float nx = p.x / p.w, ny = p.y / p.w;
        return new Vec3d((nx * 0.5 + 0.5) * sw, (1.0 - (ny * 0.5 + 0.5)) * sh, 0);
    }

    /** Screen (NDC -1..1) -> world position. nz=0.5 is roughly 0.2 blocks in front of the camera. */
    public static Vec3d unproject(float nx, float ny, float nz) {
        Vector4f p = new Vector4f(nx, ny, nz, 1f);
        invViewProj.transform(p);
        if (Math.abs(p.w) < 1.0E-6f) return cam.add(forward.multiply(0.2));
        return cam.add(p.x / p.w, p.y / p.w, p.z / p.w);
    }
}
