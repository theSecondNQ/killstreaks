package net.killstreakcod.killstreaks;

import net.killstreakcod.KillstreakcodMod;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import net.minecraft.network.chat.Component;

public enum StreakReward {
    UAV("uav", 4, 500),
    BOMB_DRONE("bomb_drone", 4, 500),
    COUNTER_UAV("counter_uav", 5, 625),
    CLUSTER_MINE("cluster_mine", 5, 625),
    CARE_PACKAGE("care_package", 5, 625),
    PRECISION_AIRSTRIKE("precision_airstrike", 6, 750),
    CRUISE_MISSILE("cruise_missile", 6, 750),
    MORTAR_STRIKE("mortar_strike", 6, 750),
    SENTRY_GUN("sentry_gun", 7, 875),
    SAE("sae", 7, 875),
    VTOL_JET("vtol_jet", 8, 1000),
    OVERWATCH_HELO("overwatch_helo", 8, 1000),
    WHEELSON("wheelson_hs", 8, 1000),
    STEALTH_BOMBER("stealth_bomber", 10, 1250),
    CHOPPER_GUNNER("chopper_gunner", 10, 1250),
    EMERGENCY_AIRDROP("emergency_airdrop", 10, 1250),
    GUNSHIP("gunship", 12, 1500),
    ADVANCED_UAV("advanced_uav", 12, 1500),
    JUGGERNAUT("juggernaut", 15, 1875);

    public static final List<StreakReward> SELECTABLE = List.copyOf(Arrays.asList(values()));
    private static final Map<String, StreakReward> BY_ID = SELECTABLE.stream()
            .collect(Collectors.toUnmodifiableMap(StreakReward::id, reward -> reward));

    private final String id;
    private final int kills;
    private final int score;

    StreakReward(String id, int kills, int score) {
        this.id = id;
        this.kills = kills;
        this.score = score;
    }

    public String id() {
        return id;
    }

    public int kills() {
        return kills;
    }

    public int score() {
        return score;
    }

    public Component displayName() {
        return Component.translatable("streak." + KillstreakcodMod.MODID + "." + id);
    }

    public static Optional<StreakReward> byId(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(BY_ID.get(id.toLowerCase(Locale.ROOT)));
    }
}
