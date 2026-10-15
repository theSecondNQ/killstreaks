package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.killstreaks.WheelsonEntity;

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

public final class WheelsonModel extends EntityModel<WheelsonEntity> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "wheelson"), "main");

    private final ModelPart root;
    private final ModelPart leftWheels;
    private final ModelPart rightWheels;
    private final ModelPart turret;

    public WheelsonModel(ModelPart root) {
        super(net.minecraft.client.renderer.RenderType::entityCutoutNoCull);
        this.root = root;
        this.leftWheels = root.getChild("left_wheels");
        this.rightWheels = root.getChild("right_wheels");
        this.turret = root.getChild("turret");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("chassis", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-7.0F, -5.0F, -10.0F, 14.0F, 7.0F, 20.0F)
                .texOffs(0, 28).addBox(-5.0F, -8.0F, -11.0F, 10.0F, 4.0F, 5.0F)
                .texOffs(36, 0).addBox(-4.0F, -9.0F, -9.0F, 8.0F, 3.0F, 1.0F), PartPose.offset(0.0F, 17.0F, 0.0F));
        root.addOrReplaceChild("left_wheels", CubeListBuilder.create().texOffs(0, 38)
                .addBox(-1.5F, -3.0F, -4.0F, 3.0F, 6.0F, 8.0F), PartPose.offset(-7.0F, 20.0F, 0.0F));
        root.addOrReplaceChild("right_wheels", CubeListBuilder.create().texOffs(22, 38)
                .addBox(-1.5F, -3.0F, -4.0F, 3.0F, 6.0F, 8.0F), PartPose.offset(7.0F, 20.0F, 0.0F));
        root.addOrReplaceChild("turret", CubeListBuilder.create().texOffs(38, 28)
                .addBox(-4.0F, -2.0F, -3.0F, 8.0F, 3.0F, 7.0F)
                .texOffs(40, 39).addBox(-1.5F, -4.0F, -10.0F, 3.0F, 2.0F, 9.0F)
                .texOffs(48, 50).addBox(-1.0F, -4.5F, -12.0F, 2.0F, 2.0F, 2.0F), PartPose.offset(0.0F, 12.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(WheelsonEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float headYaw, float headPitch) {
        float wheelRotation = limbSwing * 0.9F;
        leftWheels.xRot = wheelRotation;
        rightWheels.xRot = wheelRotation;
        turret.yRot = headYaw * ((float) Math.PI / 180.0F);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay,
            int color) {
        root.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
    }
}
