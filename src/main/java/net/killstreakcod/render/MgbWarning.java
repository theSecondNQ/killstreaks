package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.audio.StreakSounds;
import net.killstreakcod.universal.MgbCountdownPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Original MWII warning layers, driven by the server rather than action-bar messages.
 * 使用原 MWII 警告图层，由服务端时间线驱动，而非动作栏文字消息。
 */
@EventBusSubscriber(modid = KillstreakcodMod.MODID, value = Dist.CLIENT)
public final class MgbWarning {
    private static ClientLevel world;
    private static UUID strike;
    private static long sampledAt;
    private static final MgbCountdownClock CLOCK = new MgbCountdownClock();
    private static String caller = "";
    private static Vec3 center = Vec3.ZERO;
    private static boolean incoming, bombs, detonated;
    private static final List<SoundInstance> SOUNDS = new ArrayList<>();
    private static SoundInstance siren, countdown, drone;

    private MgbWarning() {}

    public static Vec3 center() {
        return center;
    }

    public static boolean active() {
        return world != null && world == Minecraft.getInstance().level && strike != null;
    }

    public static float remaining(float partial) {
        return active() && !detonated ? CLOCK.remaining(world.getGameTime(), partial) : 0;
    }

    public static void accept(MgbCountdownPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !mc.level.dimension().location().equals(payload.dimension()))
            return;
        if (payload.remaining() == Integer.MIN_VALUE) {
            clear();
            return;
        }
        if (payload.remaining() < -20 || payload.remaining() > 200 || !Double.isFinite(payload.x())
                || !Double.isFinite(payload.y()) || !Double.isFinite(payload.z()))
            return;
        boolean fresh = world != mc.level || !payload.strike().equals(strike);
        if (fresh) {
            clear();
            world = mc.level;
            strike = payload.strike();
            incoming = bombs = false;
            detonated = payload.remaining() <= 0;
            if (!detonated) {
                siren = play(StreakSounds.MGB_SIREN.get(), .60F);
                drone = play(StreakSounds.MGB_DRONE.get(), .65F);
            }

            // 晚加入的观察者不应从头听完整十秒提示音，以免与当前倒计时不同步。
            if (payload.remaining() >= 198)
                countdown = play(StreakSounds.MGB_COUNTDOWN.get(), .80F);
        }
        if (!CLOCK.synchronize(payload.gameTime(), payload.remaining(), mc.level.getGameTime()))
            return;
        sampledAt = mc.level.getGameTime();
        center = new Vec3(payload.x(), payload.y(), payload.z());
        caller = payload.caller();
        if (payload.remaining() <= 0)
            detonated();
        else if (!detonated)
            incomingAudio();
    }

    private static SoundInstance play(SoundEvent event, float volume) {
        var sound = SimpleSoundInstance.forUI(event, 1, volume);
        Minecraft.getInstance().getSoundManager().play(sound);
        SOUNDS.add(sound);
        return sound;
    }

    private static void incomingAudio() {
        float left = remaining(0);
        if (left <= 160 && !incoming) {
            incoming = true;
            play(StreakSounds.MGB_INCOMING.get(), .70F);
        }
        if (left <= 120 && !bombs) {
            bombs = true;
            play(StreakSounds.MGB_BOMBS.get(), .70F);
        }
    }

    public static void detonated() {
        detonated = true;
        var manager = Minecraft.getInstance().getSoundManager();
        for (var sound : new SoundInstance[] { siren, countdown, drone })
            if (sound != null) {
                manager.stop(sound);
                SOUNDS.remove(sound);
            }
    }

    public static void clear() {
        var manager = Minecraft.getInstance().getSoundManager();
        for (var sound : SOUNDS)
            manager.stop(sound);
        SOUNDS.clear();
        world = null;
        strike = null;
        CLOCK.reset();
        incoming = bombs = detonated = false;
        siren = countdown = drone = null;
        caller = "";
        center = Vec3.ZERO;
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
        MgbEffects.clear();
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (world == null)
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != world) {
            clear();
            return;
        }
        if (mc.isPaused())
            return;
        if (!detonated)
            incomingAudio();

        // 引爆时立即隐藏倒计时；短暂保留位置供世界特效使用，随后释放状态。
        if ((detonated && world.getGameTime() - sampledAt > 45) || world.getGameTime() - sampledAt > 200)
            clear();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void hud(RenderGuiEvent.Pre event) {
        if (Minecraft.getInstance().screen != null)
            return;
        draw(event.getGuiGraphics(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }

    @SubscribeEvent
    public static void screen(ScreenEvent.Render.Post event) {
        draw(event.getGuiGraphics(), event.getPartialTick());
    }

    private static void draw(GuiGraphics graphics, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || mc.options.hideGui)
            return;

        if (detonated)
            return;
        if (mc.isPaused())
            partial = 0;
        NukeUiRenderer.render(graphics, CLOCK.elapsedMilliseconds(world.getGameTime(), partial), remaining(partial),
                caller);
    }
}
