package net.killstreakcod.render.tablet;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.render.NativeSceneUi;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipInputStream;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * CPU screen drawing, including Minecraft's Unicode glyphs; never changes a shader framebuffer.
 * 在 CPU 上绘制屏幕内容，包括 Minecraft 的 Unicode 字形，不改动光影帧缓冲。
 */
public final class TabletCanvas {
    public static final class Box {
        final int x, y, w, h;

        public Box(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        boolean contains(double px, double py) {
            return px >= x && py >= y && px < x + w && py < y + h;
        }
    }

    private final NativeImage target;
    private Box clip;
    private static final Map<Integer, Glyph> GLYPHS = new HashMap<>();
    private static boolean fontLoaded;

    private record Glyph(int[] rows, int width, int left, int right) {
        int advance() {
            return Math.max(5, right - left + 2);
        }
    }

    public TabletCanvas(NativeImage image) {
        target = image;
        clip = new Box(0, 0, image.getWidth(), image.getHeight());
    }

    void clip(Box box) {
        clip = new Box(Math.max(0, box.x), Math.max(0, box.y),
                Math.max(0, Math.min(target.getWidth(), box.x + box.w) - Math.max(0, box.x)),
                Math.max(0, Math.min(target.getHeight(), box.y + box.h) - Math.max(0, box.y)));
    }

    void unclip() {
        clip = new Box(0, 0, target.getWidth(), target.getHeight());
    }

    void pixel(int x, int y, int argb) {
        if (!clip.contains(x, y))
            return;
        int a = argb >>> 24;
        if (a == 0)
            return;
        int rgba = 0xff000000 | ((argb & 255) << 16) | (argb & 0xff00) | ((argb >>> 16) & 255);
        if (a == 255) {
            target.setPixelRGBA(x, y, rgba);
            return;
        }
        int old = target.getPixelRGBA(x, y), out = 0xff000000;
        for (int shift = 0; shift <= 16; shift += 8)
            out |= ((((rgba >>> shift) & 255) * a + ((old >>> shift) & 255) * (255 - a) + 127) / 255) << shift;
        target.setPixelRGBA(x, y, out);
    }

    public void fill(Box b, int color) {
        for (int y = Math.max(clip.y, b.y); y < Math.min(clip.y + clip.h, b.y + b.h); y++)
            for (int x = Math.max(clip.x, b.x); x < Math.min(clip.x + clip.w, b.x + b.w); x++)
                pixel(x, y, color);
    }

    void line(int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1, dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1,
                error = dx + dy;
        for (int n = 0; n < 4096; n++) {
            pixel(x0, y0, color);
            if (x0 == x1 && y0 == y1)
                break;
            int e = 2 * error;
            if (e >= dy) {
                error += dy;
                x0 += sx;
            }
            if (e <= dx) {
                error += dx;
                y0 += sy;
            }
        }
    }

    void outline(Box b, int color) {
        line(b.x, b.y, b.x + b.w - 1, b.y, color);
        line(b.x, b.y + b.h - 1, b.x + b.w - 1, b.y + b.h - 1, color);
        line(b.x, b.y, b.x, b.y + b.h - 1, color);
        line(b.x + b.w - 1, b.y, b.x + b.w - 1, b.y + b.h - 1, color);
    }

    void ring(int x, int y, int radius, int color) {
        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI / 24, b = (i + 1) * Math.PI / 24;
            line(x + (int) (Math.cos(a) * radius), y + (int) (Math.sin(a) * radius), x + (int) (Math.cos(b) * radius),
                    y + (int) (Math.sin(b) * radius), color);
        }
    }

    void arrow(int x, int y, float yaw, int size, int color) {
        double a = Math.toRadians(yaw);
        int dx = (int) (-Math.sin(a) * size), dy = (int) (Math.cos(a) * size);
        line(x + dx, y + dy, x - dx / 2 + dy / 2, y - dy / 2 - dx / 2, color);
        line(x + dx, y + dy, x - dx / 2 - dy / 2, y - dy / 2 + dx / 2, color);
        line(x - dx / 2 + dy / 2, y - dy / 2 - dx / 2, x - dx / 2 - dy / 2, y - dy / 2 + dx / 2, color);
    }

    public void image(NativeImage source, NativeSceneUi.Rect r, float alpha, float degrees, float scale, int frame) {
        if (source == null || alpha <= 0 || r.w() <= 0 || r.h() <= 0)
            return;
        double angle = Math.toRadians(degrees), c = Math.cos(angle), s = Math.sin(angle);
        float w = r.w() * scale, h = r.h() * scale, cx = r.x() + r.w() / 2, cy = r.y() + r.h() / 2;
        double ex = (Math.abs(c) * w + Math.abs(s) * h) / 2, ey = (Math.abs(s) * w + Math.abs(c) * h) / 2;
        int columns = frame < 0 ? 1 : 5, rows = frame < 0 ? 1 : 4, col = frame < 0 ? 0 : frame % 5,
                row = frame < 0 ? 0 : frame / 5;
        for (int y = Math.max(clip.y, (int) Math.floor(cy - ey)); y < Math.min(clip.y + clip.h,
                (int) Math.ceil(cy + ey)); y++)
            for (int x = Math.max(clip.x, (int) Math.floor(cx - ex)); x < Math.min(clip.x + clip.w,
                    (int) Math.ceil(cx + ex)); x++) {
                double dx = x + .5 - cx, dy = y + .5 - cy, u = (dx * c + dy * s) / w + .5,
                        v = (-dx * s + dy * c) / h + .5;
                if (u < 0 || v < 0 || u >= 1 || v >= 1)
                    continue;
                int color = source.getPixelRGBA(
                        Math.min(source.getWidth() - 1, (int) ((col + u) * source.getWidth() / columns)),
                        Math.min(source.getHeight() - 1, (int) ((row + v) * source.getHeight() / rows)));
                int a = Mth.clamp(Math.round((color >>> 24) * alpha), 0, 255);
                pixel(x, y, a << 24 | ((color & 255) << 16) | (color & 0xff00) | ((color >>> 16) & 255));
            }
    }

