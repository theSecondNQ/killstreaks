package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.killstreaks.ArmorKit;
import net.killstreakcod.render.tablet.ScenePhoneScreen;
import net.killstreakcod.render.tablet.TabletHandModel;
import net.killstreakcod.render.tablet.TabletScreens;
import net.killstreakcod.render.tablet.TabletTargeting;
import net.killstreakcod.universal.StreakTimings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Original local CAST channels: linear translation, quaternion SLERP, matching named FK bones.
 * 使用原始局部 CAST 通道：位移做线性插值，旋转做四元数球面插值，并匹配同名正向运动学骨骼。
 */
public final class OriginalStreakAnimations {
    private static final Map<String, Optional<Clip>> CLIPS = new HashMap<>();
    private static List<Bone> skeleton;
    private static ChopperScene cachedChopper;
    private static float cachedChopperTicks = Float.NaN;
    private static Map<String, Matrix4f> cachedGunshipDeskPose;
    private static Frame cachedGunshipOperator;
    private static float cachedGunshipOperatorFrame = Float.NaN;
    private static float cachedGunshipEndElevation = Float.NaN;

    // 原桌面 mesh4 是右侧操作位下方带十字准心的矩形屏幕；源 UV 矩形为 U=.099854..184814、V=.581543..644531。
    private static final Vector3f GUNSHIP_SCREEN = new Vector3f(84.06257F, -28.469687F, 124.475136F);

    // 按面积加权计算原桌椅 mesh1 的坐垫上表面；髋关节位于接触表面上方，原脚趾已落在地板 Z=0 上。
    private static final Vector3f GUNSHIP_CUSHION = new Vector3f(85.03924F, 78.34581F, 54.84854F);
    private static final float GUNSHIP_CABIN_FLOOR_SOURCE_Z = 131.35043F;

    // 偏移 +202 厘米时，座舱 mesh5 面板位于独立原准心屏幕前方 8.7 厘米；将外围座舱后移 42 厘米，操作台、座椅、观察者和双手保持共同的源姿态。
    private static final float GUNSHIP_CABIN_SOURCE_Y = 160;
    private static final GunshipCabinLighting.Profile GUNSHIP_CABIN_LIGHTS = new GunshipCabinLighting.Profile(
            -GUNSHIP_CABIN_SOURCE_Y * .01F, -GUNSHIP_CABIN_FLOOR_SOURCE_Z * .01F, 0);
    private static final float GUNSHIP_SEAT_REFERENCE_FRAME = 70;

    private OriginalStreakAnimations() {}

    static void clearCache() {
        CLIPS.clear();
        skeleton = null;
        cachedChopper = null;
        cachedChopperTicks = Float.NaN;
        cachedGunshipDeskPose = null;
        cachedGunshipOperator = null;
        cachedGunshipOperatorFrame = Float.NaN;
        cachedGunshipEndElevation = Float.NaN;
    }

