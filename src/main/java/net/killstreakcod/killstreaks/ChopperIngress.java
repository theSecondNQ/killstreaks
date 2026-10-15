package net.killstreakcod.killstreaks;

import net.killstreakcod.universal.AircraftAirspace;
import net.killstreakcod.universal.SceneEntryLibrary;
import net.killstreakcod.universal.StreakTimings;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side flight path from the original helicopter entry root translation.
 * 根据原游戏直升机入场动作的根节点位移生成服务端飞行路径。
 */
public final class ChopperIngress {
    private static final String PATH = "/assets/killstreakcod/mw/animations/chopper_heli_intro.json";
    private static final double APPROACH_DISTANCE = 300, END_HEIGHT = 40, EXTRA_APPROACH_HEIGHT = 26;
    private static final RootTrack TRACK = load();

    private ChopperIngress() {}

    public static Vec3 spawnPoint(ServerPlayer player) {
        return position(player.position(), player.getYRot(), 0, player.level().getMinBuildHeight(),
                player.level().getMaxBuildHeight());
    }

    /**
     * Called by initializeFlight before the entity is added and its spawn packet is sent.
     * 由 initializeFlight 在实体加入世界并发送生成消息之前调用。
     */
    public static void initialize(TacticalUnitEntity unit) {
        Vec3 first = position(unit, 0), next = position(unit, 1D / Math.max(1, StreakTimings.entry("chopper_gunner")));
        unit.setPos(first);
        orient(unit, next.subtract(first));
    }

    /**
     * elapsed is the zero-based tick within the entry; the final entry tick reaches the hover point.
     * elapsed 是入场阶段从零开始计算的游戏刻数，入场的最后一刻到达悬停位置。
     */
    public static float tick(TacticalUnitEntity unit, int elapsed, int duration) {
        double progress = Mth.clamp((elapsed + 1D) / Math.max(1, duration), 0, 1);
        Vec3 previous = unit.position(), next = position(unit, progress);
        unit.flight(next);
        return (float) previous.distanceTo(next) * 20;
    }

