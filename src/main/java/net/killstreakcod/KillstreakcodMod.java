package net.killstreakcod;

import net.killstreakcod.audio.NativeAudio;
import net.killstreakcod.audio.NativeVoiceFinishedPayload;
import net.killstreakcod.audio.NativeVoicePayload;
import net.killstreakcod.audio.StreakSounds;
import net.killstreakcod.audio.SupportAudioPayload;
import net.killstreakcod.audio.SupportSoundPayload;
import net.killstreakcod.killstreaks.ArmorKit;
import net.killstreakcod.killstreaks.KillstreakControllerItem;
import net.killstreakcod.killstreaks.MinigunItem;
import net.killstreakcod.killstreaks.RewardCardItem;
import net.killstreakcod.killstreaks.SentryTurretItem;
import net.killstreakcod.killstreaks.SupportHelicopterEntity;
import net.killstreakcod.killstreaks.TacticalEquipmentItem;
import net.killstreakcod.killstreaks.TacticalUnitEntity;
import net.killstreakcod.killstreaks.WheelsonControllerItem;
import net.killstreakcod.killstreaks.WheelsonEntity;
import net.killstreakcod.universal.AircraftCrashPayload;
import net.killstreakcod.universal.BonusStatePayload;
import net.killstreakcod.universal.FriendlyPlayers;
import net.killstreakcod.universal.KillstreakContent;
import net.killstreakcod.universal.KillstreakService;
import net.killstreakcod.universal.MgbCountdownPayload;
import net.killstreakcod.universal.MgbEffectPayload;
import net.killstreakcod.universal.NativeFxPayload;
import net.killstreakcod.universal.NativePresentationConfig;
import net.killstreakcod.universal.compat.OptionalIntegrations;
import net.killstreakcod.universal.RadarPayload;
import net.killstreakcod.universal.RequestTerminalStatePayload;
import net.killstreakcod.universal.RequestWhitelistPayload;
import net.killstreakcod.universal.SaveKillstreakLoadoutPayload;
import net.killstreakcod.universal.StreakEarnedPayload;
import net.killstreakcod.universal.StreakImpactPayload;
import net.killstreakcod.universal.StreakPresentationPayload;
import net.killstreakcod.universal.StreakServerConfig;
import net.killstreakcod.universal.TacticalControlPayload;
import net.killstreakcod.universal.TacticalMapContactsPayload;
import net.killstreakcod.universal.TacticalMapPayload;
import net.killstreakcod.universal.TacticalRuntime;
import net.killstreakcod.universal.TacticalTargetPayload;
import net.killstreakcod.universal.TacticalTargetResultPayload;
import net.killstreakcod.universal.TacticalViewPayload;
import net.killstreakcod.universal.TerminalStatePayload;
import net.killstreakcod.universal.TracerPayload;
import net.killstreakcod.universal.UseKillstreakPayload;
import net.killstreakcod.universal.WhitelistActionPayload;
import net.killstreakcod.universal.WhitelistStatePayload;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(KillstreakcodMod.MODID)
public class KillstreakcodMod {

    public static final String MODID = "killstreakcod";
    public static final String NETWORK_VERSION = "1.0.0";

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE,
            MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<WheelsonEntity>> WHEELSON = ENTITY_TYPES.register(
            "wheelson",
            () -> EntityType.Builder
                    .<WheelsonEntity>of((type, level) -> new WheelsonEntity(type, level), MobCategory.CREATURE)
                    .sized(1.3F, 0.9F).clientTrackingRange(10).updateInterval(2).build(MODID + ":wheelson"));

    public static final DeferredHolder<EntityType<?>, EntityType<SupportHelicopterEntity>> SUPPORT_HELICOPTER = ENTITY_TYPES
            .register("support_helicopter",
                    () -> EntityType.Builder
                            .<SupportHelicopterEntity>of((type, level) -> new SupportHelicopterEntity(type, level),
                                    MobCategory.MISC)
                            .sized(3.6F, 2.4F).clientTrackingRange(12).updateInterval(2)
                            .build(MODID + ":support_helicopter"));

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<TacticalUnitEntity>> TACTICAL_UNIT = ENTITY_TYPES
            .register("tactical_unit",
                    () -> EntityType.Builder.<TacticalUnitEntity>of(TacticalUnitEntity::new, MobCategory.MISC)
                            .sized(2, 1).clientTrackingRange(32).updateInterval(1).build(MODID + ":tactical_unit"));

