package net.killstreakcod.render;

import net.killstreakcod.render.compat.OptionalShaderCompat;

import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.joml.Vector3f;

/**
 * Separate original view/world targeting props, native LODs and original hand attachment tags.
 * 分别使用原第一人称和世界中的指示设备、原细节级别模型及手部挂接点。
 */
public final class NativeTargetingDevices {
    private NativeTargetingDevices() {}

    private static String id(ItemStack stack, boolean view) {
        String reward = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString("Reward");
        return (reward.equals("mortar_strike") ? "designator_mortar" : "designator_precision") + (view ? "_vm" : "");
    }

    public static boolean renderItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        if (context == ItemDisplayContext.GUI)
            return false;
        String base = id(stack, context.firstPerson());
        ImportedCastModel model = ImportedCastModel.get(base);
        if (model == null)
            return false;
        String level = "";
        if (OptionalShaderCompat.shadowPass())
            level = context.firstPerson() ? "_lod1" : "_lod3";
        else if (!context.firstPerson() && context != ItemDisplayContext.NONE) {
            float distance = pose.last().pose().getTranslation(new Vector3f()).length();
            if (distance >= 20)
                level = "_lod2";
            else if (distance >= 6)
                level = "_lod1";
        }
        if (!level.isEmpty()) {
            ImportedCastModel lower = ImportedCastModel.get(base + level);
            if (lower != null)
                model = lower;
        }
        pose.pushPose();
        try {
            pose.translate(.5, .5, .5);
            boolean third = context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                    || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
            if (third)
                attachToHand(model, context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND, pose);
            model.render(pose, buffers, light, overlay, Map.of());
        } finally {
            pose.popPose();
        }
        return true;
    }

    private static void attachToHand(ImportedCastModel model, boolean left, PoseStack pose) {
        String tag = left ? "tag_ik_loc_le" : "tag_ik_loc_ri";
        ImportedCastModel.Bone anchor = null;
        for (var bone : model.bindBones())
            if (bone.name().equals(tag)) {
                anchor = bone;
                break;
            }
        if (anchor == null)
            for (var bone : model.bindBones())
                if (bone.name().equals("tag_ik_loc_ri")) {
                    anchor = bone;
                    break;
                }
        if (anchor == null)
            return;
        Vector3f offset = new Vector3f(anchor.worldPosition()).sub(model.sourceOrigin());
        float scale = model.sourceUnitScale();

        // 移除原物品原点偏移后，再减去坐标变换 C=(-Y,Z,-X)。
        pose.translate(offset.y * scale, -offset.z * scale, offset.x * scale);
    }

    /**
     * Source VM also replaces the old placeholder during the direct device raise animation.
     * 直接抬起设备的动画中也使用原第一人称模型，替换旧占位模型。
     */
    public static boolean renderFirstPerson(ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light,
            int overlay) {
        ImportedCastModel model = ImportedCastModel.get(id(stack, true));
        if (model == null)
            return false;
        model.render(pose, buffers, light, overlay, Map.of());
        return true;
    }
}
