package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.audio.StreakSounds;
import net.killstreakcod.universal.MgbEffectPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;

/**
 * World-anchored procedural nuclear clouds, flash, shock, drifting embers and audio tail.
 * 在世界固定位置绘制程序核爆粒子云、闪光、冲击、飘散余烬及尾声音效。
 */
@EventBusSubscriber(modid = KillstreakcodMod.MODID, value = Dist.CLIENT)
public final class MgbEffects {
    public static final int DURATION = 280;
    private static ClientLevel world;
    private static UUID strike;
    private static Vec3 center = Vec3.ZERO;
    private static int age = DURATION;
    private static final List<SoundInstance> SOUNDS = new ArrayList<>();

    private MgbEffects() {}

    public static boolean active() {
        return world != null && world == Minecraft.getInstance().level && age < DURATION;
    }

    public static boolean interruptingControl() {
        return active() && age < 20;
    }

    public static Vec3 center() {
        return center;
    }

    public static float age(float partial) {
        return age + partial;
    }

    public static void start(MgbEffectPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !mc.level.dimension().location().equals(payload.dimension()) || payload.age() < 0
                || payload.age() >= DURATION || !Double.isFinite(payload.x()) || !Double.isFinite(payload.y())
                || !Double.isFinite(payload.z()))
            return;
        if (world == mc.level && payload.strike().equals(strike))
            return;
        clear();
        world = mc.level;
        strike = payload.strike();
        center = new Vec3(payload.x(), payload.y(), payload.z());
        age = payload.age();
        MgbWarning.detonated();
        TacticalClient.resetControl();
        TacticalClient.impact(3);
        if (age < 12) {
            play(StreakSounds.MGB_IMPACT.get(), .90F);
            play(StreakSounds.MGB_BASS.get(), .80F);
            play(StreakSounds.MGB_SURROUND.get(), .60F);
        }
        play(StreakSounds.MGB_FIRE.get(), .60F);
        for (Vec3 point : net.killstreakcod.universal.NuclearPattern.centers(center)) {
            world.addParticle(ParticleTypes.EXPLOSION_EMITTER, true, point.x, point.y + 3, point.z, 0, 0, 0);
            world.addParticle(ParticleTypes.FLASH, true, point.x, point.y + 8, point.z, 0, 0, 0);
        }
    }

    private static void play(SoundEvent event, float volume) {
        SoundInstance sound = SimpleSoundInstance.forUI(event, 1, volume);
        SOUNDS.add(sound);
        Minecraft.getInstance().getSoundManager().play(sound);
    }

    public static void clear() {
        for (var sound : SOUNDS)
            Minecraft.getInstance().getSoundManager().stop(sound);
        SOUNDS.clear();
        world = null;
        strike = null;
        age = DURATION;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
        if (world == null)
            return;
        Minecraft mc = Minecraft.getInstance();

        // 同一世界中玩家实例更换时继续保留视觉效果，切换维度时则释放所有声音。
        if (mc.level != world) {
            clear();
            return;
        }
        if (mc.isPaused())
            return;
        if (age >= DURATION) {
            clear();
            return;
        }
        int tick = age++;
        var random = world.random;
        if (tick == 12 || tick == 28)
            TacticalClient.impact(tick == 12 ? 2.6F : 1.9F);
        if (tick < 28) {
            double radius = 5 + tick * .90;
            for (int n = 0; n < 5; n++) {
                double angle = random.nextDouble() * Math.PI * 2;
                world.addParticle(ParticleTypes.EXPLOSION, true, center.x + Math.cos(angle) * radius,
                        center.y + 3 + random.nextDouble() * radius, center.z + Math.sin(angle) * radius, 0, 0, 0);
            }
        }
        if (tick >= 8 && tick < 95 && tick % 3 == 0) {
            double radius = (tick - 8) * 2.8;
            for (int n = 0; n < 32; n++) {
                double a = n * Math.PI * 2 / 32;
                world.addParticle(ParticleTypes.CLOUD, true, center.x + Math.cos(a) * radius,
                        center.y + 1.3 + random.nextDouble() * 3, center.z + Math.sin(a) * radius, Math.cos(a) * .45,
                        .025, Math.sin(a) * .45);
            }
        }
        if (tick > 10 && tick < 220 && mc.player != null) {
            Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
            for (int n = 0; n < (tick < 90 ? 5 : 2); n++) {
                double x = eye.x + (random.nextDouble() - .5) * 24, y = eye.y + random.nextDouble() * 10,
                        z = eye.z + (random.nextDouble() - .5) * 24;
                world.addParticle(n % 3 == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.ASH, true, x, y, z, .02, -.025,
                        .01);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void flash(RenderGuiEvent.Pre event) {
        if (Minecraft.getInstance().screen != null)
            return;
        drawFlash(event.getGuiGraphics(), event.getPartialTick());
    }

    @SubscribeEvent
    public static void screenFlash(ScreenEvent.Render.Post event) {
        drawFlash(event.getGuiGraphics(), event.getPartialTick());
    }

    private static void drawFlash(GuiGraphics graphics, float partial) {
        if (!active())
            return;
        float time = age + partial, effects = Minecraft.getInstance().options.screenEffectScale().get().floatValue();

        // 先播放暖色初始冲击，再出现白色闪光；不得关闭或替换死亡界面。
        float flash = time < 4 ? .35F + time * .16F : time < 13 ? 1 : Mth.clamp(1 - (time - 13) / 34F, 0, 1);
        int alpha = (int) (flash * 238 * effects);
        if (alpha > 0)
            UiDraw.fill(graphics, "MgbEffects.drawFlash.0", 0, 0, graphics.guiWidth(), graphics.guiHeight(),
                    alpha << 24 | 0x00fff5dd);
        int dust = (int) (Mth.clamp((time - 20) / 25F, 0, 1) * Mth.clamp(1 - (time - 100) / 160F, 0, 1) * 42 * effects);
        if (dust > 0)
            UiDraw.fill(graphics, "MgbEffects.drawFlash.1", 0, 0, graphics.guiWidth(), graphics.guiHeight(),
                    dust << 24 | 0x006c675c);
        int glow = (int) (Math.max(0, 1 - time / 100) * 45 * effects);
        if (glow > 0)
            UiDraw.fill(graphics, "MgbEffects.drawFlash.2", 0, 0, graphics.guiWidth(), graphics.guiHeight(),
                    glow << 24 | 0x00e86d39);
        if (effects <= 0 || time < 15)
            return;
        for (int n = 0; n < 26; n++) {
            float seed = hash(n * 91 + 3), speed = .5F + hash(n * 27 + 11) * .7F;
            int x = (int) ((seed * graphics.guiWidth() + time * (hash(n + 6) - .5F) * .85F + graphics.guiWidth() * 2)
                    % graphics.guiWidth());
            int y = (int) ((hash(n * 43 + 7) * graphics.guiHeight() + time * speed) % graphics.guiHeight());
            int a = (int) (Math.max(0, 1 - time / DURATION) * effects * (n % 3 == 0 ? 140 : 60));
            int color = a << 24 | (n % 3 == 0 ? 0x00ffc482 : 0x002e2b28);
            UiDraw.fill(graphics, "MgbEffects.drawFlash.3", x, y, x + 1, y + (n % 3 == 0 ? 2 : 1), color);
        }
    }

    private static float hash(int n) {
        int v = n * 73428767 ^ 91227131;
        v = (v ^ (v >>> 13)) * 1274126177;
        return (v & 0x7fffffff) / (float) Integer.MAX_VALUE;
    }
}