    public static final DeferredItem<Item> CONTROLLER = ITEMS.register("killstreak_controller",
            () -> new KillstreakControllerItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> SENTRY_GUN = ITEMS.register("sentry_gun",
            () -> new SentryTurretItem(new Item.Properties()));

    public static final DeferredItem<Item> WHEELSON_CONTROLLER = ITEMS.register("wheelson_controller",
            () -> new WheelsonControllerItem(new Item.Properties()));

    public static final DeferredItem<Item> M134_MINIGUN = ITEMS.register("m134_minigun",
            () -> new MinigunItem(new Item.Properties()));

    public static final DeferredItem<Item> CARE_PACKAGE_REWARD = ITEMS.register("care_package_reward",
            () -> new RewardCardItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> DESIGNATOR = ITEMS.register("designator",
            () -> new TacticalEquipmentItem(new Item.Properties(), "designator"));

    public static final DeferredItem<Item> DROP_MARKER = ITEMS.register("drop_marker",
            () -> new TacticalEquipmentItem(new Item.Properties(), "drop_marker"));

    public static final DeferredItem<Item> CLUSTER_MINE = ITEMS.register("cluster_mine",
            () -> new TacticalEquipmentItem(new Item.Properties(), "cluster_mine"));

    public KillstreakcodMod(IEventBus modEventBus, net.neoforged.fml.ModContainer container) {

        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, NativePresentationConfig.SPEC);
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, StreakServerConfig.SPEC,
                StreakServerConfig.FILE_NAME);

        modEventBus.addListener(StreakServerConfig::reloaded);
        ArmorKit.init();
        ArmorKit.MATERIALS.register(modEventBus);
        KillstreakContent.register(modEventBus);
        NativeAudio.init();
        StreakSounds.REGISTER.register(modEventBus);
        ITEMS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(this::registerPayloads);
        modEventBus.addListener(this::registerEntityAttributes);
        NeoForge.EVENT_BUS.register(new KillstreakService());
        OptionalIntegrations.register();
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {

        PayloadRegistrar registrar = event.registrar(NETWORK_VERSION);

        registrar.playToServer(RequestWhitelistPayload.TYPE, RequestWhitelistPayload.STREAM_CODEC,
                (p, c) -> c.enqueueWork(() -> {
                    if (c.player() instanceof ServerPlayer player)
                        FriendlyPlayers.uiRequest(player, p);
                }));

        registrar.playToServer(WhitelistActionPayload.TYPE, WhitelistActionPayload.STREAM_CODEC,
                (p, c) -> c.enqueueWork(() -> {
                    if (c.player() instanceof ServerPlayer player)
                        FriendlyPlayers.uiAction(player, p);
                }));

        registrar.playToServer(NativeVoiceFinishedPayload.TYPE, NativeVoiceFinishedPayload.STREAM_CODEC,
                (p, c) -> c.enqueueWork(() -> {
                    if (c.player() instanceof ServerPlayer player)
                        TacticalRuntime.voiceFinished(player, p.gate());
                }));

        registrar.playToServer(UseKillstreakPayload.TYPE, UseKillstreakPayload.STREAM_CODEC,
                (p, c) -> c.enqueueWork(() -> {
                    if (c.player() instanceof ServerPlayer player)
                        KillstreakService.useSlot(player, p.slot());
                }));

        registrar.playToServer(TacticalControlPayload.TYPE, TacticalControlPayload.STREAM_CODEC,
                (p, c) -> c.enqueueWork(() -> {
                    if (c.player() instanceof ServerPlayer sp)
                        TacticalRuntime.control(sp, p);
                }));

        registrar.playToServer(TacticalTargetPayload.TYPE, TacticalTargetPayload.STREAM_CODEC,
                (p, c) -> c.enqueueWork(() -> {
                    if (c.player() instanceof ServerPlayer sp)
                        TacticalRuntime.target(sp, p);
                }));

        registrar.playToServer(RequestTerminalStatePayload.TYPE, RequestTerminalStatePayload.STREAM_CODEC,
                (p, c) -> c.enqueueWork(() -> {
                    if (c.player() instanceof ServerPlayer player)
                        KillstreakService.sendTerminalState(player, p.openScreen());
                }));

        registrar.playToServer(SaveKillstreakLoadoutPayload.TYPE, SaveKillstreakLoadoutPayload.STREAM_CODEC,
                (p, c) -> c.enqueueWork(() -> {
                    if (c.player() instanceof ServerPlayer player)
                        KillstreakService.saveTerminalSelection(player, p);
                }));

        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            registrar.playToClient(WhitelistStatePayload.TYPE, WhitelistStatePayload.STREAM_CODEC, (p, c) -> {
            });
            registrar.playToClient(NativeFxPayload.TYPE, NativeFxPayload.STREAM_CODEC, (p, c) -> {
            });
            registrar.playToClient(AircraftCrashPayload.TYPE, AircraftCrashPayload.STREAM_CODEC, (p, c) -> {
            });
            registrar.playToClient(NativeVoicePayload.TYPE, NativeVoicePayload.STREAM_CODEC, (p, c) -> {
            });
            registrar.playToClient(TerminalStatePayload.TYPE, TerminalStatePayload.STREAM_CODEC, (payload, context) -> {
            });
            registrar.playToClient(TacticalViewPayload.TYPE, TacticalViewPayload.STREAM_CODEC, (p, c) -> {
            });
            registrar.playToClient(TacticalMapPayload.TYPE, TacticalMapPayload.STREAM_CODEC, (p, c) -> {
            });
            registrar.playToClient(RadarPayload.TYPE, RadarPayload.STREAM_CODEC, (p, c) -> {
            });
            registrar.playToClient(BonusStatePayload.TYPE, BonusStatePayload.STREAM_CODEC, (p, c) -> {
            });
            registrar.playToClient(StreakImpactPayload.TYPE, StreakImpactPayload.STREAM_CODEC, (p, c) -> {
            });
            registrar.playToClient(StreakEarnedPayload.TYPE, StreakEarnedPayload.STREAM_CODEC, (p, c) -> {
            });
            registrar.playToClient(TacticalTargetResultPayload.TYPE, TacticalTargetResultPayload.STREAM_CODEC,
                    (p, c) -> {
                    });
            registrar.playToClient(TacticalMapContactsPayload.TYPE, TacticalMapContactsPayload.STREAM_CODEC, (p, c) -> {
            });
            registrar.playToClient(MgbEffectPayload.TYPE, MgbEffectPayload.STREAM_CODEC, (p, c) -> {
            });
            registrar.playToClient(MgbCountdownPayload.TYPE, MgbCountdownPayload.STREAM_CODEC, (p, c) -> {
            });
            registrar.playToClient(StreakPresentationPayload.TYPE, StreakPresentationPayload.STREAM_CODEC, (p, c) -> {
            });
            registrar.playToClient(SupportAudioPayload.TYPE, SupportAudioPayload.STREAM_CODEC, (p, c) -> {
            });
            registrar.playToClient(SupportSoundPayload.TYPE, SupportSoundPayload.STREAM_CODEC, (p, c) -> {
            });
            registrar.playToClient(TracerPayload.TYPE, TracerPayload.STREAM_CODEC, (p, c) -> {
            });
        }
    }

    private void registerEntityAttributes(EntityAttributeCreationEvent event) {

        event.put(TACTICAL_UNIT.get(), Mob.createMobAttributes()
                .add(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, 100)
                .add(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, .25)
                .add(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE, 160)
                .add(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE, 1)
                .add(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT, 1).build());

        event.put(WHEELSON.get(), WheelsonEntity.createAttributes().build());
        event.put(SUPPORT_HELICOPTER.get(), Mob.createMobAttributes().build());
    }
}
