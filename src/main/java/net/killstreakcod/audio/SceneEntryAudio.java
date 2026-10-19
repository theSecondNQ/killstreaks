package net.killstreakcod.audio;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.render.AircraftCrashClient;
import net.killstreakcod.render.StreakPresentation;
import net.killstreakcod.render.TacticalClient;
import net.killstreakcod.universal.SceneEntryLibrary;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;

/**
 * Local original transitions and cabin loops, stopped on view exit, death and level changes.
 * 中文：管理本地原资源过渡音和座舱循环音，在退出视角、死亡或切换世界时停止。
 */
@EventBusSubscriber(modid = KillstreakcodMod.MODID, value = Dist.CLIENT)
public final class SceneEntryAudio {
    private static Level level;
    private static String mode = "";
    private static int unit = -1, weapon = -1;
    private static boolean entryPlayed;
    private static long lastAlarm;
    private static SimpleSoundInstance loop, cruiseIntro;
    private static final List<SoundInstance> owned = new ArrayList<>();
    private static long cruiseLoopStarted = Long.MIN_VALUE;
    private static Level transitionLevel;
    private static String transitionReward = "";
    private static long transitionTick = Long.MIN_VALUE;

    private SceneEntryAudio() {}

    private static void once(String alias, float volume) {
        var sound = SimpleSoundInstance.forUI(NativeAudio.sfx("scene/" + alias, StreakSounds.RADIO.get()), 1, volume);
        Minecraft.getInstance().getSoundManager().play(sound);
        owned.add(sound);
    }

    public static void transition(String reward) {
        if (!reward.equals("chopper_gunner") && !reward.equals("gunship"))
            return;
        var level = Minecraft.getInstance().level;
        if (level == null)
            return;
        long now = level.getGameTime();
        if (level == transitionLevel && reward.equals(transitionReward) && transitionTick != Long.MIN_VALUE
                && now >= transitionTick && now - transitionTick < 20)
            return;
        transitionLevel = level;
        transitionReward = reward;
        transitionTick = now;
        once("mp_killstreak_transition_whoosh", .45F);
    }

    private static void loop(String alias) {
        if (loop != null)
            return;
        loop = new SimpleSoundInstance(NativeAudio.sfx("scene/" + alias, StreakSounds.JET.get()).getLocation(),
                SoundSource.AMBIENT, .32F, 1, SoundInstance.createUnseededRandom(), true, 0,
                SoundInstance.Attenuation.NONE, 0, 0, 0, true);
        Minecraft.getInstance().getSoundManager().play(loop);
        owned.add(loop);
    }

    private static void cruiseOnce() {
        cruiseIntro = SimpleSoundInstance.forUI(StreakSounds.CRUISE_NATIVE_INTRO.get(), 1, .7F);
        Minecraft.getInstance().getSoundManager().play(cruiseIntro);
        owned.add(cruiseIntro);
    }

    private static void cruiseLoop() {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused())
            return;

        // 遥控载具音效使用玩家的音效音量设置。尊重两项主动静音设置，不改动用户的环境音量。
        if (mc.options.getSoundSourceVolume(SoundSource.MASTER) <= 0
                || mc.options.getSoundSourceVolume(SoundSource.PLAYERS) <= 0) {
            stopLoop();
            return;
        }
        long now = mc.level.getGameTime();

        // 资源重载或声道中断后，声音实例可能仍非空但已停播；为异步音频启动保留两秒宽限。
        if (loop != null && now - cruiseLoopStarted >= 40 && !mc.getSoundManager().isActive(loop))
            stopLoop();
        if (loop != null)
            return;
        loop = new SimpleSoundInstance(StreakSounds.CRUISE_NATIVE_PLAYER.get().getLocation(), SoundSource.PLAYERS, .6F,
                1, SoundInstance.createUnseededRandom(), true, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
        cruiseLoopStarted = now;
        mc.getSoundManager().play(loop);
        owned.add(loop);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
        if (AircraftCrashClient.active()) {
            stopLoop();
            stopCruiseIntro();
            return;
        }
        var mc = Minecraft.getInstance();
        if (mc.level != level || mc.player == null || !mc.player.isAlive()) {
            reset();
            level = mc.level;
        }
        if (mc.level == null || mc.player == null)
            return;
        owned.removeIf(sound -> sound != loop && !mc.getSoundManager().isActive(sound));
        String active = TacticalClient.remote() ? TacticalClient.state.mode() : "";
        if (!active.equals(mode) || TacticalClient.state.entityId() != unit) {
            stopLoop();
            stopCruiseIntro();
            mode = active;
            unit = TacticalClient.state.entityId();
            weapon = TacticalClient.state.weapon();
            entryPlayed = false;
        }
        boolean entering = StreakPresentation.currentStage().equals("entry"),
                exiting = StreakPresentation.currentStage().equals("exit");
        String reward = StreakPresentation.currentReward();
        if (entering && !entryPlayed && (reward.equals("chopper_gunner") || reward.equals("gunship"))) {
            entryPlayed = true;
            once(reward.equals("gunship") ? "iw9_gunship_plr_intro" : "mp_killstreak_apache_transition_lr", .65F);
        }
        if (mode.equals("cruise") && !exiting) {
            if (entering && !entryPlayed) {
                entryPlayed = true;
                cruiseOnce();
            }
            if (!entering) {
                stopCruiseIntro();
                cruiseLoop();
            }
            return;
        }
        if (exiting)
            stopCruiseIntro();
        if (!exiting && (mode.equals("chopper") || mode.equals("gunship"))) {
            if (mode.equals("chopper") || !entering
                    || StreakPresentation.elapsed() >= SceneEntryLibrary.GUNSHIP_EXTERIOR)
                loop(mode.equals("chopper") ? "veh_apache_killstreak_amb_lr" : "iw8_ks_ac130_lp");
            if (mode.equals("gunship") && weapon != TacticalClient.state.weapon()) {
                weapon = TacticalClient.state.weapon();
                once("iw8_ks_ac130_weaponswitch", .6F);
            }
            if (mode.equals("gunship") && TacticalClient.state.health() < 35
                    && (lastAlarm == 0 || mc.level.getGameTime() - lastAlarm >= 120)) {
                lastAlarm = mc.level.getGameTime();
                once("ks_ac130_damage_warning", .55F);
            }
        } else
            stopLoop();
    }

    private static void stopLoop() {
        if (loop != null) {
            Minecraft.getInstance().getSoundManager().stop(loop);
            owned.remove(loop);
            loop = null;
        }
        cruiseLoopStarted = Long.MIN_VALUE;
    }

    private static void stopCruiseIntro() {
        if (cruiseIntro != null) {
            Minecraft.getInstance().getSoundManager().stop(cruiseIntro);
            owned.remove(cruiseIntro);
            cruiseIntro = null;
        }
    }

    public static void reset() {
        stopLoop();
        stopCruiseIntro();
        var manager = Minecraft.getInstance().getSoundManager();
        for (var sound : owned)
            manager.stop(sound);
        owned.clear();
        loop = cruiseIntro = null;
        mode = "";
        unit = weapon = -1;
        entryPlayed = false;
        lastAlarm = 0;
        transitionLevel = null;
        transitionReward = "";
        transitionTick = Long.MIN_VALUE;
    }
}
