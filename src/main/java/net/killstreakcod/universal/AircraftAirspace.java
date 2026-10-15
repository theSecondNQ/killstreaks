package net.killstreakcod.universal;

import net.killstreakcod.killstreaks.ChopperIngress;
import net.killstreakcod.killstreaks.DeliveryFlight;
import net.killstreakcod.killstreaks.GunshipFlight;
import net.killstreakcod.killstreaks.NativeCruiseFlight;
import net.killstreakcod.killstreaks.NativeVtolFlight;
import net.killstreakcod.killstreaks.PrecisionFlight;
import net.killstreakcod.killstreaks.StreakReward;
import net.killstreakcod.killstreaks.TacticalUnitEntity;
import net.killstreakcod.killstreaks.TacticalUnitEntity.Kind;

import java.util.BitSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntFunction;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Actual aircraft envelope, with a loaded-space hemisphere gate and an applied altitude plan.
 * 按实际飞机包络检查已加载空间中的上半球区域，并应用计算出的高度方案。
 */
public final class AircraftAirspace {
    private static final int VERTICAL_CLEARANCE = 3, SEARCH_RADIUS = 64, GRID = 129;
    private static final long CENTER_HEIGHTS = ((1L << 62) - 1) & ~7L;
    private static final Map<ServerLevel, TickPlans> PLANS = new IdentityHashMap<>();

    private AircraftAirspace() {}

    private record PlanKey(UUID owner, Vec3 player, Kind kind, Vec3 center, float heading, Vec3 cruiseLook) {
    }

    private static final class TickPlans {
        final long tick;
        final Map<PlanKey, Integer> lifts = new LinkedHashMap<>();

        TickPlans(long tick) {
            this.tick = tick;
        }
    }

    public static void clear() {
        PLANS.clear();
    }

    public static boolean direct(ServerPlayer player, StreakReward reward) {
        if (!StreakAirspace.permitted(player, reward))
            return false;
        if (reward == null)
            return true;
        Kind kind = switch (reward) {
            case UAV -> Kind.UAV;
            case COUNTER_UAV -> Kind.COUNTER_UAV;
            case ADVANCED_UAV -> Kind.ADVANCED_UAV;
            case OVERWATCH_HELO -> Kind.OVERWATCH;
            case CHOPPER_GUNNER -> Kind.CHOPPER;
            case GUNSHIP -> Kind.GUNSHIP;
            case CRUISE_MISSILE -> Kind.CRUISE;
            default -> null;
        };

        // 装备及地图类奖励以最终确认的目标位置为准检查空域。
        return kind == null || approve(player, kind, player.position(), player.getYRot());
    }

    public static boolean target(ServerPlayer player, StreakReward reward, Vec3 point, float heading) {
        if (!StreakAirspace.permitted(player, reward))
            return false;
        Kind kind = switch (reward) {
            case PRECISION_AIRSTRIKE -> Kind.PRECISION;
            case SAE -> Kind.SAE;
            case STEALTH_BOMBER -> Kind.STEALTH;
            case VTOL_JET -> Kind.VTOL;
            case MORTAR_STRIKE -> Kind.BOMB;
            default -> Kind.DELIVERY;
        };
        return approve(player, kind, TacticalRuntime.ground(player.serverLevel(), point), heading);
    }

    private static boolean approve(ServerPlayer player, Kind kind, Vec3 center, float heading) {
        if (selectLift(player, kind, center, heading) >= 0)
            return true;
        player.displayClientMessage(Component.translatable("message.killstreakcod.no_airspace"), true);
        return false;
    }

