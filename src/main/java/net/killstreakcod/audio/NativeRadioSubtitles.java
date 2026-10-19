package net.killstreakcod.audio;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.render.RemoteHudLayout;
import net.killstreakcod.universal.NativePresentationConfig;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Localized original captions selected by the actual played English sound asset.
 * 中文：根据实际播放的英文音频资源，选择当前语言对应的原游戏字幕。
 */
public final class NativeRadioSubtitles {
    private record Caption(Component text, Component speaker) {
    }

    private static final Map<String, Set<String>> ROUTES = new HashMap<>();
    private static final Map<String, Caption> CLIPS = new HashMap<>();
    private static Component message, speaker;
    private static long ends;
    private static boolean loaded;

    private NativeRadioSubtitles() {}

    private static void load() {
        if (loaded)
            return;
        loaded = true;
        try (var reader = Minecraft.getInstance().getResourceManager()
                .getResourceOrThrow(
                        ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "mw/radio-captions.json"))
                .openAsReader()) {
            var root = JsonParser.parseReader(reader).getAsJsonObject();
            for (var entry : root.getAsJsonObject("routes").entrySet()) {
                Set<String> variants = new HashSet<>();
                for (var sound : entry.getValue().getAsJsonArray())
                    variants.add(sound.getAsString());
                ROUTES.put(entry.getKey(), Set.copyOf(variants));
            }
            for (var entry : root.getAsJsonObject("clips").entrySet()) {
                var row = entry.getValue().getAsJsonObject();
                if (row.get("scope").getAsString().equals("mp_dialogue_script_route")) {
                    String label = row.has("displaySpeaker") ? row.get("displaySpeaker").getAsString()
                            : row.get("speaker").getAsString();
                    // One source speaker ID can represent different operators; use the actual display label.
                    // 中文：同一原始人物 ID 可能用于多种操作员，按实际显示名称区分翻译键。
                    CLIPS.put(entry.getKey(),
                            new Caption(
                                    Component.translatableWithFallback(
                                            "radio.killstreakcod.line." + row.get("subtitleLocalizeId").getAsString(),
                                            row.get("text").getAsString()),
                                    Component.translatableWithFallback("radio.killstreakcod.speaker."
                                            + Integer.toUnsignedString(label.hashCode(), 16), label)));
                }
            }
        }
        catch (Exception failure) {
            ROUTES.clear();
            CLIPS.clear();
            com.mojang.logging.LogUtils.getLogger().warn("Original radio captions unavailable", failure);
        }
    }

    public static void start(String reward, String phase, ResourceLocation selected, int ticks) {
        stop();
        load();
        String sourcePhase = phase.equals("response_parallel") ? "response"
                : phase.endsWith("_now") ? phase.substring(0, phase.length() - 4) : phase;
        if (selected == null
                || !ROUTES.getOrDefault(reward + "/" + sourcePhase, Set.of()).contains(selected.toString()))
            return;
        Caption caption = CLIPS.get(selected.toString());
        if (caption == null)
            return;
        message = caption.text;
        speaker = caption.speaker;
        var level = Minecraft.getInstance().level;
        ends = level == null ? 0 : level.getGameTime() + Math.max(12, ticks);
    }

    public static void render(GuiGraphics graphics) {
        var mc = Minecraft.getInstance();
        if (message == null || mc.level == null || mc.options.hideGui || !NativePresentationConfig.SUBTITLES.get()
                || mc.level.getGameTime() > ends + 4)
            return;
        var caption = speaker.copy().withStyle(ChatFormatting.AQUA)
                .append(Component.literal(": ").withStyle(ChatFormatting.AQUA))
                .append(message.copy().withStyle(ChatFormatting.WHITE));
        int maxWidth = RemoteHudLayout.active() ? RemoteHudLayout.captionWidth(graphics)
                : Math.max(80, (int) (graphics.guiWidth() * .76));
        var lines = mc.font.split(caption, maxWidth);
        int visible = lines.size();
        int y = RemoteHudLayout.active() ? RemoteHudLayout.captionY(graphics, maxWidth, visible * mc.font.lineHeight)
                : Math.max(8, Math.min(graphics.guiHeight() - 54, (int) (graphics.guiHeight() * .84))
                        - visible * mc.font.lineHeight);
        for (int i = 0; i < visible; i++) {
            var line = lines.get(i);
            graphics.drawString(mc.font, line, (graphics.guiWidth() - mc.font.width(line)) / 2,
                    y + i * mc.font.lineHeight, 0xffffffff, true);
        }
    }

    static void stop() {
        message = speaker = null;
        ends = 0;
    }

    public static void reload() {
        stop();
        loaded = false;
        ROUTES.clear();
        CLIPS.clear();
    }
}
