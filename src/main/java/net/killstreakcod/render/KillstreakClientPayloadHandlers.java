package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.audio.NativeRadioClient;
import net.killstreakcod.audio.NativeVoicePayload;
import net.killstreakcod.audio.SupportAudioPayload;
import net.killstreakcod.audio.SupportSoundClient;
import net.killstreakcod.audio.SupportSoundPayload;
import net.killstreakcod.audio.TacticalEngineSounds;
import net.killstreakcod.render.tablet.TabletTargeting;
import net.killstreakcod.universal.AircraftCrashPayload;
import net.killstreakcod.universal.BonusStatePayload;
import net.killstreakcod.universal.MgbCountdownPayload;
import net.killstreakcod.universal.MgbEffectPayload;
import net.killstreakcod.universal.NativeFxPayload;
import net.killstreakcod.universal.RadarPayload;
import net.killstreakcod.universal.ResearchStatePayload;
import net.killstreakcod.universal.StreakEarnedPayload;
import net.killstreakcod.universal.StreakImpactPayload;
import net.killstreakcod.universal.StreakPresentationPayload;
import net.killstreakcod.universal.TacticalMapContactsPayload;
import net.killstreakcod.universal.TacticalMapPayload;
import net.killstreakcod.universal.TacticalTargetResultPayload;
import net.killstreakcod.universal.TacticalViewPayload;
import net.killstreakcod.universal.TerminalStatePayload;
import net.killstreakcod.universal.TerminalSettingsStatePayload;
import net.killstreakcod.universal.TracerPayload;
import net.killstreakcod.universal.WhitelistStatePayload;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = KillstreakcodMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class KillstreakClientPayloadHandlers {
    private KillstreakClientPayloadHandlers() {}

    @SubscribeEvent
    public static void registerPayloads(FMLClientSetupEvent event) {
        var registrar = new net.killstreakcod.network.PayloadRegistrar(true);
        registrar.playToClient(TerminalSettingsStatePayload.TYPE, TerminalSettingsStatePayload.STREAM_CODEC,
                (p, c) -> c.enqueueWork(() -> {
                    if (Minecraft.getInstance().screen instanceof TerminalSettingsScreen screen)
                        screen.receiveState(p);
                }));
        registrar.playToClient(ResearchStatePayload.TYPE, ResearchStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    TacticalClient.acceptWorld();
                    TacticalClient.research = payload;
                    if (Minecraft.getInstance().screen instanceof HeadquartersTerminalScreen screen)
                        screen.receiveState(payload);
                }));
        registrar.playToClient(WhitelistStatePayload.TYPE, WhitelistStatePayload.STREAM_CODEC,
                (p, c) -> c.enqueueWork(() -> {
                    if (Minecraft.getInstance().screen instanceof KillstreakWhitelistScreen screen)
                        screen.receiveState(p);
                }));
        registrar.playToClient(AircraftCrashPayload.TYPE, AircraftCrashPayload.STREAM_CODEC,
                (p, c) -> c.enqueueWork(() -> AircraftCrashClient.start(p)));
        registrar.playToClient(NativeFxPayload.TYPE, NativeFxPayload.STREAM_CODEC,
                (p, c) -> c.enqueueWork(() -> NativeFxRenderer.accept(p)));
        registrar.playToClient(NativeVoicePayload.TYPE, NativeVoicePayload.STREAM_CODEC,
                (p, c) -> c.enqueueWork(() -> NativeRadioClient.accept(p)));
        registrar.playToClient(SupportAudioPayload.TYPE, SupportAudioPayload.STREAM_CODEC,
                (p, c) -> c.enqueueWork(() -> {
                    TacticalEngineSounds.accept(p);
                    DistantPrecisionAircraft.accept(p);
                }));
        registrar.playToClient(SupportSoundPayload.TYPE, SupportSoundPayload.STREAM_CODEC,
                (p, c) -> c.enqueueWork(() -> SupportSoundClient.accept(p)));
        registrar.playToClient(TracerPayload.TYPE, TracerPayload.STREAM_CODEC,
                (p, c) -> c.enqueueWork(() -> TracerEffects.accept(p)));
        registrar.playToClient(TacticalViewPayload.TYPE, TacticalViewPayload.STREAM_CODEC,
                (p, c) -> c.enqueueWork(() -> TacticalClient.view(p)));
        registrar.playToClient(TacticalMapPayload.TYPE, TacticalMapPayload.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> {
            TacticalClient.acceptWorld();
            TabletTargeting.open(p);
        }));
        registrar.playToClient(TacticalMapContactsPayload.TYPE, TacticalMapContactsPayload.STREAM_CODEC,
                (p, c) -> c.enqueueWork(() -> {
                    TacticalClient.acceptWorld();
                    TacticalClient.mapReward = p.reward();
                    TacticalClient.mapContacts = p.contacts();
                }));
        registrar.playToClient(MgbEffectPayload.TYPE, MgbEffectPayload.STREAM_CODEC,
                (p, c) -> c.enqueueWork(() -> MgbEffects.start(p)));
        registrar.playToClient(MgbCountdownPayload.TYPE, MgbCountdownPayload.STREAM_CODEC,
                (p, c) -> c.enqueueWork(() -> MgbWarning.accept(p)));
        registrar.playToClient(RadarPayload.TYPE, RadarPayload.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> {
            TacticalClient.acceptWorld();
            TacticalClient.radar = p;
        }));
        registrar.playToClient(BonusStatePayload.TYPE, BonusStatePayload.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> {
            TacticalClient.acceptWorld();
            TacticalClient.bonus = p;
        }));
        registrar.playToClient(StreakImpactPayload.TYPE, StreakImpactPayload.STREAM_CODEC,
                (p, c) -> c.enqueueWork(() -> TacticalClient.impact(p.strength())));
        registrar.playToClient(StreakEarnedPayload.TYPE, StreakEarnedPayload.STREAM_CODEC,
                (p, c) -> c.enqueueWork(() -> {
                    TacticalClient.acceptWorld();
                    TacticalHud.earned(p);
                }));
        registrar.playToClient(StreakPresentationPayload.TYPE, StreakPresentationPayload.STREAM_CODEC,
                (p, c) -> c.enqueueWork(() -> {
                    TacticalClient.acceptWorld();
                    if (p.stage().equals("reset_rewards")) {
                        TabletTargeting.reset(false);
                        NativeRadioClient.clear();
                        StreakNoticeRenderer.reset();
                        StreakPresentation.reset();
                        TargetingDeviceAnimations.reset();
                        return;
                    }
                    if (p.stage().equals("designator_fire")) {
                        TargetingDeviceAnimations.confirm(p.reward());
                        return;
                    }
                    if (p.stage().equals("reset")) {
                        TabletTargeting.reset(false);
                        NativeRadioClient.cancelGate();
                        TargetingDeviceAnimations.reset();
                    }
                    if (p.stage().equals("confirmed"))
                        TabletTargeting.confirmed(p.reward());
                    StreakPresentation.start(p.reward(), p.stage(), p.duration());
                }));
        registrar.playToClient(TacticalTargetResultPayload.TYPE, TacticalTargetResultPayload.STREAM_CODEC,
                (p, c) -> c.enqueueWork(() -> TabletTargeting.result(p.accepted())));
        registrar.playToClient(TerminalStatePayload.TYPE, TerminalStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    TacticalClient.acceptWorld();
                    TacticalClient.terminal = payload;
                    if (payload.openScreen()) {
                        minecraft.setScreen(new KillstreakTerminalScreen(payload));
                    } else if (minecraft.screen instanceof KillstreakTerminalScreen screen) {
                        screen.receiveState(payload);
                    } else if (minecraft.screen instanceof TerminalSettingsScreen screen) {
                        screen.receiveTerminalState(payload);
                    }
                }));
    }
}
