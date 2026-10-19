package net.killstreakcod.universal.compat;

import net.killstreakcod.universal.TacticalRuntime;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import org.joml.Vector3d;

/**
 * Optional bridge verified against the supplied Superb Warfare 0.8.9.2 JAR.
 * 针对用户提供的 Superb Warfare 0.8.9.2 JAR 核对的可选兼容桥接。
 */
public final class SuperbWarfareCompat {
    private static boolean resolved, reported;
    private static Class<?> vehicleType;
    private static Method health, lastSource, obbs, clip;
    private static final Map<Class<?>, Field> SHOOTERS = new java.util.concurrent.ConcurrentHashMap<>();

    private SuperbWarfareCompat() {}

    private static synchronized void resolve() {
        if (resolved)
            return;
        resolved = true;
        if (!ModList.get().isLoaded("superbwarfare"))
            return;
        try {
            vehicleType = Class.forName("com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity");
            health = vehicleType.getMethod("getHealth");
            try {
                lastSource = vehicleType.getMethod("getLastDamageSource");
            }
            catch (NoSuchMethodException olderVersion) {
                lastSource = null;
            }
            obbs = vehicleType.getMethod("getOBBs");
            clip = Class.forName("com.atsuishio.superbwarfare.tools.OBB").getMethod("clip", Vector3d.class,
                    Vector3d.class);
            LogUtils.getLogger()
                    .info("Killstreaks: Superb Warfare vehicle hitboxes, damage and kill accounting enabled");
        }
        catch (ReflectiveOperationException | LinkageError failure) {
            obbs = clip = null;
            warn(failure);
        }
    }

    private static void warn(Throwable failure) {
        if (!reported) {
            reported = true;
            LogUtils.getLogger()
                    .warn("Superb Warfare optional API bridge unavailable; retaining standard entity damage", failure);
        }
    }

    public static boolean vehicle(Entity entity) {
        resolve();
        return vehicleType != null && vehicleType.isInstance(entity);
    }

    public static boolean destroyed(Entity entity) {
        if (!vehicle(entity) || health == null)
            return false;
        try {
            return ((Number) health.invoke(entity)).floatValue() <= 0;
        }
        catch (ReflectiveOperationException | RuntimeException failure) {
            warn(failure);
            return false;
        }
    }

    static DamageSource lastDamage(Entity entity) {
        if (!vehicle(entity) || lastSource == null)
            return null;
        try {
            return (DamageSource) lastSource.invoke(entity);
        }
        catch (ReflectiveOperationException | RuntimeException failure) {
            warn(failure);
            return null;
        }
    }

    /**
     * Query vehicle OBBs, falling back to ordinary bounds when the optional API is unavailable.
     * 查询载具的定向包围盒；可选 API 不可用时退回普通包围盒。
     */
    public static Optional<Vec3> intercept(Entity entity, Vec3 from, Vec3 to) {
        if (!vehicle(entity) || obbs == null || clip == null)
            return entity.getBoundingBox().clip(from, to);
        try {
            Vec3 nearest = null;
            double distance = Double.MAX_VALUE;
            List<?> boxes = (List<?>) obbs.invoke(entity);
            if (boxes.isEmpty())
                return entity.getBoundingBox().clip(from, to);
            for (Object box : boxes) {
                Object result = clip.invoke(box, new Vector3d(from.x, from.y, from.z), new Vector3d(to.x, to.y, to.z));
                if (result instanceof Optional<?> hit && hit.orElse(null) instanceof Vector3d v
                        && Double.isFinite(v.lengthSquared())) {
                    Vec3 at = new Vec3(v.x, v.y, v.z);
                    double d = from.distanceToSqr(at);
                    if (d < distance) {
                        nearest = at;
                        distance = d;
                    }
                }
            }
            return Optional.ofNullable(nearest);
        }
        catch (ReflectiveOperationException | RuntimeException failure) {
            obbs = clip = null;
            warn(failure);
            return entity.getBoundingBox().clip(from, to);
        }
    }

    public static boolean blockWeapon(Entity shooter) {
        return shooter instanceof ServerPlayer player && (!player.isAlive() || TacticalRuntime.busy(player)
                || TacticalRuntime.mgbDetonating(player.serverLevel()));
    }

    public static Entity shooter(Object parameters) {
        try {
            if (parameters == null)
                return null;
            if (parameters.getClass().isRecord())
                return (Entity) parameters.getClass().getMethod("shooter").invoke(parameters);
            Field field = SHOOTERS.get(parameters.getClass());
            if (field == null) {
                field = parameters.getClass().getField("shooter");
                SHOOTERS.put(parameters.getClass(), field);
            }
            return (Entity) field.get(parameters);
        }
        catch (ReflectiveOperationException | RuntimeException failure) {
            warn(failure);
            return null;
        }
    }
}
