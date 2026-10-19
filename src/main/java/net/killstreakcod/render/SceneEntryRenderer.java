package net.killstreakcod.render;

import net.killstreakcod.render.tablet.TabletHandModel;
import net.killstreakcod.universal.SceneEntryLibrary;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.killstreakcod.render.PlayerSkin;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Source sockets compose cabin, scene props, Minecraft avatar and original camera in one frame.
 * 通过源挂点在同一坐标框架内组合座舱、场景道具、Minecraft 人物和原摄像机。
 */
public final class SceneEntryRenderer {
    private static final Matrix4f BASIS = new Matrix4f().m00(0).m01(0).m02(-1).m10(-1).m11(0).m12(0).m20(0).m21(1)
            .m22(0);
    private static final GunshipCabinLighting.Profile CABIN_LIGHTS = new GunshipCabinLighting.Profile(0, 0, 0);

    private record Prop(String role, String model, String action) {
    }

    private static final List<Prop> PROPS = List.of(
            new Prop("clipboard_1", "scene_clipboard", "vm_ks_gunship_clipboard1_intro"),
            new Prop("clipboard_2", "scene_clipboard", "vm_ks_gunship_clipboard2_intro"),
            new Prop("paper", "scene_paper", "vm_ks_gunship_paper1_intro"),
            new Prop("pen_1", "scene_pen", "vm_ks_gunship_pen1_intro"),
            new Prop("pen_2", "scene_pen", "vm_ks_gunship_pen2_intro"),
            new Prop("smartphone", "scene_phone", "vm_ks_gunship_smartphone1_intro"),
            new Prop("tablet", "scene_static_tablet", "vm_ks_gunship_tablet1_intro"),
            new Prop("binder", "scene_binder", "vm_ks_gunship_binder_intro"),
            new Prop("co_gunner", "scene_copilot", "wm_ks_gunship_pilot2_intro"));
    private static Matrix4f interiorFrame;
    private static Map<String, Matrix4f> cachedVehicle;
    private static float cachedTicks = Float.NaN;

    private SceneEntryRenderer() {}

    static void reset() {
        interiorFrame = null;
        cachedVehicle = null;
        cachedTicks = Float.NaN;
    }

    static Matrix4f camera(String reward, float ticks) {
        if (reward.equals("chopper_gunner")) {
            Map<String, Matrix4f> vehicle = vehicle(ticks);
            Matrix4f attach = SceneEntryLibrary.socket("scene_chopper", vehicle, "body_animate_jnt");
            return attach.mul(SceneEntryLibrary.track("chopper_camera_asset_space").at(ticks));
        }
        if (reward.equals("gunship")) {
            if (ticks < SceneEntryLibrary.GUNSHIP_EXTERIOR)
                return SceneEntryLibrary.track("gunship_exterior_camera").at(ticks);
            return interiorFrame().mul(deskSocket()).mul(
                    SceneEntryLibrary.track("gunship_interior_camera").at(ticks - SceneEntryLibrary.GUNSHIP_EXTERIOR));
        }
        return null;
    }

    private static Map<String, Matrix4f> vehicle(float ticks) {
        if (cachedVehicle != null && cachedTicks == ticks)
            return cachedVehicle;
        Map<String, Matrix4f> globals = SceneEntryLibrary.pose("scene_chopper", "iw9_mp_ks_ahotel64_intro_heli", ticks);
        Matrix4f inverse = SceneEntryLibrary.socket("scene_chopper", globals, "tag_origin").invert();
        Map<String, Matrix4f> local = new HashMap<>();
        globals.forEach((name, pose) -> local.put(name, new Matrix4f(inverse).mul(pose)));
        OriginalStreakAnimations.sceneRotors(ImportedCastModel.get("scene_chopper"), local, ticks);
        cachedTicks = ticks;
        cachedVehicle = local;
        return local;
    }

    private static Matrix4f interiorFrame() {
        if (interiorFrame == null) {
            Matrix4f end = deskSocket()
                    .mul(SceneEntryLibrary.track("gunship_interior_camera").at(SceneEntryLibrary.GUNSHIP_INTERIOR));
            Vector3f forward = end.getNormalizedRotation(new org.joml.Quaternionf()).transform(new Vector3f(1, 0, 0));
            interiorFrame = new Matrix4f().rotateZ((float) (Math.PI / 2 - Math.atan2(forward.y, forward.x)));
        }
        return new Matrix4f(interiorFrame);
    }

    private static Matrix4f deskSocket() {
        return SceneEntryLibrary.socket("scene_cabin", SceneEntryLibrary.pose("scene_cabin", "", 0),
                "tag_control_desk");
    }

