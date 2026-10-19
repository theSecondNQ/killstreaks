package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.audio.StreakSounds;
import net.killstreakcod.universal.MgbCountdownPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;

/**
 * Original MWII warning layers, driven by the server rather than action-bar messages.
 * 使用原 MWII 警告图层，由服务端时间线驱动，而非动作栏文字消息。
 */
@EventBusSubscriber(modid = KillstreakcodMod.MODID, value = Dist.CLIENT)
public final class MgbWarning {
    private static ClientLevel world;
    private static UUID strike;
    private static long sampledAt, lastServerTime;
    private static int remaining = 0, age;
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
        return active() ? Math.max(0, remaining - Mth.clamp(world.getGameTime() - sampledAt + partial, 0, 20)) : 0;
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
        if (!fresh && payload.gameTime() < lastServerTime)
            return;
        if (fresh) {
            clear();
            world = mc.level;
            strike = payload.strike();
            age = 0;
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
        lastServerTime = payload.gameTime();
        sampledAt = mc.level.getGameTime();
        remaining = payload.remaining();
        center = new Vec3(payload.x(), payload.y(), payload.z());
        caller = payload.caller();
        if (!detonated)
            incomingAudio();
    }

    private static SoundInstance play(SoundEvent event, float volume) {
        var sound = SimpleSoundInstance.forUI(event, 1, volume);
        Minecraft.getInstance().getSoundManager().play(sound);
        SOUNDS.add(sound);
        return sound;
    }

    private static void incomingAudio() {
        if (remaining <= 160 && !incoming) {
            incoming = true;
            play(StreakSounds.MGB_INCOMING.get(), .70F);
        }
        if (remaining <= 120 && !bombs) {
            bombs = true;
            play(StreakSounds.MGB_BOMBS.get(), .70F);
        }
    }

    public static void detonated() {
        detonated = true;
        remaining = 0;
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
        remaining = age = 0;
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
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
        if (world == null)
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != world) {
            clear();
            return;
        }
        if (mc.isPaused())
            return;
        age++;
        if (!detonated)
            incomingAudio();

        // 致命快照对应的服务端状态会在一秒后移除，爆炸后的提示条随后淡出。
        if ((detonated && world.getGameTime() - sampledAt > 45) || world.getGameTime() - sampledAt > 200)
            clear();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void hud(RenderGuiEvent.Pre event) {
        if (Minecraft.getInstance().screen != null)
            return;
        draw(event.getGuiGraphics(), event.getPartialTick());
    }

    @SubscribeEvent
    public static void screen(ScreenEvent.Render.Post event) {
        draw(event.getGuiGraphics(), event.getPartialTick());
    }

    private static void draw(GuiGraphics graphics, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || mc.options.hideGui)
            return;

        // 参考画面在顶部附近有一条窄红色提示带，数字位于下方色带。
        float slide = Mth.clamp((age + partial) / 8F, 0, 1);
        slide = 1 - (1 - slide) * (1 - slide) * (1 - slide);
        int width = Math.min(graphics.guiWidth() - 12, Math.max(150, Math.round(graphics.guiWidth() * .70F))),
                height = 27;
        int x = (graphics.guiWidth() - width) / 2, y = 4 - Math.round((1 - slide) * 35);
        float left = remaining(partial);
        float effects = mc.options.screenEffectScale().get().floatValue();
        float pulse = .80F + .20F * (float) Math.sin((age + partial) * (left < 60 ? .85 : .35));
        float alpha = detonated ? Mth.clamp(1 - (world.getGameTime() - sampledAt - 20) / 20F, 0, 1) : 1;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 600);
        try {
            graphics.fill(x, y, x + width, y + 16, ((int) (220 * alpha) << 24) | 0x0036161b);
            graphics.fill(x, y + 16, x + width, y + height, ((int) (210 * alpha) << 24) | 0x00893140);
            graphics.hLine(x, x + width - 1, y, ((int) (200 * alpha) << 24) | 0x00e26069);
            graphics.hLine(x, x + width - 1, y + 15, ((int) (220 * alpha) << 24) | 0x00c84b58);
            int stripes = Math.max(15, width / 2 - 65);
            graphics.enableScissor(x, y + 1, x + width, y + 16);
            try {
                int scroll = effects > 0 ? (age % 24) : 0;
                OriginalHud.region(graphics, "nuke_stripes_small", x + 3 - scroll, y + 3, stripes + 24, 11, 10, 24, 229,
                        16, 248, 64, pulse * alpha);
                OriginalHud.region(graphics, "nuke_stripes_small", x + width - stripes - 22 + scroll, y + 3,
                        stripes + 24, 11, 10, 24, 229, 16, 248, 64, pulse * alpha);
            } finally {
                graphics.disableScissor();
            }
            OriginalHud.layer(graphics, "nuke_skull", x + width / 2 - 63, y + 2, 12, 12, alpha);
            OriginalHud.layer(graphics, "nuke_skull", x + width / 2 + 51, y + 2, 12, 12, alpha);
            int color = ((int) (255 * alpha) << 24) | 0x00ffe7e8;
            if (alpha > .05F) {
                graphics.drawCenteredString(mc.font,
                        Component.translatable(
                                detonated ? "hud.killstreakcod.mgb_impact" : "hud.killstreakcod.mgb_inbound"),
                        x + width / 2, y + 4, color);
                String timer = String.format(Locale.ROOT, "%04.1f", left / 20F);
                graphics.drawCenteredString(mc.font, timer, x + width / 2, y + 18, color);
                String tag = mc.font.plainSubstrByWidth(caller, Math.max(0, width / 4 - 8));
                graphics.drawString(mc.font, tag, x + width - 5 - mc.font.width(tag), y + 18, color, false);
            }

            // 使用原资源括号装饰，确保各档 Minecraft GUI 缩放下倒计时都清晰可读。
            OriginalHud.region(graphics, "nuke_timer_bracket_top", x + width / 2 - 22, y + 16, 44, 2, 0, 8, 264, 13,
                    264, 32, alpha * .75F);
            OriginalHud.region(graphics, "nuke_timer_bracket_bottom", x + width / 2 - 22, y + height - 1, 44, 2, 0, 8,
                    264, 16, 264, 32, alpha * .75F);
        } finally {
            graphics.pose().popPose();
        }
    }
}
