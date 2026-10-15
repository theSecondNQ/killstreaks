package net.killstreakcod.killstreaks;

import net.killstreakcod.universal.NativeCurveSet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonParser;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Original source missile hardpoints, using the same centimetre scale as the VTOL hull.
 * 使用原游戏的导弹挂点，单位与 VTOL 机体一致，均为厘米。
 */
public final class VtolOrdnance {
    private static final Map<String, Vec3> MOUNTS = new HashMap<>();

    private record Bone(String name, int parent, Vector3f position, Quaternionf rotation, Vector3f scale) {
    }

    private static final List<Bone> BONES = new ArrayList<>();
    static {
        try (var stream = VtolOrdnance.class.getResourceAsStream("/assets/killstreakcod/mw/aircraft-bounds.json")) {
            var root = JsonParser
                    .parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject();
            for (var e : root.getAsJsonObject("vtolHardpoints").entrySet()) {
                var p = e.getValue().getAsJsonArray();
                MOUNTS.put(e.getKey(),
                        new Vec3(-p.get(1).getAsDouble(), p.get(2).getAsDouble(), -p.get(0).getAsDouble()).scale(.025));
            }
            for (var e : root.getAsJsonArray("vtolRig")) {
                var b = e.getAsJsonObject();
                var r = b.getAsJsonArray("lr");
                BONES.add(new Bone(b.get("n").getAsString(), b.get("p").getAsInt(), vector(b.getAsJsonArray("lp")),
                        new Quaternionf(r.get(0).getAsFloat(), r.get(1).getAsFloat(), r.get(2).getAsFloat(),
                                r.get(3).getAsFloat()),
                        vector(b.getAsJsonArray("s"))));
            }
        }
        catch (Exception e) {
            throw new IllegalStateException("Missing original VTOL hardpoint metadata", e);
        }
    }

    private static Vector3f vector(com.google.gson.JsonArray v) {
        return new Vector3f(v.get(0).getAsFloat(), v.get(1).getAsFloat(), v.get(2).getAsFloat());
    }

    public static Vec3 muzzle(TacticalUnitEntity unit, boolean left) {
        String tag = left ? "tag_left_archer_missile" : "tag_right_archer_missile";
        Vec3 local = MOUNTS.get(tag);
        var clip = NativeCurveSet.get("vtol_flyin");
        float frame = Math.min(clip.last, unit.nativeClock(0) * clip.fps / 20);
        var pose = new Matrix4f[BONES.size()];
        for (int i = 0; i < BONES.size(); i++) {
            var b = BONES.get(i);
            Matrix4f m = b.name.equals("tag_origin")
                    ? new Matrix4f().translation(b.position).rotate(b.rotation).scale(b.scale)
                    : clip.local(b.name, frame, b.position, b.rotation, b.scale);
            if (b.parent >= 0)
                m = new Matrix4f(pose[b.parent]).mul(m);
            pose[i] = m;
            if (b.name.equals(tag)) {
                Vector3f p = m.getTranslation(new Vector3f());
                local = new Vec3(-p.y, p.z, -p.x).scale(.025);
                break;
            }
        }
        return unit.position().add(local.xRot((float) Math.toRadians(unit.flightPitch()))
                .yRot((float) Math.toRadians(180 - unit.flightYaw())));
    }

    private VtolOrdnance() {}
}
