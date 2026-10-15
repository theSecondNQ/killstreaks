package net.killstreakcod.universal;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.audio.NativeAudio;
import net.killstreakcod.audio.StreakSounds;
import net.killstreakcod.audio.StrikeRadioReports;
import net.killstreakcod.audio.SupportAudio;
import net.killstreakcod.audio.SupportSoundPayload;
import net.killstreakcod.audio.VoiceFaction;
import net.killstreakcod.killstreaks.ArmorKit;
import net.killstreakcod.killstreaks.ChopperIngress;
import net.killstreakcod.killstreaks.GunshipFlight;
import net.killstreakcod.killstreaks.MortarStrikeFlight;
import net.killstreakcod.killstreaks.NativeCruiseFlight;
import net.killstreakcod.killstreaks.PrecisionFlight;
import net.killstreakcod.killstreaks.SaeEffects;
import net.killstreakcod.killstreaks.StreakReward;
import net.killstreakcod.killstreaks.SupportHelicopterEntity;
import net.killstreakcod.killstreaks.TacticalEquipmentItem;
import net.killstreakcod.killstreaks.TacticalUnitEntity;
import net.killstreakcod.killstreaks.WheelsonMount;
import net.killstreakcod.render.WheelsonView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import static net.killstreakcod.killstreaks.TacticalUnitEntity.Kind;
import static net.killstreakcod.universal.TacticalControlPayload.*;

/**
 * 服务端连杀调度：维护呼叫、标定和遥控会话，并推进载具、弹道、侦察与核弹状态。 客户端只提交操作意图，部署和伤害由这里的服务端状态决定。
 */
public final class TacticalRuntime {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Pending> TARGETS = new HashMap<>();
    private static final Map<UUID, CallIn> CALLS = new HashMap<>();
    private static final Map<ServerLevel, NuclearStrike> NUKES = new HashMap<>();
    private static MinecraftServer lastServer;
    private static final TicketType<UUID> UNIT_TICKET = TicketType.create("killstreakcod_support", UUID::compareTo, 40);

    private TacticalRuntime() {}

