package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;

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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/**
 * Camera-distance-independent original JDAM mesh and nuclear 32-frame material adapter.
 * 使用不随摄像机距离缩放的原 JDAM 网格，并适配 32 帧核爆材质。
 */
@EventBusSubscriber(modid = KillstreakcodMod.MODID, value = Dist.CLIENT)
public final class MgbWorldRenderer {
    private static final ResourceLocation PYRO = texture("pyro"), EMISSION = texture("pyro_emission"),
            DUST = texture("dust");

    private MgbWorldRenderer() {}

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "textures/mw/mgb/" + name + ".png");
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL
                || (!MgbEffects.active() && !MgbWarning.active()))
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return;
        float partial = event.getPartialTick();
        var buffers = mc.renderBuffers().bufferSource();
        buffers.endBatch();
        var matrix = RenderSystem.getModelViewStack();
        matrix.pushPose();
        matrix.last().pose().set(WorldRenderMatrices.view(event));
        RenderSystem.applyModelViewMatrix();
        float[] color = RenderSystem.getShaderColor().clone();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        PoseStack pose = new PoseStack();
        Vec3 eye = event.getCamera().getPosition();
        try {
            float left = MgbWarning.remaining(partial);
            if (MgbWarning.active() && left > 0 && left <= 50)
                bombs(pose, buffers, eye, left);
            if (MgbEffects.active()) {
                float t = MgbEffects.age(partial), fade = Mth.clamp((MgbEffects.DURATION - t) / 65F, 0, 1);
                int frame = Math.min(31, (int) (t / 5));
                float u = (frame % 8) / 8F, v = (frame / 8) / 4F;
                Vec3 center = MgbEffects.center();
                float height = 18 + Mth.clamp(t / 60F, 0, 1) * 86, width = height * 1.10F;
                billboard(pose, buffers, eye, center.add(0, height * .5 - 2, 0), width, height, u, v, .125F, .25F, fade,
                        PYRO, false, 0xffffff);
                billboard(pose, buffers, eye, center.add(0, height * .5 - 2, 0), width, height, u, v, .125F, .25F,
                        fade * .90F, EMISSION, true, 0xffffff);
                if (t >= 8 && t < 140) {
                    float radius = (t - 8) * 2.6F, alpha = Mth.clamp(1 - (t - 45) / 95F, 0, 1) * .33F;
                    for (int n = 0; n < 24; n++) {
                        double a = n * Math.PI * 2 / 24;
                        Vec3 at = center.add(Math.cos(a) * radius, 4 + Math.sin(n * 2.3) * 2, Math.sin(a) * radius);
                        billboard(pose, buffers, eye, at, 18 + t * .22F, 12 + t * .12F, 0, 0, 1, 1, alpha, DUST, false,
                                0xb2a18a);
                    }
                }
            }
        } finally {
            try {
                buffers.endBatch();
            } finally {
                matrix.popPose();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            }
        }
    }

    private static void bombs(PoseStack pose, MultiBufferSource buffers, Vec3 eye, float left) {
        ImportedCastModel model = ImportedCastModel.get("mgb_bomb");
        if (model == null)
            return;
        float progress = 1 - left / 50F;
        Vec3 center = MgbWarning.center();
        for (int n = 0; n < 7; n++) {
            double angle = n * Math.PI * 2 / 7, spread = n == 0 ? 0 : 24;
            Vec3 point = center.add(Math.cos(angle) * spread + (1 - progress) * 16,
                    5 + (1 - Math.pow(progress, 1.25)) * 140 + n * 1.4, Math.sin(angle) * spread);
            pose.pushPose();
            pose.translate(point.x - eye.x, point.y - eye.y, point.z - eye.z);
            pose.mulPose(Axis.XP.rotationDegrees(-90));
            model.render(pose, buffers, 15728880, OverlayTexture.NO_OVERLAY, Map.of());
            pose.popPose();
        }
    }

    private static void billboard(PoseStack pose, MultiBufferSource buffers, Vec3 eye, Vec3 at, float width,
            float height, float u, float v, float du, float dv, float alpha, ResourceLocation texture, boolean emissive,
            int rgb) {
        if (alpha <= 0)
            return;
        pose.pushPose();
        pose.translate(at.x - eye.x, at.y - eye.y, at.z - eye.z);
        float yaw = (float) Math.atan2(eye.x - at.x, eye.z - at.z);
        pose.mulPose(Axis.YP.rotation(yaw));
        VertexConsumer out = buffers.getBuffer(emissive ? RenderType.entityTranslucentEmissive(texture, false)
                : RenderType.entityTranslucent(texture));
        int color = (Math.round(Mth.clamp(alpha, 0, 1) * 255) << 24) | rgb;
        vertex(out, pose, -width / 2, -height / 2, u, v + dv, color);
        vertex(out, pose, width / 2, -height / 2, u + du, v + dv, color);
        vertex(out, pose, width / 2, height / 2, u + du, v, color);
        vertex(out, pose, -width / 2, height / 2, u, v, color);
        pose.popPose();
    }

    private static void vertex(VertexConsumer out, PoseStack pose, float x, float y, float u, float v, int color) {
        out.vertex(pose.last().pose(), x, y, 0).color(color).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(15728880).normal(pose.last().normal(), 0, 0, 1).endVertex();
    }
}
