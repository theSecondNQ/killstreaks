package net.killstreakcod.audio;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.killstreaks.TacticalUnitEntity;
import net.killstreakcod.universal.FriendlyPlayers;
import net.killstreakcod.universal.StreakNetwork;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.registries.DeferredHolder;

/** 按原游戏资源映射选择音效和阵营语音，并维护播报时长与重复播报间隔。缺失的语音映射保持静音。
 * */
public final class NativeAudio {

    private static final Map<String, DeferredHolder<SoundEvent, SoundEvent>> EVENTS = new HashMap<>();
    private static final Map<String, String> VOICES = new HashMap<>(),
            SFX = new HashMap<>();
    private static final Map<String, Map<String, String>> FACTION_VOICES = new HashMap<>();
    private static final Map<String, Float> DURATIONS = new HashMap<>();
    private static final Map<String, Float> SOUND_SECONDS = new HashMap<>();
    private static final Map<ServerPlayer, Map<String, Long>> LAST = new WeakHashMap<>();
    private static boolean initialized;

    private NativeAudio() {}

    public static void init() {
        if (initialized)
            return;

        initialized = true;

        try (var input = NativeAudio.class.getResourceAsStream("/assets/killstreakcod/mw/native-audio.json")) {
            if (input == null)
                return;

            JsonObject root = JsonParser
                    .parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .getAsJsonObject();

            for (var value : root.getAsJsonArray("events")) {
                String id = value.getAsString();
                EVENTS.put(id, StreakSounds.REGISTER
                        .register(id,
                                () -> SoundEvent
                                        .createVariableRangeEvent(ResourceLocation
                                                .fromNamespaceAndPath(KillstreakcodMod.MODID, id))));
            }

            root.getAsJsonObject("voices")
                    .entrySet()
                    .forEach(
                            e -> VOICES.put(e.getKey(), e.getValue().getAsString()));

            if (root.has("factionVoices"))
                for (var faction : root.getAsJsonObject("factionVoices").entrySet()) {
                    Map<String, String> routes = new HashMap<>();
                    faction.getValue()
                            .getAsJsonObject()
                            .entrySet()
                            .forEach(
                                    e -> routes.put(e.getKey(), e.getValue().getAsString()));
                    FACTION_VOICES.put(faction.getKey(), Map.copyOf(routes));
                }

            root.getAsJsonObject("sfx")
                    .entrySet()
                    .forEach(
                            e -> SFX.put(e.getKey(), e.getValue().getAsString()));

            if (root.has("voiceSeconds"))
                root.getAsJsonObject("voiceSeconds")
                        .entrySet()
                        .forEach(
                                e -> DURATIONS.put(e.getKey(), e.getValue().getAsFloat()));

            if (root.has("soundSeconds"))
                root.getAsJsonObject("soundSeconds")
                        .entrySet()
                        .forEach(
                                e -> SOUND_SECONDS.put(e.getKey(), e.getValue().getAsFloat()));
        }
        catch (Exception failure) {
            throw new IllegalStateException("Cannot register recovered audio catalog", failure);
        }
    }

    private static String sourcePhase(String phase) {
        if (phase.equals("response_parallel"))
            return "response";
        if (phase.endsWith("_now"))
            return phase.substring(0, phase.length() - 4);
        return phase;
    }

    public static SoundEvent voiceEvent(String reward, String phase) {
        return phase.equals("call") ? null
                : event(VOICES.get(reward + "/" + sourcePhase(phase)));
    }

    public static SoundEvent voiceEvent(String reward, String phase, String faction) {
        return phase.equals("call") ? null
                : event(FACTION_VOICES.getOrDefault(VoiceFaction.normalize(faction), VOICES)
                        .get(reward + "/" + sourcePhase(phase)));
    }

    /** 优先使用阵营语音时长，其次使用通用时长；缺失时按 8 秒换算为游戏刻。
     * */
    public static int voiceTicks(String reward, String phase, String faction) {
        String voiceKey = reward + "/" + sourcePhase(phase);
        float defaultSeconds = DURATIONS.getOrDefault(voiceKey, 8F);
        float seconds = DURATIONS.getOrDefault(VoiceFaction.normalize(faction) + "/" + voiceKey, defaultSeconds);
        return (int) Math.ceil(seconds * 20);
    }

    public static int soundTicks(String location, String reward, String phase) {
        float defaultSeconds = DURATIONS.getOrDefault(reward + "/" + sourcePhase(phase), 8F);
        float seconds = SOUND_SECONDS.getOrDefault(location, defaultSeconds);
        return (int) Math.ceil(seconds * 20);
    }