    private static JsonObject json(String id) throws IOException {
        var location = ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "mw/animations/" + id + ".json");
        try (var reader = Minecraft.getInstance().getResourceManager().getResourceOrThrow(location).openAsReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static List<Bone> bones() {
        if (skeleton != null)
            return skeleton;
        ArrayList<Bone> loaded = new ArrayList<>();
        try {
            for (var item : json("fp_reference_skeleton").getAsJsonArray("bones")) {
                var b = item.getAsJsonObject();
                loaded.add(new Bone(b.get("n").getAsString(), b.get("p").getAsInt(), vector(b.getAsJsonArray("lp")),
                        quaternion(b.getAsJsonArray("lr")), vector(b.getAsJsonArray("s"))));
            }
        }
        catch (IOException | RuntimeException failure) {
            loaded.clear();
        }
        return skeleton = List.copyOf(loaded);
    }

    private static Clip clip(String id) {
        return CLIPS.computeIfAbsent(id, key -> {
            try {
                return Optional.of(new Clip(json(key)));
            }
            catch (IOException | RuntimeException failure) {
                return Optional.empty();
            }
        }).orElse(null);
    }

    private static Vector3f vector(JsonArray a) {
        return new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
    }

    private static Quaternionf quaternion(JsonArray a) {
        return new Quaternionf(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat(),
                a.get(3).getAsFloat()).normalize();
    }

    private record Bone(String name, int parent, Vector3f position, Quaternionf rotation, Vector3f scale) {
    }

    private record Selection(String id, float frame, String device) {
    }

    /**
     * The source is played at its exported 30 FPS; any authoritative padding holds its endpoint.
     * 按导出的原始每秒 30 帧播放，服务端要求的额外时长保持在动作终点姿态。
     */
    private static Selection select(String reward, String stage, float ticks) {
        float seconds = Math.max(0, ticks) / 20;
        if (stage.equals("targeting"))
            return single("scene_vm_ks_tablet_raise", Float.MAX_VALUE, "tablet");
        if (stage.equals("remote_call"))
            return sequence(seconds, "tablet", "scene_vm_ks_tablet_raise", "scene_vm_ks_tablet_fire_tap");
        if (stage.equals("entry"))
            return switch (reward) {
                case "gunship" -> single("gunship_intro", seconds, "station");
                case "chopper_gunner" -> single("chopper_intro", seconds, "none");
                default -> null;
            };
        if (stage.equals("exit"))
            return StreakTimings.remoteReward(reward) || reward.equals("precision_airstrike")
                    ? single("scene_vm_ks_tablet_drop", seconds, "tablet")
                    : null;
        if (!stage.equals("call") && !stage.equals("confirmed"))
            return null;
        if (reward.equals("cluster_mine") || reward.equals("precision_airstrike") || reward.equals("mortar_strike")
                || reward.equals("care_package") || reward.equals("emergency_airdrop") || reward.equals("juggernaut"))
            return null;
        if (reward.equals("sentry_gun"))
            return single(stage.equals("confirmed") ? "sentry_deploy" : "sentry_raise", seconds, "sentry");
        if (stage.equals("call") && reward.equals("bomb_drone") && ticks < StreakTimings.BOMB_DEPLOY)
            return single("bomb_raise", seconds, "bomb");
        if (stage.equals("call") && StreakTimings.phoneReward(reward))
            return single("scene_vm_gesture_killstreak_smartphone_short", seconds, "phone");
        if (stage.equals("confirmed") && !reward.equals("sae") && !reward.equals("vtol_jet")
                && !reward.equals("stealth_bomber"))
            return null;

        // 表面标定时平板已经抬起，确认动作从该保持姿态继续，不再重复掏出设备。
        if (reward.equals("sae") || reward.equals("vtol_jet") || reward.equals("stealth_bomber"))
            return stage.equals("call") ? single("scene_vm_ks_tablet_raise", seconds, "tablet")
                    : sequence(seconds, "tablet", "scene_vm_ks_tablet_fire_tap", "scene_vm_ks_tablet_drop");
        if (reward.equals("mgb"))
            return sequence(seconds, "tablet", "scene_vm_ks_tablet_tac_nuke_raise", "scene_vm_ks_tablet_tac_nuke_drop");
        boolean remote = stage.equals("call") && StreakTimings.remoteReward(reward);

        // 匹配的触控笔动作使 j_pen 从收纳槽到手中的运动保持连续。
        return remote
                ? sequence(Math.max(0, seconds - StreakTimings.remoteTabletOffset(reward) / 20F), "tablet",
                        "scene_vm_ks_tablet_raise", "scene_vm_ks_tablet_fire_tap")
                : sequence(seconds, "tablet", "scene_vm_ks_tablet_raise", "scene_vm_ks_tablet_fire_tap",
                        "scene_vm_ks_tablet_drop");
    }

    private static Selection single(String id, float seconds, String device) {
        Clip c = clip(id);
        return c == null ? null : new Selection(id, Math.min(c.last, seconds * c.fps), device);
    }

    private static Selection sequence(float seconds, String device, String... ids) {
        for (int i = 0; i < ids.length; i++) {
            Clip c = clip(ids[i]);
            if (c == null)
                return null;
            float length = c.last / c.fps;
            if (seconds <= length || i == ids.length - 1)
                return new Selection(ids[i], Math.min(c.last, seconds * c.fps), device);
            seconds -= length;
        }
        return null;
    }

    public static boolean available(String reward, String stage) {
        Selection s = select(reward, stage, 0);
        return s != null && !bones().isEmpty()
                && (!s.device.equals("tablet") || ImportedCastModel.get(TabletScreens.modelId(reward)) != null)
                && (!s.device.equals("sentry") || ImportedCastModel.get("sentry_vm") != null)
                && (!s.device.equals("bomb") || ImportedCastModel.get("bomb_drone_vm") != null);
    }

    public static String actionId(String reward, String stage, float ticks) {
        Selection selected = select(reward, stage, ticks);
        return selected == null ? "" : selected.id;
    }

    /**
     * Draws actual skinned hands and attached equipment in the original camera's local frame.
     * 在原摄像机局部坐标中绘制实际蒙皮双手及挂接装备。
     */
    public static boolean render(String reward, String stage, float ticks, PoseStack pose, MultiBufferSource buffers,
            int light, TabletHandModel skinHands, ResourceLocation skin) {
        if (stage.equals("entry") && reward.equals("chopper_gunner"))
            return false; // 大型场景应在 AFTER_LEVEL 阶段绘制。
        Selection selection = select(reward, stage, ticks);
        if (selection == null)
            return false;

        // 原手机动作最后一段会在手机离开画面后重新抬起空手腕；在放低姿态结束手势，不再绘制这段回抬。
        if (selection.device.equals("phone") && selection.frame >= 44)
            return true;
        if (bones().isEmpty())
            return false;
        ImportedCastModel arms = ImportedCastModel.get("fp_arms");
        Clip action = clip(selection.id);
        Frame frame = stage.equals("entry") && reward.equals("gunship") ? gunshipOperator(action, selection.frame)
                : action.frame(selection.frame);
        boolean leftOnly = selection.device.equals("phone");
        var player = Minecraft.getInstance().player;
        boolean juggernaut = player != null
                && player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(ArmorKit.CHEST.get());
        if (juggernaut && arms != null)
            arms.renderFirstPersonArms(pose, buffers, light, OverlayTexture.NO_OVERLAY, frame.cameraBones, leftOnly);
        else
            skinHands.renderTrackedHands(pose, buffers, skin, light, frame.cameraBones, leftOnly);
        if (selection.device.equals("sentry") || selection.device.equals("bomb")) {
            ImportedCastModel equipment = ImportedCastModel
                    .get(selection.device.equals("sentry") ? "sentry_vm" : "bomb_drone_vm");
            if (equipment != null) {
                Map<String, Matrix4f> source = sampleModel(action, selection.frame, equipment);
                var root = equipment.bindBones().stream().filter(bone -> bone.parent() < 0).findFirst().orElse(null);
                Matrix4f matched = root == null ? null : frame.global.get(root.name());
                Matrix4f anchor = frame.global.get("tag_weapon");

                // 炸弹的 j_gun 已在第一人称手腕下随动作变化，应使用这个匹配的全局骨骼替换独立 VM 根节点，
                // 不再把相同相对曲线重复锚定到 tag_weapon。
                Matrix4f placement = matched != null
                        ? new Matrix4f(matched).mul(new Matrix4f(source.get(root.name())).invert())
                        : anchor;
                if (placement != null) {
                    Matrix4f view = new Matrix4f(frame.cameraInverse).mul(placement);
                    source.replaceAll((name, matrix) -> new Matrix4f(view).mul(matrix));
                    equipment.renderScene(pose, buffers, light, OverlayTexture.NO_OVERLAY, source);
                }
            }
        } else if (selection.device.equals("tablet")) {
            ImportedCastModel tablet = ImportedCastModel.get(TabletScreens.firstPersonModelId(reward));
            if (tablet == null)
                tablet = ImportedCastModel.get("tablet_uav");
            if (tablet != null) {
                Matrix4f anchor = frame.global.get("tag_weapon");
                if (anchor != null) {
                    Matrix4f accessory = new Matrix4f(anchor).mul(action.local("tag_accessory", selection.frame,
                            new Vector3f(), new Quaternionf(), new Vector3f(1)));
                    HashMap<String, Matrix4f> equipment = new HashMap<>();
                    for (var bone : tablet.bindBones()) {
                        Matrix4f source;
                        if (bone.parent() < 0)
                            source = new Matrix4f(accessory);
                        else {
                            var parent = tablet.bindBones().get(bone.parent());
                            Matrix4f parentMatrix = equipment.get(parent.name());
                            source = new Matrix4f(parentMatrix == null ? accessory : parentMatrix)
                                    .mul(action.local(bone.name(), selection.frame, bone.localPosition(),
                                            bone.localRotation(), bone.scale()));
                        }
                        equipment.put(bone.name(), source);
                    }
                    equipment.replaceAll((name, source) -> new Matrix4f(frame.cameraInverse).mul(source));
                    tablet.renderScene(pose, buffers, light, OverlayTexture.NO_OVERLAY, equipment,
                            TabletScreens.materialTextures(reward));
                    if (stage.equals("targeting"))
                        TabletTargeting.surface(tablet);
                }
            }
        } else if (selection.device.equals("phone")) {
            ImportedCastModel phone = ImportedCastModel.get("phone_native_vm");
            Matrix4f attach = frame.cameraBones.get("tag_accessory_left");

            // 原显示及隐藏注记只作用于道具，保留完整的第一人称骨骼层级。
            if (phone != null && attach != null && selection.frame >= 10 && selection.frame < 41) {
                HashMap<String, Matrix4f> equipment = new HashMap<>();
                for (var bone : phone.bindBones())
                    equipment.put(bone.name(), new Matrix4f(attach).translate(bone.worldPosition())
                            .rotate(bone.worldRotation()).scale(bone.scale()));
                phone.renderScene(pose, buffers, light, OverlayTexture.NO_OVERLAY, equipment,
                        ScenePhoneScreen.materials(reward, ticks));
            } else if (phone == null && attach != null) {

                // 这份导出不含手机网格，先校准一次制作时的绑定姿态，再完整保留原挂接位移和旋转轨迹。
                if (action.phoneBinding == null) {
                    Matrix4f reference = action.frame(Math.min(32, action.last)).cameraBones.get("tag_accessory_left");
                    action.phoneBinding = TabletHandModel.sourceCameraMatrix(reference)
                            .getNormalizedRotation(new Quaternionf()).invert().rotateX((float) Math.toRadians(-12))
                            .rotateZ((float) Math.toRadians(4));
                }
                skinHands.renderTrackedCompact(pose, buffers, light, attach, action.phoneBinding, ticks, reward);
            }
        }
        return true;
    }

    public record ChopperScene(ImportedCastModel helicopter, Map<String, Matrix4f> vehicle,
            Map<String, Matrix4f> player, Map<String, Matrix4f> rest, Matrix4f camera) {
    }

    private static Matrix4f gunshipSeatPlacement(Frame source) {
        Matrix4f left = source.global.get("j_hip_le"), right = source.global.get("j_hip_ri");
        if (left == null || right == null)
            return new Matrix4f();
        Vector3f pelvis = left.getTranslation(new Vector3f()).add(right.getTranslation(new Vector3f())).mul(.5F);

        // 让实际骨盆落座，而非按眼睛定位；不要把髋关节降到坐垫表面，否则两个原脚部接触轨迹都会埋入地面。
        // 每帧固定坐姿根节点，保留躯干和头部的相对动作，避免臀部在坐垫上滑动。
        return new Matrix4f().translation(GUNSHIP_CUSHION.x - pelvis.x, GUNSHIP_CUSHION.y - pelvis.y, 0);
    }

    private static boolean descendantOf(String name, String ancestor) {
        List<Bone> rig = bones();
        int node = -1;
        for (int i = 0; i < rig.size(); i++)
            if (rig.get(i).name.equals(name)) {
                node = i;
                break;
            }
        for (int depth = 0; node >= 0 && depth < rig.size(); depth++, node = rig.get(node).parent)
            if (rig.get(node).name.equals(ancestor))
                return true;
        return false;
    }

    private static void keepGunshipDeskContacts(Map<String, Matrix4f> global, Vector3f seat) {

        // 控制器属于桌面而非坐着的角色；在固定的源挂点处保留原手腕及手指轨迹，并将角色落座修正分配到肩部至手腕的手臂骨骼链。
        global.forEach((name, matrix) -> {
            for (String side : new String[] { "le", "ri" }) {
                float retained = -1;
                if (descendantOf(name, "j_wrist_" + side))
                    retained = 0;
                else if (name.startsWith("j_wristfronttwist1_" + side))
                    retained = .25F;
                else if (descendantOf(name, "j_elbow_" + side) || name.equals("j_elbowdq_" + side))
                    retained = .5F;
                if (retained >= 0) {
                    matrix.m30(matrix.m30() - seat.x * (1 - retained)).m31(matrix.m31() - seat.y * (1 - retained))
                            .m32(matrix.m32() - seat.z * (1 - retained));
                    break;
                }
            }
        });
    }

    /**
     * One seated actor / observer frame, anchored to the native cushion and desktop sockets.
     * 将坐姿角色及观察者坐标固定到原坐垫和桌面挂点。
     */
    private static Frame gunshipOperator(Clip player, float sample) {
        if (cachedGunshipOperator != null && cachedGunshipOperatorFrame == sample)
            return cachedGunshipOperator;
        Frame original = player.frame(sample);
        Matrix4f seat = gunshipSeatPlacement(original);
        Vector3f correction = seat.getTranslation(new Vector3f());
        Map<String, Matrix4f> global = new HashMap<>();
        original.global.forEach((name, matrix) -> global.put(name, new Matrix4f(seat).mul(matrix)));
        keepGunshipDeskContacts(global, correction);
        Matrix4f camera = global.get("tag_camera");
        if (camera != null) {
            float recenter = GUNSHIP_SCREEN.x - camera.m30();
            global.forEach((name, matrix) -> {
                if (descendantOf(name, "j_head") || descendantOf(name, "tag_cambone"))
                    matrix.m30(matrix.m30() + recenter);
            });
            Quaternionf rotation = camera.getNormalizedRotation(new Quaternionf());
            Vector3f forward = rotation.transform(new Vector3f(1, 0, 0));
            float current = (float) Math.atan2(forward.y, forward.x),
                    desired = (float) Math.atan2(GUNSHIP_SCREEN.y - camera.m31(), GUNSHIP_SCREEN.x - camera.m30());
            if (Float.isNaN(cachedGunshipEndElevation)) {
                Matrix4f endpoint = player.frame(player.last).global.get("tag_camera");
                Vector3f endForward = endpoint == null ? new Vector3f(1, 0, 0)
                        : endpoint.getNormalizedRotation(new Quaternionf()).transform(new Vector3f(1, 0, 0));
                cachedGunshipEndElevation = (float) Math.atan2(endForward.z, Math.hypot(endForward.x, endForward.y));
            }
            float originalElevation = (float) Math.atan2(forward.z, Math.hypot(forward.x, forward.y));
            Vector3f target = new Vector3f(GUNSHIP_SCREEN).sub(camera.getTranslation(new Vector3f()));
            float targetElevation = (float) Math.atan2(target.z, Math.hypot(target.x, target.y)) + originalElevation
                    - cachedGunshipEndElevation;

            // 将原低头看文件及抬头动作重新定位到实际屏幕；最终观察者正对准心屏幕中心，保留滚转和原前移过程，不产生侧向眼位漂移。
            Vector3f leftAxis = new Vector3f(-(float) Math.sin(desired), (float) Math.cos(desired), 0);
            Quaternionf aimed = new Quaternionf()
                    .rotateAxis(originalElevation - targetElevation, leftAxis.x, leftAxis.y, leftAxis.z)
                    .mul(new Quaternionf().rotateZ(desired - current).mul(rotation));
            camera = new Matrix4f().translation(camera.getTranslation(new Vector3f())).rotate(aimed);
            global.put("tag_camera", camera);
        }
        cachedGunshipOperatorFrame = sample;
        return cachedGunshipOperator = new Frame(global);
    }

    private static Map<String, Matrix4f> sampleModel(Clip clip, float time, ImportedCastModel model) {
        Map<String, Matrix4f> result = new HashMap<>();
        for (var bone : model.bindBones()) {
            Matrix4f local = clip.local(bone.name(), time, bone.localPosition(), bone.localRotation(), bone.scale());
            if (bone.parent() >= 0) {
                Matrix4f parent = result.get(model.bindBones().get(bone.parent()).name());
                if (parent != null)
                    local = new Matrix4f(parent).mul(local);
            }
            result.put(bone.name(), local);
        }
        return result;
    }

    static void sceneRotors(ImportedCastModel model, Map<String, Matrix4f> globals, float ticks) {
        if (model != null) {
            spinRotor(model, globals, "tag_main_rotor_static", ticks * 1.35F, true);
            spinRotor(model, globals, "tag_tail_rotor_static", ticks * 2F, false);
        }
    }

    private static void spinRotor(ImportedCastModel model, Map<String, Matrix4f> globals, String tag, float angle,
            boolean main) {
        List<ImportedCastModel.Bone> bones = model.bindBones();
        int hub = -1;
        for (int i = 0; i < bones.size(); i++)
            if (bones.get(i).name().equals(tag)) {
                hub = bones.get(i).parent();
                break;
            }
        if (hub < 0)
            return;
        var hubBone = bones.get(hub);
        Matrix4f current = globals.get(hubBone.name());
        if (current == null)
            return;
        Vector3f pivot = current.getTranslation(new Vector3f());

        // 在直升机动态姿态下保留源世界轴，轮毂静态预旋转已包含在绑定矩阵中。
        Quaternionf attitude = current.getNormalizedRotation(new Quaternionf())
                .mul(new Quaternionf(hubBone.worldRotation()).invert());
        Vector3f axis = attitude.transform(main ? new Vector3f(0, 0, 1) : new Vector3f(0, 1, 0)).normalize();
        Matrix4f delta = new Matrix4f().translation(pivot).rotate(angle, axis.x, axis.y, axis.z).translate(-pivot.x,
                -pivot.y, -pivot.z);
        for (int i = 0; i < bones.size(); i++) {
            int p = i;
            for (int depth = 0; p >= 0 && depth < bones.size(); depth++, p = bones.get(p).parent())
                if (p == hub) {
                    String name = bones.get(i).name();
                    globals.put(name, new Matrix4f(delta).mul(globals.get(name)));
                    break;
                }
        }
    }

    /**
     * Source camera roll is applied as an offset; authoritative remote aiming remains untouched.
     * 将原摄像机滚转作为偏移叠加，不改动由服务端确认的遥控瞄准方向。
     */
    public static float cameraRoll(String reward, String stage, float ticks) {
        Selection selection = select(reward, stage, ticks);
        if (selection == null || !stage.equals("entry"))
            return 0;
        Clip action = clip(selection.id);
        Matrix4f camera = action.frame(selection.frame).global.get("tag_camera");
        Matrix4f endpoint = action.frame(action.last).global.get("tag_camera");
        if (camera == null || endpoint == null)
            return 0;
        Quaternionf delta = endpoint.getNormalizedRotation(new Quaternionf()).invert()
                .mul(camera.getNormalizedRotation(new Quaternionf()));
        Vector3f angles = delta.getEulerAnglesXYZ(new Vector3f());
        return Mth.clamp((float) Math.toDegrees(angles.x), -12, 12);
    }

    private static final class Frame {
        final Map<String, Matrix4f> global, cameraBones;
        final Matrix4f cameraInverse;

        Frame(Map<String, Matrix4f> global) {
            this.global = global;
            Matrix4f camera = global.get("tag_camera");
            cameraInverse = camera == null ? new Matrix4f() : new Matrix4f(camera).invert();
            HashMap<String, Matrix4f> converted = new HashMap<>();
            global.forEach((name, matrix) -> converted.put(name, new Matrix4f(cameraInverse).mul(matrix)));
            cameraBones = converted;
        }
    }

    private static final class Clip {
        final float fps, last;
        final Map<String, Map<String, Curve>> curves = new HashMap<>();
        private float cachedTime = Float.NaN;
        private Frame cached;
        private Quaternionf phoneBinding;

        Clip(JsonObject source) {
            fps = source.get("fps").getAsFloat();
            last = source.get("last_frame").getAsFloat();
            for (var item : source.getAsJsonArray("curves")) {
                var c = item.getAsJsonObject();
                curves.computeIfAbsent(c.get("bone").getAsString(), key -> new HashMap<>())
                        .put(c.get("property").getAsString(), new Curve(c));
            }
        }

        Matrix4f local(String name, float time, Vector3f bindPosition, Quaternionf bindRotation, Vector3f bindScale) {
            Vector3f p = new Vector3f(bindPosition), s = new Vector3f(bindScale);
            Quaternionf q = new Quaternionf(bindRotation);
            Map<String, Curve> channels = curves.get(name);
            if (channels != null) {
                for (int axis = 0; axis < 3; axis++) {
                    Curve c = channels.get("t" + "xyz".charAt(axis));
                    if (c != null)
                        p.setComponent(axis, c.scalar(time, p.get(axis)));
                    c = channels.get("s" + "xyz".charAt(axis));
                    if (c != null)
                        s.setComponent(axis, c.scalar(time, s.get(axis)));
                }
                Curve rotation = channels.get("rq");
                if (rotation != null)
                    q = rotation.rotation(time, q);
            }
            return new Matrix4f().translation(p).rotate(q).scale(s);
        }

        Frame frame(float time) {
            if (cached != null && time == cachedTime)
                return cached;
            HashMap<String, Matrix4f> global = new HashMap<>();
            List<Bone> rest = bones();
            for (Bone bone : rest) {
                Matrix4f local = local(bone.name, time, bone.position, bone.rotation, bone.scale);
                if (bone.parent >= 0 && bone.parent < rest.size()) {
                    Matrix4f parent = global.get(rest.get(bone.parent).name);
                    if (parent != null)
                        local = new Matrix4f(parent).mul(local);
                }
                global.put(bone.name, local);
            }
            cachedTime = time;
            return cached = new Frame(global);
        }
    }

    private static final class Curve {
        final int[] frames;
        final float[] values;
        final String mode;
        final float weight;

        Curve(JsonObject c) {
            frames = new int[c.getAsJsonArray("frames").size()];
            values = new float[c.getAsJsonArray("values").size()];
            for (int i = 0; i < frames.length; i++)
                frames[i] = c.getAsJsonArray("frames").get(i).getAsInt();
            for (int i = 0; i < values.length; i++)
                values[i] = c.getAsJsonArray("values").get(i).getAsFloat();
            mode = c.get("mode").getAsString();
            weight = c.has("weight") ? c.get("weight").getAsFloat() : 1;
        }

        int index(float frame) {
            int lo = 0, hi = frames.length - 1;
            while (lo < hi) {
                int m = (lo + hi + 1) / 2;
                if (frames[m] <= frame)
                    lo = m;
                else
                    hi = m - 1;
            }
            return lo;
        }

        float fraction(float time, int lo, int hi) {
            return lo == hi ? 0 : Mth.clamp((time - frames[lo]) / (frames[hi] - frames[lo]), 0, 1);
        }

        float scalar(float time, float bind) {
            int lo = index(time), hi = Math.min(lo + 1, frames.length - 1);
            float value = Mth.lerp(fraction(time, lo, hi), values[lo], values[hi]);
            return mode.equals("absolute") ? value : bind + value * weight;
        }

        Quaternionf rotation(float time, Quaternionf bind) {
            int lo = index(time), hi = Math.min(lo + 1, frames.length - 1);
            Quaternionf a = quaternion(lo), b = quaternion(hi);
            if (a.dot(b) < 0)
                b.mul(-1);
            a.slerp(b, fraction(time, lo, hi)).normalize();
            return mode.equals("absolute") ? a
                    : new Quaternionf(bind).mul(new Quaternionf().slerp(a, Mth.clamp(weight, 0, 1))).normalize();
        }

        Quaternionf quaternion(int index) {
            int offset = index * 4;
            return new Quaternionf(values[offset], values[offset + 1], values[offset + 2], values[offset + 3])
                    .normalize();
        }
    }
}
