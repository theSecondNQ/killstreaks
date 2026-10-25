package net.killstreakcod.universal;

import net.killstreakcod.killstreaks.StreakReward;
import net.killstreakcod.killstreaks.TacticalUnitEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.server.level.ServerPlayer;

/** 一次已消耗奖励的返还凭据；同一编队及后续航段共享，只能结算一次。仅在服务端线程使用。 */
public final class AirspaceRefund {
    private static AirspaceRefund spawning;
    private final ServerPlayer player;
    private final StreakReward reward;
    private final boolean bonus;
    private final List<TacticalUnitEntity> units = new ArrayList<>();
    private boolean refunded;

    private AirspaceRefund(ServerPlayer player, StreakReward reward, boolean bonus) {
        this.player = player;
        this.reward = reward;
        this.bonus = bonus;
    }

    static void call(ServerPlayer player, StreakReward reward, boolean bonus, Runnable action) {
        AirspaceRefund receipt = spawning != null ? spawning : new AirspaceRefund(player, reward, bonus);
        inherit(receipt, () -> {
            action.run();
            return null;
        });
    }

    public static <T> T inherit(AirspaceRefund receipt, Supplier<T> action) {
        AirspaceRefund previous = spawning;
        spawning = receipt;
        try {
            return action.get();
        } finally {
            spawning = previous;
        }
    }

    static void attach(TacticalUnitEntity unit) {
        unit.airspaceRefund = spawning;
        if (spawning != null) {
            spawning.units.add(unit);
            if (spawning.refunded)
                unit.discard();
        }
    }

    static void cancelCurrent() {
        if (spawning != null)
            spawning.cancel();
    }

    public void cancel() {
        if (refunded || reward == null)
            return;
        refunded = true;
        // 先标记结算，再移除整个编队，防止多机同时受阻或后续航段重复返还。
        for (TacticalUnitEntity unit : List.copyOf(units))
            if (!unit.isRemoved())
                unit.discard();
        if (player.serverLevel().getServer().getPlayerList().getPlayer(player.getUUID()) == player)
            KillstreakService.refund(player, reward, bonus);
    }

    public void removed(TacticalUnitEntity unit) {
        units.remove(unit);
    }
}
