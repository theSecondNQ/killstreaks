package net.killstreakcod.audio;

import java.util.List;

import net.minecraft.server.level.ServerPlayer;

/**
 * Voice preference is independent of scoreboard teams and the teammate whitelist.
 * 语音阵营偏好与计分板队伍、队友白名单分别保存。
 */
public final class VoiceFaction {
    public static final String SPECGRU = "specgru", KORTAC = "kortac";
    private static final String TAG = "killstreakcod.voice_faction=";

    private VoiceFaction() {}

    public static boolean valid(String value) {
        return SPECGRU.equals(value) || KORTAC.equals(value);
    }

    public static String normalize(String value) {
        return KORTAC.equals(value) ? KORTAC : SPECGRU;
    }

    public static String get(ServerPlayer player) {
        return player.getTags().stream().filter(t -> t.startsWith(TAG)).map(t -> normalize(t.substring(TAG.length())))
                .findFirst().orElse(SPECGRU);
    }

    public static void set(ServerPlayer player, String value) {
        if (!valid(value))
            throw new IllegalArgumentException("Unknown voice faction");
        for (String tag : List.copyOf(player.getTags()))
            if (tag.startsWith(TAG))
                player.removeTag(tag);
        player.addTag(TAG + value);
    }
}
