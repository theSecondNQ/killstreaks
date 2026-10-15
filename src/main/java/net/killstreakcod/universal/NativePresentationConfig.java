package net.killstreakcod.universal;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Minecraft's Voice/Speech slider also controls the recovered radio dialogue.
 * Minecraft 的语音音量滑块同时控制原资源无线电对话。
 */
public final class NativePresentationConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue RADIO;
    public static final ModConfigSpec.BooleanValue SUBTITLES;
    public static final ModConfigSpec.DoubleValue RADIO_VOLUME;
    public static final ModConfigSpec.IntValue FX_LIMIT;
    static {
        var builder = new ModConfigSpec.Builder();
        RADIO = builder.comment("Play verified original English multiplayer radio dialogue.").define("originalRadio",
                true);
        SUBTITLES = builder.comment(
                "Show original simplified Chinese captions and the speaker label for the actual played voice variant.")
                .define("radioSubtitles", true);
        RADIO_VOLUME = builder.defineInRange("radioVolume", .75, 0, 1);
        FX_LIMIT = builder.comment("Maximum live source sprites, shared by all killstreaks.")
                .defineInRange("effectSpriteLimit", 256, 32, 512);
        SPEC = builder.build();
    }

    private NativePresentationConfig() {}
}
