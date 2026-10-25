package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.killstreaks.TacticalUnitEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public final class TacticalUnitRenderer extends MobRenderer<TacticalUnitEntity, TacticalUnitModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID,
            "textures/entity/tactical_atlas.png");
    private static final ResourceLocation EMISSIVE = ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID,
            "textures/entity/tactical_lights.png");

    public TacticalUnitRenderer(EntityRendererProvider.Context c) {
        super(c, new TacticalUnitModel(c.bakeLayer(TacticalUnitModel.LAYER)), .6F);
        addLayer(new RenderLayer<>(this) {
            @Override
            public void render(PoseStack pose, MultiBufferSource buffers, int light, TacticalUnitEntity unit, float s,
                    float a, float partial, float age, float yaw, float pitch) {
                getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(EMISSIVE)), 15728880,
                        OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
            }
        });
    }

    public ResourceLocation getTextureLocation(TacticalUnitEntity unit) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(TacticalUnitEntity unit, net.minecraft.client.renderer.culling.Frustum frustum,
            double x, double y, double z) {
        if (unit.hiddenSaePayload() || unit.awaitingVtolEntry() || unit.kind() == TacticalUnitEntity.Kind.COUNTER_UAV
                || unit.kind() == TacticalUnitEntity.Kind.BOMB && unit.stage() <= -3
                || unit.kind() == TacticalUnitEntity.Kind.PRECISION && unit.stage() < 0)
            return false;
        if (super.shouldRender(unit, frustum, x, y, z))
            return true;

        // 原飞机机翼超出战斗碰撞箱范围，需避免仍可见的翼端被提前裁掉。
        double margin = switch (unit.kind()) {
            case STEALTH -> 28;
            case GUNSHIP, PRECISION, SAE, VTOL, UAV, ADVANCED_UAV -> 24;
            case CHOPPER, OVERWATCH, DELIVERY -> 12;
            case CRATE, JUGG_CRATE -> 14;
            default -> 2;
        };
        return unit.shouldRender(x, y, z) && frustum.isVisible(unit.getBoundingBox().inflate(margin));
    }

    @Override
    public void render(TacticalUnitEntity unit, float yaw, float partial, PoseStack pose, MultiBufferSource buffers,
            int light) {
        shadowRadius = unit.hiddenSaePayload() || unit.kind() == TacticalUnitEntity.Kind.COUNTER_UAV ? 0 : .6F;
        if (unit.hiddenSaePayload() || unit.awaitingVtolEntry() || unit.kind() == TacticalUnitEntity.Kind.COUNTER_UAV
                || unit.kind() == TacticalUnitEntity.Kind.BOMB && unit.stage() <= -3
                || unit.kind() == TacticalUnitEntity.Kind.PRECISION && unit.stage() < 0
                || Minecraft.getInstance().getCameraEntity() == unit && unit.kind() != TacticalUnitEntity.Kind.WHEELSON
                        && !GunshipViewRenderer.visible(unit))
            return;
        if (StreakProjectileModels.render(unit, partial, pose, buffers, light)
                || ImportedUnits.render(unit, partial, pose, buffers, light))
            return;
        super.render(unit, yaw, partial, pose, buffers, light);
    }

    @Override
    protected void scale(TacticalUnitEntity unit, PoseStack pose, float tick) {
        float s = switch (unit.kind()) {
            case BOMB -> unit.stage() == 5 ? .3F : unit.stage() == 4 ? 1.4F : 1;
            case GUNSHIP -> 2.3F;
            case STEALTH -> 2F;
            case PRECISION, SAE, VTOL, OVERWATCH, CHOPPER, DELIVERY -> 1.6F;
            case BOMB_DRONE -> .7F;
            default -> 1;
        };
        pose.scale(s, s, s);
    }

    @Override
    protected boolean shouldShowName(TacticalUnitEntity unit) {
        return false;
    }

    @Override
    protected void setupRotations(TacticalUnitEntity unit, PoseStack pose, float age, float yaw, float partial) {
        super.setupRotations(unit, pose, age, yaw, partial);
        if (unit.kind() == TacticalUnitEntity.Kind.CRUISE || unit.kind() == TacticalUnitEntity.Kind.BOMB
                || unit.kind() == TacticalUnitEntity.Kind.ROCKET)
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-unit.getXRot()));
    }
}
