package net.killstreakcod.audio;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.universal.NativePresentationConfig;

import java.util.ArrayDeque;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/**
 * One radio transmission at a time, with bounded queue and per-life cleanup.
 * 中文：无线电每次只播放一段播报，队列有长度限制，并在每次生命结束时清理。
 */
@EventBusSubscriber(modid = KillstreakcodMod.MODID, value = Dist.CLIENT)
public final class NativeRadioClient {
    private record Pending(String reward, String phase, long received, long earliest, java.util.UUID gate,
            String faction) {
    }

    private static final ArrayDeque<Pending> QUEUE = new ArrayDeque<>();
    private static ClientLevel world;
    private static Player pawn;
    private static SoundInstance playing;
    private static Pending current;
    private static long started;
    private static long nextVoice;
    private static int selectedTicks;
    private static ResourceLocation captionedSound;
    private static boolean startedPlaying;
    private static boolean gunshipDeparted;

    private static boolean gunshipMission(String phase) {
        return !phase.equals("ready") && !phase.equals("enemy_call") && !phase.equals("friendly_call");
    }

    private NativeRadioClient() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void soundSelected(PlaySoundEvent event) {
        // 声音模组可以替换原实例；跟踪替换结果，才能读取实际选中的音频并判断播放是否结束。
        // 此处只观察事件，不替换、不取消其他模组的声音。
        if (current != null && event.getOriginalSound() == playing && event.getSound() != null)
            playing = event.getSound();
    }

