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
        RADIO = builder.comment("Play verified original English multiplayer radio dialogue.", "播放已核对的原版英文无线电语音。")
                .translation("killstreakcod.configuration.originalRadio").define("originalRadio", true);
        SUBTITLES = builder
                .comment("Show localized captions and the speaker label for the actual played voice variant.",
                        "显示当前播放语音对应的字幕与播报人物名称，未翻译的语言回退为英文。")
                .translation("killstreakcod.configuration.radioSubtitles").define("radioSubtitles", true);
        RADIO_VOLUME = builder.translation("killstreakcod.configuration.radioVolume").defineInRange("radioVolume", .75,
                0, 1);
        FX_LIMIT = builder.comment("Maximum live source sprites, shared by all killstreaks.", "所有连杀共用的同时存活特效数量上限。")
                .translation("killstreakcod.configuration.effectSpriteLimit")
                .defineInRange("effectSpriteLimit", 256, 32, 512);
        SPEC = builder.build();
    }

    private NativePresentationConfig() {}
}
