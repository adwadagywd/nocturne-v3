package dev.nocturne.gui;

import dev.nocturne.config.ConfigManager;
import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.module.ModuleManager;
import dev.nocturne.setting.*;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class ClickGui extends Screen {
    private static final int W = 134, HEAD = 26, ROW = 18, H_SMALL = 16, H_SLIDER = 24, PAD = 4;
    /** per category: x, y, open(1/0) - static so the layout survives re-opening */
    private static final Map<Category, int[]> STATE = new EnumMap<>(Category.class);

    private interface Click { void on(int button, int mx, int my); }
    private record Hit(int x, int y, int w, int h, Click click) {}

    private final List<Hit> hits = new ArrayList<>();
    private final Map<String, Float> anims = new HashMap<>();
    private final List<float[]> particles = new ArrayList<>();
    private final long openNs = System.nanoTime();
    private long lastNs;
    private float dt = 0.016f;
    private boolean closing;
    private long closeNs;
    private float scroll, scrollTarget, contentBottom;

    private Category dragPanel;
    private int dragOx, dragOy;
    private Slidable dragSlider;
    private int dragX, dragW = 1;
    private BlockListSetting searchFocus;
    private Module bindingModule;

    public ClickGui() { super(Text.literal("Nocturne")); }

    @Override public boolean shouldPause() { return false; }
    @Override public void removed() { ConfigManager.save(); }

    @Override
    public void close() {
        if (closing) return;
        closing = true;
        closeNs = System.nanoTime();
    }

    // ---------------- helpers ----------------
    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
    private static float clamp01(float t) { return t < 0f ? 0f : Math.min(t, 1f); }
    private static float ease(float t) { float u = 1f - clamp01(t); return 1f - u * u * u; }

    private float anim(Object key, String tag, float target, float speed) {
        String k = System.identityHashCode(key) + tag;
        float cur = anims.getOrDefault(k, target);
        cur += (target - cur) * (1f - (float) Math.exp(-speed * dt));
        if (Math.abs(target - cur) < 0.002f) cur = target;
        anims.put(k, cur);
        return cur;
    }
    private float peek(Object key, String tag, float def) {
        return anims.getOrDefault(System.identityHashCode(key) + tag, def);
    }

    private static int rowH(Setting<?> s) {
        if (s instanceof BlockListSetting b) return b.height();
        return s instanceof Slidable ? H_SLIDER : H_SMALL;
    }

    private static String pretty(String id) {
        int i = id.indexOf(':');
        String path = i >= 0 ? id.substring(i + 1) : id;
        String ns = (i >= 0 && !id.startsWith("minecraft:")) ? id.substring(0, i) + ":" : "";
        return ns + path.replace('_', ' ');
    }

    private static String keyName(int key) {
        String s = InputUtil.fromKeyCode(key, 0).getLocalizedText().getString();
        return s.length() > 6 ? s.substring(0, 6) : s;
    }

    private void chip(DrawContext ctx, TextRenderer tr, String label, int x, int y, int w, boolean act, int mx, int my, Runnable r) {
        boolean hv = act && in(mx, my, x, y, w, 14);
        Draw.round(ctx, x, y, w, 14, 4, hv ? Theme.accentA(0x55) : Theme.accentA(0x25));
        Draw.text(ctx, tr, label, x + (w - tr.getWidth(label)) / 2, y + 3, hv ? Theme.TEXT : Theme.accent(), false);
        if (act) hits.add(new Hit(x, y, w, 14, (b, hx, hy) -> { if (b == 0) r.run(); }));
    }
    private static int settingsH(Module m) {
        int h = 0;
        for (Setting<?> s : m.settings) if (s.visible()) h += rowH(s);
        return h;
    }

    // ---------------- render ----------------
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        long now = System.nanoTime();
        dt = lastNs == 0 ? 0.016f : Math.min(0.05f, (now - lastNs) / 1.0e9f);
        lastNs = now;
        float tOpen = (now - openNs) / 1.0e9f;

        float master = 1f;
        if (closing) {
            float cp = (now - closeNs) / 1.0e9f / 0.16f;
            if (cp >= 1f) { client.setScreen(null); return; }
            master = 1f - ease(cp);
        }
        hits.clear();
        TextRenderer tr = textRenderer;

        if (dragSlider != null) dragSlider.setFraction((mx - dragX) / (double) dragW);

        float minScroll = -Math.max(0f, contentBottom - height);
        scrollTarget = MathHelper.clamp(scrollTarget, minScroll, 0f);
        scroll += (scrollTarget - scroll) * (1f - (float) Math.exp(-14f * dt));
        int sc = Math.round(scroll);

        // ----- background: vanilla blur + accent glow + floating particles -----
        renderBackground(ctx, mx, my, delta);
        Draw.alpha = master * clamp01(tOpen / 0.25f);
        ctx.fillGradient(0, 0, width, height, Draw.col(0x00000000), Draw.col(Theme.accentA(0x2C)));

        if (particles.isEmpty()) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            for (int i = 0; i < 45; i++)
                particles.add(new float[]{r.nextFloat() * width, r.nextFloat() * height,
                        (r.nextFloat() - 0.5f) * 6f, -(4f + r.nextFloat() * 10f), 1f + r.nextFloat() * 2f, 0.25f + r.nextFloat() * 0.5f});
        }
        for (float[] p : particles) {
            p[0] += p[2] * dt;
            p[1] += p[3] * dt;
            if (p[1] < -4f) { p[1] = height + 4f; p[0] = ThreadLocalRandom.current().nextFloat() * width; }
            Draw.round(ctx, (int) p[0], (int) p[1], (int) p[4], (int) p[4], 1, Theme.accentA((int) (p[5] * 110)));
        }

        // ----- panels -----
        Module hovered = null;
        float bottom = 0f;
        int idx = 0;
        for (Category c : Category.values()) {
            int[] st = STATE.computeIfAbsent(c, k -> new int[]{24 + k.ordinal() * (W + 12), 26, 1});
            float p = ease((tOpen - idx * 0.07f) / 0.3f);
            idx++;
            Draw.alpha = master * p;
            boolean live = p > 0.7f && !closing;

            final int x = MathHelper.clamp(st[0], 0, Math.max(0, width - W));
            final int y = st[1] + sc + Math.round((1f - p) * 18f);

            List<Module> mods = ModuleManager.byCategory(c);
            float openA = anim(c, "open", st[2] == 1 ? 1f : 0f, 16f);

            // pass 1: heights
            int bodyH = 0, on = 0;
            for (Module m : mods) {
                bodyH += ROW;
                float ea = anim(m, "exp", m.expanded ? 1f : 0f, 14f);
                bodyH += Math.round(settingsH(m) * ea);
                if (m.isEnabled()) on++;
            }
            int visBody = Math.round(bodyH * openA);
            int pad = Math.round(PAD * openA);
            int totalH = HEAD + visBody + pad;
            bottom = Math.max(bottom, st[1] + HEAD + bodyH + PAD + 24);

            // frame
            Draw.shadow(ctx, x, y, W, totalH, 6);
            Draw.round(ctx, x, y, W, totalH, 6, Theme.BG);
            Draw.round(ctx, x, y, W, HEAD, 6, Theme.HEADER);
            if (openA > 0.02f) Draw.rect(ctx, x, y + HEAD - 6, W, 6, Theme.HEADER);
            Draw.bold(ctx, tr, c.label, x + 10, y + 9, Theme.TEXT, false);
            String cnt = on + "/" + mods.size();
            Draw.text(ctx, tr, cnt, x + W - 10 - tr.getWidth(cnt), y + 9, on > 0 ? Theme.accent() : Theme.DIM, false);
            Draw.hgrad(ctx, x + 8, y + HEAD - 2, W - 16, 1, Theme.accent(), Theme.accent2());
            Draw.rect(ctx, x + 8, y + HEAD - 1, W - 16, 1, Theme.accentA(0x30));

            if (live) hits.add(new Hit(x, y, W, HEAD, (b, hx, hy) -> {
                if (b == 0) { dragPanel = c; dragOx = hx - x; dragOy = hy - y; }
                else if (b == 1) st[2] ^= 1;
            }));

            if (visBody > 0) {
                int bodyTop = y + HEAD;
                int bodyEnd = bodyTop + visBody;
                ctx.enableScissor(x, bodyTop, x + W, bodyEnd);
                int cy = bodyTop;
                for (Module m : mods) {
                    int rx = x + PAD, rw = W - 2 * PAD, ry = cy + 1, rh = ROW - 2;
                    boolean hov = live && in(mx, my, rx, cy, rw, ROW) && my < bodyEnd;
                    if (hov) hovered = m;
                    float hv = anim(m, "hov", hov ? 1f : 0f, 18f);
                    float en = anim(m, "en", m.isEnabled() ? 1f : 0f, 14f);

                    if (hv > 0.01f) Draw.round(ctx, rx, ry, rw, rh, 4, Draw.scaleA(Theme.ROW_HOVER, hv));
                    if (en > 0.01f) {
                        int fw = Math.max(8, Math.round(rw * en));
                        Draw.round(ctx, rx, ry, fw, rh, 4, Draw.scaleA(Theme.accentA(0x55), en));
                        Draw.round(ctx, rx, ry + 3, 2, rh - 6, 1, Draw.scaleA(Theme.accent(), en));
                    }
                    int tc = Draw.lerp(Theme.DIM, Theme.TEXT, Math.max(en, hv * 0.7f));
                    Draw.text(ctx, tr, m.name, rx + 8 + Math.round(2 * en), cy + 5, tc, false);
                    if (!m.settings.isEmpty()) {
                        int dc = m.expanded ? Theme.accent() : Theme.DIM;
                        for (int i = 0; i < 3; i++) Draw.rect(ctx, rx + rw - 14 + i * 4, cy + 8, 2, 2, dc);
                    }
                    String kl = bindingModule == m ? "[...]" : (m.key >= 0 ? "[" + keyName(m.key) + "]" : null);
                    if (kl != null)
                        Draw.text(ctx, tr, kl, rx + rw - (m.settings.isEmpty() ? 6 : 18) - tr.getWidth(kl), cy + 5,
                                bindingModule == m ? Theme.accent() : Theme.DIM, false);
                    if (live && cy + ROW <= bodyEnd)
                        hits.add(new Hit(rx, cy, rw, ROW, (b, hx, hy) -> {
                            if (b == 0) m.toggle();
                            else if (b == 1) m.expanded = !m.expanded;
                            else if (b == 2) bindingModule = m;
                        }));
                    cy += ROW;

                    float ea = peek(m, "exp", 0f);
                    int sh = Math.round(settingsH(m) * ea);
                    if (sh > 0) {
                        ctx.enableScissor(x, cy, x + W, cy + sh);
                        Draw.round(ctx, x + PAD, cy, W - 2 * PAD, sh, 4, 0x70000000);
                        int sy = cy;
                        for (Setting<?> s : m.settings) {
                            if (!s.visible()) continue;
                            boolean act = live && ea > 0.98f && sy + rowH(s) <= bodyEnd;
                            sy = drawSetting(ctx, tr, s, x + PAD + 4, sy, W - 2 * PAD - 8, mx, my, act);
                        }
                        ctx.disableScissor();
                        cy += sh;
                    }
                }
                ctx.disableScissor();
            }
        }
        contentBottom = bottom;

        Draw.alpha = master * clamp01(tOpen / 0.4f);
        String hint = "LMB toggle    RMB settings    MMB bind key    drag headers    scroll";
        Draw.text(ctx, tr, hint, (width - tr.getWidth(hint)) / 2, height - 14, Theme.DIM, false);
        if (hovered != null && !closing) ctx.drawTooltip(tr, Text.literal(hovered.description), mx, my);
        Draw.alpha = 1f;
    }

    private int drawSetting(DrawContext ctx, TextRenderer tr, Setting<?> s, int x, int y, int w, int mx, int my, boolean act) {
        int h = rowH(s);
        boolean hov = act && in(mx, my, x, y, w, h);
        int labelCol = hov ? Theme.TEXT : Theme.SOFT;

        if (s instanceof BoolSetting b) {
            float t = anim(b, "t", b.get() ? 1f : 0f, 20f);
            Draw.text(ctx, tr, b.name, x + 2, y + 4, labelCol, false);
            int px = x + w - 18, py = y + 4;
            Draw.round(ctx, px, py, 16, 8, 4, Draw.lerp(Theme.TRACK, Theme.accent(), t));
            Draw.round(ctx, px + 1 + Math.round(8 * t), py + 1, 6, 6, 3, Theme.KNOB);
            if (act) hits.add(new Hit(x, y, w, h, (btn, hx, hy) -> b.toggle()));
        } else if (s instanceof ModeSetting m) {
            Draw.text(ctx, tr, m.name, x + 2, y + 4, labelCol, false);
            String v = m.get();
            int vw = tr.getWidth(v);
            Draw.round(ctx, x + w - vw - 10, y + 2, vw + 8, 12, 5, Theme.accentA(0x30));
            Draw.text(ctx, tr, v, x + w - vw - 6, y + 4, Theme.accent(), false);
            if (act) hits.add(new Hit(x, y, w, h, (btn, hx, hy) -> m.cycle(btn == 1 ? -1 : 1)));
        } else if (s instanceof BlockListSetting bl) {
            int cy = y + 3;
            boolean focused = searchFocus == bl;
            Draw.round(ctx, x, cy, w, 14, 4, focused ? 0xFF1F1F2A : 0xFF16161D);
            if (focused) Draw.rect(ctx, x + 3, cy + 13, w - 6, 1, Theme.accent());
            boolean empty = bl.query.isEmpty();
            String shown = empty && !focused ? "Search blocks..."
                    : bl.query + ((focused && (System.currentTimeMillis() / 450) % 2 == 0) ? "_" : "");
            Draw.text(ctx, tr, tr.trimToWidth(shown, w - 10), x + 5, cy + 3, empty ? Theme.DIM : Theme.TEXT, false);
            if (act) hits.add(new Hit(x, cy, w, 14, (btn, hx, hy) -> searchFocus = bl));
            cy += 16;

            List<String> res = bl.results();
            int n = Math.min(res.size(), BlockListSetting.MAX_RESULTS);
            for (int i = 0; i < n; i++) {
                final String id = res.get(i);
                boolean isSel = bl.has(id);
                if (act && in(mx, my, x, cy, w, 12)) Draw.round(ctx, x, cy, w, 12, 3, Theme.ROW_HOVER);
                Draw.text(ctx, tr, tr.trimToWidth((isSel ? "+ " : "  ") + pretty(id), w - 6), x + 4, cy + 2,
                        isSel ? Theme.accent() : Theme.SOFT, false);
                if (act) hits.add(new Hit(x, cy, w, 12, (btn, hx, hy) -> bl.toggle(id)));
                cy += 12;
            }
            cy += 2;
            int bw = (w - 4) / 2;
            chip(ctx, tr, "All ores", x, cy, bw, act, mx, my, bl::addAllOres);
            chip(ctx, tr, "Clear", x + bw + 4, cy, bw, act, mx, my, bl::clear);
            cy += 18;

            List<String> selected = new ArrayList<>(bl.get());
            int shownN = Math.min(selected.size(), BlockListSetting.MAX_SHOWN);
            for (int i = 0; i < shownN; i++) {
                final String id = selected.get(i);
                boolean hv = act && in(mx, my, x, cy, w, 11);
                Draw.text(ctx, tr, tr.trimToWidth(pretty(id), w - 16), x + 4, cy + 1, hv ? Theme.TEXT : Theme.accent(), false);
                Draw.text(ctx, tr, "x", x + w - 9, cy + 1, hv ? 0xFFFF5555 : Theme.DIM, false);
                if (act) hits.add(new Hit(x, cy, w, 11, (btn, hx, hy) -> bl.toggle(id)));
                cy += 11;
            }
            if (selected.size() > shownN)
                Draw.text(ctx, tr, "+" + (selected.size() - shownN) + " more", x + 4, cy + 1, Theme.DIM, false);
        } else if (s instanceof Slidable sl) {
            float f = anim(s, "f", (float) sl.fraction(), dragSlider == sl ? 60f : 22f);
            Draw.text(ctx, tr, s.name, x + 2, y + 3, labelCol, false);
            String v = s.display();
            if (!v.isEmpty()) Draw.text(ctx, tr, v, x + w - 2 - tr.getWidth(v), y + 3, Theme.DIM, false);
            final int tx = x + 2, tw = w - 4, ty = y + h - 8;
            if (s instanceof ColorSetting) {
                for (int i = 0; i < tw; i += 2)
                    Draw.rect(ctx, tx + i, ty, Math.min(2, tw - i), 4, 0xFF000000 | MathHelper.hsvToRgb(i / (float) tw, 0.75f, 1f));
                Draw.round(ctx, tx + Math.round(f * (tw - 3)) - 1, ty - 2, 5, 8, 2, 0xFFFFFFFF);
            } else {
                Draw.round(ctx, tx, ty, tw, 4, 2, Theme.TRACK);
                int fw = Math.max(4, Math.round(tw * f));
                Draw.round(ctx, tx, ty, fw, 4, 2, Theme.accent());
                Draw.round(ctx, tx + fw - 3, ty - 1, 6, 6, 3, Theme.KNOB);
            }
            if (act) hits.add(new Hit(x, y, w, h, (btn, hx, hy) -> {
                if (btn != 0) return;
                dragSlider = sl; dragX = tx; dragW = tw;
                sl.setFraction((hx - tx) / (double) tw);
            }));
        }
        return y + h;
    }

    // ---------------- input ----------------
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (closing) return true;
        searchFocus = null;
        bindingModule = null;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (in((int) mx, (int) my, h.x, h.y, h.w, h.h)) { h.click.on(button, (int) mx, (int) my); return true; }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragPanel = null; dragSlider = null;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragPanel != null) {
            int[] st = STATE.get(dragPanel);
            st[0] = MathHelper.clamp((int) mx - dragOx, 0, Math.max(0, width - W));
            st[1] = (int) my - dragOy - Math.round(scroll);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        scrollTarget += (float) vAmount * 24f;
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (searchFocus != null) {
            if (chr == ' ') chr = '_';
            if (Character.isLetterOrDigit(chr) || chr == '_' || chr == ':')
                searchFocus.setQuery(searchFocus.query + Character.toLowerCase(chr));
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (bindingModule != null) {
            if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) return true; // reserved for the GUI
            if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) bindingModule.key = -1;
            else if (key != GLFW.GLFW_KEY_ESCAPE) bindingModule.key = key;
            bindingModule = null;
            return true;
        }
        if (searchFocus != null) {
            if (key == GLFW.GLFW_KEY_BACKSPACE && !searchFocus.query.isEmpty())
                searchFocus.setQuery(searchFocus.query.substring(0, searchFocus.query.length() - 1));
            else if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)
                searchFocus = null;
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) { close(); return true; }
        return super.keyPressed(key, scan, mods);
    }
}
