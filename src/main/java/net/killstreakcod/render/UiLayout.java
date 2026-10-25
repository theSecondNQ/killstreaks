package net.killstreakcod.render;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** 布局模块统一处理归一化位置、缩放、透明度、置顶绘制和编辑草稿，不注入其他模组的 GUI。 */
public final class UiLayout {
    public record Style(float x, float y, float width, float height, float opacity, boolean top) {
        public static final Style DEFAULT = new Style(0, 0, 1, 1, 1, false);

        public Style {
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(width) || !Float.isFinite(height)
                    || !Float.isFinite(opacity) || Math.abs(x) > 2 || Math.abs(y) > 2 || width < .1F || width > 8
                    || height < .1F || height > 8 || opacity < 0 || opacity > 1)
                throw new IllegalArgumentException("Invalid UI layout");
        }
    }

    public record Box(float x, float y, float width, float height) {
        public boolean contains(double px, double py) {
            return px >= x && py >= y && px <= x + width && py <= y + height;
        }
    }

    public record Element(String key, String context, Box original, Box displayed) {
    }

    private record Deferred(GuiGraphics graphics, Matrix4f pose, float[] color, float opacity, int[] clip,
            Runnable draw) {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, Element> CATALOG = new LinkedHashMap<>();
    private static final List<Deferred> TOP = new ArrayList<>();

    private static Map<String, Style> saved = new LinkedHashMap<>(), draft;
    private static String context;
    private static float opacity = 1;
    private static boolean top, loadingDone, replaying, suppressChildren;

    private UiLayout() {}

    private static Path path() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config/killstreakcod-ui.json");
    }

    private static void load() {
        if (loadingDone)
            return;
        loadingDone = true;
        if (!Files.isRegularFile(path()))
            return;
        try (var reader = Files.newBufferedReader(path(), StandardCharsets.UTF_8)) {
            var document = JsonParser.parseReader(reader).getAsJsonObject();
            if ((document.get("version").getAsInt() != 1 && document.get("version").getAsInt() != 2))
                throw new IllegalArgumentException("Unsupported UI profile version");
            var entries = document.getAsJsonObject("elements");
            if (entries.size() > 12000)
                throw new IllegalArgumentException("Too many UI elements");
            for (var entry : entries.entrySet()) {
                if (entry.getKey().length() > 512 || !entry.getKey().contains("/group/")
                        || !configurable(entry.getKey().substring(0, entry.getKey().indexOf("/group/"))))
                    continue;
                try {
                    var s = entry.getValue().getAsJsonObject();
                    saved.put(entry.getKey(),
                            new Style(s.get("x").getAsFloat(), s.get("y").getAsFloat(), s.get("width").getAsFloat(),
                                    s.get("height").getAsFloat(), s.get("opacity").getAsFloat(),
                                    s.get("top").getAsBoolean()));
                }
                catch (RuntimeException malformed) {
                    com.mojang.logging.LogUtils.getLogger().warn("Ignoring invalid UI element {}", entry.getKey());
                }
            }
        }
        catch (Exception failure) {
            saved.clear();
            com.mojang.logging.LogUtils.getLogger().warn("Cannot read killstreak UI layout", failure);
        }
    }

    public static Scope frame(String name) {
        load();
        CATALOG.entrySet().removeIf(entry -> entry.getValue().context().equals(name));
        String previous = context;
        context = name;
        return () -> context = previous;
    }

    public static Scope screen(net.minecraft.client.gui.screens.Screen screen) {
        String name = screenName(screen);
        return frame(name);
    }

    public static String screenName(net.minecraft.client.gui.screens.Screen screen) {
        return "screen." + screen.getClass().getSimpleName()
                + (screen instanceof TerminalSettingsScreen settings ? "." + settings.layoutPage() : "");
    }

    public static Element element(String key) {
        return CATALOG.get(key);
    }

    public static boolean buttonCovered(String key, double x, double y) {
        Element current = CATALOG.get(key);
        if (current == null)
            return false;
        if (style(key).opacity() == 0)
            return true;
        boolean after = false;
        for (Element element : CATALOG.values()) {
            if (element.key().equals(key)) {
                after = true;
                continue;
            }
            if (element.context().equals(current.context()) && element.key().contains("/button/")
                    && style(element.key()).top() && style(element.key()).opacity() > 0 && (!style(key).top() || after)
                    && element.displayed().contains(x, y))
                return true;
        }
        return false;
    }

    public static Scope tag(String name) {
        return () -> {
        };
    }

    public static Style style(String key) {
        load();
        return key != null && key.contains("/group/") && configurable(key.substring(0, key.indexOf("/group/")))
                ? (draft == null ? saved : draft).getOrDefault(key, Style.DEFAULT)
                : Style.DEFAULT;
    }

    static Box project(GuiGraphics graphics, Box box) {
        var matrix = graphics.pose().last().pose();
        float minX = Float.POSITIVE_INFINITY, minY = minX, maxX = Float.NEGATIVE_INFINITY, maxY = maxX;
        for (int i = 0; i < 4; i++) {
            Vector3f p = matrix.transformPosition(box.x() + ((i & 1) == 0 ? 0 : box.width()),
                    box.y() + ((i & 2) == 0 ? 0 : box.height()), 0, new Vector3f());
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            maxX = Math.max(maxX, p.x);
            maxY = Math.max(maxY, p.y);
        }
        return new Box(minX, minY, Math.max(1, maxX - minX), Math.max(1, maxY - minY));
    }

    public static Box resolve(Box box, Style s, int width, int height) {
        if (s.equals(Style.DEFAULT))
            return box;
        float w = box.width() * s.width(), h = box.height() * s.height();
        float cx = box.x() + box.width() / 2 + s.x() * width;
        float cy = box.y() + box.height() / 2 + s.y() * height;
        // 至少留出可抓取的一角，防止缩放或改变窗口大小后把控件永久移出屏幕。
        float x = Math.max(8 - w, Math.min(width - 8, cx - w / 2));
        float y = Math.max(8 - h, Math.min(height - 8, cy - h / 2));
        return new Box(x, y, w, h);
    }

    public static boolean configurable(String context) {
        if (context == null)
            return false;
        return switch (context) {
            case "hud.normal", "hud.bomb_drone", "hud.cruise", "hud.wheelson", "hud.chopper", "hud.gunship",
                    "hud.earned", "hud.nuke", "hud.scope", "hud.radio" ->
                true;
            default -> false;
        };
    }

    public static net.minecraft.network.chat.Component label(Element element) {
        String id = element.key().substring(element.key().lastIndexOf("/group/") + 7);
        return net.minecraft.network.chat.Component.translatable(
                id.startsWith("control.") ? id.substring(8) : "screen.killstreakcod.layout.element." + id);
    }

    public static Scope group(GuiGraphics graphics, String id, Box box) {
        return configurable(context) && id != null && !id.isEmpty() ? element(graphics, "group/" + id, box, false)
                : () -> {
                };
    }

    public static Scope group(GuiGraphics graphics, String id, NativeSceneUi.Rect box) {
        return box == null ? () -> {
        } : group(graphics, id, new Box(box.x(), box.y(), box.w(), box.h()));
    }

    public static Scope element(GuiGraphics graphics, String id, Box local, boolean button) {
        if (!configurable(context) || !id.startsWith("group/") || replaying || suppressChildren)
            return () -> {
            };
        String name = context + "/" + id;
        Style s = style(name);
        if (!button && !editing() && s.equals(Style.DEFAULT))
            return () -> {
            };
        Box original = project(graphics, local);
        Box box = resolve(original, s, graphics.guiWidth(), graphics.guiHeight());
        CATALOG.put(name, new Element(name, context, original, box));
        float previousOpacity = opacity;
        boolean previousTop = top, previousSuppress = suppressChildren;
        opacity *= s.opacity();
        top |= s.top();
        suppressChildren = true;
        int[] previousClip = null;
        if ((!original.equals(box)) && org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST)) {
            previousClip = new int[4];
            org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_SCISSOR_BOX, previousClip);
            var window = Minecraft.getInstance().getWindow();
            double guiScale = window.getGuiScale();
            double sx = box.width() / original.width(), sy = box.height() / original.height();
            int left = (int) Math.floor((box.x() + (previousClip[0] / guiScale - original.x()) * sx) * guiScale);
            int upper = (int) Math.floor((box.y()
                    + ((window.getHeight() - previousClip[1] - previousClip[3]) / guiScale - original.y()) * sy)
                    * guiScale);
            int clipWidth = Math.max(0, (int) Math.ceil(previousClip[2] * sx));
            int clipHeight = Math.max(0, (int) Math.ceil(previousClip[3] * sy));
            graphics.flush();
            RenderSystem.enableScissor(left, window.getHeight() - upper - clipHeight, clipWidth, clipHeight);
        }
        int[] restoreClip = previousClip;
        graphics.pose().pushPose();
        if (!s.equals(Style.DEFAULT)) {
            var transform = new Matrix4f().translate(box.x(), box.y(), s.top() ? 500 : 0)
                    .scale(box.width() / original.width(), box.height() / original.height(), 1)
                    .translate(-original.x(), -original.y(), 0).mul(graphics.pose().last().pose());
            graphics.pose().last().pose().set(transform);
        }
        return () -> {
            if (restoreClip != null) {
                graphics.flush();
                RenderSystem.enableScissor(restoreClip[0], restoreClip[1], restoreClip[2], restoreClip[3]);
            }
            graphics.pose().popPose();
            opacity = previousOpacity;
            top = previousTop;
            suppressChildren = previousSuppress;
        };
    }

    static void draw(GuiGraphics graphics, String id, Box box, Runnable draw) {
        if (context == null || !editing() && saved.isEmpty()) {
            draw.run();
            return;
        }
        {
            if (opacity <= 0)
                return;
            if (top && !replaying) {
                int[] clip = null;
                if (org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST)) {
                    clip = new int[4];
                    org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_SCISSOR_BOX, clip);
                }
                TOP.add(new Deferred(graphics, new Matrix4f(graphics.pose().last().pose()),
                        RenderSystem.getShaderColor().clone(), opacity, clip, draw));
            } else {
                draw.run();
            }
        }
    }

    /** 顶层命令保存最终矩阵及颜色，在本模组覆盖层末尾重放，不改变其他模组的渲染状态。 */
    public static void flush(GuiGraphics graphics) {
        if (TOP.isEmpty())
            return;
        graphics.flush();
        replaying = true;
        float[] oldColor = RenderSystem.getShaderColor().clone();
        float oldOpacity = opacity;
        boolean blending = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
        int sr = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_RGB);
        int dr = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_RGB);
        int sa = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_ALPHA);
        int da = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_ALPHA);
        boolean clipped = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
        int[] previousClip = new int[4];
        org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_SCISSOR_BOX, previousClip);
        try {
            for (Deferred item : List.copyOf(TOP)) {
                GuiGraphics owner = item.graphics();
                owner.pose().pushPose();
                try {
                    owner.pose().last().pose().set(item.pose());
                    owner.setColor(item.color()[0], item.color()[1], item.color()[2], item.color()[3]);
                    opacity = item.opacity();
                    if (item.clip() == null)
                        RenderSystem.disableScissor();
                    else
                        RenderSystem.enableScissor(item.clip()[0], item.clip()[1], item.clip()[2], item.clip()[3]);
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                    item.draw().run();
                    owner.flush();
                } finally {
                    owner.pose().popPose();
                }
            }
        } finally {
            TOP.clear();
            replaying = false;
            opacity = oldOpacity;
            graphics.setColor(oldColor[0], oldColor[1], oldColor[2], oldColor[3]);
            RenderSystem.blendFuncSeparate(sr, dr, sa, da);
            if (clipped)
                RenderSystem.enableScissor(previousClip[0], previousClip[1], previousClip[2], previousClip[3]);
            else
                RenderSystem.disableScissor();
            if (!blending)
                RenderSystem.disableBlend();
        }
    }

    static int color(int color) {
        int alpha = color >>> 24;
        // 字体绘制把缺省 alpha=0 的 RGB 视为不透明；本模组显式传 ARGB，不扩大透明颜色。
        return color & 0xffffff | Math.round(alpha * opacity) << 24;
    }

    static float opacity() {
        return opacity;
    }

    public static String context() {
        return context;
    }

    public static List<Element> elements(String name) {
        return CATALOG.values().stream().filter(e -> e.context().equals(name)).toList();
    }

    public static void beginEdit() {
        load();
        draft = new LinkedHashMap<>(saved);
    }

    public static boolean editing() {
        return draft != null;
    }

    public static void change(String key, Style value) {
        if (draft != null) {
            if (value.equals(Style.DEFAULT))
                draft.remove(key);
            else
                draft.put(key, value);
        }
    }

    public static void resetContext(String name) {
        if (draft != null)
            draft.keySet().removeIf(key -> key.startsWith(name + "/"));
    }

    public static void cancel() {
        draft = null;
    }

    public static boolean save() {
        if (draft == null)
            return true;
        try {
            Files.createDirectories(path().getParent());
            if (Files.exists(path()))
                Files.copy(path(), path().resolveSibling("killstreakcod-ui.json.bak"),
                        StandardCopyOption.REPLACE_EXISTING);
            Path temporary = path().resolveSibling("killstreakcod-ui.json.tmp");
            Files.writeString(temporary, GSON.toJson(Map.of("version", 2, "elements", draft)), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (java.nio.file.AtomicMoveNotSupportedException unavailable) {
                Files.move(temporary, path(), StandardCopyOption.REPLACE_EXISTING);
            }
            saved = new LinkedHashMap<>(draft);
            draft = null;
            return true;
        }
        catch (Exception failure) {
            com.mojang.logging.LogUtils.getLogger().error("Cannot save killstreak UI layout", failure);
            return false;
        }
    }

    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