    /**
     * -1 is unavailable; a successful plan is reused by validation and spawn in the same tick.
     * 返回 -1 表示不可用，成功的方案在同一刻的验证及生成流程中复用。
     */
    public static int selectLift(ServerPlayer player, Kind kind, Vec3 center, float heading) {
        if (!StreakAirspace.permitted(player.serverLevel(), kind))
            return -1;
        ServerLevel level = player.serverLevel();
        long tick = level.getGameTime();
        TickPlans plans = PLANS.get(level);
        if (plans == null || plans.tick != tick) {
            plans = new TickPlans(tick);
            PLANS.put(level, plans);
        }
        PlanKey key = new PlanKey(player.getUUID(), player.position(), kind, center, Mth.wrapDegrees(heading),
                kind == Kind.CRUISE ? player.getLookAngle() : Vec3.ZERO);
        Integer cached = plans.lifts.get(key);
        if (cached != null)
            return cached;
        int lift = plan(player, kind, center, key.heading);
        if (plans.lifts.size() >= 128)
            plans.lifts.remove(plans.lifts.keySet().iterator().next());
        plans.lifts.put(key, lift);
        return lift;
    }

    private static int plan(ServerPlayer player, Kind kind, Vec3 center, float heading) {
        ServerLevel level = player.serverLevel();
        Checker check = new Checker(level, false);
        Vec3 cruiseLaunch = kind == Kind.CRUISE
                ? NativeCruiseFlight.clamp(level, NativeCruiseFlight.launchOrigin(player, center))
                : Vec3.ZERO;
        int blocked = mission(check, kind, center, heading, cruiseLaunch, 0);
        if (blocked == 0)
            return 0; // 默认航线畅通时，不扫描半球区域。
        if (!AircraftFlight.aircraft(kind) && kind != Kind.CRUISE)
            return -1;
        int ceiling = kind == Kind.CRUISE ? NativeCruiseFlight.ceiling(level)
                : level.getMaxBuildHeight() - 1 - VERTICAL_CLEARANCE;
        int maximum = level.getMaxBuildHeight() - level.getMinBuildHeight() - 1 - 2 * VERTICAL_CLEARANCE;
        if (kind == Kind.CRUISE)
            maximum = Math.min(maximum, (int) Math.floor(ceiling - cruiseLaunch.y));
        if (kind == Kind.PRECISION)
            maximum = Math.min(maximum, (int) Math
                    .floor(ceiling - PrecisionFlight.position(center, heading, PrecisionFlight.ATTACK_START).y));
        if (maximum < 1 || !check.hemisphere(player.position(), halfSpan(kind)))
            return -1;

        // 精确空袭会调整后退距离，以保持机炮切线对准原地面扫射线；巡航导弹在高度上限附近可能改变俯仰。
        // 两者的 X/Z航线需要分别检查，X/Z 固定的航线则可跳过已确认的障碍高度。
        boolean fixedHorizontal = kind != Kind.PRECISION && kind != Kind.CRUISE;
        int lift = fixedHorizontal ? blocked : 1;
        while (lift <= maximum) {
            blocked = mission(check, kind, center, heading, cruiseLaunch, lift);
            if (blocked == 0)
                return lift;
            if (fixedHorizontal && blocked == Integer.MAX_VALUE)
                return -1;
            lift += fixedHorizontal ? blocked : 1;
        }
        return -1;
    }

    private static int mission(Checker check, Kind kind, Vec3 center, float heading, Vec3 cruiseLaunch, int lift) {
        IntFunction<Vec3> path = route(check.level, kind, center, heading, cruiseLaunch, lift);
        int ticks = routeTicks(kind);
        int blocked = check.route(path, ticks, halfSpan(kind), kind == Kind.CRUISE);
        if (blocked != 0)
            return blocked;
        if (!AircraftFlight.persistent(kind))
            return 0;
        Vec3 last = clamp(check.level, path.apply(ticks - 1)), previous = clamp(check.level, path.apply(ticks - 2)),
                velocity = last.subtract(previous);
        float yaw = velocity.horizontalDistanceSqr() < 1e-8 ? heading
                : (float) Math.toDegrees(Math.atan2(-velocity.x, velocity.z));
        float pitch = velocity.lengthSqr() < 1e-8 ? 0
                : (float) -Math.toDegrees(Math.atan2(velocity.y, velocity.horizontalDistance()));

        // 退场从已抬升的终点开始，不重复增加抬升量。
        int departureBlocked = check.route(t -> AircraftFlight.departure(last, yaw, pitch, kind, t),
                AircraftFlight.DEPARTURE_TICKS, halfSpan(kind));

        // 高度上限裁剪可能改变退场俯仰，进而影响水平方向的衔接。
        return departureBlocked == 0 ? 0 : 1;
    }

