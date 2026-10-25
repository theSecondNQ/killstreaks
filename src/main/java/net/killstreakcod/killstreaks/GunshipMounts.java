package net.killstreakcod.killstreaks;

import net.killstreakcod.universal.StreakTimings;

import java.util.List;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** 炮艇机体、稳定摄像机和三门侧射炮共用的原资源挂点及坐标变换。 */
public final class GunshipMounts {
    private static final float RAD = (float) (Math.PI / 180);
    private static final int BANK_TICKS = 40;
    private static final Vec3 BODY = new Vec3(87.005165, 0, 415.160461);
    // 镜头位于 25mm 与 40mm 炮之间、炮架下方，避开中间炮管的近处转动范围。
    private static final Vec3 CAMERA = new Vec3(460, 335, 85);
    private static final Vec3 SMALL_PIVOT = new Vec3(761.997437, 191.241684, 168.094666);
    private static final Vec3 SMALL_TIP = new Vec3(761.997447, 348.162956, 75.459865);
    private static final Vec3 LARGE_PIVOT = new Vec3(-235.732315, 188.805817, 202.943466);
    private static final Vec3 LARGE_TIP = new Vec3(-235.748662, 557.944071, 104.041809);
    private static final List<Mount> CANNONS = List.of(
            new Mount("bone_e87b83b2", SMALL_PIVOT, SMALL_PIVOT, SMALL_TIP, 1),
            new Mount("bone_89ac323b", LARGE_PIVOT, new Vec3(240, 190, 195), LARGE_TIP, .62F),
            new Mount("bone_89ac323b", LARGE_PIVOT, LARGE_PIVOT, LARGE_TIP, 1));

    private GunshipMounts() {}

    private record Mount(String bone, Vec3 bindPivot, Vec3 pivot, Vec3 tip, float scale) {
    }

    /** deformation 将源资源绑定坐标变换到本门炮的瞄准姿态；位置单位仍为厘米。 */
    public record CannonPose(String bone, Matrix4f deformation, Vec3 muzzle, Vec3 direction) {
    }

    /** 列表顺序与现有武器槽一致：25mm、40mm、105mm。 */
    public record Battery(List<CannonPose> cannons) {
        public CannonPose cannon(int weapon) {
            return cannons.get(weapon);
        }
    }

    /** 入场末段平滑左倾 30 度，退场时由当前倾斜进度逐步改平，机头保持沿航向。 */
    public static Matrix4f bodyTransform(TacticalUnitEntity unit, float partial) {
        float t = unit.departing() ? 1 - Mth.clamp(unit.nativeClock(partial) / BANK_TICKS, 0, 1)
                : ingressProgress(unit.nativeClock(partial));
        float bank = 30 * t * t * (3 - 2 * t);
        Vec3 pivot = converted(BODY);
        return new Matrix4f().rotationY((180 - unit.renderFlightYaw(partial)) * RAD)
                .rotateX(-unit.renderFlightPitch(partial) * RAD)
                .translate((float) pivot.x, (float) pivot.y, (float) pivot.z).rotateZ(bank * RAD)
                .translate((float) -pivot.x, (float) -pivot.y, (float) -pivot.z);
    }

    /** 入场倾斜进度转换成退场改平进度，提前结束时从当时的机翼角度继续。 */
    public static int departureClock(float flightClock) {
        return Math.round(BANK_TICKS * (1 - ingressProgress(flightClock)));
    }

    private static float ingressProgress(float clock) {
        return Mth.clamp((clock - StreakTimings.entry("gunship") + BANK_TICKS) / BANK_TICKS, 0, 1);
    }

    /** 镜头固定在左翼下方靠近机体的挂点，观察方向仍使用稳定的世界空间瞄准角度。 */
    public static Vec3 camera(TacticalUnitEntity unit, float partial) {
        return world(unit, bodyTransform(unit, partial), CAMERA, partial);
    }

    /** 客户端火炮动作与服务端弹道从同一个准心目标求解，补偿三门炮和摄像机之间的视差。 */
    public static Battery aim(TacticalUnitEntity unit, float yaw, float pitch, float partial) {
        Matrix4f body = bodyTransform(unit, partial), inverse = new Matrix4f(body).invert();
        Vec3 from = world(unit, body, CAMERA, partial), direction = Vec3.directionFromRotation(pitch, yaw);
        Vec3 end = from.add(direction.scale(320));
        Vec3 target = unit.level()
                .clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, unit))
                .getLocation();
        double nearest = from.distanceToSqr(target);
        for (Entity entity : unit.level().getEntities(unit, new AABB(from, target).inflate(.3),
                candidate -> sightTarget(unit, candidate))) {
            var hit = entity.getBoundingBox().inflate(.12).clip(from, target);
            if (hit.isPresent() && from.distanceToSqr(hit.get()) < nearest) {
                target = hit.get();
                nearest = from.distanceToSqr(target);
            }
        }
        CannonPose[] poses = new CannonPose[CANNONS.size()];
        for (int weapon = 0; weapon < poses.length; weapon++) {
            Mount mount = CANNONS.get(weapon);
            Vec3 pivot = world(unit, body, mount.pivot, partial);
            Vector3f desired = inverse.transformDirection(vector(target.subtract(pivot)));
            if (desired.lengthSquared() < .000001F)
                inverse.transformDirection(vector(direction), desired);
            Vector3f sourceDirection = new Vector3f(-desired.z, -desired.x, desired.y).normalize();
            Quaternionf rotation = new Quaternionf().rotationTo(vector(mount.tip.subtract(mount.bindPivot)).normalize(),
                    sourceDirection);
            Matrix4f deformation = new Matrix4f().translation(vector(mount.pivot)).rotate(rotation).scale(mount.scale)
                    .translate(vector(mount.bindPivot.scale(-1)));
            Vector3f tip = deformation.transformPosition(vector(mount.tip));
            Vec3 muzzle = world(unit, body, new Vec3(tip.x, tip.y, tip.z), partial);
            poses[weapon] = new CannonPose(mount.bone, deformation, muzzle, target.subtract(muzzle).normalize());
        }
        return new Battery(List.of(poses));
    }

    private static boolean sightTarget(TacticalUnitEntity source, Entity candidate) {
        if (!candidate.isAlive() || !candidate.isPickable() || candidate.isSpectator())
            return false;
        if (source.ownerId != null && (source.ownerId.equals(candidate.getUUID())
                || candidate instanceof TacticalUnitEntity other && source.ownerId.equals(other.ownerId)))
            return false;
        return !(candidate instanceof TacticalUnitEntity other
                && (other.kind() == TacticalUnitEntity.Kind.ROCKET || other.kind() == TacticalUnitEntity.Kind.BOMB));
    }

    private static Vec3 world(TacticalUnitEntity unit, Matrix4f body, Vec3 source, float partial) {
        Vector3f point = body.transformPosition(vector(converted(source)));
        return unit.getPosition(partial).add(point.x, point.y, point.z);
    }

    private static Vec3 converted(Vec3 source) {
        return new Vec3(-source.y * .01, source.z * .01, -source.x * .01);
    }

    private static Vector3f vector(Vec3 point) {
        return new Vector3f((float) point.x, (float) point.y, (float) point.z);
    }
}