    public void text(Component text, Box box, int size, int color, float alpha, boolean centered) {
        text(text.getString(), box, size, color, alpha, centered);
    }

    public void text(String text, Box box, int size, int color, float alpha, boolean centered) {
        if (text.isEmpty() || alpha <= 0 || box.w <= 0 || box.h <= 0)
            return;
        loadFont();
        int[] codes = text.codePoints().toArray();
        float scale = Math.min(size, box.h) / 16F, width = 0;
        for (int cp : codes)
            width += glyph(cp).advance() * scale;
        if (width > box.w)
            scale *= box.w / width;
        width = 0;
        for (int cp : codes)
            width += glyph(cp).advance() * scale;
        if (scale <= 0)
            return;
        float x = box.x + (centered ? (box.w - width) / 2 : 0);
        int top = box.y + Math.max(0, (box.h - Math.round(16 * scale)) / 2),
                a = Mth.clamp(Math.round((color >>> 24) * alpha), 0, 255);
        for (int cp : codes) {
            Glyph glyph = glyph(cp);
            int gw = Math.max(0, glyph.right - glyph.left + 1), dw = Math.round(gw * scale),
                    dh = Math.round(16 * scale);
            for (int y = 0; y < dh; y++)
                for (int xx = 0; xx < dw; xx++) {
                    int sy = Math.min(15, (int) (y / scale)), sx = glyph.left + Math.min(gw - 1, (int) (xx / scale));
                    if ((glyph.rows[sy] & (1 << (glyph.width - 1 - sx))) != 0)
                        pixel(Math.round(x) + xx, top + y, a << 24 | (color & 0xffffff));
                }
            x += glyph.advance() * scale;
        }
    }

    private static Glyph glyph(int cp) {
        return GLYPHS.getOrDefault(cp, GLYPHS.get(63));
    }

    private static void loadFont() {
        if (fontLoaded)
            return;
        fontLoaded = true;
        Set<Integer> wanted = new HashSet<>();
        for (int i = 32; i < 127; i++)
            wanted.add(i);
        "°↑−‹›×…⌂".codePoints().forEach(wanted::add);
        var manager = Minecraft.getInstance().getResourceManager();
        Set<String> locales = new HashSet<>(
                List.of("zh_cn", "en_us", Minecraft.getInstance().getLanguageManager().getSelected()));
        for (String locale : locales)
            try (var reader = manager.openAsReader(
                    ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "lang/" + locale + ".json"))) {
                for (var e : JsonParser.parseReader(reader).getAsJsonObject().entrySet())
                    e.getValue().getAsString().codePoints().forEach(wanted::add);
            }
            catch (Exception ignored) {
            }
        GLYPHS.put(32, new Glyph(new int[16], 8, 0, 3));
        GLYPHS.put(63, new Glyph(new int[] { 0, 0, 0, 60, 66, 2, 4, 8, 16, 16, 0, 16, 16, 0, 0, 0 }, 8, 1, 6));
        try (var zip = new ZipInputStream(manager.open(ResourceLocation.parse("font/unifont.zip")))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.getName().endsWith(".hex"))
                    continue;
                BufferedReader reader = new BufferedReader(new InputStreamReader(zip, StandardCharsets.US_ASCII));
                String line;
                while ((line = reader.readLine()) != null) {
                    int colon = line.indexOf(':');
                    if (colon < 4 || colon > 6)
                        continue;
                    int cp = Integer.parseInt(line.substring(0, colon), 16);
                    if (!wanted.contains(cp))
                        continue;
                    String bits = line.substring(colon + 1).strip();
                    if (bits.length() % 16 != 0)
                        continue;
                    int digits = bits.length() / 16, width = digits * 4;
                    if (width < 8 || width > 32)
                        continue;
                    int[] rows = new int[16];
                    int left = width, right = -1;
                    for (int y = 0; y < 16; y++) {
                        rows[y] = (int) Long.parseLong(bits.substring(y * digits, (y + 1) * digits), 16);
                        for (int x = 0; x < width; x++)
                            if ((rows[y] & (1 << (width - 1 - x))) != 0) {
                                left = Math.min(left, x);
                                right = Math.max(right, x);
                            }
                    }
                    GLYPHS.put(cp, new Glyph(rows, width, right < 0 ? 0 : left, right < 0 ? 3 : right));
                }
            }
        }
        catch (Exception failure) {
            com.mojang.logging.LogUtils.getLogger().warn("Tablet Unicode glyphs unavailable", failure);
        }
    }

    public static void reset() {
        GLYPHS.clear();
        fontLoaded = false;
    }
}
