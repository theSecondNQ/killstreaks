package net.killstreakcod.render.compat;

import net.killstreakcod.render.RemoteHudLayout;
import net.killstreakcod.render.StreakPresentation;
import net.killstreakcod.render.TacticalClient;
import net.killstreakcod.render.tablet.TabletTargeting;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.neoforged.fml.ModList;

/**
 * Keep GD656's own configured icons, bonuses and feed visible in our replacement remote HUD.
 * 在替换后的遥控 HUD 中保留 GD656 自己配置的图标、奖励加成和击杀信息。
 */
public final class OptionalClientIntegrations {
    private static boolean resolved;
    private static Method gdOverlay;
    private static boolean gdOverlayHasGate;
    private static Method gdPreset, gdElements, gdConfig;
    private static List<RemoteHudLayout.Box> gdAreas = List.of();
    private static long layoutAt = Long.MIN_VALUE;
    private static int layoutWidth, layoutHeight;

    private OptionalClientIntegrations() {}

    public static boolean controlsGun() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.player != null
                && (TacticalClient.remote() || StreakPresentation.busy() || TabletTargeting.active());
    }

    public static void remoteOverlay(GuiGraphics graphics, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null && !(mc.screen instanceof ChatScreen) || mc.options.hideGui)
            return;
        resolve();
        if (gdOverlay != null) {
            graphics.flush();
            graphics.pose().pushPose();
            try {
                if (gdOverlayHasGate)
                    gdOverlay.invoke(null, graphics, partial, true);
                else
                    gdOverlay.invoke(null, graphics, partial);
            }
            catch (ReflectiveOperationException | LinkageError failure) {
                gdOverlay = null;
                LogUtils.getLogger().error("GD656 remote overlay disabled after render failure", failure);
            } finally {
                graphics.flush();
                graphics.pose().popPose();
            }
        }
    }

    private static void resolve() {
        if (!resolved) {
            resolved = true;
            if (ModList.get().isLoaded("gd656killicon"))
                try {
                    Class<?> manager = Class.forName("org.mods.gd656killicon.client.render.HudElementManager");
                    try {
                        gdOverlay = manager.getMethod("renderOverlay", GuiGraphics.class, float.class);
                    }
                    catch (NoSuchMethodException newerApi) {
                        // GD656 1.3 使用三参数入口，最后一项表示原生 player_list 叠层已到达。
                        // 遥控 HUD 替代了该叠层，在这里明确提供一次绘制许可。
                        gdOverlay = manager.getMethod("onRenderGuiOverlay", GuiGraphics.class, float.class,
                                boolean.class);
                        gdOverlayHasGate = true;
                    }
                    LogUtils.getLogger().info("Killstreaks: GD656 remote HUD bridge enabled");
                }
                catch (ReflectiveOperationException | LinkageError failure) {
                    LogUtils.getLogger().error("Could not initialize optional GD656 overlay", failure);
                }
            if (gdOverlay != null)
                try {
                    gdPreset = Class.forName("org.mods.gd656killicon.client.config.ConfigManager")
                            .getMethod("getCurrentPresetId");
                    var config = Class.forName("org.mods.gd656killicon.client.config.ElementConfigManager");
                    gdElements = config.getMethod("getElementIds", String.class);
                    try {
                        gdConfig = config.getMethod("getElementConfigWithFallback", String.class, String.class);
                    }
                    catch (NoSuchMethodException newerApi) {
                        gdConfig = config.getMethod("getElementConfig", String.class, String.class);
                    }
                }
                catch (ReflectiveOperationException | LinkageError failure) {
                    gdPreset = gdElements = gdConfig = null;
                    LogUtils.getLogger().warn(
                            "GD656 layout reservation unavailable; using side-mounted flight instruments", failure);
                }
        }
    }

    /**
     * Read GD656's configured screen areas for the shared subtitle layout.
     * 读取 GD656 配置的屏幕占用区域，供共用字幕布局避让。
     */
    public static List<RemoteHudLayout.Box> gdHudReservations(GuiGraphics gui) {
        resolve();
        if (gdPreset == null)
            return List.of();
        var mc = Minecraft.getInstance();
        long tick = mc.level == null ? 0 : mc.level.getGameTime();
        if (layoutAt != Long.MIN_VALUE && tick >= layoutAt && tick - layoutAt < 20 && layoutWidth == gui.guiWidth()
                && layoutHeight == gui.guiHeight())
            return gdAreas;
        layoutAt = tick;
        layoutWidth = gui.guiWidth();
        layoutHeight = gui.guiHeight();
        List<RemoteHudLayout.Box> areas = new ArrayList<>();
        try {
            String preset = (String) gdPreset.invoke(null);
            var elements = (Set<?>) gdElements.invoke(null, preset);
            for (Object element : elements) {
                String key = element.toString();
                Object raw = gdConfig.invoke(null, preset, key);
                if (!(raw instanceof JsonObject cfg) || cfg.has("visible") && !cfg.get("visible").getAsBoolean())
                    continue;
                float scale = value(cfg, "scale", 1), x = layoutWidth / 2F + value(cfg, "x_offset", 0),
                        y = layoutHeight - value(cfg, "y_offset", 80);
                float halfWidth = Math.min(layoutWidth * .4F, Math.max(60, 90 * scale)), above = 12 * scale,
                        below = 12 * scale;
                if (key.contains("bonus") || key.contains("subtitle") && !key.endsWith("score")) {
                    above = Math.max(above, value(cfg, "max_lines", 4) * value(cfg, "line_spacing", 10) * scale);
                }
                if (key.startsWith("kill_icon")) {
                    above = below = Math.max(20, 48 * scale);
                    halfWidth = Math.max(halfWidth, 100);
                }
                areas.add(new RemoteHudLayout.Box(Math.round(x - halfWidth), Math.round(y - above),
                        Math.round(halfWidth * 2), Math.round(above + below)));
            }
        }
        catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            gdPreset = gdElements = gdConfig = null;
            areas.clear();
            LogUtils.getLogger().warn("GD656 layout reservation disabled after read failure", failure);
        }
        return gdAreas = List.copyOf(areas);
    }

    private static float value(JsonObject cfg, String key, float fallback) {
        return cfg.has(key) && cfg.get(key).isJsonPrimitive() ? cfg.get(key).getAsFloat() : fallback;
    }
}
