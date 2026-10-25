package net.killstreakcod.render;

import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** 将完整模型旋转后的包围盒居中放入一个物品栏格，避免源模型原点偏移导致图标消失。 */
public final class InventoryModel {
    private InventoryModel() {}

    public static boolean render(String id, PoseStack pose, MultiBufferSource buffers) {
        ImportedCastModel model = ImportedCastModel.get(id);
        if (model == null)
            return false;
        Vector3f min = model.boundsMin(), max = model.boundsMax();
        Matrix4f rotation = new Matrix4f().rotateX((float) Math.toRadians(20)).rotateY((float) Math.toRadians(-45));
        Vector3f lower = new Vector3f(Float.POSITIVE_INFINITY), upper = new Vector3f(Float.NEGATIVE_INFINITY);
        for (int i = 0; i < 8; i++) {
            Vector3f point = rotation.transformPosition(new Vector3f((i & 1) == 0 ? min.x : max.x,
                    (i & 2) == 0 ? min.y : max.y, (i & 4) == 0 ? min.z : max.z));
            lower.min(point);
            upper.max(point);
        }
        float scale = .86F / Math.max(.01F, Math.max(upper.x - lower.x, upper.y - lower.y));
        Vector3f center = new Vector3f(lower).add(upper).mul(.5F);
        pose.pushPose();
        try {
            pose.translate(.5, .5, .5);
            pose.scale(scale, scale, scale);
            pose.translate(-center.x, -center.y, -center.z);
            pose.mulPose(rotation);
            model.render(pose, buffers, 15728880, OverlayTexture.NO_OVERLAY, Map.of());
        } finally {
            pose.popPose();
        }
        return true;
    }
}
