package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.killstreaks.SupportHelicopterEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public final class SupportHelicopterRenderer extends MobRenderer<SupportHelicopterEntity, SupportHelicopterModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            KillstreakcodMod.MODID, "textures/entity/support_helicopter.png");

    public SupportHelicopterRenderer(EntityRendererProvider.Context context) {
        super(context, new SupportHelicopterModel(context.bakeLayer(SupportHelicopterModel.LAYER_LOCATION)), 1.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(SupportHelicopterEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(SupportHelicopterEntity entity, PoseStack poseStack, float partialTick) {
        if (entity.isBombDrone())
            poseStack.scale(0.48F, 0.48F, 0.48F);
    }
}
