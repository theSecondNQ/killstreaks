package net.killstreakcod.universal;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Original CAST curves shared by server flight and client mechanical poses.
 * 服务端飞行与客户端机械姿态共用原 CAST 曲线。
 */
public final class NativeCurveSet {
    private static final Map<String, NativeCurveSet> CACHE = new HashMap<>();
    public final float fps, last;
    private final Map<String, Map<String, Curve>> channels = new HashMap<>();
    private final Map<String, Integer> notifications = new HashMap<>();

    private NativeCurveSet(JsonObject json) {
        fps = json.get("fps").getAsFloat();
        last = json.get("last_frame").getAsFloat();
        for (var item : json.getAsJsonArray("curves")) {
            JsonObject c = item.getAsJsonObject();
            channels.computeIfAbsent(c.get("bone").getAsString(), k -> new HashMap<>())
                    .put(c.get("property").getAsString(), new Curve(c));
        }
        if (json.has("notifications"))
            for (var item : json.getAsJsonArray("notifications")) {
                var n = item.getAsJsonObject();
                var frames = n.getAsJsonArray("frames");
                if (!frames.isEmpty())
                    notifications.put(n.get("name").getAsString(), frames.get(0).getAsInt());
            }
    }

    public static synchronized NativeCurveSet get(String id) {
        return CACHE.computeIfAbsent(id, key -> {
            try (var input = NativeCurveSet.class
                    .getResourceAsStream("/assets/killstreakcod/mw/animations/" + key + ".json")) {
                if (input == null)
                    return new NativeCurveSet(
                            JsonParser.parseString("{\"fps\":30,\"last_frame\":0,\"curves\":[]}").getAsJsonObject());
                return new NativeCurveSet(
                        JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject());
            }
            catch (IOException | RuntimeException failure) {
                com.mojang.logging.LogUtils.getLogger().warn("Native animation unavailable: {}", key, failure);
                return new NativeCurveSet(
                        JsonParser.parseString("{\"fps\":30,\"last_frame\":0,\"curves\":[]}").getAsJsonObject());
            }
        });
    }

    public Matrix4f local(String bone, float frame, Vector3f position, Quaternionf rotation, Vector3f scale) {
        var group = channels.get(bone);
        Vector3f p = new Vector3f(position), s = new Vector3f(scale);
        Quaternionf q = new Quaternionf(rotation);
        if (group != null) {
            for (int i = 0; i < 3; i++) {
                Curve c = group.get("t" + "xyz".charAt(i));
                if (c != null)
                    p.setComponent(i, c.scalar(frame, p.get(i)));
                c = group.get("s" + "xyz".charAt(i));
                if (c != null)
                    s.setComponent(i, c.scalar(frame, s.get(i)));
            }
            Curve c = group.get("rq");
            if (c != null)
                q = c.rotation(frame, q);
        }
        return new Matrix4f().translation(p).rotate(q).scale(s);
    }

    public Vec3 root(float frame) {
        Vector3f p = local("tag_origin", frame, new Vector3f(), new Quaternionf(), new Vector3f(1))
                .getTranslation(new Vector3f());
        return new Vec3(p.x, p.y, p.z);
    }

    public int notifyTick(String name, int fallback) {
        Integer frame = notifications.get(name);
        return frame == null ? fallback : (int) Math.ceil(frame / fps * 20);
    }

    private static final class Curve {
        final int[] frames;
        final float[] values;
        final String mode;
        final float weight;

        Curve(JsonObject c) {
            frames = new int[c.getAsJsonArray("frames").size()];
            values = new float[c.getAsJsonArray("values").size()];
            for (int i = 0; i < frames.length; i++)
                frames[i] = c.getAsJsonArray("frames").get(i).getAsInt();
            for (int i = 0; i < values.length; i++)
                values[i] = c.getAsJsonArray("values").get(i).getAsFloat();
            mode = c.get("mode").getAsString();
            weight = c.has("weight") ? c.get("weight").getAsFloat() : 1;
        }

        int lower(float frame) {
            int lo = 0, hi = frames.length - 1;
            while (lo < hi) {
                int mid = (lo + hi + 1) / 2;
                if (frames[mid] <= frame)
                    lo = mid;
                else
                    hi = mid - 1;
            }
            return lo;
        }

        float fraction(float t, int lo, int hi) {
            return lo == hi || frames[hi] == frames[lo] ? 0
                    : Mth.clamp((t - frames[lo]) / (frames[hi] - frames[lo]), 0, 1);
        }

        float scalar(float t, float bind) {
            int lo = lower(t), hi = Math.min(lo + 1, frames.length - 1);
            float v = Mth.lerp(fraction(t, lo, hi), values[lo], values[hi]);
            return mode.equals("absolute") ? v : bind + v * weight;
        }

        Quaternionf rotation(float t, Quaternionf bind) {
            int lo = lower(t), hi = Math.min(lo + 1, frames.length - 1);
            Quaternionf a = at(lo), b = at(hi);
            if (a.dot(b) < 0)
                b.mul(-1);
            a.slerp(b, fraction(t, lo, hi)).normalize();
            return mode.equals("absolute") ? a
                    : new Quaternionf(bind).mul(new Quaternionf().slerp(a, Mth.clamp(weight, 0, 1))).normalize();
        }

        Quaternionf at(int i) {
            int n = i * 4;
            return new Quaternionf(values[n], values[n + 1], values[n + 2], values[n + 3]).normalize();
        }
    }
}
