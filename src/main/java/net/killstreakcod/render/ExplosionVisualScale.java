package net.killstreakcod.render;

/**
 * Client sprite dimensions only; payload size, damage, particle budgets and debris stay independent.
 * 这里只调整客户端精灵尺寸，消息大小、伤害、粒子预算和碎片分别处理。
 */
public final class ExplosionVisualScale {
    private ExplosionVisualScale() {}

    static float multiplier(String profile) {
        return switch (profile) {
            case "aircraft25_25_gen", "aircraft25_25_dirt", "aircraft25_25_wood", "aircraft25_25_metal",
                    "aircraft25_25_glass", "aircraft25_25_water",
                    "aircraft25_gunship105_impact", "aircraft25_gunship105_water" ->
                2.25F;
            case "aircraft25_40_gen", "aircraft25_40_dirt", "aircraft25_40_wood", "aircraft25_40_metal",
                    "aircraft25_40_water" ->
                3.25F;
            case "aircraft25_apache_impact", "aircraft25_apache_water", "sae21_impact" -> 2F;
            case "cruise", "explosion" -> 1.8F;
            case "uav", "advanced_uav", "helicopter", "equipment", "sentry", "mine",
                    "crash_66417eeab994f07a", "crash_13ffee139ea96474", "crash_19e82a440e9fe95d",
                    "crash_1ffaacab51081351" ->
                1.75F;
            default -> 1F;
        };
    }
}
