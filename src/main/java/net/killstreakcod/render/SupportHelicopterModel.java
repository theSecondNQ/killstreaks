package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.killstreaks.SupportHelicopterEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

public final class SupportHelicopterModel extends EntityModel<SupportHelicopterEntity> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "support_helicopter"), "main");

    private final ModelPart root;
    private final ModelPart rotor;
    private final ModelPart tailRotor;

    public SupportHelicopterModel(ModelPart root) {
        super(net.minecraft.client.renderer.RenderType::entityCutoutNoCull);
        this.root = root;
        this.rotor = root.getChild("rotor");
        this.tailRotor = root.getChild("tail_rotor");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-5.0F, -4.0F, -9.0F, 10.0F, 8.0F, 18.0F)
                .texOffs(0, 28).addBox(-4.0F, -3.0F, -12.0F, 8.0F, 5.0F, 5.0F)
                .texOffs(28, 28).addBox(-1.0F, -1.0F, 8.0F, 2.0F, 2.0F, 16.0F)
                .texOffs(40, 0).addBox(-1.0F, -5.0F, 21.0F, 2.0F, 6.0F, 3.0F), PartPose.offset(0.0F, 15.0F, 0.0F));
        root.addOrReplaceChild("left_skid", CubeListBuilder.create().texOffs(0, 40)
                .addBox(-0.5F, -0.5F, -11.0F, 1.0F, 1.0F, 22.0F), PartPose.offset(-6.0F, 21.0F, 0.0F));
        root.addOrReplaceChild("right_skid", CubeListBuilder.create().texOffs(0, 44)
                .addBox(-0.5F, -0.5F, -11.0F, 1.0F, 1.0F, 22.0F), PartPose.offset(6.0F, 21.0F, 0.0F));
        root.addOrReplaceChild("rotor", CubeListBuilder.create().texOffs(0, 48)
                .addBox(-15.0F, -0.5F, -1.5F, 30.0F, 1.0F, 3.0F)
                .texOffs(0, 52).addBox(-1.5F, -0.5F, -11.0F, 3.0F, 1.0F, 22.0F), PartPose.offset(0.0F, 8.0F, 0.0F));
        root.addOrReplaceChild("tail_rotor", CubeListBuilder.create().texOffs(40, 48)
                .addBox(-0.5F, -5.0F, -0.5F, 1.0F, 10.0F, 1.0F)
                .texOffs(44, 48).addBox(-4.0F, -0.5F, -0.5F, 8.0F, 1.0F, 1.0F), PartPose.offset(0.0F, 15.0F, 24.0F));
        root.addOrReplaceChild("gun", CubeListBuilder.create().texOffs(50, 28)
                .addBox(-1.0F, -1.0F, -8.0F, 2.0F, 2.0F, 10.0F), PartPose.offset(0.0F, 17.0F, -2.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(SupportHelicopterEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float headYaw, float headPitch) {
        rotor.yRot = ageInTicks * 0.8F;
        tailRotor.zRot = ageInTicks * 1.1F;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay,
            float red, float green, float blue, float alpha) {
        root.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
