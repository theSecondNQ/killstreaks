package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.render.tablet.TabletTargeting;
import net.killstreakcod.universal.RequestTerminalStatePayload;
import net.killstreakcod.universal.TacticalControlPayload;
import net.killstreakcod.universal.UseKillstreakPayload;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = KillstreakcodMod.MODID, value = Dist.CLIENT)
public final class KillstreakClientEvents {
    private KillstreakClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
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
            PacketDistributor.sendToServer(new UseKillstreakPayload(3));
        }
        if (KillstreakKeyMappings.MGB.consumeClick()) {
            RewardRail.reveal();
            PacketDistributor.sendToServer(new UseKillstreakPayload(4));
        }
        if (KillstreakKeyMappings.BOOST.consumeClick())
            PacketDistributor.sendToServer(new TacticalControlPayload(0, 0, 0, 0, TacticalControlPayload.BOOST));

        if (KillstreakKeyMappings.OPEN_TERMINAL.consumeClick())
            PacketDistributor.sendToServer(new RequestTerminalStatePayload(true));

        if (KillstreakKeyMappings.USE_SLOT_1.consumeClick()) {
            RewardRail.reveal();
            PacketDistributor.sendToServer(new UseKillstreakPayload(0));
        }
        if (KillstreakKeyMappings.USE_SLOT_2.consumeClick()) {
            RewardRail.reveal();
            PacketDistributor.sendToServer(new UseKillstreakPayload(1));
        }
        if (KillstreakKeyMappings.USE_SLOT_3.consumeClick()) {
            RewardRail.reveal();
            PacketDistributor.sendToServer(new UseKillstreakPayload(2));
        }
    }
}