    /** 发送需要客户端播放确认的响应语音；token 用于关联待入场的连杀。
     * */
    public static boolean gate(ServerPlayer player, String reward, UUID token, int delay) {
        String faction = VoiceFaction.get(player);
        if (voiceEvent(reward, "response", faction) == null)
            return false;
        StreakNetwork.send(player, new NativeVoicePayload(player.serverLevel().dimension().location(),
                player.getId(), reward, "response", token, delay, faction));
        return true;
    }

    public static SoundEvent sfx(String name, SoundEvent fallback) {
        SoundEvent result = event(SFX.get(name));
        return result == null ? fallback : result;
    }

    public static String reward(TacticalUnitEntity unit) {
        return switch (unit.kind()) {
            case UAV -> "uav";
            case COUNTER_UAV -> "counter_uav";
            case ADVANCED_UAV -> "advanced_uav";
            case BOMB_DRONE -> "bomb_drone";
            case SENTRY -> "sentry_gun";
            case WHEELSON -> "wheelson_hs";
            case GUNSHIP -> "gunship";
            case CHOPPER -> "chopper_gunner";
            case CRUISE -> "cruise_missile";
            case PRECISION -> "precision_airstrike";
            case SAE -> "sae";
            case VTOL -> "vtol_jet";
            case OVERWATCH -> "overwatch_helo";
            case STEALTH -> "stealth_bomber";
            case CLUSTER, MINE -> "cluster_mine";
            case DELIVERY -> unit.cargo.equals("emergency") ? "emergency_airdrop"
                    : unit.cargo.equals("juggernaut") ? "juggernaut" : "care_package";
            case BOMB -> switch (unit.cargo) {
                case "mortar" -> "mortar_strike";
                case "sae_parent", "sae_bomblet" -> "sae";
                case "vtol" -> "vtol_jet";
                default -> "";
            };
            case ROCKET ->
                unit.cargo.equals("105mm") ? "gunship"
                        : unit.cargo.equals("vtol_rocket") ? "vtol_jet" : "chopper_gunner";
            default -> "";
        };
    }

    public static SoundEvent shot(Entity emitter, float damage) {
        String key = emitter instanceof TacticalUnitEntity unit ? switch (unit.kind()) {
            case GUNSHIP -> damage == 0 ? "gunship40_shot" : "gunship25_shot";
            default -> "";
        } : emitter instanceof ServerPlayer ? "m134_shot" : "";
        return sfx(key, StreakSounds.SHOT.get());
    }

    public static SoundEvent event(String id) {
        var holder = EVENTS.get(id);
        return holder == null ? null : holder.get();
    }

    public static void call(ServerPlayer player, String reward) {
        for (var listener : player.serverLevel().players())
            if (listener != player)
                voice(listener, reward, FriendlyPlayers.allied(player, listener) ? "friendly_call" : "enemy_call");
    }

    public static void ended(ServerPlayer player, String reward) {
        voice(player, reward, voiceEvent(reward, "leave") != null ? "leave" : "timeout");
    }

    public static void departure(ServerPlayer player, String reward) {
        voice(player, reward, voiceEvent(reward, "leave") != null ? "leave_now" : "timeout_now");
    }

    public static void voice(ServerPlayer player, String reward, String phase) {
        if (player == null || !player.isAlive() || player.connection == null || !player.connection.isAcceptingMessages()
                || voiceEvent(reward, phase, VoiceFaction.get(player)) == null)
            return;

        // 上一条生命遗留的控制器不能再向重生后的玩家实例发送消息。
        if (player.serverLevel().getServer().getPlayerList().getPlayer(player.getUUID()) != player)
            return;
        String key = reward + "/" + phase;
        long now = player.serverLevel().getServer().getTickCount();
        Map<String, Long> history = LAST.computeIfAbsent(player, ignored -> new HashMap<>());

        // 同一种反馈需要冷却，避免高射速武器让多段语音连续叠播。
        long gap = phase.equals("hit") || phase.equals("miss") ? 100 : phase.equals("damage") ? 200 : 8;
        if (now - history.getOrDefault(key, now - gap) < gap)
            return;
        history.put(key, now);
        StreakNetwork.send(player, new NativeVoicePayload(player.serverLevel().dimension().location(), player.getId(),
                reward, phase, NativeVoicePayload.NONE, 0, VoiceFaction.get(player)));
    }
}
