package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.killstreaks.WheelsonEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public final class WheelsonRenderer extends MobRenderer<WheelsonEntity, WheelsonModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            KillstreakcodMod.MODID, "textures/entity/wheelson.png");

    public WheelsonRenderer(EntityRendererProvider.Context context) {
        super(context, new WheelsonModel(context.bakeLayer(WheelsonModel.LAYER_LOCATION)), 0.6F);
    }

    @Override
    public ResourceLocation getTextureLocation(WheelsonEntity entity) {
        return TEXTURE;
    }
}