    private static void orient(TacticalUnitEntity unit, Vec3 velocity) {
        if (velocity.lengthSqr() < 1e-10)
            return;
        float yaw = (float) Math.toDegrees(Math.atan2(-velocity.x, velocity.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(velocity.y, velocity.horizontalDistance()));
        unit.flightOrientation(yaw, pitch);
    }

    private static Vec3 position(TacticalUnitEntity unit, double progress) {
        Vec3 nominal = position(unit.center, unit.heading, progress, unit.level().getMinBuildHeight(),
                unit.level().getMaxBuildHeight());
        return AircraftAirspace.clamp((net.minecraft.server.level.ServerLevel) unit.level(), unit.airPosition(nominal));
    }

    public static Vec3 position(Vec3 destination, float heading, double progress, int minimum, int maximum) {
        Vec3 offset;
        var sourceTrack = SceneEntryLibrary.track("chopper_flight_root");
        Vector3fSource recovered = sourceOffset(sourceTrack, progress);
        if (recovered != null) {
            offset = new Vec3(-recovered.y * recovered.horizontalScale, recovered.z * recovered.verticalScale,
                    -recovered.x * recovered.horizontalScale).yRot((float) Math.toRadians(180 - heading));
        } else if (TRACK != null) {
            Vec3 source = TRACK.sample(progress).subtract(TRACK.end);

            // CAST 的正 X 轴指向机头，Z 轴向上；先转换为 C=(-Y,Z,-X)，再应用原世界模型使用的机身朝向旋转。
            Vec3 converted = new Vec3(-source.y * TRACK.horizontalScale, source.z * TRACK.verticalScale,
                    -source.x * TRACK.horizontalScale);
            offset = converted.yRot((float) Math.toRadians(180 - heading));
        } else
            offset = Vec3.directionFromRotation(0, heading).scale(-APPROACH_DISTANCE * (1 - progress)).add(0,
                    EXTRA_APPROACH_HEIGHT * (1 - progress), 0);
        Vec3 result = destination.add(0, END_HEIGHT, 0).add(offset);
        return new Vec3(result.x, Mth.clamp(result.y, minimum + 3, maximum - 5), result.z);
    }

    private record Vector3fSource(double x, double y, double z, double horizontalScale, double verticalScale) {
    }

    private static Vector3fSource sourceOffset(SceneEntryLibrary.Track track, double progress) {
        var end = track.frame(track.last).getTranslation(new org.joml.Vector3f());
        var start = track.frame(0).getTranslation(new org.joml.Vector3f()).sub(end);
        var at = track.frame((float) progress * track.last).getTranslation(new org.joml.Vector3f()).sub(end);
        return new Vector3fSource(at.x, at.y, at.z, APPROACH_DISTANCE / Math.max(1, Math.hypot(start.x, start.y)),
                EXTRA_APPROACH_HEIGHT / Math.max(1, Math.abs(start.z)));
    }

    private record Curve(int[] frames, double[] values) {
        double sample(double frame) {
            if (frame <= frames[0])
                return values[0];
            for (int i = 1; i < frames.length; i++)
                if (frame <= frames[i]) {
                    double p = (frame - frames[i - 1]) / Math.max(1, frames[i] - frames[i - 1]);
                    return values[i - 1] + (values[i] - values[i - 1]) * p;
                }
            return values[values.length - 1];
        }
    }

    private record RootTrack(Curve x, Curve y, Curve z, int last, Vec3 end, double horizontalScale,
            double verticalScale) {
        Vec3 sample(double progress) {
            double frame = Mth.clamp(progress, 0, 1) * last;
            return new Vec3(x.sample(frame), y.sample(frame), z.sample(frame));
        }
    }

    private static RootTrack load() {
        try (var stream = ChopperIngress.class.getResourceAsStream(PATH)) {
            if (stream == null)
                throw new IllegalStateException("Missing original helicopter root track");
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            int last = root.get("last_frame").getAsInt();
            Map<String, Curve> curves = new HashMap<>();
            for (var element : root.getAsJsonArray("curves")) {
                JsonObject curve = element.getAsJsonObject();
                String property = curve.get("property").getAsString();
                if (!curve.get("bone").getAsString().equals("tag_origin") || !property.matches("t[xyz]"))
                    continue;
                JsonArray sourceFrames = curve.getAsJsonArray("frames"), sourceValues = curve.getAsJsonArray("values");
                if (sourceFrames.size() == 0 || sourceFrames.size() != sourceValues.size())
                    throw new IllegalStateException("Invalid helicopter root curve");
                int[] frames = new int[sourceFrames.size()];
                double[] values = new double[sourceFrames.size()];
                for (int i = 0; i < frames.length; i++) {
                    frames[i] = sourceFrames.get(i).getAsInt();
                    values[i] = sourceValues.get(i).getAsDouble();
                }
                curves.put(property, new Curve(frames, values));
            }
            Curve x = curves.get("tx"), y = curves.get("ty"), z = curves.get("tz");
            if (x == null || y == null || z == null)
                throw new IllegalStateException("Incomplete helicopter root translation");
            Vec3 end = new Vec3(x.sample(last), y.sample(last), z.sample(last)),
                    start = new Vec3(x.sample(0), y.sample(0), z.sample(0)).subtract(end);

            // 源资源的 Z 轴表示高度，因此使用源资源的 XY 平面计算接近距离。
            double horizontalScale = APPROACH_DISTANCE / Math.max(1, Math.hypot(start.x, start.y));
            double verticalScale = EXTRA_APPROACH_HEIGHT / Math.max(1, Math.abs(start.z));
            return new RootTrack(x, y, z, last, end, horizontalScale, verticalScale);
        }
        catch (Exception failure) {
            com.mojang.logging.LogUtils.getLogger()
                    .error("Could not read original helicopter entry path; using straight approach", failure);
            return null;
        }
    }
}
