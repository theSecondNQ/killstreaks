package net.killstreakcod.render;

import net.killstreakcod.killstreaks.TacticalUnitEntity;
import net.killstreakcod.universal.AircraftFlight;
import net.killstreakcod.universal.NativeArsenalLibrary;
import net.killstreakcod.universal.SceneEntryLibrary;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Original vehicle surfaces, with independent chassis and mechanical bone animation.
 * 绘制原资源载具表面，底盘姿态与机械骨骼动画分别计算。
 */
public final class ImportedUnits {
    private static final Map<ImportedCastModel, UnitRig> RIGS = new WeakHashMap<>();
    private static final Map<String, WorldModels> WORLD_MODELS = new HashMap<>();
    private static final Set<String> SIMPLIFIED = Set.of("crate", "jugg_crate", "parachute", "wheelson_body",
            "wheelson_turret", "sentry_fire_rig", "bomb_drone", "delivery", "chopper", "chopper_turret", "overwatch",
            "gunship", "gunship_side_door", "gunship_rear_top", "gunship_rear_bottom", "precision", "su25", "cruise",
            "vtol", "vtol_turret", "vtol_canopy", "uav", "advanced_uav", "stealth", "cluster_mine",
            "drop_marker_projectile");

    private ImportedUnits() {}

    static void clearCache() {
        RIGS.clear();
        WORLD_MODELS.clear();
    }

    private static UnitRig rig(ImportedCastModel model) {
        return RIGS.computeIfAbsent(model, UnitRig::new);
    }

    private static WorldModels models(String id) {
        return WORLD_MODELS.computeIfAbsent(id, WorldModels::new);
    }

