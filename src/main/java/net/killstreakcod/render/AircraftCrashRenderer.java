package net.killstreakcod.render;

import net.killstreakcod.killstreaks.TacticalUnitEntity;
import net.killstreakcod.render.tablet.TabletHandModel;
import net.killstreakcod.universal.AircraftCrashLibrary;
import net.killstreakcod.universal.SceneEntryLibrary;

import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;

/**
 * Native crash skins and pilot motion use the ordinary world geometry/lighting pipeline.
 * 原资源坠毁材质和驾驶员动作使用常规世界几何与光照渲染流程。
 */
public final class AircraftCrashRenderer {
    private static final Matrix4f BASIS = new Matrix4f().m00(0).m01(0).m02(-1).m10(-1).m11(0).m12(0).m20(0).m21(1)
            .m22(0);

    private AircraftCrashRenderer() {}

    static boolean world(TacticalUnitEntity unit, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        if (unit.stage() >= 200)
            return true;
        float time = unit.nativeClock(partial);
        int variant = AircraftCrashLibrary.variant(unit.stage());
        var camera = Minecraft.getInstance().getCameraEntity();
        double distance = camera == null ? 0 : camera.distanceToSqr(unit);
        int tier = ImportedModelQuality.worldTier(distance, false);
        String family = variant == 100 ? "scene_gunship"
                : variant == 104 ? "crash_hind" : variant == 103 ? "vtol" : "scene_chopper";
        String id = family.equals("vtol") ? family : SceneEntryLibrary.lod(family, tier);
        ImportedCastModel model = ImportedCastModel.get(id);
        if (model == null)
            return true;
        pose.pushPose();
        try {
            if (variant == AircraftCrashLibrary.VTOL)
                pose.scale(2.5F, 2.5F, 2.5F);
            pose.mulPose(Axis.YP.rotationDegrees(180 - unit.renderFlightYaw(partial)));
            pose.mulPose(Axis.XP.rotationDegrees(-unit.renderFlightPitch(partial)));
            pose.mulPose(Axis.ZP.rotationDegrees(AircraftCrashLibrary.roll(variant, time)));
            if (variant == AircraftCrashLibrary.VTOL) {
                ImportedUnits.crashVtol(unit, partial, pose, buffers, light, tier);
                return true;
            }
            Map<String, Matrix4f> bones = ImportedUnits.crashMechanicalPose(model, unit, partial);
            model.renderScene(pose, buffers, light, OverlayTexture.NO_OVERLAY, bones);
        } finally {
            pose.popPose();
        }
        return true;
    }

    static void movie(float ticks, PoseStack pose, MultiBufferSource buffers, int light) {
        if (AircraftCrashClient.variant() == 100) {
            SceneEntryRenderer.drawGunshipOutro(ticks, pose, buffers, light);
            return;
        }
        Matrix4f camera = AircraftCrashLibrary.pilotCamera(AircraftCrashClient.variant(), ticks),
                inverse = new Matrix4f(camera).invert();
        pose.pushPose();
        try {
            pose.mulPose(TabletHandModel.sourceCameraMatrix(inverse).mul(new Matrix4f(BASIS).invert()));
            ImportedCastModel helicopter = ImportedCastModel.get("scene_chopper");
            var bones = SceneEntryLibrary.pose("scene_chopper", "", 0);
            if (helicopter != null)
                OriginalStreakAnimations.sceneRotors(helicopter, bones, ticks);
            if (helicopter != null)
                helicopter.renderDamagedCockpit(pose, buffers, light, OverlayTexture.NO_OVERLAY, bones,
                        NativeChopperScreens.materials(), GunshipCabinLighting.CHOPPER);
            GunshipCabinLighting.renderChopperFixture(pose, buffers);
            var actor = SceneEntryLibrary.pose("crash_pilot_western",
                    AircraftCrashLibrary.pilotAction(AircraftCrashClient.variant()), ticks);
            SceneEntryRenderer.avatar(pose, buffers, OperatorSkins.skin(OperatorSkins.Role.CHOPPER_GUNNER),
                    GunshipCabinLighting.operatorLight(light), actor,
                    SceneEntryLibrary.pose("crash_pilot_western", "", 0), AircraftCrashLibrary.chopperAttach(),
                    inverse);
        } finally {
            pose.popPose();
        }
    }
}
