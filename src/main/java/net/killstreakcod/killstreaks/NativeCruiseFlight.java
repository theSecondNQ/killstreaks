package net.killstreakcod.killstreaks;

import net.killstreakcod.universal.NativeCurveSet;
import net.killstreakcod.universal.TacticalRuntime;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Static source root; only the actual tag_missile and tag_player curves move.
 * 源资源根节点保持静止，只使用实际的 tag_missile 和 tag_player 曲线驱动移动。
 */
public final class NativeCruiseFlight {
    public static final int DURATION = 60;
    public static final int LAUNCH_HEIGHT = 360;
    public static final float SHELL_TICK = 35 * 20 / 30F, THRUSTER_TICK = 49 * 20 / 30F, BLACK_TICK = 57,
            HANDOFF_TICK = 59;
    private static final JsonObject DATA = read();

    private record Bone(String name, int parent, Vector3f p, Quaternionf q, Vector3f s) {
    }

    private static final Map<String, List<Bone>> RIGS = new HashMap<>();

    private NativeCruiseFlight() {}

    /**
     * Shared requested origin; cruise alone may fly in the empty sky above the build ceiling.
     * 共用请求的起始位置；只有巡航导弹允许飞入建造高度上方的空域。
     */
    public static Vec3 launchOrigin(ServerPlayer player, Vec3 center) {
        return TacticalRuntime.ground(player.serverLevel(), center.add(player.getLookAngle().multiply(40, 0, 40)))
                .add(0, LAUNCH_HEIGHT, 0);
    }

    public static int ceiling(net.minecraft.world.level.Level level) {
        return level.getMaxBuildHeight() - 4 + LAUNCH_HEIGHT;
    }

    public static Vec3 clamp(net.minecraft.world.level.Level level, Vec3 p) {
        return new Vec3(p.x, Mth.clamp(p.y, level.getMinBuildHeight() + 3, ceiling(level)), p.z);
    }

    private static JsonObject read() {
        try (var stream = NativeCruiseFlight.class
                .getResourceAsStream("/assets/killstreakcod/mw/cruise-phone-models.json")) {
            if (stream == null)
                throw new IOException("Missing cruise model rig");
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static Vector3f vector(JsonArray a) {
        return new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
    }

    private static synchronized List<Bone> rig(String id) {
        return RIGS.computeIfAbsent(id, k -> {
            List<Bone> result = new ArrayList<>();
            for (var item : DATA.getAsJsonObject("models").getAsJsonArray(k)) {
                var b = item.getAsJsonObject();
                var q = b.getAsJsonArray("lr");
                result.add(new Bone(b.get("n").getAsString(), b.get("p").getAsInt(), vector(b.getAsJsonArray("lp")),
                        new Quaternionf(q.get(0).getAsFloat(), q.get(1).getAsFloat(), q.get(2).getAsFloat(),
                                q.get(3).getAsFloat()).normalize(),
                        vector(b.getAsJsonArray("s"))));
            }
            return List.copyOf(result);
        });
    }

    public static Map<String, Matrix4f> pose(String id, float ticks) {
        Map<String, Matrix4f> result = new LinkedHashMap<>();
        var bones = rig(id);
        NativeCurveSet clip = id.startsWith("cruise_native") && !id.startsWith("cruise_native_warhead")
                ? NativeCurveSet.get("scene_mp_cruise_missile_move_intro")
                : null;
        float frame = Mth.clamp(ticks * 1.5F, 0, 90);
        for (Bone b : bones) {
            Matrix4f matrix = clip == null ? new Matrix4f().translation(b.p).rotate(b.q).scale(b.s)
                    : clip.local(b.name, frame, b.p, b.q, b.s);
            if (b.parent >= 0)
                matrix = new Matrix4f(result.get(bones.get(b.parent).name)).mul(matrix);
            result.put(b.name, matrix);
        }
        return result;
    }

    public static Matrix4f camera(float ticks) {
        return new Matrix4f(pose("cruise_native", ticks).get("tag_player"));
    }

    /**
     * The handoff inherits the missile heading; retain source head motion as an endpoint-relative delta.
     * 视角交接沿用导弹朝向，并将源资源头部运动保留为相对动作终点的增量。
     */
    public static Quaternionf cameraRotation(Matrix4f current) {
        return current.getNormalizedRotation(new Quaternionf()).mul(CameraBasis.END_INVERSE).normalize();
    }

    private static final class CameraBasis {

        // 只固定采样一次动作终点，常规帧中已经采样了当前摄像机。
        private static final Quaternionf END_INVERSE = camera(DURATION).getNormalizedRotation(new Quaternionf())
                .invert();
    }

    public static Vector3f missile(float ticks) {
        return pose("cruise_native", ticks).get("tag_missile").getTranslation(new Vector3f());
    }

    public static Quaternionf attitude(float yaw, float pitch) {
        return new Quaternionf().rotateY((180 - yaw) * Mth.DEG_TO_RAD).rotateX(-pitch * Mth.DEG_TO_RAD);
    }

    public static Vec3 offset(Vector3f source, float yaw, float pitch) {
        Vector3f p = attitude(yaw, pitch).transform(new Vector3f(-source.y, source.z, -source.x).mul(.01F));
        return new Vec3(p.x, p.y, p.z);
    }

    public static Vec3 position(Vec3 launch, float yaw, float pitch, float ticks) {
        return launch.add(offset(missile(ticks).sub(missile(0)), yaw, pitch));
    }

    public static float pitch(double clearance) {

        // 保留原游戏的分离及摄像机动作，并在发射地面上方至少留出 82 格空间，供交接后自由操控。
        double travel = missile(DURATION).distance(missile(0)) * .01;
        return (float) Math.toDegrees(
                Math.asin(Mth.clamp((clearance - 64) / Math.max(1, travel), 0, Math.sin(Math.toRadians(78)))));
    }

    public static String lod(String family, int tier) {
        JsonArray rows = DATA.getAsJsonObject("families").getAsJsonArray(family);
        String chosen = family;
        int best = -1, wanted = tier == 0 ? 0 : tier == 1 ? 2 : 4;
        for (var item : rows) {
            var row = item.getAsJsonObject();
            int lod = row.get("lod").getAsInt();
            if (lod <= wanted && lod > best) {
                best = lod;
                chosen = row.get("id").getAsString();
            }
        }
        return chosen;
    }
}
