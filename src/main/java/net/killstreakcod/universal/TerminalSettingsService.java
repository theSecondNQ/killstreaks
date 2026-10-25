package net.killstreakcod.universal;

import net.killstreakcod.audio.VoiceFaction;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.server.level.ServerPlayer;

/** 设置修改与权限校验只发生在服务端线程；单机房主无需开启作弊即可修改自己的服务器。 */
public final class TerminalSettingsService {
    private static final Map<ServerPlayer, Integer> LAST_SAVE = new WeakHashMap<>();

    private TerminalSettingsService() {}

    public static boolean writable(ServerPlayer player) {
        var server = player.serverLevel().getServer();
        return player.hasPermissions(2)
                || server.isSingleplayer() && server.isSingleplayerOwner(player.getGameProfile());
    }

    public static void handle(ServerPlayer player, TerminalSettingsActionPayload payload) {
        boolean modifying = !payload.changes().isEmpty() || !payload.faction().isEmpty();
        String message = "";
        if (modifying) {
            int now = player.serverLevel().getServer().getTickCount();
            if (now - LAST_SAVE.getOrDefault(player, now - 10) < 5) {
                message = "wait";
            } else if (!payload.faction().isEmpty() && !VoiceFaction.valid(payload.faction())) {
                message = "invalid";
            } else if (!payload.changes().isEmpty() && !writable(player)) {
                message = "readonly";
            } else {
                LAST_SAVE.put(player, now);
                if (!payload.changes().isEmpty()
                        && !StreakServerConfig.update(player.serverLevel().getServer(), payload.changes())) {
                    message = "invalid";
                } else {
                    if (!payload.faction().isEmpty())
                        VoiceFaction.set(player, payload.faction());
                    message = "saved";
                    KillstreakService.sendTerminalState(player, false);
                }
            }
        }
        StreakNetwork.send(player, new TerminalSettingsStatePayload(payload.request(), writable(player),
                VoiceFaction.get(player), message, StreakServerConfig.snapshot(player.serverLevel().getServer())));
    }
}
