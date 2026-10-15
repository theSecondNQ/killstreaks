package net.killstreakcod.universal;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Client confirmation for contacts; collision rays never expose entities through walls.
 * 在客户端确认目标可见性，碰撞射线不得暴露墙后的实体。
 */
public final class TacticalVisibility {
    private TacticalVisibility() {}

    public static boolean visibleFromCamera(LivingEntity entity) {
        if (!entity.isAlive() || entity.isInvisible())
            return false;
        return pointVisible(entity.getEyePosition())
                || pointVisible(entity.position().add(0, entity.getBbHeight() * .55, 0));
    }

    public static boolean pointVisible(Vec3 point) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.getCameraEntity() == null)
            return false;
        return clear(mc.gameRenderer.getMainCamera().getPosition(), point);
    }

    /**
     * UAV and map feeds look down from the sky, independently of the operator's view.
     * 无人侦察机与地图画面从空中向下观察，不依赖操作者当前视角。
     */
    public static boolean surfacePointVisible(Vec3 point) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !mc.level.hasChunk((int) Math.floor(point.x) >> 4, (int) Math.floor(point.z) >> 4))
            return false;
        int top = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(point.x),
                (int) Math.floor(point.z));
        return point.y >= top || clear(new Vec3(point.x, top + 2, point.z), point.add(0, .05, 0));
    }

    private static boolean clear(Vec3 start, Vec3 end) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return false;
        var hit = mc.level.clip(
                new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.getCameraEntity()));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(end) < .0025;
    }
}
