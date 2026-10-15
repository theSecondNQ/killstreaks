package net.killstreakcod.universal.compat;

import net.killstreakcod.killstreaks.TacticalUnitEntity;
import net.killstreakcod.universal.KillstreakService;
import net.killstreakcod.universal.TacticalRuntime;

import java.lang.reflect.Method;
import java.util.function.Consumer;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

/**
 * Optional public API bridges. No third-party classes are linked when their mods are absent.
 * 通过公开 API 对接可选模组，未安装时不链接其第三方类。
 */
public final class OptionalIntegrations {
    private OptionalIntegrations() {}

    public static void register() {
        registerSuperbWarfare();
        if (!ModList.get().isLoaded("tacz"))
            return;
        try {
            Class<? extends Event> kill = eventClass("EntityKillByGunEvent");
            Method attacker = kill.getMethod("getAttacker"), victim = kill.getMethod("getKilledEntity"),
                    side = kill.getMethod("getLogicalSide");
            listen(kill, EventPriority.LOWEST, event -> {
                try {
                    if (side.invoke(event) == LogicalSide.SERVER
                            && attacker.invoke(event) instanceof ServerPlayer player
                            && victim.invoke(event) instanceof LivingEntity killed)
                        KillstreakService.queueKill(player, killed, false);
                }
                catch (ReflectiveOperationException failure) {
                    LogUtils.getLogger().error("TaCZ kill event bridge failed", failure);
                }
            });
            for (String name : new String[] { "GunShootEvent", "GunFireEvent", "GunReloadEvent", "GunMeleeEvent" })
                registerControlGuard(name);
            LogUtils.getLogger().info("Killstreaks: TaCZ {} kill accounting and controller guards enabled",
                    ModList.get().getModContainerById("tacz").orElseThrow().getModInfo().getVersion());
        }
        catch (ReflectiveOperationException | LinkageError failure) {
            LogUtils.getLogger().error("Could not initialize optional TaCZ compatibility", failure);
        }
    }

    private static void registerSuperbWarfare() {
        if (!ModList.get().isLoaded("superbwarfare"))
            return;
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, EntityLeaveLevelEvent.class, event -> {
            if (event.getLevel().isClientSide || !SuperbWarfareCompat.destroyed(event.getEntity()))
                return;
            DamageSource source = SuperbWarfareCompat.lastDamage(event.getEntity());
            if (source != null && source.getEntity() instanceof ServerPlayer player)
                KillstreakService.queueVehicleKill(player, event.getEntity(),
                        source.getDirectEntity() instanceof TacticalUnitEntity);
        });
        try {
            Class<? extends Event> type = Class
                    .forName("com.atsuishio.superbwarfare.api.event.PreKillEvent$SendKillMessage")
                    .asSubclass(Event.class);
            Method source = type.getMethod("getSource"), victim = type.getMethod("getTarget"),
                    killer = type.getMethod("getEntity");
            listen(type, EventPriority.LOWEST, event -> {
                try {
                    if (source.invoke(event) instanceof DamageSource damage
                            && victim.invoke(event) instanceof LivingEntity target && !target.level().isClientSide) {
                        Object attacker = damage.getEntity() instanceof ServerPlayer ? damage.getEntity()
                                : killer.invoke(event);
                        if (attacker instanceof ServerPlayer player)
                            KillstreakService.queueKill(player, target,
                                    damage.getDirectEntity() instanceof TacticalUnitEntity);
                    }
                }
                catch (ReflectiveOperationException failure) {
                    LogUtils.getLogger().error("Superb Warfare kill bridge failed", failure);
                }
            });
        }
        catch (ReflectiveOperationException | LinkageError failure) {
            LogUtils.getLogger().warn(
                    "Superb Warfare kill message bridge unavailable; normal death accounting remains active", failure);
        }
    }

    private static Class<? extends Event> eventClass(String name) throws ClassNotFoundException {
        return Class.forName("com.tacz.guns.api.event.common." + name).asSubclass(Event.class);
    }

    private static void listen(Class<? extends Event> type, EventPriority priority, Consumer<Event> consumer) {
        NeoForge.EVENT_BUS.addListener(priority, false, type, consumer::accept);
    }

    private static void registerControlGuard(String name) throws ReflectiveOperationException {
        Class<? extends Event> type = eventClass(name);
        Method shooter = type.getMethod(name.equals("GunReloadEvent") ? "getEntity" : "getShooter"),
                side = type.getMethod("getLogicalSide");
        listen(type, EventPriority.HIGHEST, event -> {
            try {
                if (side.invoke(event) == LogicalSide.SERVER && shooter.invoke(event) instanceof ServerPlayer player
                        && (TacticalRuntime.busy(player) || !player.isAlive()
                                || TacticalRuntime.mgbDetonating(player.serverLevel()))
                        && event instanceof ICancellableEvent cancelable)
                    cancelable.setCanceled(true);
            }
            catch (ReflectiveOperationException failure) {
                LogUtils.getLogger().error("TaCZ controller guard failed", failure);
            }
        });
    }
}