    private static int routeTicks(Kind kind) {
        return switch (kind) {
            case UAV, ADVANCED_UAV -> 690;
            case COUNTER_UAV -> 600;
            case GUNSHIP -> 1200;
            case CHOPPER -> StreakTimings.entry("chopper_gunner");
            case OVERWATCH -> 1290;
            case PRECISION -> PrecisionFlight.LIFETIME;
            case SAE, STEALTH -> AircraftFlight.PASS_LIFETIME;
            case VTOL -> 1200 + NativeVtolFlight.INGRESS_TICKS;
            case DELIVERY -> DeliveryFlight.LIFETIME;
            case CRUISE -> 60;
            default -> 0;
        };
    }

    private static IntFunction<Vec3> route(ServerLevel level, Kind kind, Vec3 c, float h, Vec3 cruiseLaunch, int lift) {
        Vec3 forward = Vec3.directionFromRotation(0, h);
        IntFunction<Vec3> base = switch (kind) {
            case UAV, ADVANCED_UAV -> t -> {
                double height = kind == Kind.UAV ? TacticalUnitEntity.UAV_HEIGHT
                        : TacticalUnitEntity.ADVANCED_UAV_HEIGHT;
                return t < 90 ? AircraftFlight.approach(c, h, t, 60, height, .022)
                        : AircraftFlight.orbit(c, h, t - 90, 60, height, .022);
            };
            case COUNTER_UAV ->
                t -> c.add(Math.sin(t * .025) * 3, TacticalUnitEntity.COUNTER_UAV_HEIGHT, Math.cos(t * .025) * 3);
            case GUNSHIP -> t -> GunshipFlight.position(c, h, t);
            case CHOPPER -> t -> ChopperIngress.position(c, h, t / (double) StreakTimings.entry("chopper_gunner"),
                    level.getMinBuildHeight(), level.getMaxBuildHeight());
            case OVERWATCH -> t -> t < 90 ? AircraftFlight.approach(c, h, t, 18, 30, .028)
                    : AircraftFlight.orbit(c, h, t - 90, 18, 30, .028);
            case PRECISION -> t -> PrecisionFlight.position(c, h, t, lift);
            case SAE, STEALTH -> t -> AircraftFlight.passPosition(kind, c, h, t);
            case VTOL -> t -> NativeVtolFlight.position(c, h, t);
            case DELIVERY -> t -> DeliveryFlight.position(c, h, t);
            case CRUISE -> {
                Vec3 launch = NativeCruiseFlight.clamp(level, cruiseLaunch.add(0, lift, 0));
                double room = launch.y - TacticalRuntime.ground(level, launch).y - 18;
                float pitch = NativeCruiseFlight.pitch(room);
                yield t -> NativeCruiseFlight.position(launch, h, pitch, t);
            }
            default -> t -> c.add(0, 55, 0);
        };
        return kind == Kind.PRECISION || kind == Kind.CRUISE ? base : t -> base.apply(t).add(0, lift, 0);
    }


    // 取整后的源机体半径已考虑倾斜网格和 VTOL 的世界缩放比例。
    private static int halfSpan(Kind kind) {
        return switch (kind) {
            case GUNSHIP -> 29;
            case CHOPPER -> 13;
            case STEALTH -> 12;
            case SAE, DELIVERY -> 11;
            case VTOL -> 9;
            case OVERWATCH -> 8;
            case PRECISION, ADVANCED_UAV -> 5;
            case UAV, CRUISE -> 3;
            case COUNTER_UAV -> 0;
            default -> 1;
        };
    }

    public static Vec3 clamp(ServerLevel level, Vec3 p) {
        return new Vec3(p.x, Mth.clamp(p.y, level.getMinBuildHeight() + VERTICAL_CLEARANCE,
                level.getMaxBuildHeight() - 1 - VERTICAL_CLEARANCE), p.z);
    }

