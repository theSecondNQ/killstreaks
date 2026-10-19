package net.killstreakcod.audio;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.universal.NativeArsenalLibrary;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;

@EventBusSubscriber(modid = KillstreakcodMod.MODID, value = Dist.CLIENT)
public final class SupportSoundClient {
    private record Active(SoundInstance sound, long expires) {
    }

    private static final List<Active> SOUNDS = new ArrayList<>();
    private static ClientLevel world;

    private SupportSoundClient() {}

    public static void accept(SupportSoundPayload p) {
        var mc = Minecraft.getInstance();
        boolean cruiseStatic = p.sound().equals(StreakSounds.CRUISE_NATIVE_BLAST.get().getLocation());
        if (mc.level == null || mc.player == null || !mc.level.dimension().location().equals(p.dimension())
                || !cruiseStatic && !SupportAudio.distantOneShot(p.sound()))
            return;
        if (!Double.isFinite(p.x()) || !Double.isFinite(p.y()) || !Double.isFinite(p.z()) || !Float.isFinite(p.radius())
                || !Float.isFinite(p.volume()) || !Float.isFinite(p.pitch()) || p.radius() <= 0)
            return;
        if (world != mc.level) {
            clear();
            world = mc.level;
        }

        // 原游戏的爆炸静电音仅用于呼叫者的传感器视角，不作为世界中的爆炸声。
        if (cruiseStatic) {
            var sound = SimpleSoundInstance.forUI(StreakSounds.CRUISE_NATIVE_BLAST.get(), 1,
                    net.minecraft.util.Mth.clamp(p.volume(), 0, 2));
            if (SOUNDS.size() >= 16)
                mc.getSoundManager().stop(SOUNDS.remove(0).sound());
            mc.getSoundManager().play(sound);
            SOUNDS.add(new Active(sound, world.getGameTime() + 40));
            return;
        }
        double distance = mc.gameRenderer.getMainCamera().getPosition().distanceTo(new Vec3(p.x(), p.y(), p.z())),
                radius = Math.min(640, p.radius());
        if (distance >= radius)
            return;
        float gain = (float) Math.sqrt(1 - distance / radius) * net.minecraft.util.Mth.clamp(p.volume(), 0, 2);
        var sound = new SimpleSoundInstance(p.sound(), SoundSource.PLAYERS, gain, net.minecraft.util.Mth.clamp(p.pitch(), .25F, 4F),
                RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, p.x(), p.y(), p.z(), false);
        int ttl = NativeArsenalLibrary.positionalSound(p.sound()) ? 400
                : StreakSounds.mortarOneShot(p.sound()) ? 60
                        : p.sound().equals(StreakSounds.AIRSTRIKE_FIRE.get().getLocation()) ? 200 : 260; // 原游戏轰炸机的接近音效长度为 10.4 秒。
        if (SOUNDS.size() >= 16)
            mc.getSoundManager().stop(SOUNDS.remove(0).sound());
        mc.getSoundManager().play(sound);
        SOUNDS.add(new Active(sound, world.getGameTime() + ttl));
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
        var mc = Minecraft.getInstance();
        if (mc.level != world || mc.player == null) {
            clear();
            return;
        }
        if (world != null)
            SOUNDS.removeIf(s -> {
                if (world.getGameTime() <= s.expires())
                    return false;
                mc.getSoundManager().stop(s.sound());
                return true;
            });
    }

    private static void clear() {
        var manager = Minecraft.getInstance().getSoundManager();
        SOUNDS.forEach(s -> manager.stop(s.sound()));
        SOUNDS.clear();
        world = null;
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }
}
