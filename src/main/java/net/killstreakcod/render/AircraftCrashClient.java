package net.killstreakcod.render;

import net.killstreakcod.audio.SceneEntryAudio;
import net.killstreakcod.killstreaks.TacticalUnitEntity;
import net.killstreakcod.universal.AircraftCrashLibrary;
import net.killstreakcod.universal.AircraftCrashPayload;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * A destruction-only camera; owner death, dimension change and nuclear interruption cancel it.
 * 仅用于击毁过程的摄像机；所有者死亡、切换维度或核弹中断时取消。
 */
public final class AircraftCrashClient {
    private static AircraftCrashPayload context;
    private static ClientLevel world;
    private static LocalPlayer player;
    private static CameraType previousCamera;
    private static final List<SoundInstance> sounds = new ArrayList<>();

    private AircraftCrashClient() {}

    static void start(AircraftCrashPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        TacticalClient.acceptWorld();
        if (mc.level == null || mc.player == null || !mc.player.isAlive() || MgbEffects.interruptingControl()
                || !mc.level.dimension().location().equals(payload.dimension()) || payload.variant() < 100
                || payload.variant() > 102 || !Double.isFinite(payload.x()) || !Double.isFinite(payload.y())
                || !Double.isFinite(payload.z()) || !Float.isFinite(payload.yaw()) || !Float.isFinite(payload.pitch()))
            return;
        int duration = payload.variant() == 100 ? 120 : payload.variant() == 101 ? 54 : 67;
        if (payload.duration() != duration || payload.entityId() < 0)
            return;

        // 攻击直升机仍拥有遥控摄像机，其现有恢复流程负责视角恢复；只有已恢复玩家本体的空中炮艇退场需要另外暂管视角。
        reset();
        context = payload;
        world = mc.level;
        player = mc.player;
        previousCamera = payload.variant() == 100 ? mc.options.getCameraType() : null;
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        SceneEntryAudio.reset();
        StreakPresentation.start(payload.variant() == 100 ? "gunship" : "chopper_gunner",
                payload.variant() == 100 ? "crash_gunship"
                        : payload.variant() == 101 ? "crash_missile" : "crash_bullet",
                duration);
        if (!active()) {
            reset();
            return;
        }
        once(payload.variant() == 100 ? "gunship_outro" : "apache_player", .7F);
        if (payload.variant() != 100)
            loop("apache_heavy", .28F);
        TacticalClient.impact(.8F);
    }

    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return context != null && mc.level == world && mc.player == player && player != null && player.isAlive()
                && !MgbEffects.interruptingControl() && StreakPresentation.currentStage().startsWith("crash_");
    }

    static int variant() {
        return context == null ? 100 : context.variant();
    }

    static Matrix4f camera() {
        return variant() == 100 ? SceneEntryRenderer.gunshipOutroCamera(StreakPresentation.elapsed())
                : AircraftCrashLibrary.pilotCamera(variant(), StreakPresentation.elapsed());
    }

    static Quaternionf attitude() {
        Minecraft mc = Minecraft.getInstance();
        float yaw = context.yaw(), pitch = context.pitch(), roll = 0;
        if (variant() != 100 && mc.level.getEntity(context.entityId()) instanceof TacticalUnitEntity unit) {
            float partial = mc.getFrameTime();
            yaw = unit.renderFlightYaw(partial);
            pitch = unit.renderFlightPitch(partial);
            roll = AircraftCrashLibrary.roll(variant(), unit.nativeClock(partial));
        }
        return AircraftCrashLibrary.attitude(yaw, pitch, roll);
    }

    static Vec3 origin() {
        Minecraft mc = Minecraft.getInstance();
        if (variant() != 100 && mc.level.getEntity(context.entityId()) instanceof TacticalUnitEntity unit)
            return unit.getPosition(mc.getFrameTime());
        return new Vec3(context.x(), context.y(), context.z());
    }

    private static void once(String role, float gain) {
        var event = AircraftCrashLibrary.sound(role, 0);
        if (event == null)
            return;
        var sound = new SimpleSoundInstance(event.getLocation(), SoundSource.PLAYERS, gain, 1, RandomSource.create(),
                false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
        sounds.add(sound);
        Minecraft.getInstance().getSoundManager().play(sound);
    }

    private static void loop(String role, float gain) {
        var event = AircraftCrashLibrary.sound(role, 0);
        if (event == null)
            return;
        var sound = new SimpleSoundInstance(event.getLocation(), SoundSource.PLAYERS, gain, 1, RandomSource.create(),
                true, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
        sounds.add(sound);
        Minecraft.getInstance().getSoundManager().play(sound);
    }

    static void reset() {
        var mc = Minecraft.getInstance();
        for (var sound : sounds)
            mc.getSoundManager().stop(sound);
        sounds.clear();
        if (previousCamera != null && mc.level == world && mc.player == player)
            mc.options.setCameraType(previousCamera);
        context = null;
        world = null;
        player = null;
        previousCamera = null;
    }
}
