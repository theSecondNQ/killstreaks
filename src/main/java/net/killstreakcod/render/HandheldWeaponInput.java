package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.universal.HandheldFirePayload;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;

/** 本地开火反馈与服务端按住心跳共用同一状态；不修改原版按键的按下状态。 */
public final class HandheldWeaponInput {
    private static boolean firing;
    private static InteractionHand hand;
    private static int ticks, heartbeat;
    private static Object world;

    private HandheldWeaponInput() {}

    public static void tick() {
        var mc = Minecraft.getInstance();
        if (world != mc.level) {
            firing = false;
            ticks = heartbeat = 0;
            world = mc.level;
        }
        InteractionHand next = mc.player != null && mc.player.getMainHandItem().is(KillstreakcodMod.M134_MINIGUN.get())
                ? InteractionHand.MAIN_HAND
                : mc.player != null && mc.player.getOffhandItem().is(KillstreakcodMod.M134_MINIGUN.get())
                        ? InteractionHand.OFF_HAND
                        : null;
        boolean pressed = next != null && mc.player.isAlive() && !mc.player.isSpectator() && !TacticalClient.remote()
                && !StreakPresentation.busy() && !MgbEffects.interruptingControl()
                && TacticalClient.held(KillstreakKeyMappings.MINIGUN_FIRE);
        boolean changed = firing != pressed || hand != next;
        if (changed)
            ticks = 0;
        firing = pressed;
        hand = next;
        if (firing)
            ticks++;
        if (mc.getConnection() != null && (changed || firing && ++heartbeat >= 4)) {
            PacketDistributor.sendToServer(new HandheldFirePayload(firing, hand == InteractionHand.OFF_HAND));
            heartbeat = 0;
        }
    }

    public static boolean firing() {
        return firing;
    }

    public static int ticks() {
        return firing ? ticks : 0;
    }

    public static boolean using(InteractionHand value) {
        return firing && hand == value;
    }
}
