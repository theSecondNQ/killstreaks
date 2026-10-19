package net.killstreakcod.render;

import net.killstreakcod.audio.NativeAudio;
import net.killstreakcod.audio.NativeRadioSubtitles;
import net.killstreakcod.audio.StreakSounds;
import net.killstreakcod.universal.NativePresentationConfig;
import net.killstreakcod.universal.StreakEarnedPayload;

import java.util.ArrayDeque;
import java.util.Deque;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Native glyph/chevron/flare composition; Minecraft supplies motion absent from recovered LUI.
 * 组合原图标、箭形及耀光，导出 LUI 中缺失的运动效果由 Minecraft 补充。
 */
public final class StreakNoticeRenderer {
    private static final int WHITE = 0xfff3f5ed, GREEN = 0xffbaf68c;
    private static final Deque<StreakEarnedPayload> queue = new ArrayDeque<>();
    private static StreakEarnedPayload active;
    private static SoundInstance ready;
    private static int age, duration = 72;
    private static float renderedAge;

    private StreakNoticeRenderer() {}

    public static void earned(StreakEarnedPayload payload) {
        queue.addLast(payload);
    }

    public static void reset() {
        queue.clear();
        active = null;
        age = 0;
        renderedAge = 0;
        stopSounds();
    }

    private static void stopSounds() {
        var sounds = Minecraft.getInstance().getSoundManager();
        if (ready != null)
            sounds.stop(ready);
        ready = null;
        for (var sound : new net.minecraft.sounds.SoundEvent[] { StreakSounds.EARNED.get(),
                StreakSounds.EARNED_AIR.get(), StreakSounds.EARNED_GUNSHIP.get(), StreakSounds.EARNED_VIP.get() })
            sounds.stop(sound.getLocation(), SoundSource.MASTER);
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused())
            return;
        if (mc.player == null || mc.level == null || !mc.player.isAlive()) {
            reset();
            return;
        }
        if (active != null && ++age >= duration) {
            active = null;
            age = 0;
        }
        if (active == null && !queue.isEmpty()) {
            active = queue.removeFirst();
            age = 0;
            renderedAge = 0;
            stopSounds();
            duration = active.reward().equals("mgb") ? 100 : 72;

            // 同一次提示切换同时启动可见图标和就绪语音；该声音不负责服务端呼叫等待状态的管理或播放确认。
            mc.getSoundManager().play(SimpleSoundInstance.forUI(StreakSounds.earned(active.reward()), 1));
            var event = NativeAudio.voiceEvent(active.reward(), "ready", active.faction());
            if (event != null && NativePresentationConfig.RADIO.get() && NativePresentationConfig.RADIO_VOLUME.get() > 0
                    && mc.options.getSoundSourceVolume(SoundSource.VOICE) > 0
                    && mc.options.getSoundSourceVolume(SoundSource.MASTER) > 0) {
                ready = new SimpleSoundInstance(event.getLocation(), SoundSource.VOICE,
                        (float) (double) NativePresentationConfig.RADIO_VOLUME.get(), 1, RandomSource.create(), false,
                        0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
                mc.getSoundManager().play(ready);
                int ticks = ready.getSound() == null
                        ? NativeAudio.voiceTicks(active.reward(), "ready", active.faction())
                        : NativeAudio.soundTicks(ready.getSound().getLocation().toString(), active.reward(), "ready");
                duration = Math.max(duration, ticks + 16);
                NativeRadioSubtitles.start(active.reward(), "ready",
                        ready.getSound() == null ? null : ready.getSound().getLocation(), ticks);
            }
        }
    }

    public static void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (active == null || mc.player == null || mc.level == null || mc.options.hideGui)
            return;
        if (!mc.isPaused())
            renderedAge = Math.max(renderedAge, age + mc.getFrameTime());
        float t = renderedAge, leave = smooth(progress(t, duration - 12, duration)),
                alpha = smooth(progress(t, 0, 5)) * (1 - leave);
        if (alpha <= .002F)
            return;
        float scale = Math.min(graphics.guiWidth() / 960F, graphics.guiHeight() / 540F), cx = graphics.guiWidth() / 2F,
                cy = graphics.guiHeight() * .16F;
        float burst = 1 - progress(t, 4, 24), settle = easeOut(progress(t, 0, 13)), badge = Mth.lerp(settle, 92, 70);
        int accent = active.reward().equals("mgb") ? 0xffffb1a3 : active.packageReward() ? 0xffeadbad : GREEN;

        // 已移除旧矩形横幅、移动分隔线、库存及标题面板和自制扫光；所有图形层均使用源图像。
        graphics.pose().pushPose();
        graphics.pose().translate(cx, cy - leave * 5 * scale, 150);
        graphics.pose().scale(scale, scale, 1);
        try {
            OriginalRewardUi.layer(graphics, "killstreak_slot_flare", -98, -100, 196, 196, WHITE,
                    alpha * burst * .55F * mc.options.screenEffectScale().get().floatValue(), true);
            OriginalRewardUi.layer(graphics, "hud_killstreak_fill", -37, -39, 74, 80, accent, alpha * .20F, false);
            OriginalRewardUi.layer(graphics, "hud_killstreak_main_shape", -37, -39, 74, 80, accent, alpha * .65F,
                    false);
            OriginalRewardUi.icon(graphics, active.reward(), 0, -5, badge * .76F, WHITE, alpha);
            float chevron = smooth(progress(t, 9, 18));
            OriginalRewardUi.layer(graphics, "hud_killstreak_chevron", -7, 35 + 3 * (1 - chevron), 14, 9, accent,
                    alpha * chevron, false);
            float label = smooth(progress(t, 11, 23)) * alpha;
            String title = Component.translatable("streak.killstreakcod." + active.reward()).getString();
            centerText(graphics, title, 50, 1.15F, accent, label, 250);
            centerText(graphics,
                    Component.translatable("hud.killstreakcod.notice_ready", button(active.slot())).getString(), 66, 1,
                    WHITE, label * smooth(progress(t, 18, 29)), 290);
            OriginalRewardUi.layer(graphics, "killstreak_select_line_dashed", -72, 80, 144, 2, accent, label * .55F,
                    false);
        } finally {
            graphics.flush();
            graphics.pose().popPose();
        }
    }

    private static void centerText(GuiGraphics graphics, String text, int y, float scale, int tint, float opacity,
            int maxWidth) {
        if (opacity <= .02F)
            return;
        var font = Minecraft.getInstance().font;
        String fitted = font.plainSubstrByWidth(text, (int) (maxWidth / scale));
        graphics.pose().pushPose();
        graphics.pose().translate(0, y, 0);
        graphics.pose().scale(scale, scale, 1);
        try {
            graphics.drawString(font, fitted, -font.width(fitted) / 2, 0,
                    Math.round(opacity * 255) << 24 | (tint & 0xffffff), true);
        } finally {
            graphics.pose().popPose();
        }
    }

    private static String button(int slot) {
        KeyMapping key = switch (slot) {
            case 4 -> KillstreakKeyMappings.MGB;
            case 3 -> KillstreakKeyMappings.BONUS;
            case 0 -> KillstreakKeyMappings.USE_SLOT_1;
            case 1 -> KillstreakKeyMappings.USE_SLOT_2;
            default -> KillstreakKeyMappings.USE_SLOT_3;
        };
        return key.getTranslatedKeyMessage().getString();
    }

    private static float progress(float t, float start, float end) {
        return Mth.clamp((t - start) / (end - start), 0, 1);
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static float easeOut(float t) {
        t = Mth.clamp(t, 0, 1);
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }
}
