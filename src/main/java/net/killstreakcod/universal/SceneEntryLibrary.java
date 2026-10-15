package net.killstreakcod.universal;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Verified source rigs, frame samples and socket hashes; shared by flight and client scenes.
 * 使用已核对的源骨架、帧样本和挂点哈希，供飞行与客户端场景共用。
 */
public final class SceneEntryLibrary {
    public static final int GUNSHIP_EXTERIOR = 70, GUNSHIP_INTERIOR = 68, GUNSHIP_ENTRY = 140;
    private static final JsonObject MODELS = read("scene-models");
    static {
        JsonObject extra = read("aircraft-crash-models");
        for (String section : new String[] { "models", "families" })
            extra.getAsJsonObject(section).entrySet()
                    .forEach(e -> MODELS.getAsJsonObject(section).add(e.getKey(), e.getValue()));
    }
    private static final Map<String, Track> TRACKS = new HashMap<>();

    private record Bone(String name, String animationName, int parent, Vector3f position, Quaternionf rotation,
            Vector3f scale) {
    }

    private static final Map<String, List<Bone>> RIGS = new HashMap<>();

    private SceneEntryLibrary() {}

    private static JsonObject read(String id) {
        try (var input = SceneEntryLibrary.class.getResourceAsStream("/assets/killstreakcod/mw/" + id + ".json")) {
            if (input == null)
                throw new IOException("Missing source scene " + id);
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        catch (IOException failure) {
            throw new IllegalStateException("Source scene resource unavailable: " + id, failure);
        }
    }

    public static synchronized Track track(String id) {
        return TRACKS.computeIfAbsent(id, k -> new Track(read("scene-tracks/" + k)));
    }

    public static String lod(String id, int tier) {
        JsonArray family = MODELS.getAsJsonObject("families").getAsJsonArray(id);
        if (family == null)
            return id;
        int requested = tier == 0 ? 0 : tier == 1 ? 2 : 4, best = -1;
        String chosen = id;
        for (var item : family) {
            var row = item.getAsJsonObject();
            int level = row.get("lod").getAsInt();
            if (level <= requested && level > best) {
                best = level;
                chosen = row.get("id").getAsString();
            }
        }
        return chosen;
    }

    public static Map<String, Matrix4f> pose(String id, String animation, float ticks) {
        JsonObject definition = MODELS.getAsJsonObject("models").getAsJsonObject(id);
        if (definition == null)
            return Map.of();
        List<Bone> rig = rig(id);
        Map<String, Matrix4f> result = new LinkedHashMap<>();
        NativeCurveSet action = animation.isEmpty() ? null : NativeCurveSet.get("scene_" + animation);
        float frame = action == null ? 0 : Mth.clamp(ticks * action.fps / 20, 0, action.last);
        for (Bone bone : rig) {
            Matrix4f local = action == null
                    ? new Matrix4f().translation(bone.position).rotate(bone.rotation).scale(bone.scale)
                    : action.local(bone.animationName, frame, bone.position, bone.rotation, bone.scale);
            if (bone.parent >= 0) {
                Matrix4f world = result.get(rig.get(bone.parent).name);
                if (world != null)
                    local = new Matrix4f(world).mul(local);
            }
            result.put(bone.name, local);
        }
        return result;
    }

    private static synchronized List<Bone> rig(String id) {
        return RIGS.computeIfAbsent(id, key -> {
            JsonObject definition = MODELS.getAsJsonObject("models").getAsJsonObject(key),
                    names = definition.getAsJsonObject("animationBoneNames");
            List<Bone> bones = new ArrayList<>();
            for (var item : definition.getAsJsonArray("bind")) {
                var b = item.getAsJsonObject();
                String name = b.get("n").getAsString();
                bones.add(new Bone(name, names != null && names.has(name) ? names.get(name).getAsString() : name,
                        b.get("p").getAsInt(), vector(b.getAsJsonArray("lp")), rotation(b.getAsJsonArray("lr")),
                        vector(b.getAsJsonArray("s"))));
            }
            return List.copyOf(bones);
        });
    }

    public static String socketName(String id, String tag) {
        JsonObject model = MODELS.getAsJsonObject("models").getAsJsonObject(id);
        if (model == null)
            return tag;
        int hash = 0x811c9dc5;
        for (byte c : tag.getBytes(StandardCharsets.UTF_8)) {
            hash ^= c & 255;
            hash *= 0x01000193;
        }
        String wanted = String.format(Locale.ROOT, "%08x", hash);
        for (var item : model.getAsJsonArray("bones")) {
            var bone = item.getAsJsonObject();
            if (bone.get("hash").getAsString().equals(wanted))
                return bone.get("name").getAsString();
        }
        return tag;
    }

    public static Matrix4f socket(String id, Map<String, Matrix4f> pose, String tag) {
        return new Matrix4f(pose.getOrDefault(socketName(id, tag), new Matrix4f()));
    }

    private static Vector3f vector(JsonArray v) {
        return new Vector3f(v.get(0).getAsFloat(), v.get(1).getAsFloat(), v.get(2).getAsFloat());
    }

    private static Quaternionf rotation(JsonArray v) {
        return new Quaternionf(v.get(0).getAsFloat(), v.get(1).getAsFloat(), v.get(2).getAsFloat(),
                v.get(3).getAsFloat()).normalize();
    }

    public static final class Track {
        public final float fps, last;
        private final float[] frames;
        private final Vector3f[] positions;
        private final Quaternionf[] rotations;

        Track(JsonObject source) {
            fps = source.get("framerate").getAsFloat();
            JsonArray samples = source.getAsJsonArray("samples");
            int size = samples.size();
            frames = new float[size];
            positions = new Vector3f[size];
            rotations = new Quaternionf[size];
            for (int i = 0; i < size; i++) {
                var row = samples.get(i).getAsJsonObject();
                frames[i] = row.get("frame").getAsFloat();
                positions[i] = vector(row.getAsJsonArray("position_game_units")).mul(2.54F);
                rotations[i] = rotation(row.getAsJsonArray("rotation_xyzw"));
            }
            last = frames[size - 1];
        }

        public Matrix4f at(float ticks) {
            return frame(Math.max(0, ticks) * fps / 20);
        }

        public Matrix4f frame(float frame) {
            int lo = 0, hi = frames.length - 1;
            while (lo + 1 < hi) {
                int mid = (lo + hi) >>> 1;
                if (frames[mid] <= frame)
                    lo = mid;
                else
                    hi = mid;
            }
            float fraction = Mth.clamp((frame - frames[lo]) / Math.max(.0001F, frames[hi] - frames[lo]), 0, 1);
            return new Matrix4f().translation(new Vector3f(positions[lo]).lerp(positions[hi], fraction))
                    .rotate(new Quaternionf(rotations[lo]).slerp(rotations[hi], fraction));
        }
    }
}