    static void draw(String reward, float ticks, PoseStack pose, MultiBufferSource buffers, int light) {
        Matrix4f camera = camera(reward, ticks);
        if (camera == null)
            return;
        Matrix4f inverse = new Matrix4f(camera).invert();
        pose.pushPose();
        try {
            pose.mulPoseMatrix(TabletHandModel.sourceCameraMatrix(inverse).mul(new Matrix4f(BASIS).invert()));
            if (reward.equals("chopper_gunner")) {
                Map<String, Matrix4f> vehicle = vehicle(ticks);
                ImportedCastModel heli = ImportedCastModel.get("scene_chopper");
                if (heli != null)
                    heli.renderCockpit(pose, buffers, light, OverlayTexture.NO_OVERLAY, vehicle,
                            NativeChopperScreens.materials(), GunshipCabinLighting.CHOPPER);
                GunshipCabinLighting.renderChopperFixture(pose, buffers);
                Matrix4f attach = SceneEntryLibrary.socket("scene_chopper", vehicle, "body_animate_jnt");
                Map<String, Matrix4f> actor = SceneEntryLibrary.pose("scene_pilot", "iw9_mp_ks_ahotel64_intro_player",
                        ticks);
                avatar(pose, buffers, OperatorSkins.skin(OperatorSkins.Role.CHOPPER_GUNNER),
                        GunshipCabinLighting.operatorLight(light), actor, SceneEntryLibrary.pose("scene_pilot", "", 0),
                        attach, inverse);
                return;
            }
            if (ticks < SceneEntryLibrary.GUNSHIP_EXTERIOR) {
                model("scene_gunship", "iw9_mp_acharlie130_intro", ticks, pose, buffers, light, new Matrix4f(), false);
                return;
            }
            float local = ticks - SceneEntryLibrary.GUNSHIP_EXTERIOR;
            Matrix4f frame = interiorFrame(), desk = new Matrix4f(frame).mul(deskSocket());
            ImportedCastModel cabin = ImportedCastModel.get("scene_cabin");
            if (cabin != null) {
                pose.pushPose();
                try {
                    pose.mulPoseMatrix(TabletHandModel.sourceCameraMatrix(frame).mul(new Matrix4f(BASIS).invert()));
                    cabin.renderCabin(pose, buffers, light, OverlayTexture.NO_OVERLAY, Map.of(), CABIN_LIGHTS);
                } finally {
                    pose.popPose();
                }
            }
            model("scene_desk", "vm_ks_gunship_desk_intro", local, pose, buffers, light, desk, true);
            Map<String, Matrix4f> deskPose = SceneEntryLibrary.pose("scene_desk", "vm_ks_gunship_desk_intro", local);
            Matrix4f joystick = new Matrix4f(desk)
                    .mul(SceneEntryLibrary.socket("scene_desk", deskPose, "tag_joystick_1"));
            model("scene_joystick", "vm_ks_gunship_joystick_intro", local, pose, buffers, light, joystick, true);
            for (Prop prop : PROPS) {
                if (prop.role.equals("co_gunner"))
                    avatar(pose, buffers, OperatorSkins.skin(OperatorSkins.Role.GUNSHIP_COPILOT),
                            GunshipCabinLighting.operatorLight(light),
                            SceneEntryLibrary.pose("scene_copilot", prop.action, local),
                            SceneEntryLibrary.pose("scene_copilot", "", 0), desk, inverse);
                else
                    model(SceneEntryLibrary.lod(prop.model, 0), prop.action, local, pose, buffers, light, desk, false);
            }
            Map<String, Matrix4f> actor = SceneEntryLibrary.pose("scene_operator", "vm_ks_gunship_player_intro", local);
            avatar(pose, buffers, OperatorSkins.skin(OperatorSkins.Role.GUNSHIP_OPERATOR),
                    GunshipCabinLighting.operatorLight(light), actor, SceneEntryLibrary.pose("scene_operator", "", 0),
                    desk, inverse);
        } finally {
            pose.popPose();
        }
    }

    static Matrix4f gunshipOutroCamera(float ticks) {
        return interiorFrame().mul(deskSocket()).mul(SceneEntryLibrary.socket("scene_operator",
                SceneEntryLibrary.pose("scene_operator", "vm_ks_gunship_player_outro", ticks), "tag_camera"));
    }