    public static boolean render(TacticalUnitEntity unit, float partial, PoseStack pose, MultiBufferSource buffers,
            int light) {
        if (unit.hiddenSaePayload())
            return true;
        if (unit.crashing())
            return AircraftCrashRenderer.world(unit, partial, pose, buffers, light);

        // 按要求不显示电子干扰机机体，但返回已处理状态，避免出现回退模型。
        if (unit.kind() == TacticalUnitEntity.Kind.COUNTER_UAV)
            return true;
        var viewer = Minecraft.getInstance().getCameraEntity();
        double distance = viewer == null ? 0 : viewer.distanceToSqr(unit);
        String id = switch (unit.kind()) {
            case UAV -> "uav";
            case ADVANCED_UAV -> "advanced_uav";
            case BOMB_DRONE -> "bomb_drone";
            case CHOPPER -> "scene_chopper";
            case OVERWATCH -> "crash_hind";
            case DELIVERY -> "delivery";
            case PRECISION -> "precision";
            case SAE -> "su25";
            case STEALTH -> "stealth";
            case VTOL -> "vtol";
            case GUNSHIP -> "scene_gunship";
            case CRUISE -> "cruise_native_warhead";
            case SENTRY -> "sentry_fire_rig";
            case WHEELSON -> "wheelson_body";
            case CRATE -> "crate";
            case JUGG_CRATE -> "jugg_crate";
            case CLUSTER -> "cluster21_body";
            case MINE -> "cluster21_disc";
            case SMOKE_MARKER -> "drop_marker_projectile";
            case BOMB -> switch (Math.abs(unit.stage())) {
                case 3 -> "mortar_shell";
                case 6 -> "fuel_bomb";
                default -> null;
            };
            default -> null;
        };
        if (unit.kind() == TacticalUnitEntity.Kind.CRUISE)
            return NativeCruiseScene.world(unit, partial, pose, buffers, light, distance);
        if (unit.kind() == TacticalUnitEntity.Kind.CLUSTER && unit.stage() < 0)
            return true;
        if (id == null)
            return false;
        WorldModels variants = models(id);
        if (variants.full == null && unit.kind() == TacticalUnitEntity.Kind.SENTRY)
            variants = models("sentry");
        if (variants.full == null)
            return false;

        // 查询 API 失败时也选择已有的轻量网格，不隐藏实体或阴影。
        int tier = ImportedModelQuality.worldTier(distance, unit.groundUnit());
        ImportedCastModel original = variants.full, model = variants.select(tier);
        boolean independentFlight = AircraftFlight.aircraft(unit.kind())
                || unit.kind() == TacticalUnitEntity.Kind.CRUISE;
        float yaw = independentFlight ? unit.renderFlightYaw(partial)
                : Mth.rotLerp(partial, unit.yRotO, unit.getYRot());
        float age = unit.tickCount + partial;
        pose.pushPose();
        try {

            // 导出的 VTOL 骨架使用较小的制作单位，需将机体适配到现有飞机尺寸。
            if (unit.kind() == TacticalUnitEntity.Kind.VTOL)
                pose.scale(2.5F, 2.5F, 2.5F);

            // 导入表面以 Y 轴向上，与此前的 Minecraft ModelPart 网格坐标约定不同。
            pose.mulPose(Axis.YP.rotationDegrees(180 - yaw));
            if (independentFlight)
                pose.mulPose(Axis.XP.rotationDegrees(-unit.renderFlightPitch(partial)));

            // 迫击炮弹和汽油弹的弹头指向源资源正 X 轴，转换后指向局部负 Z 轴。
            if (unit.kind() == TacticalUnitEntity.Kind.BOMB
                    && (Math.abs(unit.stage()) == 3 || Math.abs(unit.stage()) == 6))
                pose.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partial, unit.xRotO, unit.getXRot())));
            boolean placement = switch (unit.kind()) {
                case SENTRY, WHEELSON, CLUSTER, MINE, CRATE, JUGG_CRATE, SMOKE_MARKER -> true;
                default -> false;
            };
            if (placement) {

                // 物品包围范围已为手持显示居中，而场景顶点保留源坐标；使网格贴地前先恢复源资源 Z 坐标。
                pose.translate(0, -rig(original).sceneMin, 0);
            }
            if (unit.kind() == TacticalUnitEntity.Kind.SMOKE_MARKER && unit.stage() == 0) {
                pose.translate(0, .08, 0);
                pose.mulPose(Axis.XP.rotationDegrees(age * 24));
                pose.translate(0, -.08, 0);
            }
            Map<String, Matrix4f> bones = mechanicalPose(model, unit, yaw, age, partial);
            model.renderScene(pose, buffers, light, OverlayTexture.NO_OVERLAY, bones);
            if (unit.kind() == TacticalUnitEntity.Kind.GUNSHIP && !id.startsWith("scene_"))
                gunshipDoors(original, pose, buffers, light, tier);
            if ((unit.kind() == TacticalUnitEntity.Kind.CRATE || unit.kind() == TacticalUnitEntity.Kind.JUGG_CRATE)
                    && unit.stage() < 0) {
                var canopyModels = models("parachute");
                ImportedCastModel canopy = canopyModels.select(tier);
                if (canopy != null) {
                    pose.pushPose();
                    try {
                        pose.translate(0, rig(original).maxY - rig(canopyModels.full).minY, 0);
                        canopy.renderScene(pose, buffers, light, OverlayTexture.NO_OVERLAY, Map.of());
                    } finally {
                        pose.popPose();
                    }
                }
            }
            if (unit.kind() == TacticalUnitEntity.Kind.WHEELSON)
                attachment(original, "tag_turret", "wheelson_turret", unit, yaw, age, partial, pose, buffers, light,
                        tier);
            if (unit.kind() == TacticalUnitEntity.Kind.CHOPPER)
                attachment(original, "tag_turret", "scene_chopper_turret", unit, yaw, age, partial, pose, buffers,
                        light, tier);
            if (unit.kind() == TacticalUnitEntity.Kind.VTOL) {
                attachment(original, "tag_canopy", "vtol_canopy", unit, yaw, age, partial, pose, buffers, light, tier);
                attachment(original, "tag_turret", "vtol_turret", unit, yaw, age, partial, pose, buffers, light, tier);
            }
        } finally {
            pose.popPose();
        }
        return true;
    }

    private static void attachment(ImportedCastModel parent, String tag, String id, TacticalUnitEntity unit, float yaw,
            float age, float partial, PoseStack pose, MultiBufferSource buffers, int light, int tier) {
        ImportedCastModel child = models(id).select(tier);
        if (child == null)
            return;
        Matrix4f converted = rig(parent).attachments.get(tag);
        if (converted == null)
            return;
        if (unit.kind() == TacticalUnitEntity.Kind.VTOL) {
            var animated = NativeModelAnimations
                    .vtol(parent, unit.nativeClock(partial), unit.departing() || unit.stage() == 2).get(tag);
            if (animated != null)
                converted = rig(parent).convertBone(animated);
        }
        pose.pushPose();
        try {
            pose.mulPose(converted);
            child.renderScene(pose, buffers, light, OverlayTexture.NO_OVERLAY,
                    mechanicalPose(child, unit, yaw, age, partial, id.equals("wheelson_turret")));
        } finally {
            pose.popPose();
        }
    }

    private static void gunshipDoors(ImportedCastModel body, PoseStack pose, MultiBufferSource buffers, int light,
            int tier) {
        for (var entry : Map.of("bone_b4735888", "gunship_side_door", "bone_fa32c4a9", "gunship_rear_top",
                "bone_39ca88a", "gunship_rear_bottom").entrySet()) {
            ImportedCastModel door = tier < 0 ? models(entry.getValue()).full : models(entry.getValue()).select(tier);
            Matrix4f mount = rig(body).attachments.get(entry.getKey());
            if (door == null || mount == null)
                continue;
            pose.pushPose();
            try {
                pose.mulPose(mount);
                door.renderScene(pose, buffers, light, OverlayTexture.NO_OVERLAY, Map.of());
            } finally {
                pose.popPose();
            }
        }
    }

    private static Map<String, Matrix4f> mechanicalPose(ImportedCastModel model, TacticalUnitEntity unit, float bodyYaw,
            float age, float partial) {
        return mechanicalPose(model, unit, bodyYaw, age, partial, false);
    }

    static Map<String, Matrix4f> crashMechanicalPose(ImportedCastModel model, TacticalUnitEntity unit, float partial) {
        return mechanicalPose(model, unit, unit.renderFlightYaw(partial), unit.tickCount + partial, partial, false);
    }

    static void crashVtol(TacticalUnitEntity unit, float partial, PoseStack pose, MultiBufferSource buffers, int light,
            int tier) {
        var variants = models("vtol");
        ImportedCastModel model = variants.select(tier);
        if (model == null)
            return;
        model.renderScene(pose, buffers, light, OverlayTexture.NO_OVERLAY, crashMechanicalPose(model, unit, partial));
        float yaw = unit.renderFlightYaw(partial), age = unit.tickCount + partial;
        if (!unit.crashCanopy())
            attachment(variants.full, "tag_canopy", "vtol_canopy", unit, yaw, age, partial, pose, buffers, light, tier);
        attachment(variants.full, "tag_turret", "vtol_turret", unit, yaw, age, partial, pose, buffers, light, tier);
    }

    private static Map<String, Matrix4f> mechanicalPose(ImportedCastModel model, TacticalUnitEntity unit, float bodyYaw,
            float age, float partial, boolean roverTurret) {
        if (unit.kind() == TacticalUnitEntity.Kind.GUNSHIP)
            return Map.of();
        if (unit.kind() == TacticalUnitEntity.Kind.VTOL
                && model.bindBones().stream().anyMatch(b -> b.name().equals("back_engine_left")))
            return NativeModelAnimations.vtol(model, unit.nativeClock(partial), unit.departing() || unit.stage() == 2);
        UnitRig rig = rig(model);
        List<ImportedCastModel.Bone> bones = rig.bones;
        boolean drone = unit.kind() == TacticalUnitEntity.Kind.BOMB_DRONE
                || unit.kind() == TacticalUnitEntity.Kind.COUNTER_UAV;
        if (rig.mainRotor < 0 && rig.tailRotor < 0 && !rig.hasYaw && !rig.hasBarrel
                && unit.kind() != TacticalUnitEntity.Kind.WHEELSON && !drone)
            return Map.of();
        Arrays.fill(rig.ready, false);
        float turret = -Mth.wrapDegrees(unit.renderAimYaw(partial) - bodyYaw) * Mth.DEG_TO_RAD;
        float pitch = unit.renderAimPitch(partial) * Mth.DEG_TO_RAD;
        if (unit.kind() == TacticalUnitEntity.Kind.CHOPPER) {

            // 传感器在世界空间瞄准，运动机体另有自身姿态；转动机炮前，先把瞄准射线转换到机体空间。
            var aim = net.minecraft.world.phys.Vec3.directionFromRotation(unit.renderAimPitch(partial),
                    unit.renderAimYaw(partial));
            Vector3f local = rig.scratch.rotationY((180 - bodyYaw) * Mth.DEG_TO_RAD)
                    .rotateX(-unit.renderFlightPitch(partial) * Mth.DEG_TO_RAD).invert()
                    .transformDirection(rig.direction.set((float) aim.x, (float) aim.y, (float) aim.z));

            // 逆变换 C 将机体局部的 Minecraft 射线映射到源坐标 (-Z,-X,+Y)。
            turret = (float) Math.atan2(-local.x, -local.z);
            pitch = (float) -Math.atan2(local.y, Math.hypot(local.x, local.z));
        }
        boolean originalSentry = unit.kind() == TacticalUnitEntity.Kind.SENTRY && rig.originalSentry;
        for (int i = 0; i < bones.size(); i++) {
            var bone = bones.get(i);
            Matrix4f world = rig.worlds[i].set(rig.bindWorld[i]);
            if (bone.parent() >= 0 && bone.parent() < bones.size()) {
                if (rig.ready[bone.parent()])
                    world.set(rig.deltas[bone.parent()]).mul(rig.bindWorld[i]);
            }
            String name = bone.name();
            if (i == rig.mainRotor)
                aroundSourceAxis(rig, world, age * 1.35F, true);
            if (i == rig.tailRotor)
                aroundSourceAxis(rig, world, age * 2F, false);
            if (rig.wheel[i])
                aroundSourceAxis(rig, world, -unit.walkAnimation.position(partial) * 2.8F, false);
            if (drone && rig.dronePropeller[i])
                aroundSourceAxis(rig, world, age * 2.4F, true);

            // 原资源 59 骨骼骨架的偏航和俯仰挂点属于无几何的瞄准分支；真正带权重的炮架和枪体位于同级骨骼层级，只转动挂点不会改变可见表面。
            // 同级的 j_base 控制三脚架，保持绑定姿态。
            if (originalSentry) {
                if (name.equals("tag_turret_yaw") || name.equals("bone_8a57e9dc"))
                    world.rotateZ(turret);
                if (name.equals("tag_turret_pitch") || name.equals("tag_aim_pivot"))
                    world.rotateY(pitch);
            } else {
                if ((name.equals(rig.hasYaw ? "tag_turret_yaw" : "tag_turret")
                        && unit.kind() != TacticalUnitEntity.Kind.WHEELSON)
                        || (name.equals("tag_turret") && roverTurret))
                    world.rotateZ(turret);
                if (name.equals(rig.hasPitch ? "tag_turret_pitch" : rig.hasBarrel ? "tag_barrel" : "tag_aim_pivot"))
                    world.rotateY(pitch);
            }
            if (unit.kind() == TacticalUnitEntity.Kind.SENTRY && unit.firing()) {

                // 仅让带权重的枪管组件绕源资源正 X 射击轴旋转；机匣沿瞄准轴后坐，开火动作不改动机枪俯仰。
                if (name.equals("tag_barrel"))
                    world.rotateX(age * 1.8F);
                if (name.equals(originalSentry ? "tag_aim_animated" : "tag_barrel"))
                    world.translate(-Math.abs(Mth.sin(age * 5)) * .65F, 0, 0);
            }
            if (world.equals(rig.bindWorld[i]))
                rig.deltas[i].identity();
            else
                rig.deltas[i].set(world).mul(rig.inverseBind[i]);
            rig.ready[i] = true;
        }
        return rig.posed;
    }

    private static final class WorldModels {
        final ImportedCastModel full;
        final String[] levels;

        WorldModels(String id) {
            full = ImportedCastModel.get(id);
            levels = id.startsWith("cluster21_") || id.equals("sae21")
                    ? new String[] { NativeArsenalLibrary.lod(id, 0), NativeArsenalLibrary.lod(id, 1),
                            NativeArsenalLibrary.lod(id, 2) }
                    : id.startsWith("scene_") || id.startsWith("crash_")
                            ? new String[] { SceneEntryLibrary.lod(id, 0), SceneEntryLibrary.lod(id, 1),
                                    SceneEntryLibrary.lod(id, 2) }
                            : SIMPLIFIED.contains(id)
                                    ? new String[] { id + "_lod_near", id + "_lod_far", id + "_lod_shadow" }
                                    : null;
        }

        ImportedCastModel select(int tier) {
            if (full == null || levels == null)
                return full;
            ImportedCastModel selected = ImportedCastModel.get(levels[tier]);
            return selected == null ? full : selected;
        }
    }

    private static void aroundSourceAxis(UnitRig rig, Matrix4f world, float angle, boolean vertical) {
        float x = world.m30(), y = world.m31(), z = world.m32();
        Matrix4f rotate = rig.scratch.translation(x, y, z);
        if (vertical)
            rotate.rotateZ(angle);
        else
            rotate.rotateY(angle);
        world.set(rotate.translate(-x, -y, -z).mul(world));
    }

    /**
     * One workspace per cached asset, consumed synchronously by its draw on the render thread.
     * 每个缓存资源使用一组计算缓冲，在渲染线程中由对应绘制同步使用。
     */
    private static final class UnitRig {
        final List<ImportedCastModel.Bone> bones;
        final Matrix4f[] bindWorld, inverseBind, worlds, deltas;
        final Map<String, Matrix4f> posed, attachments = new HashMap<>();
        final Matrix4f scratch = new Matrix4f();
        final Vector3f direction = new Vector3f();
        final boolean[] ready, wheel, dronePropeller;
        final int mainRotor, tailRotor;
        final boolean hasYaw, hasPitch, hasBarrel, originalSentry;
        final float sceneMin, minY, maxY;
        final Matrix4f basis, inverseBasis;

        UnitRig(ImportedCastModel model) {
            basis = new Matrix4f().m00(0).m01(0).m02(-1).m10(-1).m11(0).m12(0).m20(0).m21(1).m22(0)
                    .scale(model.sourceUnitScale());
            inverseBasis = new Matrix4f(basis).invert();
            bones = model.bindBones();
            int size = bones.size();
            bindWorld = new Matrix4f[size];
            inverseBind = new Matrix4f[size];
            worlds = new Matrix4f[size];
            deltas = new Matrix4f[size];
            ready = new boolean[size];
            wheel = new boolean[size];
            dronePropeller = new boolean[size];
            posed = new HashMap<>(Math.max(16, size * 4 / 3 + 1));
            Map<String, Integer> index = new HashMap<>();
            for (int i = 0; i < size; i++) {
                var bone = bones.get(i);
                String name = bone.name();
                index.put(name, i);
                bindWorld[i] = bone.worldMatrix();
                inverseBind[i] = new Matrix4f(bindWorld[i]).invert();
                worlds[i] = new Matrix4f();
                deltas[i] = new Matrix4f();
                posed.put(name, worlds[i]);
                int parent = bone.parent();
                wheel[i] = parent >= 0 && parent < size && bones.get(parent).name().startsWith("tag_wheel_")
                        && Math.abs(bone.worldPosition().z - bones.get(parent).worldPosition().z) > 10;
                dronePropeller[i] = name.equals("bone_165f9ff1") || name.equals("bone_9a41abef")
                        || name.equals("bone_3e3322dd") || name.equals("bone_ca9a21a3");
            }
            mainRotor = rotorParent(index, "tag_main_rotor_static");
            tailRotor = rotorParent(index, "tag_tail_rotor_static");
            hasYaw = index.containsKey("tag_turret_yaw");
            hasPitch = index.containsKey("tag_turret_pitch");
            hasBarrel = index.containsKey("tag_barrel");
            originalSentry = index.containsKey("bone_8a57e9dc") && index.containsKey("tag_aim_animated");
            for (String tag : new String[] { "tag_turret", "tag_canopy", "bone_b4735888", "bone_fa32c4a9",
                    "bone_39ca88a" }) {
                Integer anchor = index.get(tag);
                if (anchor != null)
                    attachments.put(tag, convertBone(bindWorld[anchor]));
            }
            minY = model.boundsMin().y;
            maxY = model.boundsMax().y;
            sceneMin = minY + model.sourceOrigin().z * model.sourceUnitScale();
        }

        Matrix4f convertBone(Matrix4f source) {
            return new Matrix4f(basis).mul(source).mul(inverseBasis);
        }

        int rotorParent(Map<String, Integer> index, String tag) {
            Integer rotor = index.get(tag);
            if (rotor == null)
                return -1;
            int parent = bones.get(rotor).parent();
            return parent >= 0 && parent < bones.size() ? parent : rotor;
        }
    }
}
