package net.killstreakcod.universal;

import java.util.List;
import net.minecraft.world.phys.Vec3;

/** 三个弹着点位于半径 64 格的同一圆上，间隔 120 度；落弹和粒子云共用坐标。 */
public final class NuclearPattern {
    private NuclearPattern() {}

    public static List<Vec3> centers(Vec3 origin) {
        return java.util.stream.IntStream.range(0, 3).mapToObj(i -> {
            double angle = -Math.PI / 2 + i * Math.PI * 2 / 3;
            return origin.add(Math.cos(angle) * 64, 0, Math.sin(angle) * 64);
        }).toList();
    }
}
