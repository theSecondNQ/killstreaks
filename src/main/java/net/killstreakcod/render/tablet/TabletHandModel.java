package net.killstreakcod.render.tablet;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.render.ImportedCastModel;
import net.killstreakcod.render.NativeTargetingDevices;

import java.util.Map;

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
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/**
 * Camera-space tablet, radio and articulated hands using the current player's skin.
 * 在摄像机空间绘制平板、无线电和可活动双手，使用当前玩家的皮肤。
 */
public final class TabletHandModel {
    private static final ResourceLocation MATERIAL = asset("textures/entity/tactical_atlas.png");
    private static final ResourceLocation WHITE = asset("textures/entity/thermal_white.png");
    private final ModelPart compact, radio, stylus, leftArm, rightArm, rightIndex, indexTip, leftThumb, rightThumb,
            leftDigit, rightDigit;

    public TabletHandModel() {
        MeshDefinition device = new MeshDefinition();
        PartDefinition deviceRoot = device.getRoot();
        PartDefinition receiver = deviceRoot.addOrReplaceChild("radio",
                CubeListBuilder.create().texOffs(0, 0).addBox(2.8F, -4, 1.5F, 4.5F, 8, 2)
                        .texOffs(384, 0).addBox(3.3F, -2, 3.55F, 3.5F, 3, .15F).addBox(3.5F, 1.5F, 3.55F, 1, 1.1F, .2F)
                        .texOffs(128, 0).addBox(3.4F, -10, 2.2F, .45F, 6, .5F).addBox(6, -5, 2, .8F, 1.2F, 1)
                        .texOffs(384, 128).addBox(4.2F, -3.4F, 3.55F, 1.5F, .5F, .15F),
                PartPose.ZERO);
        receiver.addOrReplaceChild("ptt",
                CubeListBuilder.create().texOffs(384, 0).addBox(2.45F, -1, 1.8F, .45F, 2.2F, 1.2F), PartPose.ZERO);
        deviceRoot.addOrReplaceChild("stylus",
                CubeListBuilder.create().texOffs(384, 0).addBox(0, -.16F, -.16F, 5.4F, .32F, .32F)
                        .texOffs(128, 0).addBox(-.65F, -.09F, -.09F, .7F, .18F, .18F),
                PartPose.ZERO);
        deviceRoot.addOrReplaceChild("compact",
                CubeListBuilder.create().texOffs(384, 0).addBox(-3.15F, -6.35F, -.45F, 6.3F, 12.7F, .9F)
                        .texOffs(128, 0).addBox(-3.45F, -6.65F, -.35F, .5F, 13.3F, .85F)
                        .addBox(2.95F, -6.65F, -.35F, .5F, 13.3F, .85F)
                        .addBox(-3.1F, -6.8F, -.35F, 6.2F, .6F, .85F).addBox(-3.1F, 6.2F, -.35F, 6.2F, .6F, .85F)
                        .texOffs(384, 128).addBox(-1.25F, -6.2F, .5F, 2.5F, .24F, .15F),
                PartPose.ZERO);
        ModelPart baked = LayerDefinition.create(device, 512, 512).bakeRoot();
        compact = baked.getChild("compact");
        radio = baked.getChild("radio");
        stylus = baked.getChild("stylus");

        MeshDefinition hands = new MeshDefinition();
        PartDefinition handsRoot = hands.getRoot();
        hand(handsRoot, "left", true);
        hand(handsRoot, "right", false);
        ModelPart skin = LayerDefinition.create(hands, 64, 64).bakeRoot();
        leftArm = skin.getChild("left");
        rightArm = skin.getChild("right");
        rightIndex = rightArm.getChild("index");
        indexTip = rightIndex.getChild("tip");
        rightThumb = rightArm.getChild("thumb");
        leftThumb = leftArm.getChild("thumb");
        MeshDefinition digits = new MeshDefinition();
        digits.getRoot().addOrReplaceChild("left",
                CubeListBuilder.create().texOffs(34, 52).addBox(-.45F, 0, -.45F, .9F, 2.2F, .9F), PartPose.ZERO);
        digits.getRoot().addOrReplaceChild("right",
                CubeListBuilder.create().texOffs(42, 20).addBox(-.45F, 0, -.45F, .9F, 2.2F, .9F), PartPose.ZERO);
        ModelPart digitModel = LayerDefinition.create(digits, 64, 64).bakeRoot();
        leftDigit = digitModel.getChild("left");
        rightDigit = digitModel.getChild("right");
        leftDigit.visible = false;
        rightDigit.visible = false;
        for (ModelPart arm : new ModelPart[] { leftArm, rightArm })
            for (String child : new String[] { "finger0", "finger1", "finger2", "thumb", "index" })
                arm.getChild(child).visible = false;
    }