    private record ChunkInfo(LevelChunk chunk, int topExclusive) {
    }

    private static final class ColumnBits {
        final BitSet known = new BitSet(), solid = new BitSet();
    }

    private static final class Checker {
        private final ServerLevel level;
        private final boolean load;
        private final Map<Long, ChunkInfo> chunks = new HashMap<>();
        private final Map<Long, ColumnBits> columns = new HashMap<>();
        private final BlockPos.MutableBlockPos block = new BlockPos.MutableBlockPos();

        Checker(ServerLevel level, boolean load) {
            this.level = level;
            this.load = load;
        }

        private ChunkInfo chunk(int x, int z) {
            long key = net.minecraft.world.level.ChunkPos.asLong(x, z);
            ChunkInfo cached = chunks.get(key);
            if (cached != null)
                return cached;
            LevelChunk value = level.getChunkSource().getChunkNow(x, z);

            // 预飞检查不为远处航线或半球区域生成新区块。
            if (value == null && load)
                value = level.getChunk(x, z);
            int top = level.getMinBuildHeight();
            if (value != null) {
                var sections = value.getSections();
                for (int i = sections.length - 1; i >= 0; i--)
                    if (!sections[i].hasOnlyAir()) {
                        top = level.getMinBuildHeight() + (i + 1) * 16;
                        break;
                    }
            }
            ChunkInfo result = new ChunkInfo(value, top);
            chunks.put(key, result);
            return result;
        }

        private int highestSolid(ChunkInfo info, int x, int z, int low, int high) {
            if (info.topExclusive <= low)
                return Integer.MIN_VALUE;
            int top = info.chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
            if (top < low)
                return Integer.MIN_VALUE;
            high = Math.min(high, top);
            long key = ((long) x << 32) ^ (z & 0xffffffffL);
            ColumnBits bits = columns.computeIfAbsent(key, k -> new ColumnBits());
            for (int y = high; y >= low; y--) {
                int index = y - level.getMinBuildHeight(), section = index >> 4;
                if (info.chunk.getSections()[section].hasOnlyAir()) {
                    y = level.getMinBuildHeight() + (section << 4);
                    continue;
                }
                if (!bits.known.get(index)) {
                    bits.known.set(index);
                    if (!info.chunk.getBlockState(block.set(x, y, z)).isAir())
                        bits.solid.set(index);
                }
                if (bits.solid.get(index))
                    return y;
            }
            return Integer.MIN_VALUE;
        }

        int route(IntFunction<Vec3> route, int ticks, int radius) {
            return route(route, ticks, radius, false);
        }

        private Vec3 height(Vec3 p, boolean cruise) {
            return cruise ? NativeCruiseFlight.clamp(level, p) : clamp(level, p);
        }

        int route(IntFunction<Vec3> route, int ticks, int radius, boolean cruise) {
            Vec3 rawPrevious = route.apply(0), previous = height(rawPrevious, cruise);
            int blocked = box(previous, previous, radius);
            if (blocked != 0)
                return blocked;
            for (int tick = 4; tick <= ticks + 3; tick += 4) {
                Vec3 rawNext = route.apply(Math.min(tick, ticks));
                int steps = Math.max(1, (int) Math.ceil(rawPrevious.distanceTo(rawNext) / 2));

                // 按标称线段细分，因此即使世界高度上限裁剪了 Y，抬高后的固定 X/Z 航线仍检查相同方块列。
                for (int i = 1; i <= steps; i++) {
                    Vec3 next = height(rawPrevious.lerp(rawNext, i / (double) steps), cruise);
                    blocked = box(previous, next, radius);
                    if (blocked != 0)
                        return blocked;
                    previous = next;
                }
                rawPrevious = rawNext;
            }
            return 0;
        }

