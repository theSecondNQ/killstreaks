package net.killstreakcod.audio;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.render.MgbEffects;
import net.killstreakcod.universal.NativeArsenalLibrary;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;

/**
 * One stoppable source spin loop for each tracked wielder; no global stop-sound packets.
 * 每名被跟踪的持枪者只对应一个可停止的原资源旋转循环音，不发送全局停音消息。
 */
@EventBusSubscriber(modid = KillstreakcodMod.MODID, value = Dist.CLIENT)
public final class NativeMinigunAudio {
    private static ClientLevel world;
    private static final Map<Integer, Loop> LOOPS = new HashMap<>();

    private NativeMinigunAudio() {}

    private static boolean using(Player player) {
        return player.isAlive() && player.isUsingItem() && player.getUseItem().is(KillstreakcodMod.M134_MINIGUN.get());
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
        var mc = Minecraft.getInstance();
        if (mc.level != world) {
            clear();
            world = mc.level;
        }
        if (world == null || mc.player == null || !mc.player.isAlive() || MgbEffects.interruptingControl()) {
            clear();
            return;
        }
        if (mc.isPaused())
            return;
        Set<Integer> active = new HashSet<>();
        for (var p : world.players())
            if (using(p) && p.distanceToSqr(mc.player) < 96 * 96) {
                active.add(p.getId());
                if (!LOOPS.containsKey(p.getId())) {
                    Loop s = new Loop(p);
                    LOOPS.put(p.getId(), s);
                    mc.getSoundManager().play(s);
                }
            }
        LOOPS.entrySet().removeIf(entry -> {
            if (active.contains(entry.getKey()) && !entry.getValue().isStopped()
                    && mc.getSoundManager().isActive(entry.getValue()))
                return false;
            mc.getSoundManager().stop(entry.getValue());
            return true;
        });
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        final Player player;

        Loop(Player p) {
            super(NativeAudio.sfx("m134_spinloop", StreakSounds.SPIN.get()), SoundSource.PLAYERS,
                    RandomSource.create());
            player = p;
            looping = true;
            delay = 0;
            volume = .36F;
            pitch = 1;
            attenuation = Attenuation.LINEAR;
            tick();
        }

        @Override
        public void tick() {
            if (player.level() != world || !using(player)) {
                stop();
                return;
            }
            volume = player.getTicksUsingItem() < NativeArsenalLibrary.CHARGE_TICKS ? .12F : .36F;
            x = player.getX();
            y = player.getEyeY();
            z = player.getZ();
        }
    }

    public static void clear() {
        var manager = Minecraft.getInstance().getSoundManager();
        LOOPS.values().forEach(manager::stop);
        LOOPS.clear();
        world = null;
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }
}
