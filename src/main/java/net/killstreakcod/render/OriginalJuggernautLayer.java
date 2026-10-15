package net.killstreakcod.render;

import net.killstreakcod.killstreaks.ArmorKit;

import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Original armor details fitted to, and animated by, Minecraft's six body parts.
 * 将原资源护甲细节适配到 Minecraft 的六个身体部位，并随这些部位播放动作。
 */
public final class OriginalJuggernautLayer extends RenderLayer<LivingEntity, HumanoidModel<LivingEntity>> {

    // 源资源到人形骨架的正确旋转为 (X,Y,Z) -> (Y,-Z,-X)。
    private static final Quaternionf SOURCE_TO_MODEL = new Matrix4f()
            .m00(0).m01(0).m02(-1).m10(1).m11(0).m12(0).m20(0).m21(-1).m22(0).getNormalizedRotation(new Quaternionf());
    private static final Quaternionf MODEL_TO_SOURCE = new Quaternionf(SOURCE_TO_MODEL).conjugate();
    private static final Map<EquipmentSlot, String[]> PARTS = Map.of(
            EquipmentSlot.HEAD, new String[] { "jugger_head", "jugger_helmet" },
            EquipmentSlot.CHEST,
            new String[] { "jugger_chest_base", "jugger_backpack", "jugger_neckguard", "jugger_shoulder_left",
                    "jugger_shoulder_right", "jugger_forearm_left", "jugger_forearm_right" },
            EquipmentSlot.LEGS,
            new String[] { "jugger_legs_base", "jugger_thigh_left", "jugger_thigh_right", "jugger_qamis" },
            EquipmentSlot.FEET, new String[] { "jugger_boots_base" });

    public OriginalJuggernautLayer(RenderLayerParent<LivingEntity, HumanoidModel<LivingEntity>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity, float swing,
            float amount, float partial, float age, float headYaw, float headPitch) {
        boolean equipped = false;
        for (EquipmentSlot slot : PARTS.keySet())
            if (wearing(entity, slot))
                equipped = true;
        if (!equipped)
            return;
        if (ImportedCastModel.get("jugger22_chest_base") == null || entity.isInvisible())
            return;
        Map<String, Matrix4f> bodyPose = blockPose(getParentModel());
        var viewer = Minecraft.getInstance().getCameraEntity();
        boolean distant = viewer != null && viewer.distanceToSqr(entity) > 24 * 24;
        pose.pushPose();

        // 父人形模型空间的 Y 轴向下，源场景网格则 Y 轴向上且 X 方向相反；同时转换这两轴以保留顶点绕序。
        pose.translate(0, 1.501, 0);
        pose.scale(-1, -1, 1);
        for (var entry : PARTS.entrySet())
            if (wearing(entity, entry.getKey()))
                for (String id : entry.getValue()) {
                    if (distant && !id.endsWith("_base") && !id.equals("jugger_helmet") && !id.equals("jugger_head"))
                        continue;
                    String fitted = id.replace("jugger_", "jugger22_");
                    ImportedCastModel mesh = ImportedCastModel.get(distant ? fitted.replace("_base", "_lod") : fitted);
                    if (mesh != null)
                        mesh.renderScene(pose, buffers, light, OverlayTexture.NO_OVERLAY, bodyPose);
                }
        pose.popPose();
    }

    private static boolean wearing(LivingEntity entity, EquipmentSlot slot) {
        Item expected = switch (slot) {
            case HEAD -> ArmorKit.HELMET.get();
            case CHEST -> ArmorKit.CHEST.get();
            case LEGS -> ArmorKit.LEGS.get();
            case FEET -> ArmorKit.BOOTS.get();
            default -> null;
        };
        return expected != null && entity.getItemBySlot(slot).is(expected);
    }

    private static Quaternionf sourceRotation(ModelPart part) {
        Quaternionf model = new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot);
        return new Quaternionf(MODEL_TO_SOURCE).mul(model).mul(SOURCE_TO_MODEL).normalize();
    }

    private static Map<String, Matrix4f> blockPose(HumanoidModel<?> model) {
        return Map.of("mc_body", partPose(model.body), "mc_head", partPose(model.head),
                "mc_left_arm", partPose(model.leftArm), "mc_right_arm", partPose(model.rightArm),
                "mc_left_leg", partPose(model.leftLeg), "mc_right_leg", partPose(model.rightLeg));
    }

    private static Matrix4f partPose(ModelPart part) {

        // 烘焙网格使用原版实际旋转支点，坐标单位为源资源厘米。
        return new Matrix4f().translation(-part.z * 6.25F, part.x * 6.25F, 150.1F - part.y * 6.25F)
                .rotate(sourceRotation(part));
    }
}