        int segment(Vec3 from, Vec3 to, int radius) {
            int steps = Math.max(1, (int) Math.ceil(from.distanceTo(to) / 2));
            Vec3 previous = from;
            for (int i = 1; i <= steps; i++) {
                Vec3 next = from.lerp(to, i / (double) steps);
                int blocked = box(previous, next, radius);
                if (blocked != 0)
                    return blocked;
                previous = next;
            }
            return 0;
        }

        private int box(Vec3 from, Vec3 to, int radius) {
            int low = Math.max(level.getMinBuildHeight(), Mth.floor(Math.min(from.y, to.y) - VERTICAL_CLEARANCE)),
                    high = Math.min(level.getMaxBuildHeight() - 1,
                            Mth.floor(Math.max(from.y, to.y) + VERTICAL_CLEARANCE));
            if (low > high)
                return 0; // 整个包络都高于建造高度时，其中没有方块。
            int left = Mth.floor(Math.min(from.x, to.x) - radius), right = Mth.floor(Math.max(from.x, to.x) + radius),
                    front = Mth.floor(Math.min(from.z, to.z) - radius),
                    back = Mth.floor(Math.max(from.z, to.z) + radius);
            for (int cx = left >> 4; cx <= right >> 4; cx++)
                for (int cz = front >> 4; cz <= back >> 4; cz++) {
                    ChunkInfo info = chunk(cx, cz);
                    if (info.chunk == null || info.topExclusive <= low)
                        continue;
                    int x0 = Math.max(left, cx * 16), x1 = Math.min(right, cx * 16 + 15), z0 = Math.max(front, cz * 16),
                            z1 = Math.min(back, cz * 16 + 15);
                    for (int x = x0; x <= x1; x++)
                        for (int z = z0; z <= z1; z++) {
                            int obstruction = highestSolid(info, x, z, low, high);
                            if (obstruction == Integer.MIN_VALUE)
                                continue;
                            if (obstruction >= level.getMaxBuildHeight() - 1 - 2 * VERTICAL_CLEARANCE)
                                return Integer.MAX_VALUE;
                            return obstruction - low + 1;
                        }
                }
            return 0;
        }

        /**
         * A seven-block-tall full hull footprint must fit entirely in the upper hemisphere.
         * 七格高的完整机体占地范围必须全部容纳在上半球内。
         */
        boolean hemisphere(Vec3 player, int radius) {
            int base = Mth.floor(player.y), maxDy = Math.min(SEARCH_RADIUS - VERTICAL_CLEARANCE,
                    level.getMaxBuildHeight() - 1 - base - VERTICAL_CLEARANCE);
            int minDy = Math.max(VERTICAL_CLEARANCE, level.getMinBuildHeight() - base + VERTICAL_CLEARANCE);
            if (maxDy < minDy)
                return false;
            int centerTop = (int) Math.floor(Math.sqrt(SEARCH_RADIUS * SEARCH_RADIUS - 2D * radius * radius))
                    - VERTICAL_CLEARANCE;
            if (skyRoom(player, radius, minDy, Math.min(maxDy, centerTop)))
                return true;
            long allowed = CENTER_HEIGHTS & ((1L << (maxDy + 1)) - 1) & (-1L << minDy);
            long[] masks = new long[GRID * GRID];
            for (int iz = 0; iz < GRID; iz++)
                for (int ix = 0; ix < GRID; ix++) {
                    int dx = ix - SEARCH_RADIUS, dz = iz - SEARCH_RADIUS;
                    if (dx * dx + dz * dz > SEARCH_RADIUS * SEARCH_RADIUS)
                        continue;
                    int x = Mth.floor(player.x) + dx, z = Mth.floor(player.z) + dz;
                    ChunkInfo info = chunk(x >> 4, z >> 4);
                    if (info.chunk == null || !level.getWorldBorder().isWithinBounds(block.set(x, base, z)))
                        continue;
                    if (info.topExclusive <= base + minDy - VERTICAL_CLEARANCE) {
                        masks[iz * GRID + ix] = allowed;
                        continue;
                    }
                    long heights = 0;
                    int dy = minDy;
                    while (dy <= maxDy) {
                        int obstacle = highestSolid(info, x, z, base + dy - VERTICAL_CLEARANCE,
                                base + dy + VERTICAL_CLEARANCE);
                        if (obstacle == Integer.MIN_VALUE) {
                            heights |= 1L << dy;
                            dy++;
                        } else
                            dy = obstacle - base + VERTICAL_CLEARANCE + 1;
                    }
                    masks[iz * GRID + ix] = heights & allowed;
                }
            int span = 2 * radius + 1;
            masks = rangeAnd(masks, span, true);
            masks = rangeAnd(masks, span, false);
            for (int z = radius; z < GRID - radius; z++)
                for (int x = radius; x < GRID - radius; x++) {
                    int dx = Math.abs(x - SEARCH_RADIUS) + radius, dz = Math.abs(z - SEARCH_RADIUS) + radius;
                    int room = SEARCH_RADIUS * SEARCH_RADIUS - dx * dx - dz * dz;
                    if (room < 0)
                        continue;
                    int top = (int) Math.floor(Math.sqrt(room)) - VERTICAL_CLEARANCE;
                    if (top < minDy)
                        continue;
                    long sphereHeights = (1L << (Math.min(maxDy, top) + 1)) - 1;
                    if ((masks[(z - radius) * GRID + x - radius] & sphereHeights) != 0)
                        return true;
                }
            return false;
        }

