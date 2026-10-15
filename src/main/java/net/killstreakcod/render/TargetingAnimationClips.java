package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Separate native inch-space rigs; source modes are applied before parent-first FK.
 * 分别处理原资源英寸空间中的骨架，在按父骨骼优先计算正向运动学前，先应用源动作模式。
 */
public final class TargetingAnimationClips {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final Map<String, Optional<Clip>> CLIPS = new HashMap<>();
    private static final Map<String, Optional<Rig>> RIGS = new HashMap<>();

    record Bone(String name, int parent, Transform bind) {
    }

    static final class Transform {
        final Vector3f position, scale;
        final Quaternionf rotation;

        Transform(Vector3f p, Quaternionf q, Vector3f s) {
            position = new Vector3f(p);
            rotation = new Quaternionf(q);
            scale = new Vector3f(s);
        }

        Transform(Transform t) {
            this(t.position, t.rotation, t.scale);
        }

        Matrix4f matrix() {
            return new Matrix4f().translation(position).rotate(rotation).scale(scale);
        }
    }

    static final class Frame {
        final List<Transform> locals;
        final Map<String, Matrix4f> cameraBones = new HashMap<>();

        Frame(Rig rig, List<Transform> local) {
            locals = local;
            Map<String, Matrix4f> global = new HashMap<>();
            for (int i = 0; i < rig.bones.size(); i++) {
                Bone b = rig.bones.get(i);
                Matrix4f world = local.get(i).matrix();
                if (b.parent >= 0)
                    world = new Matrix4f(global.get(rig.bones.get(b.parent).name)).mul(world);
                global.put(b.name, world);
            }
            Matrix4f camera = new Matrix4f(global.getOrDefault("tag_camera", new Matrix4f())).invert();
            global.forEach((name, m) -> cameraBones.put(name, new Matrix4f(camera).mul(m)));
        }
    }

    static final class Rig {
        final List<Bone> bones = new ArrayList<>();
        final Map<String, Integer> indices = new HashMap<>();

        Rig(JsonObject o) {
            for (JsonElement element : o.getAsJsonArray("bones")) {
                JsonObject b = element.getAsJsonObject();
                String name = b.get("n").getAsString();
                int parent = b.get("p").getAsInt();
                if (parent >= bones.size() || parent < -1)
                    throw new IllegalArgumentException("Invalid targeting bone parent");
                indices.put(name, bones.size());
                bones.add(new Bone(name, parent, new Transform(vector(b.getAsJsonArray("lp")),
                        quaternion(b.getAsJsonArray("lr")), vector(b.getAsJsonArray("s")))));
            }
        }
    }

    private static final class Clip {
        final float fps, last;
        final Map<String, List<Curve>> curves = new HashMap<>();

        Clip(JsonObject o) {
            fps = o.get("fps").getAsFloat();
            last = o.get("last_frame").getAsFloat();
            if (fps <= 0 || last < 0)
                throw new IllegalArgumentException("Invalid targeting clip time");
            for (JsonElement e : o.getAsJsonArray("curves")) {
                JsonObject c = e.getAsJsonObject();
                curves.computeIfAbsent(c.get("bone").getAsString(), key -> new ArrayList<>()).add(new Curve(c));
            }
        }

        void apply(String name, float frame, Transform value, Transform bind) {
            for (Curve curve : curves.getOrDefault(name, List.of()))
                curve.apply(Mth.clamp(frame, 0, last), value, bind);
        }
    }

    private static final class Curve {
        final String property, mode;
        final float weight;
        final int[] frames;
        final float[] values;

        Curve(JsonObject c) {
            property = c.get("property").getAsString();
            mode = c.get("mode").getAsString();
            weight = c.has("weight") ? c.get("weight").getAsFloat() : 1;
            JsonArray keys = c.getAsJsonArray("frames"), data = c.getAsJsonArray("values");
            frames = new int[keys.size()];
            values = new float[data.size()];
            for (int i = 0; i < frames.length; i++)
                frames[i] = keys.get(i).getAsInt();
            for (int i = 0; i < values.length; i++)
                values[i] = data.get(i).getAsFloat();
            if (frames.length == 0 || values.length != frames.length * (property.equals("rq") ? 4 : 1))
                throw new IllegalArgumentException("Invalid targeting curve");
        }

        int index(float time) {
            int lo = 0, hi = frames.length - 1;
            while (lo < hi) {
                int mid = (lo + hi + 1) / 2;
                if (frames[mid] <= time)
                    lo = mid;
                else
                    hi = mid - 1;
            }
            return lo;
        }

        Quaternionf rotation(int i) {
            int n = i * 4;
            Quaternionf q = new Quaternionf(values[n], values[n + 1], values[n + 2], values[n + 3]);
            return q.lengthSquared() > 1e-12F ? q.normalize() : q.identity();
        }

