package net.killstreakcod.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.render.tablet.TabletHandModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/** 小型原资源枪焰紧贴动画中的 tag_flash；只绘制枪口附近，不生成覆盖摄像机的烟雾。 */
public final class MinigunMuzzleEffects {
    private static final ResourceLocation SIDE = asset("textures/csj22/773922a6ec3edba8.png");
    private static final ResourceLocation FRONT = asset("textures/csj22/516ef7f117579956.png");

    private MinigunMuzzleEffects() {}

    private static ResourceLocation asset(String path) {
        return ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, path);
    }

    public static void render(PoseStack pose, MultiBufferSource buffers, Matrix4f socket, float ticks) {
        if (socket == null)
            return;
        pose.pushPose();
        try {
            var transform = TabletHandModel.sourceCameraMatrix(socket);
            pose.mulPoseMatrix(transform);
            pose.last().normal().mul(new org.joml.Matrix3f(transform));
            float pulse = .75F + .25F * Math.abs((float) Math.sin(ticks * 9));
            float radius = .045F * pulse, length = .24F * pulse;
            int frame = Math.floorMod((int) (ticks * 3), 8);
            float u = (frame % 4) / 4F, v = (frame / 4) / 2F;
            // 图集每格单独采样，并避开边缘，避免把邻格火焰带进画面。
            float u0 = u + .75F / 512, u1 = u + .25F - .75F / 512;
            float v0 = v + .75F / 512, v1 = v + .5F - .75F / 512;
            VertexConsumer side = buffers.getBuffer(RenderType.entityTranslucentEmissive(SIDE, false));
            strip(side, pose, .005F, length, radius, false, u0, v0, u1, v1, 0xbfffffff);
            strip(side, pose, .005F, length, radius, true, u0, v0, u1, v1, 0xbfffffff);

            VertexConsumer front = buffers.getBuffer(RenderType.entityTranslucentEmissive(FRONT, false));
            float fv0 = v + .75F / 256, fv1 = v + .5F - .75F / 256;
            vertex(front, pose, .025F, -radius, -radius, u0, fv1, 0xaaffffff);
            vertex(front, pose, .025F, radius, -radius, u1, fv1, 0xaaffffff);
            vertex(front, pose, .025F, radius, radius, u1, fv0, 0xaaffffff);
            vertex(front, pose, .025F, -radius, radius, u0, fv0, 0xaaffffff);

            // 弹迹由 TracerEffects 按发射时的世界坐标绘制；手持层只保留枪口火焰，避免出现随视角摆动的第二条射流。
        } finally {
            pose.popPose();
        }
    }

    private static void strip(VertexConsumer out, PoseStack pose, float start, float end, float radius, boolean acrossY,
            float u0, float v0, float u1, float v1, int color) {
        float y = acrossY ? radius : 0, z = acrossY ? 0 : radius;
        vertex(out, pose, start, -y, -z, u0, v1, color);
        vertex(out, pose, end, -y, -z, u0, v0, color);
        vertex(out, pose, end, y, z, u1, v0, color);
        vertex(out, pose, start, y, z, u1, v1, color);
    }

    private static void vertex(VertexConsumer out, PoseStack pose, float x, float y, float z, float u, float v,
            int color) {
        out.vertex(pose.last().pose(), x, y, z).color(color).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(15728880).normal(pose.last().normal(), 0, 1, 0).endVertex();
    }
}