    public static boolean busy(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID()) || TARGETS.containsKey(player.getUUID())
                || CALLS.containsKey(player.getUUID());
    }

    public static boolean mgbActive(ServerLevel level) {
        return NUKES.containsKey(level)
                || CALLS.values().stream().anyMatch(callIn -> callIn.nuclear && callIn.level == level);
    }

    /**
     * No new controller may attach between the nuclear flash and its lethal tick.
     * 核爆闪光开始到致命伤害结算之间，不允许接入新的控制器。
     */
    public static boolean mgbDetonating(ServerLevel level) {
        NuclearStrike strike = NUKES.get(level);
        return strike != null && strike.remaining <= 0;
    }

    public static boolean controlled(TacticalUnitEntity unit) {
        Session session = SESSIONS.get(unit.ownerId);
        return session != null && session.unit == unit;
    }

    public static void deploy(ServerPlayer player, StreakReward reward, boolean bonus) {
        if (!StreakServerConfig.requireReward(player, reward.id()))
            return;
        if (reward == StreakReward.WHEELSON && wheelsonPlacement(player) == null) {
            KillstreakService.refund(player, reward, bonus);
            player.displayClientMessage(Component.translatable("message.killstreakcod.wheelson_blocked"), true);
            return;
        }
        if (instantEquipment(reward)) {
            player.stopUsingItem();
            StreakNetwork.send(player, new StreakPresentationPayload("", "reset", 0));
            deployNow(player, reward, bonus);
            return;
        }
        int duration = StreakTimings.call(reward.id());
        queueCall(new CallIn(player, player.serverLevel(), reward, bonus, false,
                player.serverLevel().getGameTime() + duration));
        player.stopUsingItem();
        StreakNetwork.send(player, new StreakPresentationPayload(reward.id(), "call", duration));
    }

    private static boolean instantEquipment(StreakReward reward) {
        return switch (reward) {
            case CLUSTER_MINE, CARE_PACKAGE, EMERGENCY_AIRDROP, MORTAR_STRIKE, JUGGERNAUT, PRECISION_AIRSTRIKE -> true;
            default -> false;
        };
    }

    private static boolean equipmentOnly(StreakReward reward) {
        return reward == StreakReward.PRECISION_AIRSTRIKE || reward == StreakReward.MORTAR_STRIKE
                || reward == StreakReward.CARE_PACKAGE || reward == StreakReward.EMERGENCY_AIRDROP
                || reward == StreakReward.JUGGERNAUT || reward == StreakReward.SENTRY_GUN
                || reward == StreakReward.WHEELSON || reward == StreakReward.CLUSTER_MINE || reward == StreakReward.SAE
                || reward == StreakReward.VTOL_JET || reward == StreakReward.STEALTH_BOMBER;
    }

    private static void queueCall(CallIn call) {
        CALLS.put(call.player.getUUID(), call);
        boolean remote = call.unit != null || call.reward != null && StreakTimings.remoteReward(call.reward.id());
        boolean phone = call.reward != null && StreakTimings.phoneReward(call.reward.id());
        if (!remote && !phone && (call.action != null || call.nuclear || !equipmentOnly(call.reward))) {
            String reward = call.nuclear ? "mgb" : call.reward.id();
            int delay = (int) Math.max(0, call.due - call.level.getGameTime());
            call.audioDone = !NativeAudio.gate(call.player, reward, call.gate, delay);
            call.deadline = call.due + NativeAudio.voiceTicks(reward, "response", VoiceFaction.get(call.player)) + 300;
        }
    }

    public static void voiceFinished(ServerPlayer player, UUID gate) {
        CallIn call = CALLS.get(player.getUUID());
        if (call != null && call.player == player && call.level == player.serverLevel() && call.gate.equals(gate))
            call.audioDone = true;
    }

    private static void refundUnavailableCall(CallIn call) {
        if (call.unit != null || call.reward == null)
            return;
        if (call.action != null && instantEquipment(call.reward))
            KillstreakService
                    .giveItem(call.player,
                            TacticalEquipmentItem.stack(
                                    call.reward == StreakReward.PRECISION_AIRSTRIKE
                                            || call.reward == StreakReward.MORTAR_STRIKE ? "designator" : "drop_marker",
                                    call.reward.id()));
        else
            KillstreakService.refund(call.player, call.reward, call.bonus);
        KillstreakService.sendTerminalState(call.player, false);
    }

    private static String rewardId(Kind kind) {
        return switch (kind) {
            case CRUISE -> "cruise_missile";
            case CHOPPER -> "chopper_gunner";
            case WHEELSON -> "wheelson_hs";
            default -> kind.name().toLowerCase(Locale.ROOT);
        };
    }

    private static void deployNow(ServerPlayer player, StreakReward reward, boolean bonus) {
        if (reward != StreakReward.SAE && reward != StreakReward.VTOL_JET && reward != StreakReward.STEALTH_BOMBER
                && reward != StreakReward.PRECISION_AIRSTRIKE && reward != StreakReward.MORTAR_STRIKE
                && reward != StreakReward.CARE_PACKAGE && reward != StreakReward.EMERGENCY_AIRDROP
                && reward != StreakReward.JUGGERNAUT && reward != StreakReward.SENTRY_GUN
                && reward != StreakReward.WHEELSON && reward != StreakReward.CLUSTER_MINE)
            NativeAudio.call(player, reward.id());
        player.displayClientMessage(Component.translatable("message.killstreakcod.called_in", reward.displayName()),
                true);
        switch (reward) {
            case UAV ->
                spawn(player, Kind.UAV, player.position().add(0, TacticalUnitEntity.UAV_HEIGHT, 0), player.position(),
                        600);
            case COUNTER_UAV -> spawn(player, Kind.COUNTER_UAV,
                    player.position().add(0, TacticalUnitEntity.COUNTER_UAV_HEIGHT, 0), player.position(), 600);
            case ADVANCED_UAV -> spawn(player, Kind.ADVANCED_UAV,
                    player.position().add(0, TacticalUnitEntity.ADVANCED_UAV_HEIGHT, 0), player.position(), 600);
            case OVERWATCH_HELO ->
                spawn(player, Kind.OVERWATCH, player.position().add(-55, 30, 0), player.position(), 1200);
            case BOMB_DRONE -> startSession(player, spawn(player, Kind.BOMB_DRONE,
                    player.getEyePosition().add(player.getLookAngle().scale(2)), player.position(), 600));
            case CRUISE_MISSILE -> {
                TacticalUnitEntity unit = spawn(player, Kind.CRUISE,
                        NativeCruiseFlight.launchOrigin(player, player.position()), player.position(), 300);
                unit.setYRot(player.getYRot());
                unit.setXRot(60);
                startSession(player, unit);
            }
            case CHOPPER_GUNNER -> startSession(player,
                    spawn(player, Kind.CHOPPER, ChopperIngress.spawnPoint(player), player.position(), 1200));
            case GUNSHIP -> startSession(player, spawn(player, Kind.GUNSHIP,
                    GunshipFlight.position(player.position(), player.getYRot(), 0), player.position(), 1200));
            case PRECISION_AIRSTRIKE ->
                KillstreakService.giveItem(player, TacticalEquipmentItem.stack("designator", reward.id()));
            case MORTAR_STRIKE -> equipMortarDesignator(player);
            case CARE_PACKAGE, EMERGENCY_AIRDROP, JUGGERNAUT ->
                KillstreakService.giveItem(player, TacticalEquipmentItem.stack("drop_marker", reward.id()));
            case CLUSTER_MINE ->
                KillstreakService.giveItem(player, TacticalEquipmentItem.stack("cluster_mine", reward.id()));
            case SENTRY_GUN ->
                KillstreakService.giveItem(player, KillstreakcodMod.SENTRY_GUN.get().getDefaultInstance());
            case WHEELSON -> {
                Vec3 at = wheelsonPlacement(player);
                if (at == null) {
                    KillstreakService.refund(player, reward, bonus);
                    StreakNetwork.send(player, new StreakPresentationPayload("", "reset", 0));
                    player.displayClientMessage(Component.translatable("message.killstreakcod.wheelson_blocked"), true);
                } else {
                    var rover = spawn(player, Kind.WHEELSON, at, player.position(), 2400);
                    rover.addTag(KillstreakService.WHEELSON_TAG);
                    startSession(player, rover);
                }
            }
            case SAE, VTOL_JET, STEALTH_BOMBER -> openMap(player, reward, bonus);
        }

        // 侦察类在手机动作结束的同一刻部署并同步播放响应语音，不在部署前等待语音，也不重复播报。
        if (StreakTimings.phoneReward(reward.id())) {
            SupportAudio.sendSnapshot(player);
            NativeAudio.voice(player, reward.id(), "response_parallel");
        }
    }

    public static TacticalUnitEntity spawn(ServerPlayer player, Kind kind, Vec3 pos, Vec3 center, int life) {
        TacticalUnitEntity unit = spawnConfigured(player, kind, pos, center, life, player.getYRot(), 0, "", Vec3.ZERO);
        if (kind == Kind.OVERWATCH)
            StrikeRadioReports.track(unit, StrikeRadioReports.begin(player, "overwatch_helo"));
        return unit;
    }

    public static TacticalUnitEntity spawnChild(TacticalUnitEntity parent, Kind kind, Vec3 pos, Vec3 center, int life,
            float heading, int delay, String cargo, Vec3 velocity) {
        return StrikeRadioReports.track(
                spawnConfigured(parent.owner(), kind, pos, center, life, heading, delay, cargo, velocity),
                parent.radioStrike);
    }

    private static void equipMortarDesignator(ServerPlayer player) {
        KillstreakService.giveItem(player, TacticalEquipmentItem.stack("designator", "mortar_strike"));
        var inventory = player.getInventory();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(KillstreakcodMod.DESIGNATOR.get()) || !stack
                    .getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                            net.minecraft.world.item.component.CustomData.EMPTY)
                    .copyTag().getString("Reward").equals("mortar_strike"))
                continue;
            if (slot < 9)
                inventory.selected = slot;
            else {
                ItemStack previous = inventory.getItem(inventory.selected);
                inventory.setItem(slot, previous);
                inventory.setItem(inventory.selected, stack);
            }
            inventory.setChanged();
            player.inventoryMenu.broadcastChanges();
            player.connection
                    .send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(inventory.selected));
            return;
        }
    }

    public static TacticalUnitEntity spawnConfigured(ServerPlayer player, Kind kind, Vec3 pos, Vec3 center, int life,
            float heading, int delay, String cargo, Vec3 velocity) {
        int lift = AircraftFlight.aircraft(kind) || kind == Kind.CRUISE
                ? AircraftAirspace.selectLift(player, kind, center, heading)
                : 0;
        return spawnConfigured(player, kind, pos, center, life, heading, delay, cargo, velocity, lift);
    }

    /**
     * Continuation hulls inherit the approved mission altitude, including both VTOL passes.
     * 后续机体沿用已批准的任务高度，包括 VTOL 的两次航段。
     */
    public static TacticalUnitEntity spawnConfigured(ServerPlayer player, Kind kind, Vec3 pos, Vec3 center, int life,
            float heading, int delay, String cargo, Vec3 velocity, int lift) {
        ServerLevel level = player.serverLevel();
        TacticalUnitEntity unit = new TacticalUnitEntity(KillstreakcodMod.TACTICAL_UNIT.get(), level);
        unit.kind(kind);
        unit.flightLift = Math.max(0, lift);
        unit.ownerId = player.getUUID();
        unit.center = center;
        unit.lifetime = life;
        unit.heading = heading;
        unit.delay = delay;
        unit.cargo = cargo;
        if (!StreakAirspace.permitted(level, kind)) {
            unit.discard();
            return unit;
        }
        if (lift < 0) {
            unit.discard();
            return unit;
        }
        // Cargo is server-only; native projectile meshes need an explicit synced variant at spawn.
        // 中文：cargo 仅供服务端使用，原弹体网格需要在生成时显式同步变体。
        if (kind == Kind.BOMB && cargo.equals("mortar"))
            unit.stage(3);
        if (kind == Kind.BOMB && cargo.equals("sae_parent"))
            unit.stage(4);
        if (kind == Kind.BOMB && cargo.equals("sae_bomblet"))
            unit.stage(5);
        if (kind == Kind.BOMB && cargo.equals("stealth"))
            unit.stage(6);
        if (kind == Kind.BOMB && delay > 0 && unit.stage() >= 3)
            unit.stage(-unit.stage());
        if (kind == Kind.PRECISION && delay > 0)
            unit.stage(-9);
        if (kind == Kind.ROCKET)
            unit.stage(cargo.equals("105mm") ? 8 : cargo.equals("vtol_rocket") ? 10 : 7);
        unit.moveTo(pos.x,
                kind == Kind.CRUISE ? NativeCruiseFlight.clamp(level, pos).y
                        : Mth.clamp(pos.y, level.getMinBuildHeight() + 1, level.getMaxBuildHeight() - 3),
                pos.z, heading, 0);
        unit.setDeltaMovement(velocity);
        unit.initializeFlight();
        unit.setOldPosAndRot();
        double health = switch (kind) {
            case UAV, COUNTER_UAV -> 40;
            case BOMB_DRONE, CLUSTER, MINE -> 12;
            case SENTRY -> 100;
            case WHEELSON -> 160;
            case VTOL, OVERWATCH -> 180;
            case CHOPPER -> 300;
            case GUNSHIP -> 450;
            default -> 80;
        };
        unit.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        unit.setHealth((float) health);
        keepLoaded(unit, unit.position());
        level.addFreshEntity(unit);
        switch (kind) {
            case SENTRY -> {
                NativeAudio.call(player, "sentry_gun");
                sound(level, unit.position(), StreakSounds.SENTRY_PLANT.get(), .8F, 1);
            }
            case WHEELSON -> {
                NativeAudio.call(player, "wheelson_hs");
                sound(level, unit.position(), StreakSounds.WHEELSON_START.get(), .7F, 1);
            }
            case CLUSTER -> NativeAudio.call(player, "cluster_mine");
            default -> {
            }
        }
        return unit;
    }

    public static void keepLoaded(TacticalUnitEntity unit, Vec3 at) {
        if (unit.level() instanceof ServerLevel l) {
            ChunkPos chunk = new ChunkPos(BlockPos.containing(at));
            l.getChunkSource().addRegionTicket(UNIT_TICKET, chunk, 3, unit.getUUID(), true);
            l.getChunk(chunk.x, chunk.z);
        }
    }

    /**
     * Nearby solid ground, within one block vertically; never teleport a rover onto a roof.
     * 寻找附近垂直差不超过一格的坚实地面，不把无人车传送到屋顶。
     */
    private static Vec3 wheelsonPlacement(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (int offset : new int[] { 0, 90, -90, 180, 45, -45, 135, -135 }) {
            Vec3 horizontal = player.position()
                    .add(Vec3.directionFromRotation(0, player.getYRot() + offset).scale(2.5));
            for (double dy : new double[] { 0, .5, -.5, 1, -1 }) {
                Vec3 at = horizontal.add(0, dy, 0);
                AABB hull = new AABB(at.add(-.85, 0, -.85), at.add(.85, 1.05, .85));
                if (level.noCollision(hull) && !level.noCollision(hull.move(0, -.15, 0))
                        && !level.getBlockState(BlockPos.containing(at)).liquid())
                    return at;
            }
        }
        return null;
    }

    /**
     * Wheelson equipment/interaction links through the same tablet call as the air controllers.
     * 遥控战车的装备及交互通过与空中遥控载具相同的平板呼叫流程连接。
     */
    public static void linkControl(ServerPlayer player, TacticalUnitEntity unit) {
        if (!player.isAlive() || player.isSpectator() || mgbDetonating(player.serverLevel()) || busy(player)
                || unit == null || unit.isRemoved() || unit.level() != player.level()
                || !player.getUUID().equals(unit.ownerId))
            return;
        unit.autopilot = false;
        player.stopUsingItem();
        queueCall(new CallIn(player, player.serverLevel(), StreakReward.WHEELSON, false, false,
                player.serverLevel().getGameTime() + StreakTimings.REMOTE_CALL, unit));
        StreakNetwork.send(player, new StreakPresentationPayload("wheelson_hs", "call", StreakTimings.REMOTE_CALL));
    }

    public static void startSession(ServerPlayer player, TacticalUnitEntity unit) {
        if (!player.isAlive() || player.isSpectator() || mgbDetonating(player.serverLevel()) || busy(player)
                || unit == null || unit.isRemoved())
            return;
        Session session = new Session(player, unit, player.position());
        session.yaw = player.getYRot();
        session.pitch = unit.kind() == Kind.CRUISE ? 60 : unit.kind() == Kind.WHEELSON ? 15 : 0;
        if (unit.kind() == Kind.GUNSHIP || unit.kind() == Kind.CHOPPER) {
            Vec3 aim = unit.center.subtract(unit.getEyePosition());
            session.yaw = (float) (Math.atan2(-aim.x, aim.z) * 180 / Math.PI);
            session.pitch = (float) (-Math.atan2(aim.y, aim.horizontalDistance()) * 180 / Math.PI);
        }
        if (unit.kind() == Kind.GUNSHIP) {
            session.yaw = GunshipFlight.mountYaw(unit.flightYaw());
            session.pitch = GunshipFlight.clampPitch(session.pitch);
        }
        if (unit.kind() == Kind.WHEELSON)
            unit.center = player.position();
        int intro = StreakTimings.entry(rewardId(unit.kind()));
        if (unit.kind() == Kind.CRUISE) {
            double room = unit.getY() - ground(player.serverLevel(), unit.position()).y - 18;
            session.cruiseIntroPitch = NativeCruiseFlight.pitch(room);
            session.yaw = unit.heading;
            session.pitch = session.cruiseIntroPitch;
            unit.flightOrientation(unit.heading, session.cruiseIntroPitch);
            unit.cruiseClock(0);
        }
        session.introUntil = player.serverLevel().getGameTime() + intro;
        SESSIONS.put(player.getUUID(), session);
        unit.autopilot = false;
        if (unit.radioStrike == null)
            StrikeRadioReports.track(unit, StrikeRadioReports.begin(player, NativeAudio.reward(unit)));
        RemoteViewSubscriptions.begin(player, unit);
        NativeAudio.voice(player, rewardId(unit.kind()), "response_parallel");
        StreakNetwork.send(player, new StreakPresentationPayload(rewardId(unit.kind()), "entry", intro));
        sendView(session);
        player.stopUsingItem();
        player.setNoGravity(true);
        player.setDeltaMovement(Vec3.ZERO);
        player.setItemSlot(EquipmentSlot.OFFHAND, KillstreakcodMod.CONTROLLER.get().getDefaultInstance());
    }

    public static void control(ServerPlayer player, TacticalControlPayload in) {
        if (!Float.isFinite(in.yaw()) || !Float.isFinite(in.pitch()) || !Float.isFinite(in.forward())
                || !Float.isFinite(in.strafe()))
            return;
        Session session = SESSIONS.get(player.getUUID());
        if (session != null && session.player != player) {
            abortSession(session.player);
            return;
        }
        if (session == null) {
            if (in.sequence() == 0 && (in.flags() & BOOST) != 0)
                for (Entity entity : player.serverLevel().getAllEntities())
                    if (entity instanceof TacticalUnitEntity e && e.kind() == Kind.VTOL && e.onStation()
                            && player.getUUID().equals(e.ownerId)) {
                        e.center = player.position();
                        player.displayClientMessage(Component.translatable("message.killstreakcod.vtol_move"), true);
                    }
            return;
        }
        if (in.sequence() != 0 && in.sequence() <= session.lastSequence)
            return;
        if (in.sequence() != 0)
            session.lastSequence = in.sequence();
        session.lastInput = player.serverLevel().getGameTime();
        session.yaw = session.unit.kind() == Kind.GUNSHIP ? GunshipFlight.clampYaw(in.yaw(), session.unit.flightYaw())
                : Mth.wrapDegrees(in.yaw());
        session.pitch = session.unit.kind() == Kind.GUNSHIP ? GunshipFlight.clampPitch(in.pitch())
                : session.unit.kind() == Kind.CRUISE ? Mth.wrapDegrees(in.pitch())
                        : Mth.clamp(in.pitch(), session.unit.kind() == Kind.WHEELSON ? -45 : -85,
                                session.unit.kind() == Kind.WHEELSON ? 60 : 88);
        session.forward = Mth.clamp(in.forward(), -1, 1);
        session.strafe = Mth.clamp(in.strafe(), -1, 1);
        session.flags = in.flags() & HELD_MASK;
        session.actions |= in.flags() & ACTION_MASK;
    }

    public static void endSession(ServerPlayer player, boolean auto) {
        endSession(player, auto, true);
    }

    /**
     * Death, disconnection and dimension changes must clear gestures instead of starting a new exit pose.
     * 死亡、断线或切换维度时直接清理手势，不能再启动新的收起姿态。
     */
    public static void abortSession(ServerPlayer player) {
        endSession(player, false, false);
    }

    /**
     * Release only the operator of this wreck; a later reward session must remain attached.
     * 只释放这架残骸对应的操作者，之后建立的奖励会话必须保持连接。
     */
    static void releaseDestroyedControl(TacticalUnitEntity unit, boolean animate) {
        for (Session session : List.copyOf(SESSIONS.values()))
            if (session.unit == unit)
                endSession(session.player, false, animate);
    }

    private static void endSession(ServerPlayer player, boolean auto, boolean animate) {
        Session existing = SESSIONS.get(player.getUUID());

        // 上一条生命迟到的回调不能断开重生后玩家的控制器。
        if (existing != null && existing.player != player
                && player.serverLevel().getServer().getPlayerList().getPlayer(player.getUUID()) != player)
            return;
        CallIn activeCall = CALLS.get(player.getUUID());
        if (activeCall != null && activeCall.player != player
                && player.serverLevel().getServer().getPlayerList().getPlayer(player.getUUID()) != player)
            return;
        CallIn call = CALLS.remove(player.getUUID());
        Session session = SESSIONS.remove(player.getUUID());
        Pending pending = TARGETS.remove(player.getUUID());
        ServerPlayer body = session == null ? player : session.player;
        animate &= player.isAlive() && !player.isSpectator() && player.connection != null
                && player.connection.isAcceptingMessages()
                && (call == null || call.level == player.serverLevel())
                && (session == null || session.unit.level() == player.serverLevel())
                && (pending == null || pending.level == player.serverLevel());
        if (!animate)
            StreakNetwork.send(player, new StreakPresentationPayload("", "reset", 0));

        // 在死亡掉落、实体移除或区块观察回调前，先恢复玩家原本的本体状态。
        if (session != null) {
            body.stopUsingItem();
            body.setNoGravity(session.previousGravity);
            body.setDeltaMovement(Vec3.ZERO);
            body.setItemSlot(EquipmentSlot.OFFHAND, session.previousOffhand);
        }
        StreakNetwork.send(player, new TacticalViewPayload(-1, "", 0, 0, 0, false));

        // 放下设备的过场属于已恢复玩家的第一人称视角，不属于载具摄像机。
        if (animate && session != null)
            StreakNetwork.send(player, new StreakPresentationPayload(rewardId(session.unit.kind()), "exit",
                    StreakTimings.exit(rewardId(session.unit.kind()))));
        else if (animate && call != null)
            StreakNetwork.send(player,
                    new StreakPresentationPayload(call.nuclear ? "mgb" : call.reward.id(), "exit", 6));
        try {
            RemoteViewSubscriptions.end(body);
        } finally {
            if (session != null && !session.unit.isRemoved()) {
                if (session.unit.crashing())
                    session.unit.setDeltaMovement(Vec3.ZERO);
                else if (session.unit.kind() == Kind.WHEELSON) {
                    session.unit.autopilot = auto && body.isAlive();
                    session.unit.setDeltaMovement(Vec3.ZERO);
                } else if (animate && (session.unit.kind() == Kind.CHOPPER || session.unit.kind() == Kind.GUNSHIP))
                    session.unit.beginDeparture();
                else {
                    try {
                        if (animate && session.unit.kind() == Kind.BOMB_DRONE)
                            blast(session.unit, session.unit.position(), 5, 32);
                    } finally {
                        session.unit.discard();
                    }
                }
            }
        }
        if (call != null && call.unit == null) {
            if (call.nuclear)
                KillstreakService.refundMgb(player);
            else
                KillstreakService.refund(player, call.reward, call.bonus);
        }
        if (pending != null) {
            clearMapContacts(player, pending);
            KillstreakService.refund(player, pending.reward, pending.bonus);
        }
        if (session != null && !session.unit.departing())
            StrikeRadioReports.endControl(session.unit);
    }

    private static void sendView(Session session) {
        if (SESSIONS.get(session.player.getUUID()) != session || !session.player.isAlive() || session.unit.isRemoved()
                || session.player.serverLevel().getServer().getPlayerList()
                        .getPlayer(session.player.getUUID()) != session.player)
            return;
        long now = session.player.serverLevel().getGameTime();
        int[] reloads = new int[3];
        for (int i = 0; i < 3; i++)
            reloads[i] = (int) Math.max(0, session.gunshipReload[i] - now);
        int cooldown = session.unit.kind() == Kind.GUNSHIP
                ? Math.max(reloads[session.weapon], (int) Math.max(0, session.gunshipNextShot[session.weapon] - now))
                : (int) Math.max(0, session.nextShot - now);
        StreakNetwork.send(session.player,
                new TacticalViewPayload(session.unit.getId(), session.unit.kind().name().toLowerCase(Locale.ROOT),
                        Math.max(0, session.unit.lifetime - session.unit.age), session.weapon,
                        (int) (session.unit.getHealth() * 100 / session.unit.getMaxHealth()), session.thermal,
                        session.unit.rockets, session.flares, session.yaw, session.pitch, cooldown,
                        (int) Math.max(0, session.nextRocket - now), session.speed, reloads, session.gunshipAmmo));
    }

    public static void tick(MinecraftServer server) {
        if (lastServer != server) {
            SESSIONS.clear();
            TARGETS.clear();
            CALLS.clear();
            NUKES.clear();
            StrikeRadioReports.clear();
            AircraftAirspace.clear();
            lastServer = server;
        }
        for (CallIn call : List.copyOf(CALLS.values())) {
            ServerPlayer player = call.player;
            if (!player.isAlive() || player.isSpectator() || player.serverLevel() != call.level
                    || server.getPlayerList().getPlayer(player.getUUID()) != player
                    || call.unit != null && call.unit.isRemoved()) {
                abortSession(player);
                continue;
            }
            String reward = call.nuclear ? "mgb" : call.reward == null ? "" : call.reward.id();
            if (!StreakServerConfig.requireReward(player, reward)) {
                abortSession(player);
                continue;
            }
            if (call.level.getGameTime() >= call.due && (call.audioDone || call.level.getGameTime() >= call.deadline)) {
                CALLS.remove(player.getUUID());
                if (!StreakAirspace.player(player, call.reward)) {
                    refundUnavailableCall(call);
                    StreakNetwork.send(player, new StreakPresentationPayload("", "reset", 0));
                    continue;
                }
                if (call.action != null)
                    call.action.run();
                else if (call.nuclear)
                    beginMgb(player);
                else if (call.unit != null)
                    startSession(player, call.unit);
                else
                    deployNow(player, call.reward, call.bonus);
                KillstreakService.sendTerminalState(player, false);
            }
        }
        if (server.getTickCount() % 10 == 0)
            for (ServerLevel level : server.getAllLevels())
                for (Entity entity : level.getAllEntities())
                    if (entity instanceof TacticalUnitEntity e && e.isAlive())
                        keepLoaded(e, e.position());
        for (Session session : List.copyOf(SESSIONS.values())) {
            if (SESSIONS.get(session.player.getUUID()) != session)
                continue;
            ServerPlayer p = session.player;
            TacticalUnitEntity unit = session.unit;
            ServerLevel l = p.serverLevel();
            long time = l.getGameTime();
            if (!p.isAlive() || p.isSpectator() || unit.level() != l || !p.connection.isAcceptingMessages()
                    || server.getPlayerList().getPlayer(p.getUUID()) != p) {
                abortSession(p);
                continue;
            }
            if (unit.isRemoved() || unit.departing()) {
                endSession(p, false);
                continue;
            }
            if (unit.crashing()) {
                if (time % 2 == 0)
                    sendView(session);
                continue;
            }
            if (time - session.lastInput > 60) {
                endSession(p, false);
                continue;
            }
            if (p.position().distanceToSqr(session.anchor) > .04)
                p.connection.teleport(session.anchor.x, session.anchor.y, session.anchor.z, session.bodyYaw,
                        session.bodyPitch);
            p.setYRot(session.bodyYaw);
            p.setXRot(session.bodyPitch);
            p.yRotO = session.bodyYaw;
            p.xRotO = session.bodyPitch;
            p.yHeadRot = p.yHeadRotO = session.bodyHeadYaw;
            p.yBodyRot = p.yBodyRotO = session.bodyTorsoYaw;
            p.setDeltaMovement(Vec3.ZERO);
            int edge = session.actions;
            session.actions = 0;
            if (time - session.lastInput > 4) {
                session.flags = 0;
                session.forward = 0;
                session.strafe = 0;
            }
            if ((edge & EXIT) != 0 || (edge & AUTO) != 0 && unit.kind() == Kind.WHEELSON) {
                endSession(p, unit.kind() == Kind.WHEELSON);
                continue;
            }
            if (time < session.introUntil) {
                session.flags = 0;
                session.forward = 0;
                session.strafe = 0;
                edge = 0;
            }
            if ((edge & THERMAL) != 0 && unit.kind() != Kind.CRUISE)
                session.thermal = !session.thermal;
            for (int i = 0; i < 3; i++)
                if (session.gunshipReload[i] > 0 && time >= session.gunshipReload[i]) {
                    session.gunshipAmmo[i] = StreakWeaponProfiles.MAGAZINE[i];
                    session.gunshipReload[i] = 0;
                    NativeAudio.voice(p, "gunship", "reload" + (i == 0 ? "25" : i == 1 ? "40" : "105"));
                }
            if (unit.kind() == Kind.GUNSHIP) {
                if ((edge & CYCLE) != 0)
                    session.weapon = (session.weapon + 1) % 3;
                if ((edge & CYCLE_BACK) != 0)
                    session.weapon = (session.weapon + 2) % 3;
                if ((edge & WEAPON_1) != 0)
                    session.weapon = 0;
                if ((edge & WEAPON_2) != 0)
                    session.weapon = 1;
                if ((edge & WEAPON_3) != 0)
                    session.weapon = 2;
            }
            if ((edge & FLARES) != 0 && session.flares > 0 && time >= session.nextFlare
                    && unit.kind() == Kind.CHOPPER) {
                session.flares--;
                session.nextFlare = time + 100;
                NativeAudio.voice(p, "chopper_gunner", "flares");
                NativeFxDelivery.emit(l, "flares", unit.position(), 8);
                l.sendParticles(ParticleTypes.END_ROD, unit.getX(), unit.getY(), unit.getZ(), 20, 8, 3, 8, .12);
                l.sendParticles(ParticleTypes.FLAME, unit.getX(), unit.getY(), unit.getZ(), 15, 5, 3, 5, .15);
                for (Entity ent : l.getAllEntities())
                    if (ent instanceof TacticalUnitEntity rocket && rocket.kind() == Kind.ROCKET
                            && !p.getUUID().equals(rocket.ownerId) && rocket.distanceToSqr(unit) < 48 * 48)
                        rocket.center = unit.position().add(30, -20, 30);
            }
            if (unit.kind() == Kind.GUNSHIP) {
                if (time < session.introUntil) {
                    Vec3 aim = p.position().subtract(unit.getEyePosition());
                    session.yaw = GunshipFlight.mountYaw(unit.flightYaw());
                    session.pitch = (float) -Math.toDegrees(Math.atan2(aim.y, aim.horizontalDistance()));
                }
                session.yaw = GunshipFlight.clampYaw(session.yaw, unit.flightYaw());
                session.pitch = GunshipFlight.clampPitch(session.pitch);
            }
            Vec3 wheelsonAim = unit.kind() == Kind.WHEELSON ? wheelsonDirection(session) : null;
            if (wheelsonAim != null)
                unit.aim((float) Math.toDegrees(Math.atan2(-wheelsonAim.x, wheelsonAim.z)),
                        (float) -Math.toDegrees(Math.atan2(wheelsonAim.y, wheelsonAim.horizontalDistance())));
            else
                unit.aim(session.yaw, session.pitch);
            session.speed = 0;
            Vec3 forward = Vec3.directionFromRotation(0, session.yaw),
                    side = Vec3.directionFromRotation(0, session.yaw + 90);
            boolean chopperEntering = unit.kind() == Kind.CHOPPER && time < session.introUntil;
            if (chopperEntering) {
                int duration = StreakTimings.entry("chopper_gunner");
                session.speed = ChopperIngress.tick(unit, (int) (time - (session.introUntil - duration)), duration);
            }
            if (unit.kind() == Kind.BOMB_DRONE || unit.kind() == Kind.CHOPPER && !chopperEntering
                    || unit.kind() == Kind.WHEELSON) {
                double speed = unit.kind() == Kind.BOMB_DRONE ? .65 : unit.kind() == Kind.WHEELSON ? .23 : .55;
                if ((session.flags & BOOST) != 0)
                    speed *= 1.6;
                Vec3 motion = forward.scale(session.forward * speed).add(side.scale(session.strafe * speed));
                if (motion.lengthSqr() > speed * speed)
                    motion = motion.normalize().scale(speed);
                if (unit.kind() == Kind.WHEELSON) {
                    unit.heading = Mth.wrapDegrees(unit.heading + session.strafe * 4);
                    motion = Vec3.directionFromRotation(0, unit.heading).scale(session.forward * speed);
                }
                if (unit.kind() != Kind.WHEELSON) {
                    double lift = unit.kind() == Kind.BOMB_DRONE ? .45 : .32;
                    if (unit.kind() == Kind.BOMB_DRONE && (session.flags & BOOST) != 0)
                        lift *= 1.6;
                    motion = motion.add(0,
                            ((session.flags & UP) != 0 ? lift : 0) - ((session.flags & DOWN) != 0 ? lift : 0), 0);
                }
                Vec3 next = unit.position().add(motion);
                if (next.distanceToSqr(unit.kind() == Kind.CHOPPER ? unit.airPosition(unit.center) : unit.center) > 128
                        * 128)
                    motion = Vec3.ZERO;
                if (unit.kind() == Kind.WHEELSON) {
                    unit.setDeltaMovement(motion.x, unit.getDeltaMovement().y, motion.z);
                    unit.setYRot(unit.heading);
                    session.speed = (float) motion.length() * 20;
                } else if (l.noCollision(unit, unit.getBoundingBox().move(motion)) && next.y > l.getMinBuildHeight() + 2
                        && next.y < l.getMaxBuildHeight() - 3) {
                    unit.flight(next);
                    if (unit.kind() == Kind.BOMB_DRONE)
                        unit.setYRot(session.yaw);
                    session.speed = (float) motion.length() * 20;
                }
            }
            if (unit.kind() == Kind.CRUISE) {
                boolean entering = time < session.introUntil;

                // 每次导弹会话只确认一次加速，松开按键或短暂输入中断清除按住标志后，加速仍然保留。
                if (!entering && !session.cruiseBoosting && (session.flags & (BOOST | FIRE)) != 0) {
                    session.cruiseBoosting = true;
                    sound(l, unit.position(), StreakSounds.CRUISE_BOOST.get(), .8F, 1);
                    StreakNetwork.send(p, new StreakImpactPayload(.75F));
                }
                float flightPitch = entering ? session.cruiseIntroPitch : session.pitch,
                        flightYaw = entering ? unit.heading : session.yaw;
                int introClock = (int) (time - (session.introUntil - NativeCruiseFlight.DURATION)) + 1;
                Vec3 step = entering
                        ? NativeCruiseFlight
                                .position(session.cruiseLaunch, unit.heading, session.cruiseIntroPitch, introClock)
                                .subtract(unit.position())
                        : Vec3.directionFromRotation(flightPitch, flightYaw).scale(session.cruiseBoosting ? 2.8 : 1.5);
                unit.cruiseClock(entering ? introClock : NativeCruiseFlight.DURATION);
                if (entering && !session.cruiseShellReleased && introClock >= NativeCruiseFlight.SHELL_TICK) {
                    session.cruiseShellReleased = true;
                    NativeFxDelivery.emit(l, "cruise", unit.position().add(step), 1.8F);
                }
                session.speed = (float) step.length() * 20;
                BlockHitResult hit = l.clip(new ClipContext(unit.position(), unit.position().add(step),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, unit));
                if (hit.getType() != HitResult.Type.MISS || unit.getY() < l.getMinBuildHeight() + 2) {
                    blast(unit, impactPosition(hit), 22, 180);
                    unit.discard();
                    endSession(p, false);
                    continue;
                }
                Vec3 previous = unit.position();
                unit.flight(previous.add(step));
                unit.aim(flightYaw, flightPitch);
                unit.flightOrientation(flightYaw, flightPitch);

                // 推进器状态绑定尚未恢复，仅在源状态启用时保留小规模 Minecraft 尾迹适配效果。
                if (!entering || introClock < NativeCruiseFlight.SHELL_TICK
                        || introClock >= NativeCruiseFlight.THRUSTER_TICK
                                && introClock < NativeCruiseFlight.HANDOFF_TICK) {
                    Vec3 exhaust = previous.subtract(step.normalize().scale(1.3));
                    l.sendParticles(ParticleTypes.FLAME, exhaust.x, exhaust.y, exhaust.z, 2, .06, .06, .06, .015);
                    l.sendParticles(ParticleTypes.SMOKE, exhaust.x, exhaust.y, exhaust.z, 2, .1, .1, .1, 0);
                }
            }
            if (unit.isRemoved()) {
                endSession(p, false);
                continue;
            }
            if ((session.flags & FIRE) != 0
                    && time >= (unit.kind() == Kind.GUNSHIP ? session.gunshipNextShot[session.weapon]
                            : session.nextShot)) {
                Vec3 direction = wheelsonAim != null ? wheelsonAim
                        : Vec3.directionFromRotation(session.pitch, session.yaw);
                if (unit.kind() == Kind.BOMB_DRONE) {
                    blast(unit, unit.position(), 5, 32);
                    unit.discard();
                    endSession(p, false);
                    continue;
                }
                if (unit.kind() == Kind.WHEELSON || unit.kind() == Kind.CHOPPER) {
                    boolean rover = unit.kind() == Kind.WHEELSON;
                    session.nextShot = time + (rover ? 2 : StreakWeaponProfiles.CHOPPER_FIRE_INTERVAL);
                    Vec3 muzzle = rover ? WheelsonMount.muzzle(unit, unit.aimYaw(), unit.aimPitch())
                            : unit.getEyePosition();
                    explosiveFire(unit, muzzle, direction, 180, rover ? 6 : StreakWeaponProfiles.CHOPPER_DIRECT_DAMAGE,
                            rover ? 2.2F : 2.5F, rover ? 8 : StreakWeaponProfiles.CHOPPER_BLAST_DAMAGE);
                }
                if (unit.kind() == Kind.GUNSHIP && session.gunshipReload[session.weapon] == 0
                        && session.gunshipAmmo[session.weapon] > 0) {
                    int weapon = session.weapon;
                    session.gunshipNextShot[weapon] = time + StreakWeaponProfiles.FIRE_INTERVAL[weapon];
                    if (weapon == 0)
                        explosiveFire(unit, unit.getEyePosition(), direction, 256, 8, 3, 12);
                    else if (weapon == 1) {
                        unit.noteShot();
                        Vec3 impact = hitscan(p, unit, unit.getEyePosition(), direction, 256, 0, false);
                        if (unit.getEyePosition().distanceToSqr(impact) < 256 * 256 - .01)
                            blast(unit, impact, 5, 45);
                    } else {
                        Vec3 impact = trace(l, unit.getEyePosition(), direction, 320, unit);
                        TacticalUnitEntity shell = spawnChild(unit, Kind.ROCKET,
                                unit.getEyePosition().add(direction.scale(4)), impact, 120, session.yaw, 0, "105mm",
                                direction.scale(5));
                        shell.setNoGravity(true);
                        unit.noteShot();
                        AircraftFireEffects.shot(unit, unit.getEyePosition(), 2);
                        AircraftFireEffects.rocketLaunch(shell);
                    }
                    if (--session.gunshipAmmo[weapon] == 0) {
                        session.gunshipReload[weapon] = time + StreakWeaponProfiles.RELOAD_TICKS[weapon];
                        String caliber = weapon == 0 ? "25" : weapon == 1 ? "40" : "105";
                        sound(l, unit.position(),
                                NativeAudio.sfx("gunship" + caliber + "_reload", StreakSounds.RADIO.get()), .65F, 1);
                    }
                }
            }
            if ((session.flags & SECONDARY) != 0 && time >= session.nextRocket && unit.kind() == Kind.CHOPPER
                    && unit.rockets > 0) {
                session.nextRocket = time;
                unit.rockets--;
                Vec3 d = Vec3.directionFromRotation(session.pitch, session.yaw);
                Vec3 mount = AircraftFireEffects.rocketMount(unit);
                TacticalUnitEntity rocket = spawnChild(unit, Kind.ROCKET, mount.add(d.scale(.6)), unit.center, 100,
                        session.yaw, 0, "helo_rocket", d.scale(4.5));
                AircraftFireEffects.rocketLaunch(rocket);
            }
            if (unit.kind() == Kind.GUNSHIP)
                session.speed = GunshipFlight.speed();
            if (time % 2 == 0 || edge != 0)
                sendView(session);
        }
        for (var entry : List.copyOf(TARGETS.entrySet())) {
            Pending pending = entry.getValue();
            ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey());
            if (p == null) {
                TARGETS.remove(entry.getKey());
                continue;
            }
            if (!p.isAlive() || p.isSpectator() || p.serverLevel() != pending.level) {
                abortSession(p);
                continue;
            }
            if (p.serverLevel().getGameTime() - pending.created > 1200) {
                TARGETS.remove(entry.getKey());
                StreakNetwork.send(p, new StreakPresentationPayload("", "reset", 0));
                clearMapContacts(p, pending);
                KillstreakService.refund(p, pending.reward, pending.bonus);
            }
        }
        for (var entry : List.copyOf(NUKES.entrySet())) {
            NuclearStrike strike = entry.getValue();
            // Cancel a disabled strike before its lethal tick; completed blasts keep their visual cleanup.
            // 禁用后在致命伤害发生前取消核弹，已经引爆的效果仍完成清理。
            if (strike.remaining > 0 && !StreakServerConfig.rewardEnabled(server, "mgb")) {
                NUKES.remove(entry.getKey(), strike);
                for (ServerPlayer player : List.copyOf(entry.getKey().players()))
                    syncMgb(player);
                continue;
            }
            int left = --strike.remaining;
            ServerLevel l = entry.getKey();
            if (left >= 0 && left % 10 == 0)
                for (ServerPlayer p : List.copyOf(l.players()))
                    sendMgb(p, strike);
            if (left == 0) {
                interruptLevelControls(l);
                for (ServerPlayer p : List.copyOf(l.players())) {
                    StreakNetwork.send(p, new MgbEffectPayload(l.dimension().location(), strike.id, 0, strike.center.x,
                            strike.center.y, strike.center.z));
                    StreakNetwork.send(p, new StreakImpactPayload(3));
                }
            }
            if (left <= -20) {
                try {
                    applyMgbDamage(l);
                } finally {
                    NUKES.remove(l, strike);
                }
            }
        }
        if (server.getTickCount() % 20 == 0)
            updateRadar(server);
        if (server.getTickCount() % 10 == 0)
            updateMapContacts(server);
        StrikeRadioReports.tick(server);
    }

    private static void interruptLevelControls(ServerLevel level) {
        for (ServerPlayer player : List.copyOf(level.players()))
            abortSession(player);
    }

    private static void applyMgbDamage(ServerLevel level) {

        // getAllEntities() 返回 fastutil 映射的实时视图；死亡可能移除遥控摄像机并加入掉落物，使该映射的链式迭代器失效。
        List<LivingEntity> victims = new ArrayList<>();
        for (Entity entity : level.getAllEntities())
            if (entity instanceof LivingEntity living && living.isAlive()
                    && (entity instanceof ServerPlayer || entity instanceof Enemy))
                victims.add(living);
        interruptLevelControls(level);
        for (LivingEntity victim : victims)
            if (victim.level() == level && !victim.isRemoved() && victim.isAlive())
                victim.hurt(level.damageSources().genericKill(), 100000);
    }

    /**
     * A new life has the same UUID but a different ServerPlayer instance.
     * 新的一条生命保留相同 UUID，但使用不同的 ServerPlayer 实例。
     */
    public static void resetAfterRespawn(ServerPlayer player) {
        abortSession(player);
        RemoteViewSubscriptions.restoreBodyView(player);
    }

    public static void stop(MinecraftServer server) {
        for (Session session : List.copyOf(SESSIONS.values()))
            abortSession(session.player);
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers()))
            abortSession(player);
        SESSIONS.clear();
        TARGETS.clear();
        CALLS.clear();
        NUKES.clear();
        StrikeRadioReports.clear();
        AircraftAirspace.clear();
        lastServer = null;
    }

    public static void openMap(ServerPlayer player, StreakReward reward, boolean bonus) {
        TARGETS.put(player.getUUID(), new Pending(reward, bonus, player.serverLevel(),
                player.serverLevel().getGameTime(), player.blockPosition()));
        int width = TacticalMapPayload.WIDTH;
        int[] colors = new int[width * width], heights = new int[width * width];
        ServerLevel level = player.serverLevel();
        for (int z = 0; z < width; z++)
            for (int x = 0; x < width; x++) {
                int bx = player.getBlockX() + x - 66, bz = player.getBlockZ() + z - 66, i = z * width + x;
                BlockPos at = new BlockPos(bx, 0, bz);
                if (!level.hasChunkAt(at) || !level.getWorldBorder().isWithinBounds(at)) {
                    heights[i] = Integer.MIN_VALUE;
                    continue;
                }
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) - 1;
                at = new BlockPos(bx, y, bz);
                heights[i] = y;
                colors[i] = level.getBlockState(at).getMapColor(level, at).col;
            }
        StreakNetwork.send(player,
                new TacticalMapPayload(reward.id(), player.getBlockX(), player.getBlockZ(), width, colors, heights));
        sendMapContacts(player, TARGETS.get(player.getUUID()));
    }

    public static void target(ServerPlayer player, TacticalTargetPayload in) {
        Pending pending = TARGETS.get(player.getUUID());
        if (pending == null) {
            StreakNetwork.send(player, new TacticalTargetResultPayload(false));
            return;
        }
        if (in.cancel()) {
            TARGETS.remove(player.getUUID());
            StreakNetwork.send(player, new StreakPresentationPayload("", "reset", 0));
            clearMapContacts(player, pending);
            KillstreakService.refund(player, pending.reward, pending.bonus);
            return;
        }
        if (!player.isAlive() || player.level() != pending.level) {
            StreakNetwork.send(player, new TacticalTargetResultPayload(false));
            return;
        }
        int count = pending.reward == StreakReward.SAE ? 3 : 1;
        if (in.points().length != count * 2 || in.headings().length != count) {
            StreakNetwork.send(player, new TacticalTargetResultPayload(false));
            return;
        }
        List<Vec3> points = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int x = in.points()[i * 2], z = in.points()[i * 2 + 1];
            if (!Float.isFinite(in.headings()[i]) || Math.abs((long) x - pending.origin.getX()) > 66
                    || Math.abs((long) z - pending.origin.getZ()) > 66) {
                StreakNetwork.send(player, new TacticalTargetResultPayload(false));
                return;
            }
            BlockPos at = new BlockPos(x, 0, z);
            if (!pending.level.hasChunkAt(at) || !pending.level.getWorldBorder().isWithinBounds(at)) {
                StreakNetwork.send(player, new TacticalTargetResultPayload(false));
                return;
            }
            points.add(ground(pending.level, new Vec3(x + .5, 0, z + .5)));
        }
        for (int i = 0; i < points.size(); i++)
            if (!StreakAirspace.target(player, pending.reward, points.get(i), in.headings()[i])) {
                StreakNetwork.send(player, new TacticalTargetResultPayload(false));
                return;
            }
        TARGETS.remove(player.getUUID());
        float[] headings = in.headings().clone();
        queueCall(new CallIn(player, player.serverLevel(), pending.reward, pending.bonus, false,
                player.serverLevel().getGameTime() + StreakTimings.confirmed(pending.reward.id()), null, () -> {
                    for (int i = 0; i < points.size(); i++)
                        if (!StreakAirspace.target(player, pending.reward, points.get(i), headings[i])) {
                            KillstreakService.refund(player, pending.reward, pending.bonus);
                            return;
                        }
                    NativeAudio.call(player, pending.reward.id());
                    UUID report = StrikeRadioReports.begin(player, pending.reward.id());
                    for (int i = 0; i < points.size(); i++) {
                        Vec3 c = ground(player.serverLevel(), points.get(i));
                        Kind k = pending.reward == StreakReward.SAE ? Kind.SAE
                                : pending.reward == StreakReward.VTOL_JET ? Kind.VTOL : Kind.STEALTH;
                        StrikeRadioReports.track(spawnConfigured(player, k, c.add(0, 40, 0), c,
                                k == Kind.VTOL ? 1200 : 110, Mth.wrapDegrees(headings[i]), i * 20, "", Vec3.ZERO),
                                report);
                    }
                }));
        clearMapContacts(player, pending);
        StreakNetwork.send(player, new StreakPresentationPayload(pending.reward.id(), "confirmed",
                StreakTimings.confirmed(pending.reward.id())));
        StreakNetwork.send(player, new TacticalTargetResultPayload(true));
        KillstreakService.sendTerminalState(player, false);
    }

    public static boolean designate(ServerPlayer player, StreakReward reward, Vec3 point) {
        if (!StreakAirspace.target(player, reward, point, player.getYRot()))
            return false;
        ServerLevel level = player.serverLevel();
        Vec3 c = ground(level, point);
        float heading = player.getYRot();
        queueCall(new CallIn(player, level, reward, false, false, level.getGameTime() + 1, null, () -> {
            if (!StreakAirspace.target(player, reward, point, heading)) {
                KillstreakService.giveItem(player, TacticalEquipmentItem.stack("designator", reward.id()));
                return;
            }
            NativeAudio.call(player, reward.id());
            UUID report = StrikeRadioReports.begin(player, reward.id());
            Vec3 actualCenter = ground(level, point);
            if (reward == StreakReward.PRECISION_AIRSTRIKE)
                for (int i = 0; i < 2; i++)
                    StrikeRadioReports.track(spawnConfigured(player, Kind.PRECISION, actualCenter.add(0, 36, 0),
                            actualCenter, PrecisionFlight.LIFETIME, heading, i * PrecisionFlight.PLANE_INTERVAL, "",
                            Vec3.ZERO), report);
            else if (reward == StreakReward.MORTAR_STRIKE)
                for (int volley = 0; volley < MortarStrikeFlight.VOLLEYS; volley++)
                    for (int shell = 0; shell < MortarStrikeFlight.SHELLS_PER_VOLLEY; shell++) {
                        Vec3 target = MortarStrikeFlight.target(player, actualCenter, heading, volley, shell),
                                from = MortarStrikeFlight.start(player, target, heading);
                        StrikeRadioReports.track(spawnConfigured(player, Kind.BOMB, from, target, 220, heading,
                                volley * MortarStrikeFlight.VOLLEY_INTERVAL, "mortar",
                                MortarStrikeFlight.velocity(from, target)), report);
                    }
        }));
        level.sendParticles(ParticleTypes.DUST_PLUME, c.x, c.y, c.z, 15, .5, .3, .5, .03);
        return true;
    }

    public static void throwMarker(ServerPlayer player, StreakReward reward) {
        if (!StreakServerConfig.requireReward(player, reward.id()))
            return;
        Vec3 direction = player.getLookAngle();
        TacticalUnitEntity marker = spawnConfigured(player, Kind.SMOKE_MARKER,
                player.getEyePosition().add(direction.scale(.4)), player.position(), 640, player.getYRot(), 0,
                reward.id(), direction.scale(1.15).add(0, .12, 0));
        marker.markerOwnerEntityId = player.getId();
        player.swing(InteractionHand.MAIN_HAND, true);
    }

    public static void airdrop(ServerPlayer player, StreakReward reward, Vec3 at) {
        if (!StreakServerConfig.requireReward(player, reward.id()))
            return;
        if (!StreakAirspace.target(player, reward, at, player.getYRot())) {
            KillstreakService.giveItem(player, TacticalEquipmentItem.stack("drop_marker", reward.id()));
            return;
        }
        float heading = player.getYRot();
        queueCall(new CallIn(player, player.serverLevel(), reward, false, false, player.serverLevel().getGameTime() + 1,
                null, () -> {
                    if (!StreakAirspace.target(player, reward, at, heading)) {
                        KillstreakService.giveItem(player, TacticalEquipmentItem.stack("drop_marker", reward.id()));
                        return;
                    }
                    NativeAudio.call(player, reward.id());
                    Vec3 c = ground(player.serverLevel(), at);
                    spawnConfigured(player, Kind.DELIVERY, c.add(-300, 48, 0), c, 260, heading, 0,
                            reward == StreakReward.EMERGENCY_AIRDROP ? "emergency"
                                    : reward == StreakReward.JUGGERNAUT ? "juggernaut" : "care",
                            Vec3.ZERO);
                }));
    }

    public static StreakReward randomPackage(MinecraftServer server) {
        List<StreakReward> pool = StreakReward.SELECTABLE.stream()
                .filter(reward -> reward != StreakReward.CARE_PACKAGE && reward != StreakReward.EMERGENCY_AIRDROP)
                .filter(reward -> StreakServerConfig.rewardEnabled(server, reward.id()))
                .toList();
        int total = pool.stream().mapToInt(r -> Math.max(1, 20 - r.kills())).sum();
        int n = new Random().nextInt(total);
        for (StreakReward r : pool) {
            n -= Math.max(1, 20 - r.kills());
            if (n < 0)
                return r;
        }
        return StreakReward.UAV;
    }

    public static boolean equipJuggernaut(ServerPlayer player) {
        if (!StreakServerConfig.requireReward(player, "juggernaut"))
            return false;
        ArmorKit.equip(player);
        player.addEffect(new MobEffectInstance(MobEffects.HEALTH_BOOST, Integer.MAX_VALUE, 9, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 2, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, Integer.MAX_VALUE, 0, false, false));
        player.setHealth(player.getMaxHealth());
        player.addTag("killstreakcod.juggernaut");
        KillstreakService.giveItem(player, KillstreakcodMod.M134_MINIGUN.get().getDefaultInstance());
        return true;
    }

    public static void launchMgb(ServerPlayer player) {
        if (!StreakServerConfig.requireReward(player, "mgb"))
            return;
        if (mgbActive(player.serverLevel()))
            return;
        int duration = StreakTimings.call("mgb");
        queueCall(new CallIn(player, player.serverLevel(), null, false, true,
                player.serverLevel().getGameTime() + duration));
        player.stopUsingItem();
        StreakNetwork.send(player, new StreakPresentationPayload("mgb", "call", duration));
    }

    private static void beginMgb(ServerPlayer player) {
        NativeAudio.call(player, "mgb");
        Vec3 horizontal = player.getLookAngle().multiply(1, 0, 1).normalize();
        if (horizontal.lengthSqr() < .01)
            horizontal = new Vec3(0, 0, 1);
        NuclearStrike strike = new NuclearStrike(
                ground(player.serverLevel(), player.position().add(horizontal.scale(70))),
                player.getGameProfile().getName());
        NUKES.put(player.serverLevel(), strike);
        for (ServerPlayer observer : List.copyOf(player.serverLevel().players()))
            sendMgb(observer, strike);
        player.serverLevel().getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("message.killstreakcod.mgb", player.getDisplayName()), false);
    }

    private static void sendMgb(ServerPlayer player, NuclearStrike strike) {
        StreakNetwork.send(player,
                new MgbCountdownPayload(player.serverLevel().dimension().location(), strike.id,
                        player.serverLevel().getGameTime(), strike.remaining, strike.center.x, strike.center.y,
                        strike.center.z, strike.caller));
    }

    public static void syncMgb(ServerPlayer player) {
        NuclearStrike strike = NUKES.get(player.serverLevel());
        if (strike != null) {
            sendMgb(player, strike);
            if (strike.remaining <= 0)
                StreakNetwork.send(player, new MgbEffectPayload(player.serverLevel().dimension().location(), strike.id,
                        -strike.remaining, strike.center.x, strike.center.y, strike.center.z));
        } else
            StreakNetwork.send(player, new MgbCountdownPayload(player.serverLevel().dimension().location(),
                    new UUID(0, 0), player.serverLevel().getGameTime(), Integer.MIN_VALUE, 0, 0, 0, ""));
    }

    public static Vec3 ground(ServerLevel level, Vec3 at) {
        int x = Mth.floor(at.x), z = Mth.floor(at.z);
        return new Vec3(at.x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), at.z);
    }

    public static boolean enemy(ServerPlayer owner, LivingEntity livingEntity) {
        return livingEntity != owner && livingEntity.isAlive() && !(livingEntity instanceof TacticalUnitEntity)
                && !(livingEntity instanceof SupportHelicopterEntity)
                && (livingEntity instanceof Enemy || StreakCombat.boss(livingEntity)
                        || livingEntity instanceof ServerPlayer && !livingEntity.isSpectator())
                && !StreakCombat.allied(owner, livingEntity);
    }

    public static boolean inTargetRange(TacticalUnitEntity unit, LivingEntity target, double range) {
        return target.position().distanceToSqr(unit.position().add(0, -unit.targetingLift(), 0)) <= range * range;
    }

    public static LivingEntity nearest(TacticalUnitEntity unit, double range, boolean arc) {
        ServerPlayer player = unit.owner();
        if (player == null)
            return null;
        Vec3 direction = Vec3.directionFromRotation(0, unit.heading);
        return unit.level()
                .getEntitiesOfClass(LivingEntity.class,
                        unit.getBoundingBox().move(0, -unit.targetingLift(), 0).inflate(range),
                        livingEntity -> enemy(player, livingEntity) && inTargetRange(unit, livingEntity, range)
                                && (!arc || livingEntity.position().subtract(unit.position()).multiply(1, 0, 1)
                                        .normalize().dot(direction) >= 0)
                                && clear(unit.level(), unit.getEyePosition(), livingEntity.getEyePosition(), unit))
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(unit))).orElse(null);
    }

    public static boolean clear(net.minecraft.world.level.Level level, Vec3 from, Vec3 to, Entity owner) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner))
                .getType() == HitResult.Type.MISS;
    }

    public static Vec3 impactPosition(BlockHitResult hit) {
        return hit.getType() == HitResult.Type.MISS ? hit.getLocation()
                : hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(.08));
    }

    public static Vec3 trace(ServerLevel level, Vec3 from, Vec3 direction, double range, Entity entity) {
        return level.clip(new ClipContext(from, from.add(direction.scale(range)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, entity)).getLocation();
    }

    public static void fire(TacticalUnitEntity unit, Vec3 from, Vec3 direction, double range, float damage,
            boolean incendiary) {
        ServerPlayer player = unit.owner();
        if (player != null) {
            unit.noteShot();
            hitscan(player, unit, from, direction, range, damage, incendiary);
        }
    }

    public static void explosiveFire(TacticalUnitEntity unit, Vec3 from, Vec3 direction, double range,
            float directDamage, float radius, float splashDamage) {
        ServerPlayer player = unit.owner();
        if (player != null) {
            unit.noteShot();
            Vec3 impact = hitscan(player, unit, from, direction, range, directDamage, false);
            if (from.distanceToSqr(impact) < range * range - .01)
                blast(unit, impact, radius, splashDamage);
        }
    }

    public static Vec3 hitscan(ServerPlayer player, Entity emitter, Vec3 from, Vec3 direction, double range,
            float damage, boolean incendiary) {
        ServerLevel level = player.serverLevel();
        BlockHitResult block = level.clip(new ClipContext(from, from.add(direction.scale(range)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, emitter));
        Vec3 end = block.getLocation();
        double nearest = from.distanceToSqr(end);
        Entity hit = null;
        for (Entity entity : StreakCombat.targets(level, player, emitter, new AABB(from, end).inflate(1), false)) {
            Optional<Vec3> intercept = StreakCombat.intercept(entity, from, end, .12);
            if (intercept.isPresent() && from.distanceToSqr(intercept.get()) < nearest) {
                nearest = from.distanceToSqr(intercept.get());
                end = intercept.get();
                hit = entity;
            }
        }
        boolean confirmed = false;
        if (hit != null && damage > 0) {
            boolean hostile = StreakCombat.hostile(player, hit);
            confirmed = StreakCombat.hurt(player, emitter, hit, damage, false, from) && hostile;
            if (incendiary)
                hit.igniteForSeconds(2);
        }
        if (emitter instanceof TacticalUnitEntity unit && unit.kind() == Kind.GUNSHIP && damage == 0)
            TracerDelivery.shot(level, emitter, from, end, TracerDelivery.HEAVY); // 40 mm uses splash-only hitscan.
        // 中文：40 毫米武器使用仅结算溅射伤害的即时射线命中。
        else
            TracerDelivery.shot(level, emitter, from, end);
        if (!AircraftFireEffects.shot(emitter, from, damage)) {
            level.sendParticles(ParticleTypes.FLAME, from.x, from.y, from.z, 3, .05, .05, .05, .02);
            sound(level, from, NativeAudio.shot(emitter, damage), .55F, 1);
        }
        level.sendParticles(ParticleTypes.SMOKE, end.x, end.y, end.z, 3, .05, .05, .05, .03);
        if (emitter instanceof TacticalUnitEntity unit)
            StrikeRadioReports.impact(unit, confirmed);
        return hit != null || block.getType() == HitResult.Type.MISS ? end : impactPosition(block);
    }

    /**
     * Chase camera and gun origin differ; converge the muzzle ray on the visible target.
     * 追尾摄像机与炮口原点不同，需要让炮口射线汇聚到可见目标。
     */
    private static Vec3 wheelsonDirection(Session session) {
        TacticalUnitEntity unit = session.unit;
        ServerLevel level = session.player.serverLevel();
        Vec3 from = WheelsonView.position(unit, session.yaw, session.pitch, 1),
                direction = Vec3.directionFromRotation(session.pitch, session.yaw);
        Vec3 target = trace(level, from, direction, 180, unit);
        double closest = from.distanceToSqr(target);
        for (Entity enemy : StreakCombat.targets(level, session.player, unit, new AABB(from, target).inflate(.2),
                false)) {
            var intercept = StreakCombat.intercept(enemy, from, target, .12);
            if (intercept.isPresent() && from.distanceToSqr(intercept.get()) < closest) {
                target = intercept.get();
                closest = from.distanceToSqr(target);
            }
        }
        Vec3 aim = target.subtract(WheelsonMount.muzzle(unit, session.yaw, session.pitch));
        return aim.lengthSqr() < .0001 ? direction : aim.normalize();
    }

    public static void blast(TacticalUnitEntity emitter, Vec3 center, float radius, float damage) {
        ServerLevel level = (ServerLevel) emitter.level();
        ServerPlayer player = emitter.owner();
        if (emitter.kind() == Kind.CRUISE && player != null) {
            Session rider = SESSIONS.get(player.getUUID());
            if (rider != null && rider.unit == emitter)
                StreakNetwork.send(player, new SupportSoundPayload(level.dimension().location(),
                        StreakSounds.CRUISE_NATIVE_BLAST.get().getLocation(), center.x, center.y, center.z, .8F, 1, 1));
        }
        boolean nativeAircraftImpact = !emitter.cargo.equals("sae_bomblet")
                && AircraftFireEffects.impact(emitter, center, radius);
        if (!emitter.cargo.equals("sae_bomblet") && !nativeAircraftImpact)
            NativeFxDelivery
                    .emit(level,
                            emitter.kind() == Kind.CRUISE ? "cruise"
                                    : emitter.kind() == Kind.MINE ? "mine"
                                            : emitter.kind() == Kind.BOMB_DRONE ? "equipment" : "explosion",
                            center, radius);
        if (emitter.cargo.equals("sae_bomblet"))
            SaeEffects.impact(level, emitter, center, radius);
        else {
            boolean small = radius <= 3;
            if (!nativeAircraftImpact) {
                level.sendParticles(small ? ParticleTypes.EXPLOSION : ParticleTypes.EXPLOSION_EMITTER, center.x,
                        center.y + .2, center.z, 1, 0, 0, 0, 0);
                level.sendParticles(ParticleTypes.FLAME, center.x, center.y + .2, center.z, small ? 6 : 40,
                        radius * .25, small ? .15 : .9, radius * .25, .06);
                level.sendParticles(small ? ParticleTypes.SMOKE : ParticleTypes.CAMPFIRE_COSY_SMOKE, center.x,
                        center.y + .4, center.z, small ? 5 : 30, radius * .2, small ? .2 : 1, radius * .2, .02);
            }
            SoundEvent impact = StreakSounds.impact(emitter, radius);
            sound(level, center, impact, small ? .65F : 1.3F,
                    impact == StreakSounds.EXPLOSION.get() ? (small ? 1.2F : .8F) : 1);
            for (ServerPlayer receiver : level.players()) {
                double distance = RemoteViewSubscriptions.origin(receiver).distanceTo(center);
                if (distance < 64)
                    StreakNetwork.send(receiver,
                            new StreakImpactPayload((float) (Math.min(3, radius * .3) * (1 - distance / 64))));
            }
        }
        if (player == null)
            return;
        boolean hitEnemy = StreakCombat.blast(level, player, emitter, center, radius, damage);
        StrikeRadioReports.impact(emitter, hitEnemy);
    }

    public static void sound(ServerLevel level, Vec3 p, SoundEvent sound, float volume, float pitch) {
        SupportAudio.broadcast(level, p, sound, volume, pitch);
    }

    private static void updateRadar(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            List<TacticalUnitEntity> units = new ArrayList<>();
            for (Entity entity : level.getAllEntities())
                if (entity instanceof TacticalUnitEntity u && u.isAlive() && u.onStation())
                    units.add(u);
            for (ServerPlayer player : level.players()) {
                boolean advanced = false, active = false, jammed = false;
                for (TacticalUnitEntity unit : units) {
                    ServerPlayer owner = unit.owner();
                    if (owner == null)
                        continue;
                    boolean ally = FriendlyPlayers.allied(player, owner);
                    if (ally && (unit.kind() == Kind.UAV || unit.kind() == Kind.ADVANCED_UAV)) {
                        active = true;
                        advanced |= unit.kind() == Kind.ADVANCED_UAV;
                    }
                    if (!ally && unit.kind() == Kind.COUNTER_UAV
                            && player.position().subtract(unit.position()).horizontalDistanceSqr() < 192 * 192)
                        jammed = true;
                }
                Session session = SESSIONS.get(player.getUUID());
                boolean sensor = session != null;
                Entity observer = sensor ? session.unit : player;
                List<LivingEntity> targets = (active || sensor) && !jammed
                        ? level.getEntitiesOfClass(LivingEntity.class, observer.getBoundingBox().inflate(sensor
                                ? 256
                                : 160),
                                livingEntity -> enemy(player, livingEntity) && surfaceVisible(livingEntity) &&
                                        (sensor ? clear(level, observer.getEyePosition(), livingEntity.getEyePosition(),
                                                observer)
                                                : units.stream().anyMatch(
                                                        u -> (u.kind() == Kind.UAV || u.kind() == Kind.ADVANCED_UAV)
                                                                && u.owner() != null
                                                                && FriendlyPlayers.allied(player, u.owner())
                                                                && clear(level, u.getEyePosition(),
                                                                        livingEntity.getEyePosition(), u))))
                        : List.of();

                // 普通无人侦察机周期扫描，尖端无人机持续刷新带方向的目标信息。
                if (!advanced && !sensor && server.getTickCount() % 60 != 0 && active && !jammed)
                    continue;
                int size = Math.min(128, targets.size());
                int[] contacts = new int[size * 4];
                for (int i = 0; i < size; i++) {
                    LivingEntity e = targets.get(i);
                    contacts[i * 4] = (int) (e.getX() * 10);
                    contacts[i * 4 + 1] = (int) (e.getEyeY() * 10);
                    contacts[i * 4 + 2] = (int) (e.getZ() * 10);
                    contacts[i * 4 + 3] = (int) e.getYRot();
                }
                StreakNetwork.send(player, new RadarPayload(active || sensor, advanced || sensor, jammed, contacts));
            }
        }
    }

    private static boolean surfaceVisible(LivingEntity entity) {
        var level = entity.level();
        Vec3 eye = entity.getEyePosition();
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, entity.getBlockX(), entity.getBlockZ());
        return clear(level, new Vec3(eye.x, Math.max(eye.y + .1, top + 2), eye.z), eye, entity);
    }

    private static void updateMapContacts(MinecraftServer server) {
        for (var entry : List.copyOf(TARGETS.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Pending pending = entry.getValue();
            if (player != null && player.isAlive() && player.serverLevel() == pending.level)
                sendMapContacts(player, pending);
        }
    }

    private static void sendMapContacts(ServerPlayer player, Pending pending) {
        ServerLevel level = pending.level;
        BlockPos origin = pending.origin;
        AABB area = new AABB(origin.getX() - 66, level.getMinBuildHeight(), origin.getZ() - 66, origin.getX() + 67,
                level.getMaxBuildHeight() + 128, origin.getZ() + 67);
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, area,
                livingEntity -> livingEntity != player && livingEntity.isAlive() && !livingEntity.isSpectator()
                        && !(livingEntity instanceof TacticalUnitEntity)
                        && !(livingEntity instanceof SupportHelicopterEntity)
                        && (enemy(player, livingEntity) || StreakCombat.allied(player, livingEntity))
                        && surfaceVisible(livingEntity));
        entities.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
        int size = Math.min(256, entities.size());
        int[] contacts = new int[size * 5];
        for (int i = 0; i < size; i++) {
            LivingEntity e = entities.get(i);
            contacts[i * 5] = (int) (e.getX() * 10);
            contacts[i * 5 + 1] = (int) (e.getEyeY() * 10);
            contacts[i * 5 + 2] = (int) (e.getZ() * 10);
            contacts[i * 5 + 3] = (int) e.getYRot();
            contacts[i * 5 + 4] = enemy(player, e) ? 1 : 0;
        }
        StreakNetwork.send(player, new TacticalMapContactsPayload(pending.reward.id(), contacts));
    }

    private static void clearMapContacts(ServerPlayer player, Pending pending) {
        StreakNetwork.send(player, new TacticalMapContactsPayload(pending.reward.id(), new int[0]));
    }

    private static final class CallIn {
        final ServerPlayer player;
        final ServerLevel level;
        final StreakReward reward;
        final boolean bonus, nuclear;
        final long due;
        final TacticalUnitEntity unit;
        final Runnable action;
        final UUID gate = UUID.randomUUID();
        boolean audioDone = true;
        long deadline;

        CallIn(ServerPlayer player, ServerLevel level, StreakReward reward, boolean bonus, boolean nuclear, long due) {
            this(player, level, reward, bonus, nuclear, due, null, null);
        }

        CallIn(ServerPlayer player, ServerLevel level, StreakReward reward, boolean bonus, boolean nuclear, long due,
                TacticalUnitEntity unit) {
            this(player, level, reward, bonus, nuclear, due, unit, null);
        }

        CallIn(ServerPlayer player, ServerLevel level, StreakReward reward, boolean bonus, boolean nuclear, long due,
                TacticalUnitEntity unit, Runnable action) {
            this.player = player;
            this.level = level;
            this.reward = reward;
            this.bonus = bonus;
            this.nuclear = nuclear;
            this.due = due;
            this.unit = unit;
            this.action = action;
            deadline = due;
        }
    }

    private record Pending(StreakReward reward, boolean bonus, ServerLevel level, long created, BlockPos origin) {
    }

    private static final class Session {
        final ServerPlayer player;
        final TacticalUnitEntity unit;
        final Vec3 anchor, cruiseLaunch;
        final float bodyYaw, bodyPitch, bodyHeadYaw, bodyTorsoYaw;
        final ItemStack previousOffhand;
        final boolean previousGravity;
        final long[] gunshipReload = new long[3], gunshipNextShot = new long[3];
        final int[] gunshipAmmo = StreakWeaponProfiles.MAGAZINE.clone();
        float yaw, pitch, forward, strafe, speed, cruiseIntroPitch;
        int flags, actions, lastSequence, weapon, flares = 3;
        boolean thermal, cruiseBoosting, cruiseShellReleased;
        long lastInput, nextShot, nextRocket, nextFlare, introUntil;

        Session(ServerPlayer p, TacticalUnitEntity e, Vec3 anchor) {
            player = p;
            unit = e;
            this.anchor = anchor;
            this.cruiseLaunch = unit.position();
            bodyYaw = p.getYRot();
            bodyPitch = p.getXRot();
            bodyHeadYaw = p.yHeadRot;
            bodyTorsoYaw = p.yBodyRot;
            previousOffhand = p.getOffhandItem().copy();
            previousGravity = p.isNoGravity();
            lastInput = p.serverLevel().getGameTime();
        }
    }

    private static final class NuclearStrike {
        final Vec3 center;
        final UUID id = UUID.randomUUID();
        final String caller;
        int remaining = 200;

        NuclearStrike(Vec3 center, String caller) {
            this.center = center;
            this.caller = caller.substring(0, Math.min(64, caller.length()));
        }
    }
}
