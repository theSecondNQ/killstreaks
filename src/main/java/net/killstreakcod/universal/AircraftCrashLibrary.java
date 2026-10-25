package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.audio.NativeAudio;
import net.killstreakcod.killstreaks.TacticalUnitEntity;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Shared source sockets, native action timing and bank-specific sound variants.
 * 共用源挂点、原动作时序及各音库专属的音效变体。
 */
public final class AircraftCrashLibrary {
    public static final int GUNSHIP = 100, APACHE_MISSILE = 101, APACHE_BULLET = 102, VTOL = 103, ESCORT = 104;
    public static final int GUNSHIP_AIRBURST = 180, GUNSHIP_DELETE = 380, GUNSHIP_SHAKE = 187, GUNSHIP_OUTRO = 120;
    private static final JsonObject AUDIO = read("aircraft-crash-audio");

    private AircraftCrashLibrary() {}

    private static JsonObject read(String name) {
        try (var in = AircraftCrashLibrary.class.getResourceAsStream("/assets/killstreakcod/mw/" + name + ".json")) {
            if (in == null)
                throw new IOException("Missing crash resource " + name);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Invalid crash resources", e);
        }
    }

    public static int variant(int stage) {
        return stage >= 200 ? stage - 100 : stage;
    }

    public static boolean stage(int stage) {
        int v = variant(stage);
        return v >= GUNSHIP && v <= ESCORT;
    }

    public static String pilotAction(int variant) {
        return variant == APACHE_MISSILE ? "iw9_mp_ks_ahotel64_crash_player" : "iw9_mp_ks_ahotel64_shot_player";
    }

    public static int pilotEnd(int variant) {
        return NativeCurveSet.get("scene_" + pilotAction(variant)).notifyTick("end",
                variant == APACHE_MISSILE ? 54 : 67);
    }

    public static SoundEvent sound(String role, int selection) {
        JsonArray variants = AUDIO.getAsJsonObject("roles").getAsJsonArray(role);
        if (variants == null || variants.isEmpty())
            return null;
        return NativeAudio.event(variants.get(Math.floorMod(selection, variants.size())).getAsString());
    }

    public static boolean positionalSound(net.minecraft.resources.ResourceLocation id) {
        if (!id.getNamespace().equals(KillstreakcodMod.MODID))
            return false;
        for (var role : AUDIO.getAsJsonObject("roles").entrySet()) {
            if (role.getKey().equals("gunship_outro") || role.getKey().equals("apache_player"))
                continue;
            for (var event : role.getValue().getAsJsonArray())
                if (event.getAsString().equals(id.getPath()))
                    return true;
        }
        return false;
    }

    public static float roll(int variant, float ticks) {
        return variant == GUNSHIP ? 0
                : (float) Math.sin(Math.min(1, ticks / 16F) * Math.PI / 2) * Math.min(50, ticks * 1.5F);
    }

    public static Quaternionf attitude(float yaw, float pitch, float roll) {
        return new Quaternionf().rotateY((float) Math.toRadians(180 - yaw)).rotateX((float) Math.toRadians(-pitch))
                .rotateZ((float) Math.toRadians(roll));
    }

    public static Vec3 worldOffset(Vector3f source, float yaw, float pitch, float roll) {
        Vector3f p = attitude(yaw, pitch, roll).transform(new Vector3f(-source.y, source.z, -source.x).mul(.01F));
        return new Vec3(p.x, p.y, p.z);
    }

    public static Matrix4f chopperAttach() {
        return SceneEntryLibrary.socket("scene_chopper", SceneEntryLibrary.pose("scene_chopper", "", 0),
                "body_animate_jnt");
    }

    public static Matrix4f pilotCamera(int variant, float ticks) {
        return chopperAttach().mul(SceneEntryLibrary.track(
                variant == APACHE_MISSILE ? "crash_gunner_missile_crash_camera" : "crash_gunner_bullet_crash_camera")
                .at(ticks));
    }

    public static Vec3 pilotTag(TacticalUnitEntity unit, String tag, float ticks) {
        var actor = SceneEntryLibrary.pose("crash_pilot_western", pilotAction(variant(unit.stage())), ticks);
        Matrix4f attach = chopperAttach().mul(SceneEntryLibrary.socket("crash_pilot_western", actor, tag));
        return unit.position().add(worldOffset(attach.getTranslation(new Vector3f()), unit.flightYaw(),
                unit.flightPitch(), roll(variant(unit.stage()), ticks)));
    }
}
