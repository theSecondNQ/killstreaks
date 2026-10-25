package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.killstreaks.TacticalUnitEntity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

/** 配置值为原有武器的基础伤害；比例换算保留升级、自动射击变体和爆炸衰减。 */
public final class StreakDamage {
    public enum Channel {
        BOMB_DRONE("bombDrone", 32), CLUSTER_MINE("clusterMine", 35), MORTAR("mortar", 64),
        PRECISION("precisionAirstrike", 24), CRUISE("cruiseMissile", 180), SAE("sae", 240), SENTRY("sentryGun", 3),
        WHEELSON("wheelsonCannon", 6), WHEELSON_SPLASH("wheelsonSplash", 8), OVERWATCH("overwatchHelo", 3),
        VTOL("vtolCannon", 3.5), VTOL_ROCKET("vtolRocket", 55), VTOL_25MM("vtolHerculesCannon", 8),
        VTOL_25MM_SPLASH("vtolHerculesSplash", 12), STEALTH("stealthBomber", 100), CHOPPER("chopperCannon", 14),
        CHOPPER_SPLASH("chopperSplash", 20), CHOPPER_ROCKET("chopperRocket", 60), GUNSHIP_25MM("gunship25mm", 8),
        GUNSHIP_25MM_SPLASH("gunship25mmSplash", 12), GUNSHIP_40MM("gunship40mm", 45),
        GUNSHIP_105MM("gunship105mm", 100), GUIDED_ROCKET("guidedRocket", 55), M134("m134", 8), MGB("mgb", 100000);

        public final String id;
        public final double base;

        Channel(String id, double base) {
            this.id = id;
            this.base = base;
        }

        public String key() {
            return "damage." + id;
        }
    }

    private StreakDamage() {}

    public static float direct(ServerPlayer player, Entity emitter, float amount) {
        Channel channel = null;
        if (emitter instanceof TacticalUnitEntity unit) {
            channel = switch (unit.kind()) {
                case SENTRY -> Channel.SENTRY;
                case WHEELSON -> Channel.WHEELSON;
                case OVERWATCH -> Channel.OVERWATCH;
                case VTOL -> ResearchEffects.has(unit, "vtol_jet.hercules") ? Channel.VTOL_25MM : Channel.VTOL;
                case CHOPPER -> Channel.CHOPPER;
                case GUNSHIP -> Channel.GUNSHIP_25MM;
                default -> null;
            };
        } else if (emitter == player && (player.getMainHandItem().is(KillstreakcodMod.M134_MINIGUN.get())
                || player.getOffhandItem().is(KillstreakcodMod.M134_MINIGUN.get()))) {
            channel = Channel.M134;
        }
        return scale(player.serverLevel().getServer(), channel, amount);
    }

    public static float explosion(TacticalUnitEntity unit, float radius, float amount) {
        Channel channel = switch (unit.cargo) {
            case "sae", "sae_bomblet" -> Channel.SAE;
            case "mortar" -> Channel.MORTAR;
            case "stealth" -> Channel.STEALTH;
            case "vtol", "vtol_rocket" -> Channel.VTOL_ROCKET;
            case "105mm" -> Channel.GUNSHIP_105MM;
            case "helo_rocket" -> Channel.CHOPPER_ROCKET;
            case "guided" -> Channel.GUIDED_ROCKET;
            default -> switch (unit.kind()) {
                case BOMB_DRONE -> Channel.BOMB_DRONE;
                case MINE -> Channel.CLUSTER_MINE;
                case CRUISE -> Channel.CRUISE;
                case PRECISION -> Channel.PRECISION;
                case GUNSHIP -> radius <= 3 ? Channel.GUNSHIP_25MM_SPLASH : Channel.GUNSHIP_40MM;
                case CHOPPER -> Channel.CHOPPER_SPLASH;
                case WHEELSON -> Channel.WHEELSON_SPLASH;
                case VTOL -> ResearchEffects.has(unit, "vtol_jet.hercules") ? Channel.VTOL_25MM_SPLASH : null;
                default -> null;
            };
        };
        return unit.level() instanceof net.minecraft.server.level.ServerLevel level
                ? scale(level.getServer(), channel, amount)
                : amount;
    }

    private static float scale(MinecraftServer server, Channel channel, float amount) {
        if (channel == null || amount <= 0)
            return amount;
        return (float) (amount * StreakServerConfig.number(server, channel.key()) / channel.base);
    }
}
