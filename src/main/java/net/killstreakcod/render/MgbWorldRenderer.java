package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.render.compat.OptionalShaderCompat;
import net.killstreakcod.universal.NuclearPattern;

import java.util.Map;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** 程序生成的三组蘑菇状粒子云；只用纯白像素提交径向渐变粒子，不绘制核爆序列帧贴图。 */
@EventBusSubscriber(modid = KillstreakcodMod.MODID, value = Dist.CLIENT)
public final class MgbWorldRenderer {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID,
            "textures/entity/thermal_white.png");

    private MgbWorldRenderer() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || OptionalShaderCompat.shadowPass()
                || !MgbEffects.active() && !MgbWarning.active())
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var buffers = mc.renderBuffers().bufferSource();
        buffers.endBatch();
        var matrix = RenderSystem.getModelViewStack();
        matrix.pushMatrix().set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        PoseStack pose = new PoseStack();
        Vec3 eye = event.getCamera().getPosition();
        try {
            float left = MgbWarning.remaining(partial);
            if (MgbWarning.active() && left > 0 && left <= 50)
                bombs(pose, buffers, eye, left);
            if (MgbEffects.active()) {
                float time = MgbEffects.age(partial);
                int index = 0;
                for (Vec3 center : NuclearPattern.centers(MgbEffects.center()))
                    cloud(pose, buffers, eye, center, time, index++);
            }
        } finally {
            try {
                buffers.endBatch();
            } finally {
                matrix.popMatrix();
                RenderSystem.applyModelViewMatrix();
            }
        }
    }

    private static void bombs(PoseStack pose, MultiBufferSource buffers, Vec3 eye, float left) {
        ImportedCastModel model = ImportedCastModel.get("mgb_bomb");
        if (model == null)
            return;
        float progress = Mth.clamp(1 - left / 50F, 0, 1);
        for (Vec3 center : NuclearPattern.centers(MgbWarning.center())) {
            Vec3 point = center.add((1 - progress) * -24, (1 - progress * progress) * 220, 0);
            pose.pushPose();
            pose.translate(point.x - eye.x, point.y - eye.y, point.z - eye.z);
            pose.mulPose(Axis.XP.rotationDegrees(-90));
            model.render(pose, buffers, 15728880, OverlayTexture.NO_OVERLAY, Map.of());
            pose.popPose();
        }
    }

    private static void cloud(PoseStack pose, MultiBufferSource buffers, Vec3 eye, Vec3 center, float time, int index) {
        float grow = 1 - (float) Math.exp(-time / 42F), height = 6 + 94 * grow;
        float radius = 5 + 33 * grow;
        float fade = Mth.clamp((MgbEffects.DURATION - time) / 80F, 0, 1);
        // 茎部逐段抬升，顶帽沿径向翻卷，低空尘环独立扩散。
        for (int i = 0; i < 30; i++) {
            double t = i / 29D, angle = i * 2.39996 + index * 1.7 + time * .006;
            double spread = (2 + 3 * Math.sin(t * Math.PI)) * grow;
            Vec3 at = center.add(Math.cos(angle) * spread, t * height, Math.sin(angle) * spread);
            puff(pose, buffers, eye, at, (float) (5 + t * 4 + Math.sin(i * 2.1) * 1.2), color(time, i), fade * .75F,
                    time < 18);
        }
        for (int i = 0; i < 70; i++) {
            double t = Math.sqrt((i + .5) / 70D), a = i * 2.39996 + index + time * .004;
            double r = radius * t, curl = Math.sin(time * .035 - t * Math.PI) * 3 * grow;
            Vec3 at = center.add(Math.cos(a) * r, height + (1 - t * t) * radius * .38 + curl, Math.sin(a) * r);
            puff(pose, buffers, eye, at, 6 + 5 * grow + (float) Math.sin(i * 3.1), color(time + (float) t * 16, i + 31),
                    fade * .88F, time < 15);
        }
        if (time < 150)
            for (int i = 0; i < 32; i++) {
                double a = i * Math.PI * 2 / 32, r = 5 + time * .9;
                puff(pose, buffers, eye, center.add(Math.cos(a) * r, 2 + grow * 3, Math.sin(a) * r), 5 + time * .05F,
                        0x807969, fade * Math.max(0, 1 - time / 150) * .5F, false);
            }
    }

    private static int color(float time, int seed) {
        float ash = Mth.clamp(time / 65F, 0, 1), variation = (float) Math.sin(seed * 7.3) * 12;
        int r = Mth.clamp(Math.round(Mth.lerp(ash, 255, 172) + variation), 0, 255);
        int g = Mth.clamp(Math.round(Mth.lerp(ash, 175, 166) + variation), 0, 255);
        int b = Mth.clamp(Math.round(Mth.lerp(ash, 70, 153) + variation), 0, 255);
        return r << 16 | g << 8 | b;
    }

    private static void puff(PoseStack pose, MultiBufferSource buffers, Vec3 eye, Vec3 at, float radius, int rgb,
            float alpha, boolean glow) {
        if (alpha <= .005F || at.distanceToSqr(eye) > 1024D * 1024)
            return;
        pose.pushPose();
        try {
            pose.translate(at.x - eye.x, at.y - eye.y, at.z - eye.z);
            pose.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
            VertexConsumer out = buffers.getBuffer(
                    glow ? RenderType.entityTranslucentEmissive(WHITE, false) : RenderType.entityTranslucent(WHITE));
            int middle = Math.round(alpha * 255) << 24 | rgb, edge = rgb;
            for (int i = 0; i < 12; i++) {
                double a = i * Math.PI * 2 / 12, b = (i + 1) * Math.PI * 2 / 12;
                vertex(out, pose, 0, 0, middle);
                vertex(out, pose, (float) Math.cos(a) * radius, (float) Math.sin(a) * radius, edge);
                vertex(out, pose, (float) Math.cos(b) * radius, (float) Math.sin(b) * radius, edge);
                vertex(out, pose, 0, 0, middle);
            }
        } finally {
            pose.popPose();
        }
    }

    private static void vertex(VertexConsumer out, PoseStack pose, float x, float y, int color) {
        out.addVertex(pose.last().pose(), x, y, 0).setColor(color).setUv(.5F, .5F).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880).setNormal(pose.last(), 0, 0, 1);
    }
}
