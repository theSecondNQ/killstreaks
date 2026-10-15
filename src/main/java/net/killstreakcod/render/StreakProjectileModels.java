package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.killstreaks.TacticalUnitEntity;
import net.killstreakcod.render.compat.OptionalShaderCompat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
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

/**
 * Distinct fallback ordnance: short 105mm shell and long finned helicopter rocket.
 * 分别提供备用弹药模型：短型 105 毫米炮弹与带长尾翼的直升机火箭弹。
 */
public final class StreakProjectileModels {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID,
            "textures/entity/tactical_atlas.png");
    private static ModelPart shell, rocket;

    private StreakProjectileModels() {}

    private static void box(PartDefinition root, String name, int material, float x, float y, float z, float w, float h,
            float d) {
        root.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(material % 4 * 128, material / 4 * 128).addBox(x, y, z, w, h, d),
                PartPose.ZERO);
    }

    private static void load() {
        if (shell != null)
            return;
        var s = new MeshDefinition();
        var a = s.getRoot();
        box(a, "steel_body", 9, -.84F, -.84F, -4, 1.68F, 1.68F, 8);
        box(a, "copper_band", 4, -.89F, -.89F, 2.1F, 1.78F, 1.78F, .55F);
        box(a, "nose", 1, -.58F, -.58F, -5.9F, 1.16F, 1.16F, 1.9F);
        box(a, "tip", 1, -.28F, -.28F, -6.8F, .56F, .56F, .9F);
        box(a, "base", 9, -.78F, -.78F, 4, 1.56F, 1.56F, .3F);
        shell = LayerDefinition.create(s, 512, 512).bakeRoot();
        var r = new MeshDefinition();
        a = r.getRoot();
        box(a, "rocket_body", 8, -.95F, -.95F, -10, 1.9F, 1.9F, 22);
        box(a, "warhead", 0, -1.05F, -1.05F, -15, 2.1F, 2.1F, 5);
        box(a, "nose", 1, -.7F, -.7F, -18, 1.4F, 1.4F, 3);
        box(a, "tip", 1, -.3F, -.3F, -19, .6F, .6F, 1);
        box(a, "motor", 9, -.8F, -.8F, 12, 1.6F, 1.6F, 1.8F);
        box(a, "fins_vertical", 1, -.16F, -2.9F, 7, .32F, 5.8F, 5);
        box(a, "fins_horizontal", 1, -2.9F, -.16F, 7, 5.8F, .32F, 5);
        rocket = LayerDefinition.create(r, 512, 512).bakeRoot();
    }

    public static boolean render(TacticalUnitEntity unit, float partial, PoseStack pose, MultiBufferSource buffers,
            int light) {
        if (unit.kind() != TacticalUnitEntity.Kind.ROCKET)
            return false;
        if (unit.stage() == 10) {
            double distance = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()
                    .distanceToSqr(unit.position());
            int lod = OptionalShaderCompat.shadowPass() ? 4
                    : distance < 24 * 24 ? 0
                            : distance < 48 * 48 ? 1 : distance < 96 * 96 ? 2 : distance < 160 * 160 ? 3 : 4;
            var nativeMissile = ImportedCastModel.get(lod == 0 ? "vtol_rocket" : "vtol_rocket_lod" + lod);
            if (nativeMissile == null)
                nativeMissile = ImportedCastModel.get("vtol_rocket");
            if (nativeMissile != null) {
                pose.pushPose();
                try {

                    // 导出的压缩坐标单位为英寸，而通用网格单位为 0.01 米。
                    pose.mulPose(Axis.YP.rotationDegrees(180 - Mth.rotLerp(partial, unit.yRotO, unit.getYRot())));
                    pose.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partial, unit.xRotO, unit.getXRot())));
                    pose.scale(2.54F, 2.54F, 2.54F);
                    nativeMissile.renderScene(pose, buffers, light, OverlayTexture.NO_OVERLAY, java.util.Map.of());
                } finally {
                    pose.popPose();
                }
                return true;
            }
        }
        load();
        pose.pushPose();
        try {
            pose.mulPose(Axis.YP.rotationDegrees(180 - Mth.rotLerp(partial, unit.yRotO, unit.getYRot())));
            pose.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partial, unit.xRotO, unit.getXRot())));
            pose.mulPose(Axis.ZP.rotationDegrees(180));
            (unit.stage() == 8 ? shell : rocket).render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)),
                    light, OverlayTexture.NO_OVERLAY, 0xffffffff);
        } finally {
            pose.popPose();
        }
        return true;
    }
}
