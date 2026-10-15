package net.killstreakcod.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * The hand-deployment propellers sound before the authoritative remote unit exists. 炸弹无人机的起飞音效处理
 */
public final class DroneDeploymentSounds {

    private static Takeoff loop;

    private DroneDeploymentSounds() {}

    public static void start() {
        if (loop != null)
            return;
        loop = new Takeoff();
        Minecraft.getInstance()
                .getSoundManager()
                .play(loop);
    }

    public static void stop() {
        if (loop != null) {
            loop.finish();
            Minecraft.getInstance()
                    .getSoundManager()
                    .stop(loop);
            loop = null;
        }
    }

    private static final class Takeoff extends AbstractTickableSoundInstance {

        private final net.minecraft.client.multiplayer.ClientLevel world;
        private final net.minecraft.client.player.LocalPlayer owner;
        private int ticks;

        Takeoff() {
            super(StreakSounds.BOMB_DRONE_ENGINE.get(),
                    SoundSource.PLAYERS,
                    RandomSource.create());

            var mc = Minecraft.getInstance();
            world = mc.level;
            owner = mc.player;
            relative = true;
            looping = true;
            attenuation = Attenuation.NONE;
            volume = .1F;
            pitch = 1;
        }

        void finish() {
            stop();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            var mc = Minecraft.getInstance();

            if (mc.level != world || mc.player != owner || owner == null || !owner.isAlive()) {
                stop();
                return;
            }

            if (!mc.isPaused())
                volume = Math.min(.65F, .1F + (++ticks) * .055F);
        }

    }
}
