package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.killstreaks.StreakReward;
import net.killstreakcod.render.tablet.TabletScreens;

import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Original CAST equipment in hand; the inventory retains the corresponding reward icon.
 * 手持时绘制原 CAST 装备模型，物品栏继续显示相应奖励图标。
 */
public final class ImportedEquipmentItemRenderer extends BlockEntityWithoutLevelRenderer {
    private final String modelId;
    private final ResourceLocation icon;

    public ImportedEquipmentItemRenderer(String modelId, String itemId) {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        this.modelId = modelId;
        icon = ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "textures/item/" + itemId + ".png");
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers,
            int light, int overlay) {
        if (modelId.equals("designator_precision")
                && NativeTargetingDevices.renderItem(stack, context, pose, buffers, light, overlay))
            return;
        String selected = modelId.equals("cluster_mine") ? (context.firstPerson() ? "cluster21_vm" : "cluster21_item")
                : modelId;
        if (modelId.equals("drop_marker") && context.firstPerson())
            selected = "drop_marker_vm";
        if (modelId.equals("designator_precision") && stack.getOrCreateTag().getString("Reward").equals("mortar_strike"))
            selected = "designator_mortar";
        Map<String, ResourceLocation> screenMaterials = modelId.equals("tablet_wheelson")
                ? TabletScreens.materialTextures("wheelson_hs")
                : Map.of();
        ImportedCastModel model = context == ItemDisplayContext.GUI ? null : ImportedCastModel.get(selected);
        if (model != null && (selected.equals("cluster_mine") || selected.equals("drop_marker_vm"))) {
            ImportedCastModel near = ImportedCastModel.get(selected + "_lod_near");
            if (near != null)
                model = near;
        }
        if (model == null) {
            ResourceLocation image = icon;
            if (context == ItemDisplayContext.GUI && (modelId.equals("drop_marker") || modelId.equals("cluster_mine")
                    || modelId.equals("designator_precision"))) {
                String reward = stack.getOrCreateTag()
                        .getString("Reward");
                String fallback = modelId.equals("drop_marker") ? "care_package"
                        : modelId.equals("cluster_mine") ? "cluster_mine" : "precision_airstrike";
                reward = StreakReward.byId(reward).map(StreakReward::id).orElse(fallback);
                image = ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID,
                        "textures/gui/streaks/" + reward + ".png");
                if (modelId.equals("designator_precision") && reward.equals("precision_airstrike"))
                    image = NativePrecisionScopeHud.inventoryIcon();
            }
            VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(image));
            quad(out, pose, 0, 0, 0, 1);
            quad(out, pose, 1, 0, 1, 1);
            quad(out, pose, 1, 1, 1, 0);
            quad(out, pose, 0, 1, 0, 0);
            return;
        }
        pose.pushPose();
        pose.translate(.5, .5, .5);
        model.render(pose, buffers, light, overlay, Map.of(), screenMaterials);
        pose.popPose();
    }

    private static void quad(VertexConsumer out, PoseStack pose, float x, float y, float u, float v) {
        out.vertex(pose.last().pose(), x, y, .5F).color(0xffffffff).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(pose.last().normal(), 0, 0, 1).endVertex();
    }
}
