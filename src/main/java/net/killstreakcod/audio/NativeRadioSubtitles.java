package net.killstreakcod.audio;

import net.killstreakcod.render.UiDraw;
import net.killstreakcod.render.UiLayout;
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

    private static final Map<String, Caption> CLIPS = new HashMap<>();
    private static final Set<String> MISSING = new HashSet<>();
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
            int skipped = 0;
            for (var entry : root.getAsJsonObject("clips").entrySet()) {
                try {
                    var row = entry.getValue().getAsJsonObject();
                    if (row.get("scope").getAsString().equals("mp_dialogue_script_route")) {
                        String label = row.has("displaySpeaker") ? row.get("displaySpeaker").getAsString()
                                : row.get("speaker").getAsString();
                        // One source speaker ID can represent different operators; use the actual display label.
                        // 中文：同一原始人物 ID 可能用于多种操作员，按实际显示名称区分翻译键。
                        CLIPS.put(entry.getKey(),
                                new Caption(
                                        Component.translatableWithFallback(
                                                "radio.killstreakcod.line."
                                                        + row.get("subtitleLocalizeId").getAsString(),
                                                row.get("text").getAsString()),
                                        Component.translatableWithFallback("radio.killstreakcod.speaker."
                                                + Integer.toUnsignedString(label.hashCode(), 16), label)));
                    }
                }
                catch (RuntimeException invalidCaption) {
                    // 资源包中的单条错误不能让其余所有无线电字幕一起失效。
                    skipped++;
                }
            }
            if (skipped > 0)
                com.mojang.logging.LogUtils.getLogger().warn("[Killstreak radio] Skipped {} malformed captions",
                        skipped);
        }
        catch (Exception failure) {
            CLIPS.clear();
            com.mojang.logging.LogUtils.getLogger().warn("[Killstreak radio] Original captions unavailable", failure);
        }
    }

    public static void start(String reward, String phase, ResourceLocation selected, int ticks) {
        stop();
        load();
        if (selected == null)
            return;
        // 实际音频资源唯一决定字幕；不要再用呼叫阶段的索引重复过滤阵营或模组替换后的声音。
        Caption caption = CLIPS.get(selected.toString());
        if (caption == null) {
            if (MISSING.size() < 32 && MISSING.add(selected.toString()))
                com.mojang.logging.LogUtils.getLogger().warn("[Killstreak radio] No caption for {}/{}: {}", reward,
                        phase, selected);
            return;
        }
        message = caption.text;
        speaker = caption.speaker;
        var level = Minecraft.getInstance().level;
        ends = level == null ? 0 : level.getGameTime() + Math.max(12, ticks);
    }

    public static boolean visible() {
        var mc = Minecraft.getInstance();
        return message != null && (UiLayout.editing() || mc.level != null && !mc.options.hideGui
                && NativePresentationConfig.SUBTITLES.get() && mc.level.getGameTime() <= ends + 4);
    }

    public static void render(GuiGraphics graphics) {
        if (!visible())
            return;
        try (UiLayout.Scope layoutFrame = UiLayout.frame("hud.radio")) {
            var mc = Minecraft.getInstance();
            var caption = speaker.copy().withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(": ").withStyle(ChatFormatting.AQUA))
                    .append(message.copy().withStyle(ChatFormatting.WHITE));
            int maxWidth = RemoteHudLayout.active() ? RemoteHudLayout.captionWidth(graphics)
                    : Math.max(80, (int) (graphics.guiWidth() * .76));
            var lines = mc.font.split(caption, maxWidth);
            int visible = lines.size();
            int y = RemoteHudLayout.active()
                    ? RemoteHudLayout.captionY(graphics, maxWidth, visible * mc.font.lineHeight)
                    : Math.max(8, Math.min(graphics.guiHeight() - 54, (int) (graphics.guiHeight() * .84))
                            - visible * mc.font.lineHeight);
            try (UiLayout.Scope group = UiLayout.group(graphics, "captions", new UiLayout.Box(
                    (graphics.guiWidth() - maxWidth) / 2F, y, maxWidth, visible * mc.font.lineHeight))) {
                for (int i = 0; i < visible; i++) {
                    var line = lines.get(i);
                    UiDraw.drawString(graphics, "NativeRadioSubtitles.render.0", mc.font, line,
                            (graphics.guiWidth() - mc.font.width(line)) / 2, y + i * mc.font.lineHeight, 0xffffffff,
                            true);
                }
            }
        }
    }

    static void stop() {
        message = speaker = null;
        ends = 0;
    }

    public static void preview(GuiGraphics graphics) {
        Component previousMessage = message, previousSpeaker = speaker;
        try {
            message = Component.translatable("screen.killstreakcod.layout.caption_sample");
            speaker = Component.translatable("screen.killstreakcod.layout.speaker_sample");
            render(graphics);
        } finally {
            message = previousMessage;
            speaker = previousSpeaker;
        }
    }

    public static void reload() {
        stop();
        loaded = false;
        CLIPS.clear();
        MISSING.clear();
    }
}
