package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.audio.NativeAudio;
import net.killstreakcod.audio.StreakSounds;
import net.killstreakcod.killstreaks.MinigunItem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.TickEvent;

/** 独立于原版“使用物品”按键的 M134 服务端时钟；失焦、换手、死亡或心跳超时立即收枪。 */
@EventBusSubscriber(modid = KillstreakcodMod.MODID)
public final class MinigunControls {
    private static final Map<ServerPlayer, Hold> HOLDS = new HashMap<>();

    private static final class Hold {
        final InteractionHand hand;
        final ServerLevel level;
        final long started;
        long heartbeat;

        Hold(InteractionHand hand, ServerLevel level, long now) {
            this.hand = hand;
            this.level = level;
            started = heartbeat = now;
        }
    }

    private MinigunControls() {}

    public static void input(ServerPlayer player, HandheldFirePayload input) {
        if (!input.firing()) {
            stop(player);
            return;
        }
        InteractionHand hand = input.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        if (!valid(player, hand)) {
            stop(player);
            return;
        }
        long now = player.serverLevel().getGameTime();
        Hold hold = HOLDS.get(player);
        if (hold == null || hold.hand != hand || hold.level != player.serverLevel()) {
            stop(player);
            hold = new Hold(hand, player.serverLevel(), now);
            HOLDS.put(player, hold);
            TacticalRuntime.sound(player.serverLevel(), player.getEyePosition(),
                    NativeAudio.sfx("m134_spinup", StreakSounds.SPIN.get()), .8F, 1);
        }
        hold.heartbeat = now;
    }

    private static boolean valid(ServerPlayer player, InteractionHand hand) {
        return player.isAlive() && !player.isSpectator() && !player.isRemoved() && player.connection != null
                && net.killstreakcod.network.StreakPackets.connected(player)
                && player.getItemInHand(hand).is(KillstreakcodMod.M134_MINIGUN.get()) && !TacticalRuntime.busy(player)
                && !TacticalRuntime.mgbDetonating(player.serverLevel());
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
        for (var entry : List.copyOf(HOLDS.entrySet())) {
            ServerPlayer player = entry.getKey();
            Hold hold = entry.getValue();
            long now = player.serverLevel().getGameTime();
            if (player.serverLevel().getServer() != event.getServer())
                continue;
            if (!valid(player, hold.hand) || hold.level != player.serverLevel() || now - hold.heartbeat > 10) {
                stop(player);
                continue;
            }
            // 供旁观玩家显示持枪姿势；开火时钟独立保存，不被原版松开使用物品的消息重置。
            if (!player.isUsingItem() || player.getUsedItemHand() != hold.hand)
                player.startUsingItem(hold.hand);
            MinigunItem.fire(player, (int) (now - hold.started));
        }
    }

    private static void stop(ServerPlayer player) {
        Hold removed = HOLDS.remove(player);
        if (removed == null)
            return;
        if (player.isUsingItem() && player.getUseItem().is(KillstreakcodMod.M134_MINIGUN.get()))
            player.stopUsingItem();
        if (player.connection != null && net.killstreakcod.network.StreakPackets.connected(player))
            TacticalRuntime.sound(player.serverLevel(), player.getEyePosition(),
                    NativeAudio.sfx("m134_spindown", StreakSounds.SPIN.get()), .5F, 1);
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        for (ServerPlayer player : List.copyOf(HOLDS.keySet()))
            if (player.serverLevel().getServer() == event.getServer())
                stop(player);
    }
}
