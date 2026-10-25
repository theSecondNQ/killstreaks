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
import net.minecraft.util.RandomSource;

/**
 * Queues earned notifications and starts their sound/subtitles with the first visible frame.
 * 管理获取提示队列，并在首个可见帧同时启动音效、就绪语音与字幕。
 */
public final class StreakNoticeRenderer {
    private static final Deque<StreakEarnedPayload> queue = new ArrayDeque<>();
    private static StreakEarnedPayload active;
    private static SoundInstance ready;
    private static int age, duration = 52, nextDelay, voiceRemaining;
    private static float renderedAge;

    private StreakNoticeRenderer() {}

    public static void earned(StreakEarnedPayload payload) {
        queue.addLast(payload);
    }

    public static void reset() {
        queue.clear();
        active = null;
        age = 0;
        nextDelay = 0;
        voiceRemaining = 0;
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
        if (voiceRemaining > 0)
            voiceRemaining--;
        if (nextDelay > 0)
            nextDelay--;
        if (active != null && ++age >= duration) {
            active = null;
            age = 0;
            var animation = EarnedSplashRenderer.animation();
            nextDelay = ticks(animation == null ? 300 : animation.messageDelay());
        }
        if (active == null && nextDelay == 0 && voiceRemaining == 0 && !queue.isEmpty() && !mc.options.hideGui) {
            active = queue.removeFirst();
            age = 0;
            renderedAge = 0;
            var animation = EarnedSplashRenderer.animation();
            duration = ticks(animation == null ? 2600 : animation.totalTime());

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
                // 横幅按原生时长退场；队列等当前播报结束，避免下一条获取提示截断声音。
                voiceRemaining = ticks;
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
            renderedAge = Math.max(renderedAge, age + mc.getTimer().getGameTimeDeltaPartialTick(true));
        String hint = Component.translatable("hud.killstreakcod.notice_ready", button(active.slot())).getString();
        EarnedSplashRenderer.render(graphics, active.reward(), hint, renderedAge * 50);
    }

    private static int ticks(int milliseconds) {
        return Math.max(1, (milliseconds + 49) / 50);
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

}