        void apply(float time, Transform target, Transform bind) {
            int lo = index(time), hi = Math.min(lo + 1, frames.length - 1);
            float f = lo == hi ? 0 : Mth.clamp((time - frames[lo]) / (frames[hi] - frames[lo]), 0, 1);
            if (property.equals("rq")) {
                Quaternionf q = rotation(lo), b = rotation(hi);
                if (q.dot(b) < 0)
                    b.mul(-1);
                q.slerp(b, f).normalize();
                if (mode.equals("absolute"))
                    target.rotation.set(q);
                else {
                    Quaternionf base = mode.equals("additive") ? new Quaternionf(target.rotation)
                            : new Quaternionf(bind.rotation);
                    target.rotation.set(base.mul(new Quaternionf().slerp(q, Mth.clamp(weight, 0, 1))).normalize());
                }
            } else if (property.length() == 2 && (property.charAt(0) == 't' || property.charAt(0) == 's')) {
                int axis = "xyz".indexOf(property.charAt(1));
                if (axis < 0)
                    return;
                Vector3f value = property.charAt(0) == 't' ? target.position : target.scale;
                Vector3f reference = property.charAt(0) == 't' ? bind.position : bind.scale;
                float sampled = Mth.lerp(f, values[lo], values[hi]);
                value.setComponent(axis, mode.equals("absolute") ? sampled
                        : (mode.equals("additive") ? value.get(axis) : reference.get(axis)) + sampled * weight);
            }
        }
    }

    private static JsonObject load(String id) throws Exception {
        ResourceLocation path = ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID,
                "mw/animations/" + id + ".json");
        try (var stream = Minecraft.getInstance().getResourceManager().open(path);
                var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static Clip clip(String id) {
        return CLIPS.computeIfAbsent(id, key -> {
            try {
                return Optional.of(new Clip(load(key)));
            }
            catch (Exception failure) {
                LOGGER.warn("Targeting clip {} unavailable; using the static item renderer", key, failure);
                return Optional.empty();
            }
        }).orElse(null);
    }

    static Rig rig(String family) {
        return RIGS.computeIfAbsent(family, key -> {
            try {
                return Optional.of(new Rig(load(key + "_rig")));
            }
            catch (Exception failure) {
                LOGGER.warn("Targeting rig {} unavailable; using the static item renderer", key, failure);
                return Optional.empty();
            }
        }).orElse(null);
    }

    static boolean available(String family) {
        if (rig(family) == null)
            return false;
        for (String action : new String[] { "raise", "idle", "idle_active", "fire", "drop" })
            if (clip(family + "_" + action) == null)
                return false;
        return !family.equals("spotter") || clip("spotter_ads_up") != null && clip("spotter_ads_down") != null
                && clip("spotter_ads_settle") != null;
    }

    static float ticks(String id) {
        Clip c = clip(id);
        return c == null ? 0 : c.last / c.fps * 20;
    }

    static Transform aim(String id, float ticks) {
        Rig rig = rig("spotter");
        Clip c = clip(id);
        if (rig == null || c == null)
            return null;
        Bone b = rig.bones.get(rig.indices.get("tag_ads"));
        Transform t = new Transform(b.bind);
        c.apply(b.name, ticks * c.fps / 20, t, b.bind);
        return t;
    }

    static Frame sample(String family, String action, float ticks, boolean breathe, Transform aim, Frame from,
            float blend) {
        Rig rig = rig(family);
        Clip main = clip(family + "_" + action), idle = breathe ? clip(family + "_idle_active") : null;
        if (rig == null || main == null)
            return null;
        List<Transform> locals = new ArrayList<>(rig.bones.size());
        for (int i = 0; i < rig.bones.size(); i++) {
            Bone b = rig.bones.get(i);
            Transform t = new Transform(b.bind);
            main.apply(b.name, ticks * main.fps / 20, t, b.bind);
            if (breathe && idle != null)
                idle.apply(b.name, (ticks * idle.fps / 20) % Math.max(1, idle.last), t, b.bind);
            if (aim != null && b.name.equals("tag_ads"))
                t = new Transform(aim);
            if (from != null && i < from.locals.size() && blend < 1) {
                Transform old = from.locals.get(i);
                t.position.set(new Vector3f(old.position).lerp(t.position, blend));
                t.scale.set(new Vector3f(old.scale).lerp(t.scale, blend));
                Quaternionf target = new Quaternionf(t.rotation);
                if (old.rotation.dot(target) < 0)
                    target.mul(-1);
                t.rotation.set(old.rotation).slerp(target, blend).normalize();
            }
            locals.add(t);
        }
        return new Frame(rig, locals);
    }

    static void clear() {
        CLIPS.clear();
        RIGS.clear();
    }

    private static Vector3f vector(JsonArray a) {
        return new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
    }

    private static Quaternionf quaternion(JsonArray a) {
        return new Quaternionf(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat(),
                a.get(3).getAsFloat()).normalize();
    }
}