        /**
         * Common outdoor/roof case: a heightmap proof avoids building any voxel mask.
         * 常见室外或屋顶场景可通过高度图直接证明空间畅通，避免构建体素遮罩。
         */
        private boolean skyRoom(Vec3 player, int radius, int minDy, int maxDy) {
            if (maxDy < minDy)
                return false;
            int base = Mth.floor(player.y), left = Mth.floor(player.x) - radius, front = Mth.floor(player.z) - radius;
            int bottom = base + maxDy - VERTICAL_CLEARANCE;
            for (int x = left; x <= left + 2 * radius; x++)
                for (int z = front; z <= front + 2 * radius; z++) {
                    ChunkInfo info = chunk(x >> 4, z >> 4);
                    if (info.chunk == null || !level.getWorldBorder().isWithinBounds(block.set(x, base, z)))
                        return false;
                    if (info.topExclusive <= bottom)
                        continue;
                    if (info.chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15) >= bottom)
                        return false;
                }
            return true;
        }

        /**
         * Separable sliding AND in O(grid^2 log hullWidth), independent of the hull area.
         * 使用可分离滑动逻辑与，复杂度为 O(grid^2 log hullWidth)，不随机体占地面积线性增长。
         */
        private static long[] rangeAnd(long[] input, int span, boolean horizontal) {
            int stride = horizontal ? 1 : GRID, power = Integer.highestOneBit(span);
            long[] current = input;
            for (int length = 1; length < power; length *= 2) {
                long[] next = new long[GRID * GRID];
                for (int z = 0; z < GRID; z++)
                    for (int x = 0; x < GRID; x++)
                        if ((horizontal ? x : z) + 2 * length <= GRID) {
                            int i = z * GRID + x;
                            next[i] = current[i] & current[i + length * stride];
                        }
                current = next;
            }
            if (power == span)
                return current;
            long[] result = new long[GRID * GRID];
            int remainder = span - power;
            for (int z = 0; z < GRID; z++)
                for (int x = 0; x < GRID; x++)
                    if ((horizontal ? x : z) + span <= GRID) {
                        int i = z * GRID + x;
                        result[i] = current[i] & current[i + remainder * stride];
                    }
            return result;
        }
    }

    public static boolean movement(TacticalUnitEntity unit, Vec3 from, Vec3 to) {
        if (!(unit.level() instanceof ServerLevel level) || !AircraftFlight.visible(unit.kind()))
            return true;
        if (from.distanceToSqr(to) > 64 * 64)
            return false;

        // 重新检查已加载区域的机体占地，运行时不得把飞机抬升穿过新出现的屋顶。
        return new Checker(level, true).segment(clamp(level, from), clamp(level, to), halfSpan(unit.kind())) == 0;
    }
}
