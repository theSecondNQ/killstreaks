package net.killstreakcod.render;

import net.killstreakcod.universal.NativeCurveSet;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix4f;

/**
 * Recovered local poses. Root translation is handled by the server once.
 * 使用导出的局部姿态，根节点位移仅由服务端统一处理一次。
 */
public final class NativeModelAnimations {
    private NativeModelAnimations() {}

    public static Map<String, Matrix4f> vtol(ImportedCastModel model, float ticks, boolean departing) {
        NativeCurveSet clip = NativeCurveSet.get(departing ? "vtol_exit" : "vtol_flyin");
        float frame = Math.min(clip.last, Math.max(0, ticks) * clip.fps / 20);
        Map<String, Matrix4f> pose = new HashMap<>();
        var bones = model.bindBones();
        for (var b : bones) {
            Matrix4f m = b.name().equals("tag_origin") ? b.localMatrix()
                    : clip.local(b.name(), frame, b.localPosition(), b.localRotation(), b.scale());
            if (b.parent() >= 0) {
                var parent = pose.get(bones.get(b.parent()).name());
                if (parent != null)
                    m = new Matrix4f(parent).mul(m);
            }
            pose.put(b.name(), m);
        }
        return pose;
    }

}
