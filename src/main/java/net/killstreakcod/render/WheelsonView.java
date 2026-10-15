package net.killstreakcod.render;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Shared chase-camera geometry also lets the server converge fire on the visible crosshair.
 * 共用追尾摄像机几何计算，使服务端也能把火力汇聚到可见准心处。
 */
public final class WheelsonView {
    public static final double DISTANCE = 4.5, FOCUS_HEIGHT = .55;

    private WheelsonView() {}

    public static Vec3 position(Entity vehicle, float yaw, float pitch, float partial) {
        Vec3 focus = vehicle.getPosition(partial).add(0, vehicle.getEyeHeight() + FOCUS_HEIGHT, 0);
        Vec3 back = Vec3.directionFromRotation(pitch, yaw).scale(-1);
        double distance = DISTANCE;

        // 使用与原版分离摄像机相同的八角探测方式。
        for (int i = 0; i < 8; i++) {
            Vec3 from = focus.add(((i & 1) * 2 - 1) * .1, (((i >> 1) & 1) * 2 - 1) * .1, (((i >> 2) & 1) * 2 - 1) * .1);
            var hit = vehicle.level().clip(new ClipContext(from, from.add(back.scale(DISTANCE)),
                    ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, vehicle));
            if (hit.getType() != HitResult.Type.MISS)
                distance = Math.min(distance, Math.max(.15, from.distanceTo(hit.getLocation()) - .1));
        }
        return focus.add(back.scale(distance));
    }
}
