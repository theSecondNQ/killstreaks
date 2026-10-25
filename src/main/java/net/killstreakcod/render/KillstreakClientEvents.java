package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.render.tablet.TabletTargeting;
import net.killstreakcod.universal.RequestTerminalStatePayload;
import net.killstreakcod.universal.TacticalControlPayload;
import net.killstreakcod.universal.UseKillstreakPayload;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;
import net.killstreakcod.network.StreakPackets;

@EventBusSubscriber(modid = KillstreakcodMod.MODID, value = Dist.CLIENT)
public final class KillstreakClientEvents {
    private KillstreakClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
        HandheldWeaponInput.tick();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null)
            return;
        if (TacticalClient.remote() || TabletTargeting.active())
            return;
        if (StreakPresentation.calling() || StreakPresentation.transitioning()) {
            for (var key : new net.minecraft.client.KeyMapping[] { KillstreakKeyMappings.USE_SLOT_1,
                    KillstreakKeyMappings.USE_SLOT_2, KillstreakKeyMappings.USE_SLOT_3, KillstreakKeyMappings.BONUS,
                    KillstreakKeyMappings.MGB, KillstreakKeyMappings.OPEN_TERMINAL, KillstreakKeyMappings.BOOST })
                while (key.consumeClick()) {
                }
            return;
        }
        if (KillstreakKeyMappings.BONUS.consumeClick()) {
            RewardRail.reveal();
            StreakPackets.sendToServer(new UseKillstreakPayload(3));
        }
        if (KillstreakKeyMappings.MGB.consumeClick()) {
            RewardRail.reveal();
            StreakPackets.sendToServer(new UseKillstreakPayload(4));
        }
        if (KillstreakKeyMappings.BOOST.consumeClick())
            StreakPackets.sendToServer(new TacticalControlPayload(0, 0, 0, 0, TacticalControlPayload.BOOST));

        if (KillstreakKeyMappings.OPEN_TERMINAL.consumeClick())
            StreakPackets.sendToServer(new RequestTerminalStatePayload(true));

        if (KillstreakKeyMappings.USE_SLOT_1.consumeClick()) {
            RewardRail.reveal();
            StreakPackets.sendToServer(new UseKillstreakPayload(0));
        }
        if (KillstreakKeyMappings.USE_SLOT_2.consumeClick()) {
            RewardRail.reveal();
            StreakPackets.sendToServer(new UseKillstreakPayload(1));
        }
        if (KillstreakKeyMappings.USE_SLOT_3.consumeClick()) {
            RewardRail.reveal();
            StreakPackets.sendToServer(new UseKillstreakPayload(2));
        }
    }
}
