package net.killstreakcod.universal;

/**
 * Shared values are Minecraft balancing values, not extracted IW engine data.
 * 这些共享参数是 Minecraft 玩法平衡值，不是从 IW 引擎提取的原始数据。
 */
public final class StreakWeaponProfiles {
    public static final int[] MAGAZINE = { 30, 5, 1 };
    public static final int[] RELOAD_TICKS = { 60, 80, 120 };
    public static final int[] FIRE_INTERVAL = { 2, 12, 1 };
    public static final int CHOPPER_FIRE_INTERVAL = 3;
    public static final float CHOPPER_DIRECT_DAMAGE = 14, CHOPPER_BLAST_DAMAGE = 20;

    private StreakWeaponProfiles() {}
}
