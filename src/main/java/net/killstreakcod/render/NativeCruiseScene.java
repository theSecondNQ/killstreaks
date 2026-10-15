package net.killstreakcod.render;

import net.killstreakcod.killstreaks.NativeCruiseFlight;
import net.killstreakcod.killstreaks.TacticalUnitEntity;
import net.killstreakcod.render.compat.OptionalShaderCompat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Original animated fairings and camera attachment, rendered in the world's shader pass.
 * 在世界的光影渲染阶段绘制原资源动态整流罩及摄像机挂接动作。
 */
public final class NativeCruiseScene {
    private NativeCruiseScene() {}

    static void movie(PoseStack pose, MultiBufferSource buffers, int light, float ticks) {
        if (ticks >= NativeCruiseFlight.HANDOFF_TICK)
            return;
        var model = ImportedCastModel.get("cruise_native");
        if (model == null)
            return;
        var bones = NativeCruiseFlight.pose("cruise_native", ticks);
        Matrix4f inverse = new Matrix4f(bones.get("tag_player")).invert();
        bones.replaceAll((name, matrix) -> new Matrix4f(inverse).mul(matrix));
        model.renderScene(pose, buffers, light, OverlayTexture.NO_OVERLAY, bones);
    }

    static boolean world(TacticalUnitEntity unit, float partial, PoseStack pose, MultiBufferSource buffers, int light,
            double distance) {
        int tier = OptionalShaderCompat.shadowPass() ? 2 : distance > 48 * 48 ? 1 : 0;
        boolean live = unit.stage() > 0;
        float ticks = Mth.clamp(unit.nativeClock(partial), 0, NativeCruiseFlight.DURATION);
        String id = NativeCruiseFlight.lod(live ? "cruise_native_warhead" : "cruise_native", tier);
        var model = ImportedCastModel.get(id);
        if (model == null)
            return false;
        var bones = NativeCruiseFlight.pose(id, ticks);
        Vector3f origin = live ? new Vector3f() : NativeCruiseFlight.missile(ticks);
        Matrix4f shift = new Matrix4f().translation(-origin.x, -origin.y, -origin.z);
        bones.replaceAll((name, matrix) -> new Matrix4f(shift).mul(matrix));
        pose.pushPose();
        try {
            pose.mulPose(Axis.YP.rotationDegrees(180 - unit.renderFlightYaw(partial)));
            pose.mulPose(Axis.XP.rotationDegrees(-unit.renderFlightPitch(partial)));
            model.renderScene(pose, buffers, light, OverlayTexture.NO_OVERLAY, bones);
        } finally {
            pose.popPose();
        }
        return true;
    }

    static Vec3 camera(TacticalUnitEntity unit, float ticks, boolean entering, float partial) {
        Vector3f offset = entering
                ? NativeCruiseFlight.camera(ticks).getTranslation(new Vector3f()).sub(NativeCruiseFlight.missile(ticks))
                : NativeCruiseFlight.pose("cruise_native_warhead", 0).get("tag_player").getTranslation(new Vector3f());

        // 使用与 EntryScenes 相同的发射姿态；第一帧不从刚生成实体的默认旋转插值挂接姿态。
        float yaw = TacticalClient.yaw(), pitch = TacticalClient.pitch();
        return unit.getPosition(partial).add(NativeCruiseFlight.offset(offset, yaw, pitch));
    }
}
