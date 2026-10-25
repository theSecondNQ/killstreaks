package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/**
 * Verified SpotterScope images, constructor rectangles and native alpha tracks.
 * 使用已核对的 SpotterScope 图像、构造布局矩形及原透明度动画轨迹。
 */
public final class NativePrecisionScopeHud {
    private record Rect(float left, float right, float top, float bottom) {
        float width() {
            return right - left;
        }

        float height() {
            return bottom - top;
        }
    }

    private record Node(Rect rect, float alpha, int font, String align) {
    }

    private record Image(ResourceLocation texture, int width, int height) {
    }

    private record Step(float alpha, float milliseconds, boolean snap) {
    }

    private record Sequence(float duration, boolean loop, Map<String, List<Step>> elements) {
    }

    private static final ResourceLocation INVENTORY = ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID,
            "textures/gui/precision_scope/hud_icon_equipment_spotter_scope.png");
    private static final Map<String, Node> NODES = new HashMap<>(), CHILDREN = new HashMap<>();
    private static final Map<String, Image> IMAGES = new HashMap<>();
    private static final Map<String, Sequence> SEQUENCES = new HashMap<>();
    private static final Map<String, String> TEXT_KEYS = new HashMap<>();
    private static boolean attempted, ready, open, valid, danger, fired, sampling;
    private static ClientLevel world;
    private static LocalPlayer pawn;
    private static Vec3 target;
    private static int distance;
    private static double stateAt, dangerAt, distanceAt = -10000, markedAt = -10000;
    private static float dangerStart, referenceHeight = 1080, distanceRefresh = 200, rayRange = 160;
    private static double dangerRadius = 1280 * .0254;

    private NativePrecisionScopeHud() {}

    public static ResourceLocation inventoryIcon() {
        return INVENTORY;
    }

    private static void loadNodes(JsonObject source, Map<String, Node> result) {
        for (var entry : source.entrySet()) {
            var row = entry.getValue().getAsJsonObject();
            var r = row.getAsJsonArray("rect");
            result.put(entry.getKey(),
                    new Node(
                            new Rect(r.get(0).getAsFloat(), r.get(1).getAsFloat(), r.get(2).getAsFloat(),
                                    r.get(3).getAsFloat()),
                            row.has("alpha") ? row.get("alpha").getAsFloat() : 1,
                            row.has("fontSize") ? row.get("fontSize").getAsInt() : 28,
                            row.has("alignment") ? row.get("alignment").getAsString() : "center"));
        }
    }

    private static boolean load() {
        if (attempted)
            return ready;
        attempted = true;
        var mc = Minecraft.getInstance();
        try (var reader = mc.getResourceManager()
                .getResourceOrThrow(
                        ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "mw/precision-scope.json"))
                .openAsReader()) {
            var data = JsonParser.parseReader(reader).getAsJsonObject();
            loadNodes(data.getAsJsonObject("nodes"), NODES);
            loadNodes(data.getAsJsonObject("childNodes"), CHILDREN);
            for (var entry : data.getAsJsonObject("images").entrySet()) {
                var row = entry.getValue().getAsJsonObject();
                ResourceLocation texture = ResourceLocation.parse(row.get("texture").getAsString());
                if (mc.getResourceManager().getResource(texture).isEmpty())
                    throw new java.io.IOException("Missing scope image " + texture);
                IMAGES.put(entry.getKey(),
                        new Image(texture, row.get("width").getAsInt(), row.get("height").getAsInt()));
            }
            for (var entry : data.getAsJsonObject("sequences").entrySet()) {
                var row = entry.getValue().getAsJsonObject();
                Map<String, List<Step>> elements = new HashMap<>();
                for (var field : row.getAsJsonObject("elements").entrySet()) {
                    List<Step> steps = new ArrayList<>();
                    for (var item : field.getValue().getAsJsonArray()) {
                        var s = item.getAsJsonObject();
                        steps.add(new Step(s.get("value").getAsFloat(), s.get("milliseconds").getAsFloat(),
                                s.get("snap").getAsBoolean()));
                    }
                    elements.put(field.getKey(), List.copyOf(steps));
                }
                SEQUENCES.put(entry.getKey(), new Sequence(row.get("milliseconds").getAsFloat(),
                        row.get("loop").getAsBoolean(), Map.copyOf(elements)));
            }
            data.getAsJsonObject("textKeys").entrySet()
                    .forEach(e -> TEXT_KEYS.put(e.getKey(), e.getValue().getAsString()));
            var adapter = data.getAsJsonObject("adapter");
            referenceHeight = adapter.get("referenceHeight").getAsFloat();
            distanceRefresh = adapter.get("distanceRefreshMs").getAsFloat();
            rayRange = adapter.get("rayRangeBlocks").getAsFloat();
            dangerRadius = adapter.get("dangerNativeUnits").getAsDouble()
                    * adapter.get("nativeUnitsToMeters").getAsDouble() / adapter.get("blockMeters").getAsDouble();
            if (referenceHeight <= 0 || distanceRefresh <= 0 || rayRange <= 0 || rayRange > 256
                    || !Double.isFinite(dangerRadius) || dangerRadius <= 0)
                throw new IllegalArgumentException("Invalid scope adapter");
            ready = NODES.keySet()
                    .containsAll(Set.of("Reticle", "TextBox", "DistanceLabel", "MetersLabel", "CoordBoxX", "CoordBoxY",
                            "CoordBoxZ", "NoTargetText", "DangerCloseText", "MarkingDescription"))
                    && CHILDREN.keySet().containsAll(Set.of("Text", "TextBox", "TextDegreesSymbol"))
                    && SEQUENCES.keySet()
                            .containsAll(Set.of("Onfire", "DangerCloseOn", "DangerCloseOff", "ShowNoTarget",
                                    "HideNoTarget", "ShowDistanceInMetersDisplay", "HideDistanceInMetersDisplay"));
            if (!ready)
                throw new IllegalArgumentException("Missing required scope element/sequence");
        }
        catch (Exception failure) {
            ready = false;
            com.mojang.logging.LogUtils.getLogger().warn("Native precision scope HUD unavailable until resource reload",
                    failure);
        }
        return ready;
    }

    private static boolean viewOpen() {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (mc.level == null || player == null || !player.isAlive() || player.isSpectator() || mc.screen != null
                || TacticalClient.remote() || StreakPresentation.busy() || MgbEffects.interruptingControl()
                || mc.options.getCameraType() != CameraType.FIRST_PERSON)
            return false;
        if (TargetingDeviceAnimations.active())
            return TargetingDeviceAnimations.opticsVisible();
        return player.isUsingItem() && player.getUseItem().is(KillstreakcodMod.DESIGNATOR.get())
                && !player.getUseItem().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
                        .getString("Reward").equals("mortar_strike");
    }

    public static boolean visible() {
        return viewOpen() && load();
    }

    private static double now() {
        var mc = Minecraft.getInstance();
        return mc.level == null ? 0 : (mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(true)) * 50D;
    }

    private static float alpha(String sequence, String element, double elapsed, float initial) {
        Sequence s = SEQUENCES.get(sequence);
        if (s == null)
            return initial;
        double time = Math.max(0, elapsed);
        if (s.loop && s.duration > 0)
            time %= s.duration;
        float value = initial;
        for (Step step : s.elements.getOrDefault(element, List.of())) {
            if (step.milliseconds <= 0) {
                value = step.alpha;
                continue;
            }
            float fraction = Mth.clamp((float) (time / step.milliseconds), 0, 1);
            if (fraction < 1)
                return step.snap ? value : Mth.lerp(fraction, value, step.alpha);
            value = step.alpha;
            time -= step.milliseconds;
        }
        return Mth.clamp(value, 0, 1);
    }

    private static void validity(boolean next, double clock) {
        if (valid != next) {
            valid = next;
            stateAt = clock;
            distanceAt = -10000;
        }
    }

    /**
     * One collision ray per frame; distance digits follow the native 200 ms callback.
     * 每帧只做一次碰撞射线检测，距离数字按原资源 200 毫秒回调节奏更新。
     */
    public static void frame() {
        var mc = Minecraft.getInstance();
        if (!visible()) {
            reset();
            return;
        }
        if (world != mc.level || pawn != mc.player) {
            reset();
            world = mc.level;
            pawn = mc.player;
        }
        if (mc.isPaused())
            return;
        double clock = now();
        if (!open) {
            open = true;
            stateAt = dangerAt = clock;
        }
        if (fired || TargetingDeviceAnimations.precisionFiring())
            return; // 原 deploy_fired 状态会在收起动作期间停止更新标记。
        if (TargetingDeviceAnimations.active() && !TargetingDeviceAnimations.precisionFullyAimed()) {
            sampling = false;
            return;
        }
        if (!sampling) {
            sampling = true;
            danger = false;
            dangerStart = 0;
            dangerAt = clock;
        } // 原标记更新器在每次恢复完全瞄准状态时重置危险提示。
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(true);
        Vec3 from = pawn.getEyePosition(partial), to = from.add(pawn.getViewVector(partial).scale(rayRange));
        BlockHitResult hit = world
                .clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, pawn));
        validity(hit.getType() == HitResult.Type.BLOCK, clock);
        if (!valid) {
            target = null;
            return;
        } // 原逻辑的无效目标分支保留上一次危险状态。
        target = hit.getLocation();
        boolean nextDanger = pawn.position().subtract(target).horizontalDistanceSqr() <= dangerRadius * dangerRadius;
        if (danger != nextDanger) {
            dangerStart = danger ? alpha("DangerCloseOn", "DangerCloseText", clock - dangerAt, 1) : 0;
            danger = nextDanger;
            dangerAt = clock;
        }
        if (clock - distanceAt >= distanceRefresh) {
            distance = (int) Math.round(pawn.position().distanceTo(target));
            distanceAt = clock;
        }
    }

    /**
     * Existing server success payload replaces native shotCounter; rejected marks do not blink.
     * 用现有服务端成功消息代替原 shotCounter，被拒绝的标记不播放闪烁。
     */
    public static void marked() {
        if (Minecraft.getInstance().level == null)
            return;
        if (!open)
            frame();
        markedAt = now();
        fired = true;
    }

    public static void reset() {
        world = null;
        pawn = null;
        target = null;
        open = valid = danger = fired = sampling = false;
        stateAt = dangerAt = 0;
        distance = 0;
        distanceAt = markedAt = -10000;
        dangerStart = 0;
    }

    private static Node node(String name) {
        return NODES.get(name);
    }

    private static void image(GuiGraphics graphics, String name, Rect r, float opacity) {
        Image image = IMAGES.get(name);
        if (image == null || r == null || opacity <= .002F)
            return;

        try (UiLayout.Scope group = UiLayout.group(graphics, name.contains("reticle") ? "reticle" : null,
                new UiLayout.Box(r.left, r.top, r.width(), r.height()))) {
            graphics.flush();
            graphics.setColor(1, 1, 1, Mth.clamp(opacity, 0, 1));
            UiDraw.blit(graphics, "NativePrecisionScopeHud.image.0", image.texture, Math.round(r.left),
                    Math.round(r.top), Math.round(r.width()), Math.round(r.height()), 0, 0, image.width, image.height,
                    image.width, image.height);
            graphics.flush();
            graphics.setColor(1, 1, 1, 1);

        }
    }

    private static void text(GuiGraphics graphics, Node node, String value, float opacity, int rgb) {
        if (node == null || value.isEmpty() || opacity < .02F)
            return;
        var font = Minecraft.getInstance().font;
        Rect r = node.rect;
        float scale = Math.min(node.font, r.height()) / font.lineHeight;
        scale = Math.min(scale, r.width() / Math.max(1, font.width(value)));
        if (scale <= 0)
            return;
        float w = font.width(value) * scale, x = node.align.equals("left") ? r.left
                : node.align.equals("right") ? r.right - w : (r.left + r.right - w) / 2;
        float y = (r.top + r.bottom - font.lineHeight * scale) / 2;
        int color = Math.round(Mth.clamp(opacity, 0, 1) * 255) << 24 | rgb;
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale(scale, scale, 1);
            UiDraw.drawString(graphics, "NativePrecisionScopeHud.text.0", font, value, 0, 0, color, false);
        } finally {
            graphics.pose().popPose();
        }
    }

    private static String label(String element) {
        return Component.translatable(TEXT_KEYS.getOrDefault(element, element)).getString();
    }

    public static void render(GuiGraphics graphics) {
        try (UiLayout.Scope layoutFrame = UiLayout.frame("hud.scope")) {
            if (!load())
                return;
            if ((!visible() || !open) && !UiLayout.editing())
                return;
            double clock = now();
            float scale = Math.min(graphics.guiHeight() / referenceHeight,
                    Math.max(1, graphics.guiWidth() - 24) / 540F);
            try (GuiState ignored = new GuiState(graphics)) {
                graphics.pose().pushPose();
                try {
                    graphics.pose().translate(graphics.guiWidth() / 2F, graphics.guiHeight() / 2F, 0);
                    graphics.pose().scale(scale, scale, 1);
                    Node reticle = node("Reticle");
                    image(graphics, "overlay_spotter_scope_reticle", reticle.rect,
                            alpha("Onfire", "Reticle", clock - markedAt, reticle.alpha));
                    String distanceSequence = valid ? "ShowDistanceInMetersDisplay" : "HideDistanceInMetersDisplay";
                    image(graphics, "overlay_spotter_scope_meter_text_box", node("TextBox").rect,
                            alpha(distanceSequence, "TextBox", 0, 0));
                    text(graphics, node("DistanceLabel"), valid ? Integer.toString(distance) : "-",
                            alpha(distanceSequence, "DistanceLabel", 0, 0), 0xffffff);
                    text(graphics, node("MetersLabel"), "M", alpha(distanceSequence, "MetersLabel", 0, 0), 0xffffff);
                    String targetSequence = valid ? "HideNoTarget" : "ShowNoTarget";
                    for (int i = 0; i < 3; i++) {
                        String field = "CoordBox" + "XYZ".charAt(i);
                        Node box = node(field);
                        float opacity = alpha(targetSequence, field, clock - stateAt, 1);
                        if (opacity <= .002F)
                            continue;
                        graphics.pose().pushPose();
                        try {
                            graphics.pose().translate((box.rect.left + box.rect.right) / 2,
                                    (box.rect.top + box.rect.bottom) / 2, 0);
                            image(graphics, "overlay_spotter_scope_meter_text_box", CHILDREN.get("TextBox").rect,
                                    CHILDREN.get("TextBox").alpha * opacity);
                            String value = target == null ? "---"
                                    : Integer.toString((int) (i == 0 ? target.x : i == 1 ? target.y : target.z));
                            Node digits = CHILDREN.get("Text"), degrees = CHILDREN.get("TextDegreesSymbol");
                            text(graphics, digits, value, digits.alpha * opacity, 0xffffff);
                            text(graphics, degrees, "°", degrees.alpha * opacity, 0xffffff);
                        } finally {
                            graphics.pose().popPose();
                        }
                    }
                    text(graphics, node("NoTargetText"), label("NoTargetText"),
                            alpha(targetSequence, "NoTargetText", clock - stateAt, 0), 0xf07363);
                    text(graphics, node("DangerCloseText"), label("DangerCloseText"),
                            danger ? alpha("DangerCloseOn", "DangerCloseText", clock - dangerAt, 1)
                                    : alpha("DangerCloseOff", "DangerCloseText", clock - dangerAt, dangerStart),
                            0xf07363);
                    text(graphics, node("MarkingDescription"), label("MarkingDescription"),
                            node("MarkingDescription").alpha, 0xffffff);

                    // DangerCloseImage 原构造参数中的透明度为零，且没有图像动画序列。
                } finally {
                    graphics.pose().popPose();
                }
            }

        }
    }

    public static void reload() {
        reset();
        attempted = ready = false;
        NODES.clear();
        CHILDREN.clear();
        IMAGES.clear();
        SEQUENCES.clear();
        TEXT_KEYS.clear();
    }

    private static final class GuiState implements AutoCloseable {
        final GuiGraphics gui;
        final boolean blend;
        final float[] color;
        final int[] factors;

        GuiState(GuiGraphics g) {
            gui = g;
            g.flush();
            blend = GL11.glIsEnabled(GL11.GL_BLEND);
            color = RenderSystem.getShaderColor().clone();
            factors = new int[] { GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA) };
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            g.setColor(1, 1, 1, 1);
        }

        @Override
        public void close() {
            gui.flush();
            gui.setColor(color[0], color[1], color[2], color[3]);
            RenderSystem.blendFuncSeparate(factors[0], factors[1], factors[2], factors[3]);
            if (blend)
                RenderSystem.enableBlend();
            else
                RenderSystem.disableBlend();
        }
    }

}
