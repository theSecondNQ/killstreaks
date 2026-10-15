package net.killstreakcod.audio;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.killstreaks.TacticalUnitEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class StreakSounds {
    public static final DeferredRegister<SoundEvent> REGISTER = DeferredRegister.create(Registries.SOUND_EVENT,
            KillstreakcodMod.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> ROTOR = make("rotor"), JET = make("jet"),
            SHOT = make("shot"), EXPLOSION = make("blast"), RADIO = make("radio"), ALARM = make("alarm"),
            SPIN = make("spin"), MOTOR = make("motor");

    public static final DeferredHolder<SoundEvent, SoundEvent> TABLET_ON = make("mwii_tablet_on"),
            TABLET_SELECT = make("mwii_tablet_select"), TABLET_CONFIRM = make("mwii_tablet_confirm"),
            PHONE_UI = make("mwii_phone"), TABLET_GEAR = make("mwii_tablet_gear");

    public static final DeferredHolder<SoundEvent, SoundEvent> EARNED = make("mwii_earned"),
            EARNED_AIR = make("mwii_earned_air"), EARNED_GUNSHIP = make("mwii_earned_gunship"),
            EARNED_VIP = make("mwii_earned_vip");

    public static final DeferredHolder<SoundEvent, SoundEvent> WHEELSON_START = make("mwii_wheelson_start"),
            WHEELSON_STOP = make("mwii_wheelson_stop"), WHEELSON_ENGINE = make("mwii_wheelson_engine");

    public static final DeferredHolder<SoundEvent, SoundEvent> SENTRY_PLANT = make("mwii_sentry_plant"),
            SENTRY_LOCK = make("mwii_sentry_lock"), SENTRY_SERVO = make("mwii_sentry_servo"),
            SENTRY_PICKUP = make("mwii_sentry_pickup");

    public static final DeferredHolder<SoundEvent, SoundEvent> BOMB_DRONE_ENGINE = make("mwii_bomb_drone_engine"),
            BOMB_DRONE_ACTIVATE = make("mwii_bomb_drone_activate"), BOMB_DRONE_BLAST = make("mwii_bomb_drone_blast");

    public static final DeferredHolder<SoundEvent, SoundEvent> CHOPPER_ENGINE = make("mwii_chopper_engine"),
            CHOPPER_APPROACH = make("mwii_chopper_approach"), DELIVERY_ENGINE = make("mwii_delivery_engine"),
            GUNSHIP_ENGINE = make("mwii_gunship_engine");

    public static final DeferredHolder<SoundEvent, SoundEvent> CRUISE_ENGINE = make("mwii_cruise_engine"),
            CRUISE_BOOST = make("mwii_cruise_boost"), CRUISE_BLAST = make("mwii_cruise_blast");

    public static final DeferredHolder<SoundEvent, SoundEvent> CRUISE_NATIVE_INTRO = make(
            "iw9_cruise_missile_plr_intro"), CRUISE_NATIVE_PLAYER = make("iw9_cruise_missile_plr"),
            CRUISE_NATIVE_BLAST = make("iw9_cruise_missile_exp_static");

    public static final DeferredHolder<SoundEvent, SoundEvent> AIRSTRIKE_APPROACH = make("mwii_airstrike_approach"),
            AIRSTRIKE_FIRE = make("mwii_airstrike_fire", 256), SAE_APPROACH = make("mwii_sae_approach"),
            SAE_RELEASE = make("mwii_sae_release"), SAE_BLAST = make("mwii_sae_blast");

    public static final DeferredHolder<SoundEvent, SoundEvent> STEALTH_APPROACH = make("mwii_stealth_approach", 512),
            STEALTH_FLYBY = make("mwii_stealth_flyby", 512), STEALTH_LEAVE = make("mwii_stealth_leave", 512);

    public static final DeferredHolder<SoundEvent, SoundEvent> IMPACT_40MM = make("mwii_40mm_impact"),
            IMPACT_105MM = make("mwii_105mm_impact"), CAREPACKAGE_RETRIEVE = make("mwii_carepackage_retrieve"),
            CAREPACKAGE_RELEASE = make("mwii_carepackage_release");

    public static final DeferredHolder<SoundEvent, SoundEvent> MGB_TABLET = make("mwii_mgb_tablet"),
            MGB_SIREN = make("mwii_mgb_siren"), MGB_IMPACT = make("mwii_mgb_impact");

    public static final DeferredHolder<SoundEvent, SoundEvent> MGB_COUNTDOWN = make("mwii_mgb_countdown"),
            MGB_DRONE = make("mwii_mgb_drone"), MGB_INCOMING = make("mwii_mgb_incoming"),
            MGB_BOMBS = make("mwii_mgb_bombs_incoming"), MGB_BASS = make("mwii_mgb_impact_bass"),
            MGB_SURROUND = make("mwii_mgb_impact_surround"), MGB_FIRE = make("mwii_mgb_fire");

    public static final DeferredHolder<SoundEvent, SoundEvent> MORTAR_BLAST = make("mwii_mortar_blast", 256);

    public static boolean mortarOneShot(ResourceLocation id) {
        return id.equals(MORTAR_BLAST.get().getLocation()) || id
                .equals(ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "native.sfx.mortar_incoming"));
    }

    public static SoundEvent earned(String reward) {
        return switch (reward) {
            case "gunship" -> EARNED_GUNSHIP.get();
            case "chopper_gunner", "overwatch_helo" -> EARNED_AIR.get();
            case "juggernaut", "mgb" -> EARNED_VIP.get();
            default -> EARNED.get();
        };
    }

    /**
     * Long original loops are played by one client sound instance per tracked unit.
     * 每个被跟踪的载具由一个客户端声音实例播放较长的原资源循环音。
     */
    public static SoundEvent engine(TacticalUnitEntity.Kind kind) {
        return switch (kind) {
            case SENTRY -> SENTRY_SERVO.get();
            case WHEELSON -> WHEELSON_ENGINE.get();
            case BOMB_DRONE -> BOMB_DRONE_ENGINE.get();
            case CHOPPER, OVERWATCH -> CHOPPER_ENGINE.get();
            case DELIVERY -> DELIVERY_ENGINE.get();
            case GUNSHIP -> GUNSHIP_ENGINE.get();
            case CRUISE -> CRUISE_ENGINE.get();
            default -> null;
        };
    }

    public static SoundEvent impact(TacticalUnitEntity unit, float radius) {
        if (unit.kind() == TacticalUnitEntity.Kind.CRUISE)
            return CRUISE_BLAST.get();
        if (unit.kind() == TacticalUnitEntity.Kind.BOMB_DRONE)
            return BOMB_DRONE_BLAST.get();
        if (unit.kind() == TacticalUnitEntity.Kind.MINE)
            return NativeAudio.sfx("cluster_blast", EXPLOSION.get());
        if (unit.cargo.equals("mortar"))
            return MORTAR_BLAST.get();
        if (unit.cargo.equals("105mm"))
            return NativeAudio.sfx("aircraft105_impact", IMPACT_105MM.get());
        if (unit.cargo.equals("helo_rocket"))
            return NativeAudio.sfx("chopper_rocket_impact", EXPLOSION.get());
        if (unit.cargo.equals("sae"))
            return SAE_BLAST.get();
        if (unit.kind() == TacticalUnitEntity.Kind.GUNSHIP && radius == 5)
            return NativeAudio.sfx("aircraft40_impact", IMPACT_40MM.get());
        return EXPLOSION.get();
    }

    private static DeferredHolder<SoundEvent, SoundEvent> make(String name) {
        return REGISTER.register(name, () -> SoundEvent
                .createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, name)));
    }

    /**
     * Native JSON attenuation does not expand the server's default 16-block audience.
     * 原资源 JSON 中的衰减设置不会扩大服务端默认的 16 格声音接收范围。
     */
    private static DeferredHolder<SoundEvent, SoundEvent> make(String name, float range) {
        return REGISTER.register(name, () -> SoundEvent
                .createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, name), range));
    }

    private StreakSounds() {}
}
