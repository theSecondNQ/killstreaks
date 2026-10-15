package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.render.compat.OptionalShaderCompat;
import net.killstreakcod.render.tablet.TabletHandModel;
import net.killstreakcod.universal.NativeArsenalLibrary;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * First and third person minigun with six spinning barrels, belt box and recoil.
 * 绘制第一、第三人称机枪，包含六根旋转枪管、弹链箱和后坐效果。
 */
public final class MinigunItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID,
            "textures/entity/tactical_atlas.png");
    private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID,
            "textures/item/m134_minigun.png");
    private final ModelPart root, barrels, muzzleFlash;
    private TabletHandModel nativeHands;

    private static void box(PartDefinition parent, String name, int material, float x, float y, float z, float w,
            float h, float d) {
        parent.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(material % 4 * 128, material / 4 * 128).addBox(x, y, z, w, h, d),
                PartPose.ZERO);
    }

    public MinigunItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        MeshDefinition m = new MeshDefinition();
        PartDefinition r = m.getRoot();
        PartDefinition receiver = r.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        box(receiver, "receiver", 9, -3, -3, -3, 6, 6, 12);
        box(receiver, "motor", 1, -2.2F, -2.2F, 9, 4.4F, 4.4F, 3);
        box(receiver, "rear_cap", 3, -1.6F, -1.6F, 12, 3.2F, 3.2F, .7F);
        box(receiver, "receiver_cover", 1, -3.2F, -3.3F, -1, 6.4F, .6F, 8);
        box(receiver, "feed_box", 0, 3, -1.8F, 1, 5.8F, 6.8F, 8);
        box(receiver, "feed_lid", 8, 2.9F, -2.2F, .9F, 6, .6F, 8.2F);
        box(receiver, "feed_latch", 1, 8.8F, .2F, 4, .4F, 1.4F, 2);
        box(receiver, "carry_handle_a", 1, -1.4F, -6.2F, 2, 2.8F, 3.2F, 1);
        box(receiver, "carry_handle_b", 1, -1.4F, -6.2F, 7, 2.8F, 3.2F, 1);
        box(receiver, "carry_grip", 3, -1.6F, -6.7F, 2, 3.2F, .8F, 6);
        box(receiver, "trigger_grip", 3, -1.1F, 3, 4, 2.2F, 5, 2.8F);
        box(receiver, "trigger_guard_bottom", 1, -1.3F, 4.4F, 1.5F, 2.6F, .5F, 3);
        box(receiver, "trigger_guard_front", 1, -1.3F, 2.8F, 1.5F, 2.6F, 1.8F, .5F);
        for (int i = 0; i < 5; i++) {
            box(receiver, "cooling_vent" + i, 3, -3.15F, -1.7F, -.4F + i * 1.6F, .2F, 3.4F, .6F);
            box(receiver, "feed_round" + i, 1, 2.8F + i * .55F, 1.3F, -.3F + i * .65F, .8F, 1.5F, .6F);
            box(receiver, "feed_tip" + i, 4, 2.95F + i * .55F, 1.1F, -.15F + i * .65F, .5F, .35F, .3F);
        }
        box(receiver, "serial_plate", 14, -2.6F, -3.4F, 3, 2.6F, .15F, 1.5F);
        box(receiver, "safety", 4, -3.25F, 1.2F, 6, .25F, .7F, 1.2F);
        PartDefinition b = r.addOrReplaceChild("barrels", CubeListBuilder.create(), PartPose.offset(0, 0, -3));
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3;
            float x = (float) Math.cos(a) * 2, y = (float) Math.sin(a) * 2;
            box(b, "tube" + i, 1, x - .55F, y - .55F, -24, 1.1F, 1.1F, 24);
            box(b, "muzzle_rim" + i, 9, x - .65F, y - .65F, -24.3F, 1.3F, 1.3F, .5F);
            box(b, "bore" + i, 3, x - .35F, y - .35F, -24.4F, .7F, .7F, .2F);
        }
        PartDefinition ring = b.addOrReplaceChild("ring", CubeListBuilder.create(), PartPose.ZERO);
        for (int i = 0; i < 2; i++) {
            float z = i == 0 ? -20 : -6;
            box(ring, "upper" + i, 1, -3.2F, -3.2F, z, 6.4F, .75F, 1.1F);
            box(ring, "lower" + i, 1, -3.2F, 2.45F, z, 6.4F, .75F, 1.1F);
            box(ring, "left" + i, 1, -3.2F, -2.45F, z, .75F, 4.9F, 1.1F);
            box(ring, "right" + i, 1, 2.45F, -2.45F, z, .75F, 4.9F, 1.1F);
        }
        PartDefinition flash = r.addOrReplaceChild("muzzle_flash", CubeListBuilder.create(),
                PartPose.offset(0, 0, -28));
        box(flash, "core", 4, -.5F, -.5F, -2, 1, 1, 3);
        box(flash, "cross_a", 4, -2, -.2F, -1, 4, .4F, .8F);
        box(flash, "cross_b", 4, -.2F, -2, -1, .4F, 4, .8F);
        root = LayerDefinition.create(m, 512, 512).bakeRoot();
        barrels = root.getChild("barrels");
        muzzleFlash = root.getChild("muzzle_flash");
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffer,
            int light, int overlay) {
        String nativeId = context.firstPerson() ? "m13421_vm"
                : NativeArsenalLibrary.lod("m13421_wm",
                        context == ItemDisplayContext.GUI ? 1 : OptionalShaderCompat.shadowPass() ? 2 : 0);
        ImportedCastModel nativeGun = ImportedCastModel.get(nativeId);
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.world.entity.LivingEntity wielder = context.firstPerson() ? mc.player
                : NativeMinigunPresentation.wielder(stack);
        boolean firing = wielder != null && wielder.isUsingItem() && wielder.getUseItem() == stack;
        float t = mc.level == null ? 0 : mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(true);
        if (nativeGun != null) {
            pose.pushPose();
            try {
                pose.translate(.5, .5, .5);
                float length = nativeGun.boundsMax().z - nativeGun.boundsMin().z;
                float fit = (context == ItemDisplayContext.GUI ? .95F : 1.35F) / Math.max(.1F, length);
                if (context == ItemDisplayContext.GUI) {
                    pose.mulPose(Axis.YP.rotationDegrees(75));
                    pose.mulPose(Axis.XP.rotationDegrees(-15));
                }
                float partial = mc.getTimer().getGameTimeDeltaPartialTick(true);
                int using = firing ? wielder.getTicksUsingItem() : 0;
                var action = NativeArsenalView.gunPose(nativeGun, firing, using, partial);
                pose.pushPose();
                try {
                    pose.scale(fit, fit, fit);
                    nativeGun.render(pose, buffer, light, overlay, action);
                } finally {
                    pose.popPose();
                }
                if (context.firstPerson() && mc.player != null && !StreakPresentation.busy()) {
                    if (nativeHands == null)
                        nativeHands = new TabletHandModel();
                    nativeHands.renderTrackedHands(pose, buffer, mc.player.getSkin().texture(), light,
                            NativeMinigunPresentation.wrists(nativeGun, action, fit), false);
                }
            } finally {
                pose.popPose();
            }
            return;
        }
        if (context == ItemDisplayContext.GUI) {
            VertexConsumer v = buffer.getBuffer(RenderType.entityCutoutNoCull(ICON));
            iconVertex(v, pose, 0, 0, 0, 1);
            iconVertex(v, pose, 1, 0, 1, 1);
            iconVertex(v, pose, 1, 1, 1, 0);
            iconVertex(v, pose, 0, 1, 0, 0);
            return;
        }
        barrels.zRot = firing ? t * (wielder.getTicksUsingItem() < 10 ? .3F : 1.4F) : 0;
        muzzleFlash.visible = firing && ((int) (t * 2) & 1) == 0;
        pose.pushPose();
        pose.translate(.5, .5, .5);

        // 立方体模型空间的 Y 轴向下，旋转半周使枪管朝向物品空间负 Z；物品栏变换、手持变换和此坐标转换各司其职。
        pose.mulPose(Axis.ZP.rotationDegrees(180));
        pose.scale(1.05F, 1.05F, 1.05F);
        if (firing)
            pose.translate(0, 0, Math.sin(t * 5) * .015);
        root.render(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, overlay, 0xffffffff);
        if (muzzleFlash.visible)
            muzzleFlash.render(pose, buffer.getBuffer(RenderType.eyes(TEXTURE)), 15728880, overlay, 0xffffffff);
        pose.popPose();
    }

    private static void iconVertex(VertexConsumer v, PoseStack pose, float x, float y, float u, float vv) {
        v.addVertex(pose.last().pose(), x, y, .5F).setColor(0xffffffff).setUv(u, vv)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose.last(), 0, 0, 1);
    }
}