    private static ResourceLocation asset(String path) {
        return ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, path);
    }

    /**
     * Retarget the source's actual wrist and every finger-joint matrix to Minecraft skin meshes.
     * 将源资源实际手腕及每个指关节矩阵重定向到 Minecraft 皮肤网格。
     */
    public void renderTrackedHands(PoseStack pose, MultiBufferSource buffers, ResourceLocation skin, int light,
            Map<String, Matrix4f> cameraBones, boolean leftOnly) {
        for (String side : leftOnly ? new String[] { "le" } : new String[] { "le", "ri" }) {
            ModelPart arm = side.equals("le") ? leftArm : rightArm, digit = side.equals("le") ? leftDigit : rightDigit;
            Matrix4f wrist = cameraBones.get("j_wrist_" + side);
            if (wrist == null)
                continue;
            for (String child : new String[] { "finger0", "finger1", "finger2", "thumb", "index" })
                arm.getChild(child).visible = false;
            arm.x = arm.y = arm.z = arm.xRot = arm.yRot = arm.zRot = 0;
            pose.pushPose();
            pose.mulPoseMatrix(cameraMatrix(wrist, true));
            pose.scale(.45F, .45F, .45F);
            try {
                arm.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(skin)), light,
                        OverlayTexture.NO_OVERLAY);
            } finally {
                pose.popPose();
            }
            for (String finger : new String[] { "index", "mid", "ring", "pinky", "thumb" })
                for (int joint = 1; joint <= 3; joint++) {
                    Matrix4f matrix = cameraBones.get("j_" + finger + "_" + side + "_" + joint);
                    if (matrix == null)
                        continue;
                    Matrix4f next = cameraBones.get("j_" + finger + "_" + side + "_" + (joint + 1));
                    float length = next == null ? .025F
                            : next.getTranslation(new org.joml.Vector3f())
                                    .distance(matrix.getTranslation(new org.joml.Vector3f())) * .01F;
                    pose.pushPose();
                    pose.mulPoseMatrix(cameraMatrix(matrix, false));
                    pose.scale(.45F, Mth.clamp(length, .01F, .12F) / (2.2F / 16), .45F);
                    digit.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(skin)), light,
                            OverlayTexture.NO_OVERLAY);
                    pose.popPose();
                }
        }
    }

    private static Matrix4f cameraMatrix(Matrix4f source, boolean forearm) {
        return sourceCameraMatrix(source).rotateZ(forearm ? (float) Math.PI / 2 : -(float) Math.PI / 2);
    }

    public static Matrix4f sourceCameraMatrix(Matrix4f source) {
        Matrix4f basis = new Matrix4f().m00(0).m01(0).m02(-1).m10(-1).m11(0).m12(0).m20(0).m21(1).m22(0);
        Matrix4f mapped = basis.mul(source);
        return mapped.m30(mapped.m30() * .01F).m31(mapped.m31() * .01F).m32(mapped.m32() * .01F);
    }

    /**
     * The phone geometry is authored; its attachment pose is the original smartphone animation.
     * 手机使用制作好的几何模型，挂接姿态来自原手机动画。
     */
    public void renderTrackedCompact(PoseStack pose, MultiBufferSource buffers, int light, Matrix4f anchor,
            org.joml.Quaternionf bindAlignment, float ticks, String reward) {
        pose.pushPose();
        pose.mulPoseMatrix(sourceCameraMatrix(anchor).rotate(bindAlignment));
        pose.scale(.30F, -.30F, .30F);
        compact.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(MATERIAL)), light,
                OverlayTexture.NO_OVERLAY);
        pose.pushPose();
        pose.scale(1 / 16F, 1 / 16F, 1 / 16F);
        VertexConsumer screen = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        quad(screen, pose, -2.6F, -5.5F, 2.6F, 5.5F, .51F, 0xff03110b);
        for (int n = 0; n < 5; n++) {
            float x = -2.3F + n * 1.15F, y = -5.1F + n * 2.15F;
            quad(screen, pose, x, -5.1F, x + .045F, 3.7F, .53F, 0xff1a3427);
            quad(screen, pose, -2.3F, y, 2.3F, y + .045F, .53F, 0xff1a3427);
        }
        float scan = -5.05F + ticks * .22F % 8.4F;
        quad(screen, pose, -2.4F, scan, 2.4F, scan + .1F, .54F, 0xff5a9a67);
        quad(screen, pose, -2.4F, 4.3F, 2.4F, 5.18F, .55F, ticks > 15 && ticks < 25 ? 0xffa0d68b : 0xff306749);
        quad(screen, pose, -2.4F, 3.82F, -2.4F + 4.8F * ease((ticks - 24) / 8), 3.94F, .55F, 0xffb8e5a0);
        quad(buffers.getBuffer(RenderType.entityCutoutNoCull(asset("textures/gui/streaks/" + reward + ".png"))), pose,
                -1.5F, -1.3F, 1.5F, 1.7F, .55F, 0xffffffff);
        pose.popPose();
        pose.popPose();
    }

    private static void hand(PartDefinition parent, String name, boolean left) {
        int u = left ? 32 : 40, v = left ? 48 : 16;
        PartDefinition arm = parent.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(u, v).addBox(-2, 0, -2, 4, 10, 4)
                        .texOffs(u, v + 4).addBox(-1.9F, -3, -1.65F, 3.8F, 3.2F, 3.3F),
                PartPose.ZERO);
        for (int n = 0; n < 3; n++)
            arm.addOrReplaceChild("finger" + n, CubeListBuilder.create().texOffs(u + 3, v + 4)
                    .addBox(-.45F, -2.2F, -.5F, .9F, 2.4F, 1), PartPose.offset(-.25F + n * .95F, -2.5F, -1.1F));
        arm.addOrReplaceChild("thumb",
                CubeListBuilder.create().texOffs(u, v + 3).addBox(-.5F, -2, -.55F, 1, 2.4F, 1.1F),
                PartPose.offsetAndRotation(left ? 2.15F : -2.15F, -.7F, .1F, 0, 0, left ? -.5F : .5F));
        PartDefinition index = arm.addOrReplaceChild("index", CubeListBuilder.create().texOffs(u + 2, v + 4)
                .addBox(-.5F, -2, -.5F, 1, 2.2F, 1), PartPose.offset(-1.35F, -2.5F, -1.05F));
        index.addOrReplaceChild("tip",
                CubeListBuilder.create().texOffs(u + 2, v + 5).addBox(-.45F, -1.7F, -.45F, .9F, 1.8F, .9F),
                PartPose.offset(0, -1.9F, 0));
    }

    public void render(PoseStack pose, MultiBufferSource buffers, ResourceLocation skin, int light, float visibility,
            float tap, float age, float stow, float movement, float transmit, boolean radioCall, boolean confirmed,
            String reward) {
        float appear = Mth.clamp(visibility, 0, 1), ready = ease((appear - .25F) / .75F);
        leftThumb.resetPose();
        pose.pushPose();
        float sway = (float) Math.sin(age * .66F) * movement * .006F * appear * (1 - stow);
        pose.translate((radioCall ? .03 : -.16 * (1 - appear)) + stow * .16,
                -1.03 + appear * (radioCall ? .79 : .91) + sway, -1.10 + appear * .14 - stow * .10);
        pose.mulPose(Axis.XP.rotationDegrees((1 - appear) * 42 + (radioCall ? -3 : -17) + stow * 13));
        pose.mulPose(Axis.YP.rotationDegrees((1 - appear) * -20 + stow * 23));
        if (!radioCall)
            pose.mulPose(Axis.ZP.rotationDegrees(appear * 2.5F + stow * 7));
        pose.scale(.94F, -.94F, .94F);
        var material = buffers.getBuffer(RenderType.entityCutoutNoCull(MATERIAL));
        if (radioCall) {
            radio.getChild("ptt").x = tap * .3F;
            radio.render(pose, material, light, OverlayTexture.NO_OVERLAY);
            pose.pushPose();
            pose.scale(1 / 16F, 1 / 16F, 1 / 16F);
            quad(buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE, false)), pose, 4.2F, -3.4F, 5.7F, -2.95F,
                    3.75F, tap > .1F ? 0xffc8f199 : 0xff52856c);
            pose.popPose();
        } else {
            // The fallback gesture also uses the source mesh. No old cube tablet
            // or generic tablet_surface texture can reappear for an unhandled reward.
            // 中文：回退手势同样使用源网格，未单独处理的奖励也不能重新显示旧方块平板或通用 tablet_surface 贴图。
            ImportedCastModel original = ImportedCastModel.get(TabletScreens.modelId(reward));
            if (original != null) {
                pose.pushPose();
                try {
                    pose.scale(1, -1, 1);
                    pose.mulPose(Axis.YP.rotationDegrees(180));
                    original.render(pose, buffers, light, OverlayTexture.NO_OVERLAY, Map.of(),
                            TabletScreens.materialTextures(reward));
                } finally {
                    pose.popPose();
                }
            }
        }
        float withdraw = ease(stow / .55F), handLag = (1 - ready) * 5;
        float penX = 3.6F + (1 - tap) * 3.7F + withdraw * 2, penY = 1.9F + (1 - tap) * 1.4F + handLag + withdraw * 3,
                penZ = .67F + (1 - tap) * 2.8F + withdraw * 1.5F;
        if (!radioCall) {
            stylus.x = penX;
            stylus.y = penY;
            stylus.z = penZ;
            stylus.yRot = -.42F;
            stylus.zRot = .12F;
            stylus.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(MATERIAL)), light,
                    OverlayTexture.NO_OVERLAY);
        }
        leftArm.x = radioCall ? 1.2F : -7.5F;
        leftArm.y = radioCall ? 5.8F : 1.9F;
        leftArm.z = 2.4F;
        leftArm.xRot = radioCall ? .65F : .48F;
        leftArm.zRot = radioCall ? .2F : .22F;
        leftArm.yRot = radioCall ? -.2F : .15F;
        rightArm.x = radioCall ? 5.8F : penX + 4.6F;
        rightArm.y = radioCall ? 3.9F : penY + 2.5F;
        rightArm.z = radioCall ? 4.2F : penZ + 3.2F;
        rightArm.xRot = .65F;
        rightArm.zRot = radioCall ? -.08F : -.15F;
        rightArm.yRot = radioCall ? .06F : -.20F;
        rightIndex.xRot = radioCall ? .85F + tap * .4F : 1.5F;
        indexTip.xRot = radioCall ? tap * .6F : .8F;
        rightThumb.xRot = radioCall ? tap * .4F : 0;
        rightThumb.zRot = .5F + (radioCall ? tap * .2F : 0);
        VertexConsumer player = buffers.getBuffer(RenderType.entityCutoutNoCull(skin));
        leftArm.render(pose, player, light, OverlayTexture.NO_OVERLAY);
        rightArm.render(pose, player, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    /**
     * UAV uses the reference's portrait touch terminal held and confirmed by the left hand.
     * 无人侦察机使用参考中的竖屏触控终端，由左手持握并确认。
     */
    public void renderCompact(PoseStack pose, MultiBufferSource buffers, ResourceLocation skin, int light,
            float visibility, float tap, float age, float stow, float movement, float transmit, String reward) {
        float appear = Mth.clamp(visibility, 0, 1), sway = (float) Math.sin(age * .68F) * movement * .005F * appear;
        pose.pushPose();
        pose.translate(-.34 - (1 - appear) * .18 - stow * .12, -1.02 + appear * .83 + sway,
                -.93 + appear * .09 - stow * .10);
        pose.mulPose(Axis.XP.rotationDegrees(34 * (1 - appear) - 12 + stow * 11));
        pose.mulPose(Axis.YP.rotationDegrees(-7 - (1 - appear) * 18 - stow * 12));
        pose.mulPose(Axis.ZP.rotationDegrees(4 + (1 - appear) * -18 - stow * 9));
        pose.scale(.91F, -.91F, .91F);
        compact.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(MATERIAL)), light,
                OverlayTexture.NO_OVERLAY);
        pose.pushPose();
        pose.scale(1 / 16F, 1 / 16F, 1 / 16F);
        VertexConsumer screen = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        quad(screen, pose, -2.6F, -5.5F, 2.6F, 5.5F, .51F, 0xff03110b);
        for (int n = 0; n < 5; n++) {
            float x = -2.3F + n * 1.15F, y = -5.1F + n * 2.15F;
            quad(screen, pose, x, -5.1F, x + .045F, 3.7F, .53F, 0xff1a3427);
            quad(screen, pose, -2.3F, y, 2.3F, y + .045F, .53F, 0xff1a3427);
        }
        float scan = -5.05F + age * .22F % 8.4F;
        quad(screen, pose, -2.4F, scan, 2.4F, scan + .10F, .54F, 0xff5a9a67);
        quad(screen, pose, -2.4F, 4.3F, 2.4F, 5.18F, .55F, tap > .35F ? 0xffa0d68b : 0xff306749);
        quad(screen, pose, -2.4F, 3.82F, -2.4F + 4.8F * transmit, 3.94F, .55F, 0xffb8e5a0);
        for (int n = 0; n < 3; n++) {
            float y = -4.6F + n * .23F;
            quad(screen, pose, -1.5F, y, 1.5F, y + .08F, .55F, transmit > .65F ? 0xff86cf82 : 0xff426d51);
        }
        quad(buffers.getBuffer(RenderType.entityCutoutNoCull(asset("textures/gui/streaks/" + reward + ".png"))), pose,
                -1.5F, -1.3F, 1.5F, 1.7F, .55F, 0xffffffff);
        pose.popPose();
        leftArm.x = -3.65F;
        leftArm.y = 5.6F;
        leftArm.z = 2.2F;
        leftArm.xRot = .26F;
        leftArm.yRot = .04F;
        leftArm.zRot = -.045F;
        leftThumb.resetPose();
        leftThumb.x = 2.8F - tap * .55F;
        leftThumb.y = -.05F + tap * .3F;
        leftThumb.z = -.95F - tap * .65F;
        leftThumb.xRot = .12F + tap * .65F;
        leftThumb.zRot = -.55F + tap * .52F;
        leftArm.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(skin)), light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    /**
     * Placement confirmation pushes the real equipment model forward, then releases both hands.
     * 放置确认时，将实际装备模型向前推出，再松开双手。
     */
    public void renderTakeEquipment(PoseStack pose, MultiBufferSource buffers, ResourceLocation skin, int light,
            ItemStack equipment, float progress) {
        float lift = ease(progress / .45F);
        pose.pushPose();
        pose.translate(0, -.92 + lift * .53, -.90 + lift * .08);
        pose.mulPose(Axis.XP.rotationDegrees((1 - lift) * 32 - 8));
        pose.pushPose();
        pose.scale(.57F, .57F, .57F);
        Minecraft.getInstance().getItemRenderer().renderStatic(equipment, ItemDisplayContext.NONE, light,
                OverlayTexture.NO_OVERLAY, pose, buffers, Minecraft.getInstance().level, 0);
        pose.popPose();
        pose.scale(.92F, -.92F, .92F);
        equipmentHands(pose, buffers, skin, light, 0);
        pose.popPose();
    }

    /**
     * A binocular target marker or slim mortar laser pen is raised directly, without a tablet.
     * 直接抬起双筒目标指示器或细长的迫击炮激光笔，不经过平板动作。
     */
    public void renderTakeDesignator(PoseStack pose, MultiBufferSource buffers, ResourceLocation skin, int light,
            ItemStack equipment, float progress, boolean pen) {
        float lift = ease(progress / .55F);
        pose.pushPose();
        try {
            pose.translate(pen ? .28 : 0, -.88 + lift * (pen ? .57 : .49), -.75 + lift * .06);
            pose.mulPose(Axis.XP.rotationDegrees((1 - lift) * 28 - 5));
            pose.pushPose();
            try {
                pose.scale(pen ? .60F : .72F, pen ? .60F : .72F, pen ? .60F : .72F);
                if (!NativeTargetingDevices.renderFirstPerson(equipment, pose, buffers, light,
                        OverlayTexture.NO_OVERLAY))
                    Minecraft.getInstance().getItemRenderer().renderStatic(equipment, ItemDisplayContext.NONE, light,
                            OverlayTexture.NO_OVERLAY, pose, buffers, Minecraft.getInstance().level, 0);
            } finally {
                pose.popPose();
            }
            pose.scale(.92F, -.92F, .92F);
            if (pen) {
                rightArm.x = 2;
                rightArm.y = 3.5F;
                rightArm.z = 3;
                rightArm.xRot = .86F;
                rightArm.yRot = -.12F;
                rightArm.zRot = -.16F;
                rightArm.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(skin)), light,
                        OverlayTexture.NO_OVERLAY);
            } else
                equipmentHands(pose, buffers, skin, light, 0);
        } finally {
            pose.popPose();
        }
    }

    public void renderThrowEquipment(PoseStack pose, MultiBufferSource buffers, ResourceLocation skin, int light,
            ItemStack equipment, float progress) {
        float throwOut = ease((progress - .14F) / .58F), release = ease((progress - .42F) / .58F);
        pose.pushPose();
        pose.translate(.11, -.39 - throwOut * .28, -.70 - throwOut * 1.65);
        if (progress < .73F) {
            pose.pushPose();
            pose.mulPose(Axis.XP.rotationDegrees(-15 + throwOut * 95));
            pose.scale(.63F, .63F, .63F);
            Minecraft.getInstance().getItemRenderer().renderStatic(equipment, ItemDisplayContext.NONE, light,
                    OverlayTexture.NO_OVERLAY, pose, buffers, Minecraft.getInstance().level, 0);
            pose.popPose();
        }
        pose.translate(0, -release * .25, release * 1.2);
        pose.scale(.92F, -.92F, .92F);
        equipmentHands(pose, buffers, skin, light, release);
        pose.popPose();
    }

    private void equipmentHands(PoseStack pose, MultiBufferSource buffers, ResourceLocation skin, int light,
            float release) {
        leftArm.x = -5.4F - release * 3;
        leftArm.y = 4.3F;
        leftArm.z = 4;
        leftArm.xRot = .80F;
        leftArm.yRot = .1F - release * .3F;
        leftArm.zRot = .30F + release * .20F;
        rightArm.x = 5.4F + release * 3;
        rightArm.y = 4.3F;
        rightArm.z = 4;
        rightArm.xRot = .80F;
        rightArm.yRot = -.1F + release * .3F;
        rightArm.zRot = -.30F - release * .20F;
        VertexConsumer player = buffers.getBuffer(RenderType.entityCutoutNoCull(skin));
        leftArm.render(pose, player, light, OverlayTexture.NO_OVERLAY);
        rightArm.render(pose, player, light, OverlayTexture.NO_OVERLAY);
    }

    public void renderDeployment(PoseStack pose, MultiBufferSource buffers, ResourceLocation skin, int light,
            ItemStack equipment, float progress) {
        leftThumb.resetPose();
        float reach = ease(progress / .72F), release = ease((progress - .58F) / .42F);
        pose.pushPose();
        pose.translate(0, -.34 - reach * .30, -.66 - reach * .38);
        if (progress < .72F) {
            pose.pushPose();
            pose.mulPose(Axis.XP.rotationDegrees(8 + reach * 16));
            pose.scale(.66F, .66F, .66F);
            Minecraft.getInstance().getItemRenderer().renderStatic(equipment, ItemDisplayContext.NONE, light,
                    OverlayTexture.NO_OVERLAY, pose, buffers, Minecraft.getInstance().level, 0);
            pose.popPose();
        }
        pose.translate(0, -release * .22, release * .30);
        pose.scale(.92F, -.92F, .92F);
        leftArm.x = -5.4F - release * 3;
        leftArm.y = 4.3F;
        leftArm.z = 4;
        leftArm.xRot = .80F;
        leftArm.yRot = .1F - release * .3F;
        leftArm.zRot = .30F + release * .20F;
        rightArm.x = 5.4F + release * 3;
        rightArm.y = 4.3F;
        rightArm.z = 4;
        rightArm.xRot = .80F;
        rightArm.yRot = -.1F + release * .3F;
        rightArm.zRot = -.30F - release * .20F;
        rightIndex.xRot = .45F * (1 - release);
        indexTip.xRot = .35F * (1 - release);
        rightThumb.xRot = 0;
        rightThumb.zRot = .5F;
        VertexConsumer player = buffers.getBuffer(RenderType.entityCutoutNoCull(skin));
        leftArm.render(pose, player, light, OverlayTexture.NO_OVERLAY);
        rightArm.render(pose, player, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    private static float ease(float value) {
        float n = Mth.clamp(value, 0, 1);
        return n * n * (3 - 2 * n);
    }

    private static void quad(VertexConsumer vertices, PoseStack pose, float x1, float y1, float x2, float y2, float z,
            int color) {
        point(vertices, pose, x1, y2, z, 0, 1, color);
        point(vertices, pose, x2, y2, z, 1, 1, color);
        point(vertices, pose, x2, y1, z, 1, 0, color);
        point(vertices, pose, x1, y1, z, 0, 0, color);
    }

    private static void point(VertexConsumer vertices, PoseStack pose, float x, float y, float z, float u, float v,
            int color) {
        vertices.vertex(pose.last().pose(), x, y, z).color(color).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(pose.last().normal(), 0, 0, 1).endVertex();
    }
}