    static void drawGunshipOutro(float ticks, PoseStack pose, MultiBufferSource buffers, int light) {
        Matrix4f inverse = gunshipOutroCamera(ticks).invert(), frame = interiorFrame(),
                desk = new Matrix4f(frame).mul(deskSocket());
        pose.pushPose();
        try {
            pose.mulPoseMatrix(TabletHandModel.sourceCameraMatrix(inverse).mul(new Matrix4f(BASIS).invert()));
            ImportedCastModel cabin = ImportedCastModel.get("scene_cabin");
            if (cabin != null) {
                pose.pushPose();
                try {
                    pose.mulPoseMatrix(TabletHandModel.sourceCameraMatrix(frame).mul(new Matrix4f(BASIS).invert()));
                    cabin.renderCabin(pose, buffers, light, OverlayTexture.NO_OVERLAY, Map.of(), CABIN_LIGHTS);
                } finally {
                    pose.popPose();
                }
            }
            model("scene_desk", "vm_ks_gunship_desk_outro", ticks, pose, buffers, light, desk, true);
            var deskPose = SceneEntryLibrary.pose("scene_desk", "vm_ks_gunship_desk_outro", ticks);
            Matrix4f joystick = new Matrix4f(desk)
                    .mul(SceneEntryLibrary.socket("scene_desk", deskPose, "tag_joystick_1"));
            model("scene_joystick", "vm_ks_gunship_joystick_outro", ticks, pose, buffers, light, joystick, true);
            for (Prop prop : PROPS) {
                String action = prop.action.replace("_intro", "_outro");
                if (prop.role.equals("co_gunner"))
                    avatar(pose, buffers, OperatorSkins.skin(OperatorSkins.Role.GUNSHIP_COPILOT),
                            GunshipCabinLighting.operatorLight(light),
                            SceneEntryLibrary.pose("scene_copilot", action, ticks),
                            SceneEntryLibrary.pose("scene_copilot", "", 0), desk, inverse);
                else
                    model(SceneEntryLibrary.lod(prop.model, 0), action, ticks, pose, buffers, light, desk, false);
            }
            var actor = SceneEntryLibrary.pose("scene_operator", "vm_ks_gunship_player_outro", ticks);
            avatar(pose, buffers, OperatorSkins.skin(OperatorSkins.Role.GUNSHIP_OPERATOR),
                    GunshipCabinLighting.operatorLight(light), actor, SceneEntryLibrary.pose("scene_operator", "", 0),
                    desk, inverse);
        } finally {
            pose.popPose();
        }
    }

    static void avatar(PoseStack scene, MultiBufferSource buffers, PlayerSkin skin, int light,
            Map<String, Matrix4f> actor, Map<String, Matrix4f> rest, Matrix4f attach, Matrix4f inverse) {
        Map<String, Matrix4f> bones = new HashMap<>();
        actor.forEach((name, m) -> bones.put(name, new Matrix4f(inverse).mul(attach).mul(m)));
        Matrix4f left = bones.get("j_hip_le"), right = bones.get("j_hip_ri");
        if (left != null && right != null) {
            Vector3f pelvis = left.getTranslation(new Vector3f()).add(right.getTranslation(new Vector3f())).mul(.5F);
            bones.put("j_mainroot", new Matrix4f(left).setTranslation(pelvis));
        }

        // 源摄像机相对的人物渲染器已自行完成坐标转换。
        scene.pushPose();
        try {
            scene.mulPoseMatrix(TabletHandModel.sourceCameraMatrix(new Matrix4f(inverse)).mul(new Matrix4f(BASIS).invert())
                    .invert());
            EntryScenes.renderPlayer(scene, buffers, light, skin.texture(), skin.model() == PlayerSkin.Model.SLIM,
                    bones, rest);
        } finally {
            scene.popPose();
        }
    }

    private static void model(String id, String action, float ticks, PoseStack pose, MultiBufferSource buffers,
            int light, Matrix4f attach, boolean station) {
        ImportedCastModel model = ImportedCastModel.get(id);
        if (model == null)
            return;
        Matrix4f converted = TabletHandModel.sourceCameraMatrix(attach).mul(new Matrix4f(BASIS).invert());
        pose.pushPose();
        try {
            pose.mulPoseMatrix(converted);
            Map<String, Matrix4f> bones = SceneEntryLibrary.pose(id, action, ticks);
            if (station)
                model.renderCabin(pose, buffers, light, OverlayTexture.NO_OVERLAY, bones, GunshipCabinLighting.STATION);
            else
                model.renderScene(pose, buffers, light, OverlayTexture.NO_OVERLAY, bones);
        } finally {
            pose.popPose();
        }
    }
}