    public static void accept(NativeVoicePayload payload) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.player.isAlive() || mc.player.getId() != payload.listenerId()
                || !mc.level.dimension().location().equals(payload.dimension()))
            return;
        if (!NativePresentationConfig.RADIO.get() || NativePresentationConfig.RADIO_VOLUME.get() == 0
                || mc.options.getSoundSourceVolume(SoundSource.VOICE) == 0
                || mc.options.getSoundSourceVolume(SoundSource.MASTER) == 0
                || NativeAudio.voiceEvent(payload.reward(), payload.phase(), payload.faction()) == null) {
            ack(payload.gate());
            return;
        }
        if (world != mc.level || pawn != mc.player) {
            clear();
            world = mc.level;
            pawn = mc.player;
        }
        if (payload.reward().equals("gunship")) {
            if (payload.phase().equals("response_parallel"))
                gunshipDeparted = false;
            boolean leaving = payload.phase().equals("leave_now") || payload.phase().equals("timeout_now");
            if (gunshipDeparted && gunshipMission(payload.phase())) {
                ack(payload.gate());
                return;
            }
            if (leaving) {
                gunshipDeparted = true;
                QUEUE.removeIf(p -> {
                    if (!p.reward.equals("gunship") || !gunshipMission(p.phase))
                        return false;
                    ack(p.gate);
                    return true;
                });
            }
        }
        QUEUE.removeIf(p -> p.gate.equals(NativeVoicePayload.NONE) && p.reward.equals(payload.reward())
                && p.phase.equals(payload.phase()));
        while (QUEUE.size() >= 8) {
            Pending removed = QUEUE.removeLast();
            ack(removed.gate);
        }
        long now = world.getGameTime();
        var pending = new Pending(payload.reward(), payload.phase(), now,
                now + Math.max(0, Math.min(160, payload.delay())), payload.gate(), payload.faction());
        boolean ending = payload.phase().endsWith("_now");
        if (payload.phase().equals("response_parallel")
                || ending && current != null && current.reward.equals(payload.reward())
                        && (current.phase.equals("response_parallel") || current.phase.equals("engage")
                                || payload.reward().equals("gunship") && gunshipMission(current.phase))) {

            // 遥控入场与响应语音同时开始。替换此前的就绪播报，其他排队的结束及战果评估播报保持原顺序。
            if (playing != null)
                mc.getSoundManager().stop(playing);
            if (current != null)
                ack(current.gate);
            playing = null;
            current = null;
            NativeRadioSubtitles.stop();
            nextVoice = now;
            QUEUE.addFirst(pending);
        } else if (ending) {
            nextVoice = now;
            QUEUE.addFirst(pending);
        } else if (payload.gate().equals(NativeVoicePayload.NONE))
            QUEUE.addLast(pending);
        else
            QUEUE.addFirst(pending);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (world == null)
            return;
        if (mc.level != world || mc.player != pawn || !pawn.isAlive()) {
            clear();
            return;
        }
        if (!NativePresentationConfig.RADIO.get() || NativePresentationConfig.RADIO_VOLUME.get() == 0
                || mc.options.getSoundSourceVolume(SoundSource.VOICE) == 0
                || mc.options.getSoundSourceVolume(SoundSource.MASTER) == 0) {
            if (current != null)
                ack(current.gate);
            QUEUE.forEach(p -> ack(p.gate));
            clear();
            return;
        }
        if (mc.isPaused())
            return;
        long now = world.getGameTime();
        if (playing != null) {
            syncCaption(now);
            boolean active = mc.getSoundManager().isActive(playing);
            startedPlaying |= active;
            if (now - started > selectedTicks + 60
                    || (startedPlaying && now - started >= Math.max(12, selectedTicks - 2) && !active)) {
                mc.getSoundManager().stop(playing);
                playing = null;
                NativeRadioSubtitles.stop();
                ack(current.gate);
                current = null;
                nextVoice = now + 12;
            } else
                return;
        }
        QUEUE.removeIf(p -> p.gate.equals(NativeVoicePayload.NONE) && now - p.received > 400);
        if (QUEUE.isEmpty() || now < nextVoice || now < QUEUE.peekFirst().earliest)
            return;
        Pending next = QUEUE.removeFirst();
        var sound = NativeAudio.voiceEvent(next.reward, next.phase, next.faction);
        if (sound == null) {
            ack(next.gate);
            return;
        }
        playing = new SimpleSoundInstance(sound.getLocation(), SoundSource.VOICE,
                (float) (double) NativePresentationConfig.RADIO_VOLUME.get(), 1, RandomSource.create(), false, 0,
                SoundInstance.Attenuation.NONE, 0, 0, 0, true);
        current = next;
        started = now;
        startedPlaying = false;
        captionedSound = null;
        selectedTicks = NativeAudio.voiceTicks(next.reward, next.phase, next.faction);
        mc.getSoundManager().play(playing);
        syncCaption(now);
    }

    private static void syncCaption(long now) {
        var selected = playing.getSound();
        if (selected == null || selected.getLocation().equals(captionedSound))
            return;
        captionedSound = selected.getLocation();
        selectedTicks = NativeAudio.soundTicks(captionedSound.toString(), current.reward, current.phase);
        // 延后解析的音频也能补上字幕，但只显示剩余时间，不重新计时或重新抽取随机台词。
        int remaining = (int) Math.max(0, started + selectedTicks - now);
        if (remaining > 0)
            NativeRadioSubtitles.start(current.reward, current.phase, captionedSound, remaining);
    }

    private static void ack(java.util.UUID gate) {
        if (!gate.equals(NativeVoicePayload.NONE) && Minecraft.getInstance().getConnection() != null)
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new NativeVoiceFinishedPayload(gate));
    }

    public static void cancelGate() {
        if (current != null && !current.gate.equals(NativeVoicePayload.NONE)) {
            if (playing != null)
                Minecraft.getInstance().getSoundManager().stop(playing);
            playing = null;
            current = null;
            NativeRadioSubtitles.stop();
        }
        QUEUE.removeIf(p -> !p.gate.equals(NativeVoicePayload.NONE));
    }

    public static void clear() {
        if (playing != null)
            Minecraft.getInstance().getSoundManager().stop(playing);
        playing = null;
        current = null;
        captionedSound = null;
        NativeRadioSubtitles.stop();
        QUEUE.clear();
        world = null;
        pawn = null;
        nextVoice = 0;
        gunshipDeparted = false;
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }
}
