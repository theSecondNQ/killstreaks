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
    public static final ModConfigSpec.IntValue GUNSHIP_VIEW_DISTANCE;
    public static final ModConfigSpec.IntValue REWARD_RAIL_STYLE;
    public static final ModConfigSpec.BooleanValue REWARD_RAIL_AUTO_HIDE;
    static {
        var builder = new ModConfigSpec.Builder();
        RADIO = builder
                .comment("Play verified original English multiplayer radio dialogue.", "播放已核对的原版英文无线电语音。",
                        "Also requires nonzero radioVolume, Minecraft Voice/Speech and Master volume.",
                        "同时需要 radioVolume、原版语音音量和主音量大于零；关闭播报也会停止对应字幕。")
                .translation("killstreakcod.configuration.originalRadio").define("originalRadio", true);
        SUBTITLES = builder.comment(
                "Show localized captions and the speaker label for the actual played voice variant.",
                "显示当前播放语音对应的字幕与播报人物名称，未翻译的语言回退为英文。",
                "Captions follow actual radio playback. F1 hides captions; vanilla subtitle settings are independent.",
                "字幕跟随实际播放的无线电，F1 隐藏字幕；原版辅助字幕开关不控制此处字幕。").translation("killstreakcod.configuration.radioSubtitles")
                .define("radioSubtitles", true);
        RADIO_VOLUME = builder.translation("killstreakcod.configuration.radioVolume").defineInRange("radioVolume", .75,
                0, 1);
        FX_LIMIT = builder.comment("Maximum live source sprites, shared by all killstreaks.", "所有连杀共用的同时存活特效数量上限。")
                .translation("killstreakcod.configuration.effectSpriteLimit")
                .defineInRange("effectSpriteLimit", 256, 32, 512);
        GUNSHIP_VIEW_DISTANCE = builder
                .comment("炮艇地面渲染半径，单位为区块；实际不超过服务端发送范围。",
                        "Gunship ground render radius in chunks, bounded by the server watch radius.")
                .translation("killstreakcod.configuration.gunshipViewDistance")
                .defineInRange("gunshipViewDistance", 32, 16, 32);
        REWARD_RAIL_STYLE = builder.comment("右侧连杀栏：0详细，1默认（图标与进度条），2精简（仅图标）。")
                .translation("killstreakcod.configuration.rewardRailStyle").defineInRange("rewardRailStyle", 1, 0, 2);
        REWARD_RAIL_AUTO_HIDE = builder.comment("连续3秒未呼叫连杀后收为右侧竖条，呼叫或获取新奖励时展开。")
                .translation("killstreakcod.configuration.rewardRailAutoHide").define("rewardRailAutoHide", true);
        SPEC = builder.build();
    }

    private NativePresentationConfig() {}
}
