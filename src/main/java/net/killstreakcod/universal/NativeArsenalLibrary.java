package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;

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
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Read-only native sockets and LODs; the recovered scripts remain data.
 * 只读取原挂点和细节级别数据，导出的脚本仅作为数据，不执行。
 */
public final class NativeArsenalLibrary {
    private static final JsonObject DATA = read();
    private static final JsonArray MINIGUN_RIG = minigunRig();
    private static final Map<Integer, Map<String, Vector3f>> SOCKETS = new LinkedHashMap<>();
    public static final int CHARGE_TICKS = DATA.getAsJsonObject("m134").get("chargeTicks").getAsInt();
    public static final int THROW_RELEASE = DATA.getAsJsonObject("cluster").get("throwReleaseTicks").getAsInt();
    public static final int THROW_DURATION = DATA.getAsJsonObject("cluster").get("throwDurationTicks").getAsInt();

    private NativeArsenalLibrary() {}

    private static JsonObject read() {
        try (var in = NativeArsenalLibrary.class.getResourceAsStream("/assets/killstreakcod/mw/csj21.json")) {
            if (in == null)
                throw new IllegalStateException("Missing native arsenal config");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        catch (Exception failure) {
            throw new IllegalStateException("Cannot load native arsenal config", failure);
        }
    }

    public static String lod(String family, int tier) {
        JsonArray rows = DATA.getAsJsonObject("lods").getAsJsonArray(family);
        if (rows == null || rows.isEmpty())
            return family;
        int wanted = tier == 0 ? Math.min(1, rows.size() - 1)
                : tier == 1 ? Math.max(0, rows.size() - 2) : rows.size() - 1;
        return rows.get(wanted).getAsJsonObject().get("id").getAsString();
    }

    public static Vec3 discOffset(int index, float yaw) {
        JsonArray a = DATA.getAsJsonObject("cluster").getAsJsonArray("discs").get(index).getAsJsonArray();
        Vec3 source = new Vec3(-a.get(1).getAsDouble(), a.get(2).getAsDouble(), -a.get(0).getAsDouble()).scale(.0254);
        return source.yRot((180 - yaw) * Mth.DEG_TO_RAD);
    }

    public static int releaseWait() {
        return DATA.getAsJsonObject("cluster").get("releaseWaitTicks").getAsInt();
    }

    private static JsonArray minigunRig() {
        try (var in = NativeArsenalLibrary.class
                .getResourceAsStream("/assets/killstreakcod/mw/animations/m13421_rig.json")) {
            if (in == null)
                throw new IllegalStateException("Missing minigun rig");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
                    .getAsJsonArray("bones");
        }
        catch (Exception failure) {
            throw new IllegalStateException("Cannot load minigun rig", failure);
        }
    }

    private static Vector3f vector(JsonArray a) {
        return new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
    }

    /**
     * Cosmetic source sockets, without assigning the unbound recoil Delta to an invented node.
     * 仅使用源挂点生成外观效果，不把未绑定的后坐力 Delta 强行分配到虚构节点。
     */
    public static Vec3 minigunSocket(net.minecraft.world.entity.player.Player player, String name, int using) {
        NativeCurveSet clip = NativeCurveSet.get(using < CHARGE_TICKS ? "m13421_charge" : "m13421_fire");
        float frame = using < CHARGE_TICKS ? using * clip.fps / 20
                : ((using - CHARGE_TICKS) * clip.fps / 20) % Math.max(1, clip.last);
        int key = using < CHARGE_TICKS ? using
                : CHARGE_TICKS + (using - CHARGE_TICKS) % Math.max(1, (int) (clip.last * 2));
        Vector3f p = sockets(key, clip, frame).get(name);
        if (p == null)
            return player.getEyePosition();
        boolean left = (player.getUsedItemHand() == net.minecraft.world.InteractionHand.MAIN_HAND ? player.getMainArm()
                : player.getMainArm().getOpposite()) == net.minecraft.world.entity.HumanoidArm.LEFT;
        Vec3 offset = new Vec3(left ? p.y : -p.y, p.z, -p.x).scale(.0254).xRot(player.getXRot() * Mth.DEG_TO_RAD)
                .yRot((180 - player.getYRot()) * Mth.DEG_TO_RAD);
        return player.getEyePosition().add(offset);
    }

    private static synchronized Map<String, Vector3f> sockets(int key, NativeCurveSet clip, float frame) {
        Map<String, Vector3f> cached = SOCKETS.get(key);
        if (cached != null)
            return cached;
        Map<String, Matrix4f> global = new HashMap<>();
        List<String> names = new ArrayList<>();
        for (var element : MINIGUN_RIG) {
            JsonObject b = element.getAsJsonObject();
            String bone = b.get("n").getAsString();
            var q = b.getAsJsonArray("lr");
            Matrix4f m = clip.local(
                    bone, frame, vector(b.getAsJsonArray("lp")), new Quaternionf(q.get(0).getAsFloat(),
                            q.get(1).getAsFloat(), q.get(2).getAsFloat(), q.get(3).getAsFloat()),
                    vector(b.getAsJsonArray("s")));
            int parent = b.get("p").getAsInt();
            if (parent >= 0)
                m = new Matrix4f(global.get(names.get(parent))).mul(m);
            global.put(bone, m);
            names.add(bone);
        }
        Map<String, Vector3f> result = new HashMap<>();
        Matrix4f camera = global.get("tag_camera");
        if (camera != null) {
            Matrix4f inverse = new Matrix4f(camera).invert();
            for (String name : new String[] { "tag_flash", "tag_brass" })
                if (global.containsKey(name))
                    result.put(name, new Matrix4f(inverse).mul(global.get(name)).getTranslation(new Vector3f()));
        }
        if (SOCKETS.size() >= 256)
            SOCKETS.remove(SOCKETS.keySet().iterator().next());
        SOCKETS.put(key, Map.copyOf(result));
        return result;
    }

    public static boolean positionalSound(net.minecraft.resources.ResourceLocation id) {
        return id.getNamespace().equals(KillstreakcodMod.MODID) && id.getPath().startsWith("native.csj21.");
    }
}
