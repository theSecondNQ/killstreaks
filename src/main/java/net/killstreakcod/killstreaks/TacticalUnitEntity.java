package net.killstreakcod.killstreaks;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.audio.NativeAudio;
import net.killstreakcod.audio.StreakSounds;
import net.killstreakcod.audio.StrikeRadioReports;
import net.killstreakcod.universal.AircraftAirspace;
import net.killstreakcod.universal.AircraftBounds;
import net.killstreakcod.universal.AircraftCrash;
import net.killstreakcod.universal.AircraftCrashLibrary;
import net.killstreakcod.universal.AircraftFireEffects;
import net.killstreakcod.universal.AircraftFlight;
import net.killstreakcod.universal.FriendlyPlayers;
import net.killstreakcod.universal.KillstreakService;
import net.killstreakcod.universal.NativeArsenalLibrary;
import net.killstreakcod.universal.NativeFxDelivery;
import net.killstreakcod.universal.StreakAirspace;
import net.killstreakcod.universal.StreakCombat;
import net.killstreakcod.universal.StreakTimings;
import net.killstreakcod.universal.TacticalRuntime;
import net.killstreakcod.universal.ResearchCatalog;
import net.killstreakcod.universal.ResearchEffects;
import net.killstreakcod.universal.ResearchSystem;
import net.killstreakcod.universal.TracerDelivery;

import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A server simulated unit; no vanilla animal AI or projectile substitutes.
 * 由服务端模拟的作战单位，不借用原版动物 AI 或其他投射物代替。
 */
public final class TacticalUnitEntity extends PathfinderMob {
    public static final double UAV_HEIGHT = 120, COUNTER_UAV_HEIGHT = 100, ADVANCED_UAV_HEIGHT = 160;
    public static final double SAE_CLUSTER_RADIUS = 7.5;
    public static final float SAE_BLAST_RADIUS = 9, SAE_BLAST_DAMAGE = 240;

    public enum Kind {
        UAV, COUNTER_UAV, ADVANCED_UAV, BOMB_DRONE, CRUISE, PRECISION, SAE, VTOL, OVERWATCH, STEALTH, CHOPPER, GUNSHIP,
        DELIVERY, SENTRY, WHEELSON, CLUSTER, MINE, CRATE, JUGG_CRATE, BOMB, ROCKET, SMOKE_MARKER
    }

    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(TacticalUnitEntity.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STAGE = SynchedEntityData.defineId(TacticalUnitEntity.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> AIM_YAW = SynchedEntityData.defineId(TacticalUnitEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> AIM_PITCH = SynchedEntityData.defineId(TacticalUnitEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> FIRING = SynchedEntityData.defineId(TacticalUnitEntity.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> FLIGHT_YAW = SynchedEntityData.defineId(TacticalUnitEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FLIGHT_PITCH = SynchedEntityData.defineId(TacticalUnitEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> NATIVE_CLOCK = SynchedEntityData.defineId(TacticalUnitEntity.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DEPARTING = SynchedEntityData.defineId(TacticalUnitEntity.class,
            EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> CRASH_CANOPY = SynchedEntityData.defineId(TacticalUnitEntity.class,
            EntityDataSerializers.BOOLEAN);
    public UUID ownerId;
    public UUID radioStrike;
    public Vec3 center = Vec3.ZERO;
    public float heading;
    public int age, lifetime = 1200, delay, shotAt, rockets = 8;
    public long researchUpgrades;
    public String researchSource = "";
    public int researchStarted = -1, researchSonicRounds, researchMineGeneration;

    /**
     * Server flight plan; ground target stays fixed. Old saves default to zero lift.
     * 保存服务端飞行计划，地面目标保持固定；旧存档默认不增加高度。
     */
    public int flightLift;
    public int markerOwnerEntityId = -1;
    private int markerLandedAt = -1;
    private int clusterPlantedAt = -1, clusterReleased;
    private boolean crateLanded;
    private boolean departureVoiceSent;
    public boolean autopilot, burstDone;
    public String cargo = "";
    private UUID openingPlayer;
    private int openingTicks;
    private LivingEntity trackedTarget;
    private int lockTicks, burstShots, burstPauseUntil;
    private boolean targetLockAnnounced;
    private Vec3 delayedMotion;
    private double projectileSpeed;
    private float previousAimYaw, previousAimPitch;
    private float previousFlightYaw, previousFlightPitch;
    private boolean aimPoseInitialized;
    private boolean flightPoseInitialized;
    private Vec3 departureStart = Vec3.ZERO;
    private int departureAge;
    private float departureYaw, departurePitch;
    private UUID vtolSuccessor;
    public AircraftCrash.State crash;

    public boolean crashing() {
        return AircraftCrashLibrary.stage(stage());
    }

    public void beginCrash(int phase) {
        entityData.set(DEPARTING, false);
        entityData.set(CRASH_CANOPY, false);
        entityData.set(FIRING, 0);
        trackedTarget = null;
        autopilot = false;
        stage(phase);
        crashClock(0);
    }

    public void crashClock(int ticks) {
        entityData.set(NATIVE_CLOCK, ticks);
    }

    public boolean crashCanopy() {
        return entityData.get(CRASH_CANOPY);
    }

    /**
     * Cargo is server-only; stages 4/5 are the synced SAE mother/submunition variants.
     * cargo 只在服务端使用，阶段 4 和 5 表示已同步的 SAE 母弹与子弹药变体。
     */
    public boolean hiddenSaePayload() {
        return kind() == Kind.BOMB && (Math.abs(stage()) == 4 || Math.abs(stage()) == 5);
    }

    public void crashCanopy(boolean ejected) {
        entityData.set(CRASH_CANOPY, ejected);
    }

    public void cancelVtolSuccessor() {
        if (vtolSuccessor != null && level() instanceof ServerLevel server
                && server.getEntity(vtolSuccessor) instanceof TacticalUnitEntity next)
            next.discard();
        vtolSuccessor = null;
    }

    public TacticalUnitEntity(EntityType<? extends TacticalUnitEntity> type, Level level) {
        super(type, level);
        setNoAi(true);
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(KIND, 0);
        b.define(STAGE, 0);
        b.define(AIM_YAW, 0F);
        b.define(AIM_PITCH, 0F);
        b.define(FIRING, 0);
        b.define(FLIGHT_YAW, 0F);
        b.define(FLIGHT_PITCH, 0F);
        b.define(NATIVE_CLOCK, 0);
        b.define(DEPARTING, false);
        b.define(CRASH_CANOPY, false);
    }

    public boolean departing() {
        return entityData.get(DEPARTING);
    }

    public boolean awaitingVtolEntry() {
        return kind() == Kind.VTOL && stage() == -12;
    }

    @Override
    public boolean isInvisible() {
        return awaitingVtolEntry() || super.isInvisible();
    }

    private boolean vtolPatrol() {
        return kind() == Kind.VTOL && cargo.equals("vtol_patrol");
    }

    public boolean onStation() {
        return !departing() && !crashing()
                && (kind() == Kind.VTOL ? vtolPatrol() && age - delay >= NativeVtolFlight.SECOND_INGRESS_TICKS
                        : age - delay >= AircraftFlight.approachTicks(kind()));
    }

    public void beginDeparture() {
        if (level().isClientSide || departing() || crashing() || isRemoved() || !AircraftFlight.persistent(kind()))
            return;
        departureStart = position();
        departureAge = age;
        departureYaw = flightYaw();
        departurePitch = flightPitch();
        entityData.set(DEPARTING, true);
        entityData.set(FIRING, 0);
        trackedTarget = null;
        setDeltaMovement(Vec3.ZERO);
        if (kind() == Kind.VTOL)
            entityData.set(NATIVE_CLOCK, 0);
        announceDeparture();
        if (kind() == Kind.VTOL)
            TacticalRuntime.sound((ServerLevel) level(), position(),
                    NativeAudio.sfx("vtol_leave", StreakSounds.JET.get()), .8F, 1);
    }

    /**
     * A visual-only far-distance proxy never ticks AI or participates in combat.
     * 远距离代理仅供显示，不执行 AI 更新，也不参与战斗。
     */
    public void visualSnapshot(int clock, int stage, float yaw, float pitch, boolean leaving) {
        entityData.set(NATIVE_CLOCK, clock);
        entityData.set(DEPARTING, leaving);
        stage(stage);
        flightOrientation(yaw, pitch);
        previousFlightYaw = flightYaw();
        previousFlightPitch = flightPitch();
        flightPoseInitialized = true;
        tickCount = clock;
    }

    public float nativeClock(float partial) {
        return entityData.get(NATIVE_CLOCK) + partial;
    }

    public void cruiseClock(int ticks) {
        if (kind() == Kind.CRUISE) {
            entityData.set(NATIVE_CLOCK, Math.min(NativeCruiseFlight.DURATION, ticks));
            stage(ticks >= NativeCruiseFlight.HANDOFF_TICK ? 1 : 0);
        }
    }

    public boolean firing() {
        return entityData.get(FIRING) > 0;
    }

    public void noteShot() {
        entityData.set(FIRING, 6);
    }

    public Kind kind() {
        return Kind.values()[Mth.clamp(entityData.get(KIND), 0, Kind.values().length - 1)];
    }

    public void kind(Kind k) {
        entityData.set(KIND, k.ordinal());
        refreshDimensions();
        setNoGravity(!groundUnit());
    }

    public int stage() {
        return entityData.get(STAGE);
    }

    public void stage(int n) {
        entityData.set(STAGE, n);
    }

    public float aimYaw() {
        return entityData.get(AIM_YAW);
    }

    public float aimPitch() {
        return entityData.get(AIM_PITCH);
    }

    public float renderAimYaw(float partialTick) {
        return Mth.rotLerp(partialTick, previousAimYaw, aimYaw());
    }

    public float renderAimPitch(float partialTick) {
        return Mth.lerp(partialTick, previousAimPitch, aimPitch());
    }

    /**
     * Sensor aim changes must not turn the aircraft hull away from its velocity.
     * 传感器瞄准变化不应让飞机机体偏离飞行速度方向。
     */
    public float flightYaw() {
        return entityData.get(FLIGHT_YAW);
    }

    public float flightPitch() {
        return entityData.get(FLIGHT_PITCH);
    }

    public float renderFlightYaw(float partialTick) {
        return Mth.rotLerp(partialTick, previousFlightYaw, flightYaw());
    }

    public float renderFlightPitch(float partialTick) {
        return Mth.lerp(partialTick, previousFlightPitch, flightPitch());
    }

    public void flightOrientation(float yaw, float pitch) {
        entityData.set(FLIGHT_YAW, Mth.wrapDegrees(yaw));
        entityData.set(FLIGHT_PITCH, Mth.clamp(pitch, -90, 90));
        setYRot(flightYaw());
        yBodyRot = flightYaw();
        yHeadRot = flightYaw();
        if (AircraftFlight.visible(kind()))
            setBoundingBox(makeBoundingBox());
    }

    public void aim(float yaw, float pitch) {
        entityData.set(AIM_YAW, yaw);
        entityData.set(AIM_PITCH, pitch);
        setXRot(pitch);
        if (groundTurret())
            setYHeadRot(yaw);
    }

    private boolean groundTurret() {
        return kind() == Kind.SENTRY || kind() == Kind.WHEELSON;
    }

    private boolean independentFlightPose() {
        return AircraftFlight.aircraft(kind()) || kind() == Kind.CRUISE;
    }

    @Override
    protected float tickHeadTurn(float bodyYaw, float movement) {
        if (!groundTurret())
            return super.tickHeadTurn(bodyYaw, movement);
        // Mechanical mounts keep the chassis facing its placement/driving direction. The gun turns independently.
        // 中文：机械武器座的底盘保持放置或行驶朝向，枪炮独立转动。
        yBodyRot = getYRot();
        yHeadRot = aimYaw();
        return movement;
    }

    /**
     * Call after configuring a new entity, before adding it to the level. Saved entities keep their saved position.
     * 新实体配置完成后、加入世界之前调用；从存档加载的实体保留已保存的位置。
     */
    public void initializeFlight() {
        Vec3 d = Vec3.directionFromRotation(0, heading);
        Vec3 first = switch (kind()) {
            case UAV, ADVANCED_UAV -> AircraftFlight.approach(center, heading, 0, 60,
                    kind() == Kind.UAV ? UAV_HEIGHT : ADVANCED_UAV_HEIGHT, .022);
            case COUNTER_UAV -> center.add(0, COUNTER_UAV_HEIGHT, 3);
            case GUNSHIP -> GunshipFlight.position(center, heading, 0);
            case PRECISION -> PrecisionFlight.position(center, heading, 0, flightLift);
            case SAE, STEALTH -> AircraftFlight.passPosition(kind(), center, heading, 0);
            case VTOL -> vtolPatrol() ? NativeVtolFlight.secondPosition(center, heading, 0)
                    : NativeVtolFlight.firstPosition(center, heading, 0);
            case DELIVERY -> DeliveryFlight.position(center, heading, 0);
            case OVERWATCH -> AircraftFlight.approach(center, heading, 0, 18, 30, .028);
            default -> position();
        };
        if (kind() != Kind.PRECISION && (AircraftFlight.aircraft(kind()) || kind() == Kind.CRUISE))
            first = airPosition(first);
        if (level() instanceof ServerLevel && AircraftFlight.visible(kind()))
            TacticalRuntime.keepLoaded(this, first);
        setPos(first.x,
                kind() == Kind.CRUISE ? NativeCruiseFlight.clamp(level(), first).y
                        : Mth.clamp(first.y,
                                groundUnit() ? level().getMinBuildHeight() + 1 : level().getMinBuildHeight() + 3,
                                groundUnit() ? level().getMaxBuildHeight() - 3 : level().getMaxBuildHeight() - 4),
                first.z);
        lifetime += vtolPatrol() ? NativeVtolFlight.SECOND_INGRESS_TICKS : AircraftFlight.approachTicks(kind());
        if (kind() == Kind.SAE || kind() == Kind.STEALTH)
            lifetime = AircraftFlight.PASS_LIFETIME;
        float initialYaw = kind() == Kind.COUNTER_UAV ? -90
                : vtolPatrol() ? NativeVtolFlight.secondYaw(heading, 0) : heading;
        setYRot(initialYaw);
        yBodyRot = initialYaw;
        yHeadRot = initialYaw;
        aim(initialYaw, 0);
        if (!groundUnit())
            flightOrientation(initialYaw, 0);
        if (kind() == Kind.PRECISION)
            flightOrientation(initialYaw, 15);
        if (kind() == Kind.VTOL) {
            entityData.set(NATIVE_CLOCK, NativeVtolFlight.animationClock(vtolPatrol(), 0));
            stage(vtolPatrol() ? (delay > 0 ? -12 : 3) : 0);
        }
        if (kind() == Kind.CHOPPER)
            ChopperIngress.initialize(this);
        if (kind() == Kind.ROCKET || kind() == Kind.BOMB) {
            setNoGravity(poweredProjectile());
            setDiscardFriction(poweredProjectile());
            projectileSpeed = getDeltaMovement().length();
            orientProjectile(getDeltaMovement());
            if (delay > 0)
                delayedMotion = getDeltaMovement();
        }
    }

    public ServerPlayer owner() {
        if (ownerId == null || !(level() instanceof ServerLevel l))
            return null;
        ServerPlayer player = l.getServer().getPlayerList().getPlayer(ownerId);
        return player != null ? player : l.getPlayerByUUID(ownerId) instanceof ServerPlayer local ? local : null;
    }

    public boolean groundUnit() {
        return switch (kind()) {
            case SENTRY, WHEELSON, CLUSTER, MINE, CRATE, JUGG_CRATE, BOMB, ROCKET, SMOKE_MARKER -> true;
            default -> false;
        };
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        Kind k = kind();
        float w = switch (k) {
            case SENTRY -> 1.1F;
            case WHEELSON -> 1.7F;
            case CRATE, JUGG_CRATE -> 1.4F;
            case SMOKE_MARKER -> .15F;
            case MINE, CLUSTER, BOMB, ROCKET -> 0.45F;
            case BOMB_DRONE, CRUISE -> 0.8F;
            case GUNSHIP, STEALTH -> 8F;
            case UAV, COUNTER_UAV, ADVANCED_UAV -> 2.5F;
            default -> 4F;
        };
        float h = switch (k) {
            case SENTRY -> 1.7F;
            case WHEELSON -> 1.05F;
            case MINE, CLUSTER -> 0.22F;
            case CRATE, JUGG_CRATE -> 1.2F;
            default -> Math.min(w, 2F);
        };
        return EntityDimensions.scalable(w, h)
                .withEyeHeight(k == Kind.WHEELSON ? 0.95F : k == Kind.SENTRY ? 1.4F : 0.5F);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (KIND.equals(key))
            refreshDimensions();
        if (AIM_YAW.equals(key)) {
            if (!aimPoseInitialized)
                previousAimYaw = aimYaw();
            if (groundTurret())
                setYHeadRot(aimYaw());
        }
        if (AIM_PITCH.equals(key) && !aimPoseInitialized)
            previousAimPitch = aimPitch();
        if (FLIGHT_YAW.equals(key) && !flightPoseInitialized)
            previousFlightYaw = flightYaw();
        if (FLIGHT_PITCH.equals(key) && !flightPoseInitialized)
            previousFlightPitch = flightPitch();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected AABB makeBoundingBox() {
        AABB aircraft = entityData == null ? null : AircraftBounds.box(this);
        return aircraft == null ? super.makeBoundingBox() : aircraft;
    }

    @Override
    public boolean isControlledByLocalInstance() {
        return !level().isClientSide;
    }

    @Override
    public boolean isPickable() {
        return super.isPickable() && !awaitingVtolEntry() && !crashing();
    }

    @Override
    public boolean canBeCollidedWith() {
        return !awaitingVtolEntry() && !crashing();
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        // Small shells formerly inherited a ~29-block cutoff from their combat hitbox.
        // 中文：小型炮弹原先因战斗碰撞箱较小，继承了约 29 格的渲染截止距离。
        return kind() == Kind.CRUISE ? distance < 768D * 768D
                : AircraftFlight.aircraft(kind()) ? distance < 512D * 512D
                        : kind() == Kind.SMOKE_MARKER ? distance < 128D * 128D
                                : kind() == Kind.BOMB || kind() == Kind.ROCKET ? distance < 256D * 256D
                                        : super.shouldRenderAtSqrDistance(distance);
    }

    @Override
    protected void registerGoals() {}

    /**
     * Reward classification, independent of whether a remote operator is currently connected.
     * 奖励类别判断与当前是否有遥控操作者连接无关。
     */
    public boolean canBeDestroyed() {
        return switch (kind()) {
            case UAV, COUNTER_UAV, ADVANCED_UAV, BOMB_DRONE, CRUISE, WHEELSON, CHOPPER, GUNSHIP, VTOL, OVERWATCH,
                    SENTRY ->
                true;
            default -> false;
        };
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !canBeDestroyed() || crashing() || awaitingVtolEntry() || super.isInvulnerableTo(source);
    }

    @Override
    public boolean hurt(DamageSource source, float damage) {
        if (!canBeDestroyed() || crashing() || awaitingVtolEntry())
            return false;

        // 允许击毁的机体接受武器直接伤害，包括单人世界中所有者的射击；自动支援火力的选敌规则仍排除友军载具。
        if (!AircraftFlight.aircraft(kind()) && kind() != Kind.SENTRY && kind() != Kind.WHEELSON
                && source.getEntity() instanceof ServerPlayer p && owner() != null
                && FriendlyPlayers.allied(p, owner()))
            return false;
        if (kind() == Kind.BOMB_DRONE && !level().isClientSide) {
            ResearchSystem.destroyed(this);
            TacticalRuntime.blast(this, position(), 5, 32);
            discard();
            return true;
        }
        return super.hurt(source, damage);
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide)
            ResearchSystem.destroyed(this);
        if (AircraftCrash.begin(this, source))
            return;
        if (!level().isClientSide && vtolSuccessor != null && level() instanceof ServerLevel server
                && server.getEntity(vtolSuccessor) instanceof TacticalUnitEntity next)
            next.discard();
        if (level() instanceof ServerLevel l) {
            StrikeRadioReports.destroyed(this);
            NativeFxDelivery.emit(l, NativeFxDelivery.destruction(kind()), position(), 6);
            if (kind() == Kind.BOMB_DRONE)
                TacticalRuntime.blast(this, position(), 5, 32);
            else {
                l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
                boolean helicopter = kind() == Kind.CHOPPER || kind() == Kind.OVERWATCH || kind() == Kind.DELIVERY;
                TacticalRuntime.sound(l, position(),
                        helicopter ? NativeAudio.sfx("helicopter_blast", StreakSounds.EXPLOSION.get())
                                : StreakSounds.EXPLOSION.get(),
                        1F, helicopter ? 1 : .9F);
            }
        }
        discard();
        if (radioStrike == null)
            NativeAudio.voice(owner(), NativeAudio.reward(this), "destroyed");
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        super.remove(reason);
        if (!level().isClientSide
                && (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED))
            StrikeRadioReports.removed(this);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer p))
            return InteractionResult.SUCCESS;
        if (kind() == Kind.CRATE || kind() == Kind.JUGG_CRATE) {
            if (!onGround() || openingPlayer != null)
                return InteractionResult.CONSUME;
            openingPlayer = p.getUUID();
            openingTicks = 0;
            return InteractionResult.CONSUME;
        }
        if (p == owner() && kind() == Kind.SENTRY && p.isShiftKeyDown()) {
            var kit = KillstreakcodMod.SENTRY_GUN.get().getDefaultInstance();
            CompoundTag data = new CompoundTag();
            data.putInt("Remaining", Math.max(1, lifetime - age));
            data.putFloat("Health", getHealth());
            data.put("ResearchNodes", ResearchCatalog.saveNodes(researchUpgrades));
            kit.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.of(data));
            KillstreakService.giveItem(p, kit);
            TacticalRuntime.sound(p.serverLevel(), position(), StreakSounds.SENTRY_PICKUP.get(), .7F, 1);
            discard();
            return InteractionResult.CONSUME;
        }
        if (p == owner() && kind() == Kind.WHEELSON) {
            TacticalRuntime.linkControl(p, this);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void tick() {
        if (level().isClientSide) {
            previousAimYaw = aimYaw();
            previousAimPitch = aimPitch();
            aimPoseInitialized = true;
        }
        if (level().isClientSide) {
            previousFlightYaw = flightYaw();
            previousFlightPitch = flightPitch();
            flightPoseInitialized = true;
        }
        if (crashing()) {
            noPhysics = true;
            setNoGravity(true);
            setDeltaMovement(Vec3.ZERO);
            super.tick();
            if (!level().isClientSide)
                AircraftCrash.tick(this);
            return;
        }
        Vec3 previousPosition = position();
        boolean waiting = age < delay;
        if (!level().isClientSide && waiting && kind() == Kind.CLUSTER) {
            ServerPlayer operator = owner();
            if (operator == null || !operator.isAlive() || operator.level() != level()
                    || TacticalRuntime.mgbDetonating(operator.serverLevel())) {
                discard();
                return;
            }
            Vec3 direction = operator.getLookAngle(), hand = operator.getEyePosition().add(direction.scale(.7));
            setPos(hand);
            setOldPosAndRot();
            heading = operator.getYRot();
            delayedMotion = direction.scale(.85).add(0, .15, 0);
        }
        if (!level().isClientSide && age % 10 == 0)
            TacticalRuntime.keepLoaded(this, position());
        if (!level().isClientSide && waiting) {
            if (delayedMotion == null)
                delayedMotion = getDeltaMovement();
            setNoGravity(true);
            setDeltaMovement(Vec3.ZERO);
        } else if (!level().isClientSide && (kind() == Kind.ROCKET || kind() == Kind.BOMB)) {
            boolean mortar = kind() == Kind.BOMB && cargo.equals("mortar");
            setNoGravity(poweredProjectile() || mortar);
            if (mortar)
                setDeltaMovement(MortarStrikeFlight.velocity(previousPosition, center));
        }
        Vec3 projectedPosition = previousPosition.add(getDeltaMovement());
        super.tick();
        if (level().isClientSide)
            return;
        ServerLevel l = (ServerLevel) level();
        ServerPlayer player = owner();
        age++;
        if (entityData.get(FIRING) > 0)
            entityData.set(FIRING, entityData.get(FIRING) - 1);
        if (age < delay)
            return;
        if (waiting && vtolPatrol()) {

            // 预留机体此时仍不可见，显示前重新检查新加载的地形，避免已可见的飞机突然向上瞬移。
            if (player == null || player.level() != l) {
                discard();
                return;
            }
            int approved = AircraftAirspace.selectLift(player, Kind.VTOL, center, heading);
            if (approved < 0) {
                discard();
                return;
            }
            flightLift = Math.max(flightLift, approved);
            Vec3 start = airPosition(NativeVtolFlight.secondPosition(center, heading, 0));
            setPos(start.x, Mth.clamp(start.y, l.getMinBuildHeight() + 3, l.getMaxBuildHeight() - 4), start.z);
            setOldPosAndRot();
        }
        if (waiting && delayedMotion != null) {
            if (kind() == Kind.CLUSTER)
                stage(0);
            setDeltaMovement(delayedMotion);
            delayedMotion = null;
            if (kind() == Kind.BOMB && stage() <= -3)
                stage(-stage());
            if (kind() == Kind.PRECISION)
                stage(0);
        }
        if (groundUnit() && kind() != Kind.WHEELSON)
            setNoGravity(poweredProjectile() || kind() == Kind.CLUSTER && clusterPlantedAt >= 0);
        int t = age - delay;
        if (AircraftFlight.aircraft(kind()))
            entityData.set(NATIVE_CLOCK, kind() == Kind.VTOL ? NativeVtolFlight.animationClock(vtolPatrol(), t) : t);
        if (player == null && kind() != Kind.CRATE && kind() != Kind.JUGG_CRATE) {
            discard();
            return;
        }
        if (player != null && player.level() != l && kind() != Kind.CRATE && kind() != Kind.JUGG_CRATE) {
            discard();
            return;
        }
        if (t >= lifetime && !departing()) {
            StrikeRadioReports.timedOut(this);
            if (AircraftFlight.persistent(kind()))
                beginDeparture();
            else {
                if (kind() == Kind.BOMB_DRONE)
                    TacticalRuntime.blast(this, position(), 5, 32);
                if (kind() == Kind.WHEELSON)
                    TacticalRuntime.sound(l, position(), StreakSounds.WHEELSON_STOP.get(), .7F, 1);
                discard();
                if (radioStrike == null && !departureVoiceSent)
                    NativeAudio.ended(player, NativeAudio.reward(this));
                return;
            }
        }
        if (departing()) {
            int elapsed = age - departureAge;
            if (kind() == Kind.VTOL)
                entityData.set(NATIVE_CLOCK, elapsed);
            flight(AircraftFlight.departure(departureStart, departureYaw, departurePitch, kind(), elapsed));
            if (elapsed >= AircraftFlight.DEPARTURE_TICKS)
                discard();
            return;
        }

        ResearchEffects.unitTick(this);

        // 飞机引擎音通过 SupportAudio 快照发送，也覆盖实体跟踪范围外的载具。
        if (t == 1) {
            if (kind() == Kind.VTOL)
                TacticalRuntime.sound(l, position(), NativeAudio.sfx("vtol_approach", StreakSounds.JET.get()), 1, 1);
            if (kind() == Kind.PRECISION)
                TacticalRuntime.sound(l, position(), StreakSounds.AIRSTRIKE_APPROACH.get(), 1, 1);
            else if (kind() == Kind.STEALTH)
                TacticalRuntime.sound(l, position(), StreakSounds.STEALTH_APPROACH.get(), 1, 1);
            else if (kind() == Kind.OVERWATCH)
                TacticalRuntime.sound(l, position(), StreakSounds.CHOPPER_APPROACH.get(), .8F, 1);
        }
        if (kind() == Kind.SAE && t == 40)
            TacticalRuntime.sound(l, position(), NativeAudio.sfx("sae21_approach", StreakSounds.SAE_APPROACH.get()), 1,
                    1);
        if (t == StreakTimings.entry(NativeAudio.reward(this)) + 1
                && (kind() == Kind.GUNSHIP || kind() == Kind.CHOPPER))
            NativeAudio.voice(player, NativeAudio.reward(this), "engage");
        if (cargo.equals("mortar") && t == 2)
            TacticalRuntime.sound(l, position(), NativeAudio.sfx("mortar_incoming", StreakSounds.RADIO.get()), .85F, 1);
        if (getHealth() < getMaxHealth() * .4F && age % 20 == 0) {
            NativeAudio.voice(player, NativeAudio.reward(this), "damage");
            NativeFxDelivery.emit(l, "smoke", position(), 3);
        }
        if (getHealth() < getMaxHealth() * 0.4F && age % 4 == 0)
            l.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.6, getZ(), 3, .5, .2, .5, .01);
        switch (kind()) {
            case UAV, ADVANCED_UAV -> {
                double h = kind() == Kind.UAV ? UAV_HEIGHT : ADVANCED_UAV_HEIGHT;
                plannedFlight(t < AircraftFlight.APPROACH_TICKS
                        ? AircraftFlight.approach(center, heading, t, 60, h, .022)
                        : AircraftFlight.orbit(center, heading, t - AircraftFlight.APPROACH_TICKS, 60, h, .022));
            }
            case COUNTER_UAV -> {
                Vec3 v = center.add(Math.sin(t * .025) * 3, COUNTER_UAV_HEIGHT, Math.cos(t * .025) * 3);
                plannedFlight(v);
            }
            case PRECISION, SAE, STEALTH -> pass(t);
            case VTOL -> {
                if (vtolPatrol()) {
                    stage(t < NativeVtolFlight.SECOND_INGRESS_TICKS ? 3 : 1);
                    plannedFlight(NativeVtolFlight.secondPosition(center, heading, t));
                    if (isRemoved())
                        return;
                    flightOrientation(NativeVtolFlight.secondYaw(heading, t), NativeVtolFlight.secondPitch(heading, t,
                            center.y + flightLift, l.getMinBuildHeight() + 3, l.getMaxBuildHeight() - 4));
                    if (onStation())
                        automaticFire(72, 4, 3.5F, true);
                    break;
                }
                stage(t <= NativeVtolFlight.STRIKE_END ? 0 : 2);
                if (t > NativeVtolFlight.STRIKE_END)
                    reserveVtolPatrol(player, t);
                if (t == NativeVtolFlight.STRIKE_END + 1) {
                    entityData.set(FIRING, 0);
                    StrikeRadioReports.attackFinished(this);
                    TacticalRuntime.sound(l, position(), NativeAudio.sfx("vtol_leave", StreakSounds.JET.get()), .8F, 1);
                }
                plannedFlight(NativeVtolFlight.firstPosition(center, heading, t));
                if (isRemoved())
                    return;
                if (t >= NativeVtolFlight.FIRST_EXIT_END) {
                    discard();
                    return; // 预留机体保留自己已保存的入场倒计时。
                }
                if (t >= NativeVtolFlight.ATTACK_TICK && t <= NativeVtolFlight.STRIKE_END
                        && (t - NativeVtolFlight.ATTACK_TICK) % 3 == 0) {
                    boolean left = ((t - NativeVtolFlight.ATTACK_TICK) / 3) % 2 == 0;
                    Vec3 muzzle = VtolOrdnance.muzzle(this, left);
                    Vec3 target = TacticalRuntime.ground(l, NativeVtolFlight.rocketTarget(center, heading, t)).add(0,
                            .4, 0);
                    Vec3 velocity = target.subtract(muzzle).normalize().scale(3.2);
                    TacticalRuntime.spawnChild(this, Kind.ROCKET, muzzle, target, 100, flightYaw(), 0, "vtol_rocket",
                            velocity);
                    noteShot();
                    TacticalRuntime.sound(l, muzzle, StreakSounds.CRUISE_BOOST.get(), .3F, 1.3F);
                }
            }
            case OVERWATCH -> {
                if (t < AircraftFlight.APPROACH_TICKS)
                    plannedFlight(AircraftFlight.approach(center, heading, t, 18, 30, .028));
                else {
                    center = player.position();
                    Vec3 destination = airPosition(
                            AircraftFlight.orbit(center, heading, t - AircraftFlight.APPROACH_TICKS, 18, 30, .028));
                    flight(position().lerp(destination, .18));
                    automaticFire(64, 5, 3, false);
                }
            }
            case GUNSHIP -> {
                if (!TacticalRuntime.controlled(this))
                    beginDeparture();
                else {
                    center = player.position();
                    plannedFlight(GunshipFlight.position(center, heading, t));
                }
            }
            case CHOPPER, BOMB_DRONE, CRUISE -> {
                if (!TacticalRuntime.controlled(this)) {
                    if (kind() == Kind.CHOPPER)
                        beginDeparture();
                    else {
                        if (kind() == Kind.BOMB_DRONE)
                            TacticalRuntime.blast(this, position(), 5, 32);
                        discard();
                    }
                } else if (kind() == Kind.BOMB_DRONE && isInWater()) {
                    TacticalRuntime.blast(this, position(), 5, 32);
                    discard();
                }
            }
            case DELIVERY -> delivery(t);
            case SMOKE_MARKER -> smokeMarker(l, player);
            case SENTRY -> automaticFire(36, 3, 3, false);
            case WHEELSON -> {
                if (autopilot && !TacticalRuntime.controlled(this))
                    automaticFire(36, 4, 4, false);
                if (isInWater()) {
                    setNoGravity(true);
                    setDeltaMovement(getDeltaMovement().multiply(.9, 0, .9).add(0, .04, 0));
                } else
                    setNoGravity(false);
            }
            case CLUSTER -> {
                if (clusterPlantedAt < 0 && (onGround() || horizontalCollision || verticalCollision && t > 0)) {
                    clusterPlantedAt = age;
                    stage(10);
                    setNoGravity(true);
                    setDeltaMovement(Vec3.ZERO);
                    TacticalRuntime.sound(l, position(),
                            NativeAudio.sfx("cluster_plant", StreakSounds.SENTRY_PLANT.get()), .85F, 1);
                    NativeFxDelivery.emit(l, "cluster21_plant", position(), .65F);
                }
                if (clusterPlantedAt >= 0) {
                    setDeltaMovement(Vec3.ZERO);
                    if (age - clusterPlantedAt >= NativeArsenalLibrary.releaseWait() + clusterReleased
                            && clusterReleased < 4) {
                        Vec3 socket = NativeArsenalLibrary.discOffset(clusterReleased, heading),
                                radial = socket.normalize();
                        Vec3 q = position().add(socket).add(radial.scale(.0508));
                        TacticalRuntime.spawnChild(this, Kind.MINE, q, q, 1200, heading, 0, "",
                                radial.scale(.381).add(0, .254, 0));
                        if (clusterReleased == 0)
                            TacticalRuntime.sound(l, position(),
                                    NativeAudio.sfx("cluster_release", StreakSounds.SENTRY_PLANT.get()), .85F, 1);
                        stage(10 + (++clusterReleased));
                    }
                    if (clusterReleased >= 4)
                        discard();
                }
            }
            case MINE -> {
                if (stage() == 2) {
                    if (age >= shotAt) {
                        ResearchEffects.splitMine(this);
                        TacticalRuntime.blast(this, position(), 5.5F, 35);
                        discard();
                    }
                } else {
                    stage(t > 20 ? 1 : 0);
                    if (t > 20 && onGround() && TacticalRuntime.nearest(this, 4.5, false) != null) {
                        stage(2);
                        shotAt = age + 6;
                        setDeltaMovement(0, .36, 0);
                        TacticalRuntime.sound(l, position(),
                                NativeAudio.sfx("cluster_trigger", StreakSounds.RADIO.get()), .65F, 1);
                    }
                }
            }
            case CRATE, JUGG_CRATE -> openCrate(l, player);
            case BOMB, ROCKET -> {
                if (!waiting) {
                    Vec3 impact = projectileImpact(l, previousPosition, projectedPosition);
                    if (impact != null) {
                        projectileBlast(impact);
                        return;
                    }
                }
                if (onGround() || horizontalCollision || verticalCollision && t > 0) {
                    projectileBlast(position().add(0, .2, 0));
                    return;
                }
                if (cargo.equals("mortar") && position().distanceToSqr(center) <= .25) {
                    projectileBlast(position());
                    return;
                }
                if (cargo.equals("sae_parent") && (getY() <= center.y + 12 || t >= 20)) {
                    releaseSaeBomblets(position());
                    discard();
                    return;
                }
                if (cargo.equals("guided")) {
                    Vec3 d = center.subtract(position()).normalize();
                    setDeltaMovement(getDeltaMovement().lerp(d.scale(2.1), .18));
                    if (position().distanceToSqr(center) < 4) {
                        projectileBlast(position());
                        return;
                    }
                } else if (poweredProjectile() && getDeltaMovement().lengthSqr() > .001) {
                    if (projectileSpeed <= 0)
                        projectileSpeed = getDeltaMovement().length();
                    setDeltaMovement(getDeltaMovement().normalize().scale(projectileSpeed));
                }
                orientProjectile(cargo.equals("mortar") ? position().subtract(previousPosition) : getDeltaMovement());
                if (kind() == Kind.ROCKET) {
                    projectileTrail(l, previousPosition, position());
                    TracerDelivery.projectile(l, this, previousPosition, position());
                }
                if (cargo.equals("sae_parent") || cargo.equals("sae_bomblet"))
                    SaeEffects.trail(l, this, previousPosition, position(), t);
                else if (cargo.equals("mortar") && t % 2 == 0)
                    l.sendParticles(ParticleTypes.SMOKE, getX(), getY() + .25, getZ(), 1, .015, .015, .015, 0);
                else if (t % 2 == 0)
                    l.sendParticles(ParticleTypes.SMOKE, getX(), getY(), getZ(), 2, .1, .1, .1, .01);
            }
        }
        if (kind() == Kind.SENTRY || kind() == Kind.WHEELSON) {
            setYBodyRot(getYRot());
        }
        if ((kind() == Kind.CRATE || kind() == Kind.JUGG_CRATE) && !onGround()) {
            setDeltaMovement(getDeltaMovement().multiply(.8, 0, .8).add(0, -.18, 0));
            stage(-1);
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    public Vec3 airPosition(Vec3 nominal) {
        return nominal.add(0, flightLift, 0);
    }

    private void plannedFlight(Vec3 nominal) {
        flight(airPosition(nominal));
    }

    public void flight(Vec3 v) {
        if (isRemoved())
            return;
        v = kind() == Kind.CRUISE ? NativeCruiseFlight.clamp(level(), v)
                : new Vec3(v.x, Mth.clamp(v.y, level().getMinBuildHeight() + 3, level().getMaxBuildHeight() - 4), v.z);
        if (!crashing() && !AircraftAirspace.movement(this, position(), v)) {

            // 扫射完成后的退场航线可能遇到新加载地形；此时安全移除载具，不再提示已经完成的呼叫失败。
            boolean completedPass = departing() || departureVoiceSent
                    || kind() == Kind.PRECISION && age - delay > PrecisionFlight.ATTACK_END
                    || kind() == Kind.VTOL && stage() == 2;
            ServerPlayer operator = owner();
            if (operator != null && !completedPass)
                operator.displayClientMessage(Component.translatable("message.killstreakcod.flight_blocked"), true);
            if (!(kind() == Kind.VTOL && stage() == 2 && vtolSuccessor != null))
                announceDeparture();
            discard();
            return;
        }
        if (!level().isClientSide)
            TacticalRuntime.keepLoaded(this, v);
        Vec3 delta = v.subtract(position());
        setDeltaMovement(Vec3.ZERO);
        setPos(v);
        if (independentFlightPose()) {
            if (delta.lengthSqr() > .000001) {
                float yaw = delta.horizontalDistanceSqr() > .000001
                        ? (float) Math.toDegrees(Math.atan2(-delta.x, delta.z))
                        : flightYaw();
                float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance()));
                flightOrientation(yaw, pitch);
            }
            return;
        }
        if (delta.horizontalDistanceSqr() > .0001)
            setYRot((float) (Math.atan2(-delta.x, delta.z) * 180 / Math.PI));
        yBodyRot = getYRot();
        yHeadRot = getYRot();
    }

    private void reserveVtolPatrol(ServerPlayer player, int tick) {
        if (!(level() instanceof ServerLevel server) || player == null)
            return;
        TacticalUnitEntity next = vtolSuccessor == null ? null
                : server.getEntity(vtolSuccessor) instanceof TacticalUnitEntity unit ? unit : null;
        if (vtolSuccessor != null && next == null)
            TacticalRuntime.keepLoaded(this, airPosition(NativeVtolFlight.secondPosition(center, heading, 0)));
        if (vtolSuccessor == null) {
            int remaining = Math.max(NativeVtolFlight.EMPTY_TICKS, NativeVtolFlight.SECOND_ENTRY_START - tick),
                    patrolLife = Math.max(1, lifetime - NativeVtolFlight.INGRESS_TICKS);
            next = TacticalRuntime.spawnConfigured(player, Kind.VTOL, position(), center, patrolLife, heading,
                    remaining, "vtol_patrol", Vec3.ZERO, flightLift);
            vtolSuccessor = next.getUUID();
            StrikeRadioReports.track(next, radioStrike);
        }
        if (next != null && !next.isRemoved())
            next.setHealth(Math.min(next.getMaxHealth(), getHealth()));
    }

    private void pass(int t) {
        if (kind() == Kind.PRECISION) {
            Vec3 intended = PrecisionFlight.position(center, heading, t, flightLift);
            flight(intended);
            if (isRemoved())
                return;
            flightOrientation(heading, PrecisionFlight.pitch(t, flightLift));
            if (t == PrecisionFlight.ATTACK_START)
                TacticalRuntime.sound((ServerLevel) level(), position(), StreakSounds.AIRSTRIKE_FIRE.get(), 1, 1);
            if (t >= PrecisionFlight.ATTACK_START && t <= PrecisionFlight.ATTACK_END
                    && (t - PrecisionFlight.ATTACK_START) % 3 == 0) {
                ServerLevel server = (ServerLevel) level();
                Vec3 muzzle = PrecisionCannonMount.muzzle(this),
                        direction = PrecisionCannonMount.direction(flightYaw(), flightPitch());
                BlockHitResult hit = server.clip(new ClipContext(muzzle,
                        muzzle.add(direction.scale(640 + flightLift / Math.sin(Math.toRadians(15)))),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
                Vec3 impact = TacticalRuntime.impactPosition(hit);
                noteShot();
                TracerDelivery.shot(server, this, muzzle, hit.getLocation(), TracerDelivery.CANNON);
                if (hit.getType() != HitResult.Type.MISS)
                    TacticalRuntime.blast(this, impact, 4, 24);
            }
            if (t == PrecisionFlight.ATTACK_END + 1)
                StrikeRadioReports.attackFinished(this);
            if (t == 110)
                announceDeparture();
            return;
        }
        Vec3 pass = AircraftFlight.passPosition(kind(), center, heading, t);
        t -= AircraftFlight.PASS_OFFSET;
        Vec3 d = Vec3.directionFromRotation(0, heading);
        plannedFlight(pass);
        if (isRemoved())
            return;
        setYRot(heading);
        if (kind() == Kind.STEALTH && t == 42)
            TacticalRuntime.sound((ServerLevel) level(), position(), StreakSounds.STEALTH_FLYBY.get(), 1, 1);
        if (kind() == Kind.STEALTH && t == 60)
            TacticalRuntime.sound((ServerLevel) level(), position(), StreakSounds.STEALTH_LEAVE.get(), .9F, 1);
        if (t == 30) {
            if (kind() == Kind.PRECISION)
                TacticalRuntime.sound((ServerLevel) level(), position(), StreakSounds.AIRSTRIKE_FIRE.get(), 1, 1);
            else if (kind() == Kind.SAE)
                TacticalRuntime.sound((ServerLevel) level(), position(), StreakSounds.SAE_RELEASE.get(), .8F, 1);
        }
        if (kind() == Kind.SAE && t == 42)
            TacticalRuntime.sound((ServerLevel) level(), position(),
                    NativeAudio.sfx("sae21_flyby", StreakSounds.SAE_APPROACH.get()), 1, 1);
        if (kind() == Kind.SAE && t == 60)
            TacticalRuntime.sound((ServerLevel) level(), position(),
                    NativeAudio.sfx("sae21_leave", StreakSounds.SAE_APPROACH.get()), .9F, 1);
        if (kind() == Kind.SAE) {
            if (t == 30) {
                Vec3 release = position().add(0, -2, 0), fuse = center.add(0, 12, 0);
                TacticalRuntime.spawnChild(this, Kind.BOMB, release, center, 120, heading, 0, "sae_parent",
                        fuse.subtract(release).scale(1D / 20));
            }
            if (t == 31)
                StrikeRadioReports.attackFinished(this);
            if (t == 42)
                announceDeparture();
            return; // 以目标点为中心投放一组集束弹，不使用沿轰炸机航线铺开的地毯轰炸。
        }
        if (t >= 30 && t <= 56 && t % 3 == 0) {
            if (kind() == Kind.STEALTH || kind() == Kind.VTOL) {
                String payload = kind() == Kind.STEALTH ? "stealth" : "vtol";
                TacticalRuntime.spawnChild(this, Kind.BOMB, position().add(0, -2, 0), center, 140, heading, 0, payload,
                        d.scale(.2).add(0, -.8, 0));
            } else {
                Vec3 impact = center.add(d.scale((t - 42) * 1.8));
                impact = TacticalRuntime.ground((ServerLevel) level(), impact);
                Vec3 muzzle = position().add(0, -1, 0);
                TracerDelivery.shot((ServerLevel) level(), this, muzzle, impact, TracerDelivery.CANNON);
                TacticalRuntime.blast(this, impact, 4, 24);
            }
        }
        if (t == 57) {
            StrikeRadioReports.attackFinished(this);
            announceDeparture();
        }
    }

    private void delivery(int t) {
        plannedFlight(DeliveryFlight.position(center, heading, t));
        if (isRemoved())
            return;
        if (t == 135)
            announceDeparture();
        if (t == 65) {
            TacticalRuntime.sound((ServerLevel) level(), position(), StreakSounds.CAREPACKAGE_RELEASE.get(), .8F, 1);
            for (int i = 0; i < (cargo.equals("emergency") ? 3 : 1); i++) {
                Vec3 at = new Vec3(center.x + (i - (cargo.equals("emergency") ? 1 : 0)) * 2.7, getY() - 2, center.z);
                String reward = cargo.equals("juggernaut") ? "juggernaut" : ResearchEffects.packageReward(this).id();
                TacticalRuntime.spawnChild(this, cargo.equals("juggernaut") ? Kind.JUGG_CRATE : Kind.CRATE, at, center,
                        6000, heading, 0, reward, Vec3.ZERO);
            }
        }
    }

    private void announceDeparture() {
        if (departureVoiceSent)
            return;
        departureVoiceSent = true;
        StrikeRadioReports.attackFinished(this);
        if (radioStrike != null)
            StrikeRadioReports.departing(this);
        else
            NativeAudio.departure(owner(), NativeAudio.reward(this));
    }

    private void smokeMarker(ServerLevel level, ServerPlayer player) {
        if (player == null || !player.isAlive() || player.getId() != markerOwnerEntityId
                || TacticalRuntime.mgbDetonating(level)) {
            discard();
            return;
        }
        if (stage() == 0 && (onGround() || isInWater())) {
            stage(1);
            markerLandedAt = age;
            setDeltaMovement(Vec3.ZERO);
        }
        if (stage() > 0) {
            setDeltaMovement(Vec3.ZERO);
            if (age % 3 == 0)
                level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, getX(), getY() + .12, getZ(), 3, .08, .15, .08,
                        .012);
            if (stage() == 1) {
                StreakReward reward = StreakReward.byId(cargo).orElse(StreakReward.CARE_PACKAGE);
                if (!StreakAirspace.target(player, reward, position(), player.getYRot())) {
                    KillstreakService.giveItem(player, TacticalEquipmentItem.stack("drop_marker", reward.id()));
                    discard();
                    return;
                }
                if (!TacticalRuntime.busy(player)) {
                    stage(2);
                    TacticalRuntime.airdrop(player, reward, position());
                } else if (age - markerLandedAt > 120) {
                    KillstreakService.giveItem(player, TacticalEquipmentItem.stack("drop_marker", reward.id()));
                    discard();
                }
            }
        }
    }

    private void openCrate(ServerLevel level, ServerPlayer owner) {
        if (onGround() && !crateLanded) {
            crateLanded = true;
            NativeFxDelivery.emit(level, "landing", position().add(0, .08, 0), 1.8F);
            stage(0);
        }
        if (openingPlayer == null) {
            if (onGround())
                stage(0);
            return;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(openingPlayer);
        if (player == null || !player.isAlive() || player.level() != level || player.distanceToSqr(this) > 16) {
            openingPlayer = null;
            openingTicks = 0;
            stage(0);
            return;
        }
        int duration = owner != null && FriendlyPlayers.allied(player, owner) ? 40 : 80;
        if (ResearchEffects.fastCrate(this))
            duration /= 2;
        openingTicks++;
        stage(Math.min(100, openingTicks * 100 / duration));
        if (openingTicks % 10 == 0)
            player.displayClientMessage(Component.translatable("message.killstreakcod.capture", stage()), true);
        if (openingTicks >= duration) {
            boolean granted = kind() == Kind.JUGG_CRATE ? TacticalRuntime.equipJuggernaut(player)
                    : KillstreakService.grantBonus(player, StreakReward.byId(cargo).orElse(StreakReward.UAV));
            if (!granted) {
                openingPlayer = null;
                openingTicks = 0;
                stage(0);
                return;
            }
            TacticalRuntime.sound(level, position(), StreakSounds.CAREPACKAGE_RETRIEVE.get(), .8F, 1);
            discard();
        }
    }

    /**
     * Keep the original ground acquisition footprint when a patrol was raised.
     * 巡逻高度提高后，保留原有地面索敌覆盖范围。
     */
    public double targetingLift() {
        double base = kind() == Kind.OVERWATCH ? 30 : kind() == Kind.VTOL ? 34 : Double.POSITIVE_INFINITY;
        return Math.max(0, Math.min(flightLift, getY() - center.y - base));
    }

    private void automaticFire(double range, int interval, float damage, boolean incendiary) {
        if (kind() == Kind.SENTRY && ResearchEffects.has(this, "sentry_gun.heavy"))
            damage *= 1.5F;
        ServerPlayer operator = owner();
        if (operator == null || isRemoved())
            return;

        // 一轮点射期间保持可见目标，但目标进入遮挡后立即解除锁定。
        if (!validAutomaticTarget(trackedTarget, operator, range)) {
            trackedTarget = null;
            lockTicks = 0;
            burstShots = 0;
            targetLockAnnounced = false;
        }
        if (trackedTarget == null) {
            trackedTarget = TacticalRuntime.nearest(this, range, kind() == Kind.SENTRY);
            if (trackedTarget != null) {
                lockTicks = 0;
                burstShots = 0;
                shotAt = Math.max(shotAt, age + 4);
            }
        }
        if (trackedTarget == null) {
            float sweep = heading + (float) Math.sin((age - delay) * .025) * 55;
            aim(Mth.approachDegrees(aimYaw(), sweep, 2), Mth.approachDegrees(aimPitch(), 0, 2));
            return;
        }
        Vec3 targetPoint = trackedTarget.position().add(0, trackedTarget.getBbHeight() * .68, 0);
        Vec3 v = targetPoint.subtract(
                kind() == Kind.WHEELSON ? WheelsonMount.muzzle(this, aimYaw(), aimPitch()) : getEyePosition());
        float yaw = (float) (Math.atan2(-v.x, v.z) * 180 / Math.PI),
                pitch = (float) (-Math.atan2(v.y, v.horizontalDistance()) * 180 / Math.PI);
        float turn = switch (kind()) {
            case SENTRY -> 4.5F;
            case WHEELSON -> 4;
            case VTOL -> 2.8F;
            default -> 3.2F;
        };
        float nextYaw = Mth.approachDegrees(aimYaw(), yaw, turn), nextPitch = Mth.approachDegrees(aimPitch(), pitch, 3);
        aim(nextYaw, nextPitch);
        boolean aligned = Math.abs(Mth.wrapDegrees(yaw - nextYaw)) <= 4 && Math.abs(pitch - nextPitch) <= 4;
        lockTicks = aligned ? Math.min(40, lockTicks + 1) : Math.max(0, lockTicks - 2);
        int requiredLock = kind() == Kind.SENTRY || kind() == Kind.WHEELSON ? 12 : 18;
        if (kind() == Kind.SENTRY && lockTicks >= requiredLock && !targetLockAnnounced) {
            targetLockAnnounced = true;
            TacticalRuntime.sound((ServerLevel) level(), position(), StreakSounds.SENTRY_LOCK.get(), .55F, 1);
        }
        if (lockTicks < requiredLock || age < shotAt || age < burstPauseUntil)
            return;
        float spread = kind() == Kind.SENTRY ? 1.1F : kind() == Kind.WHEELSON ? 1.4F : 1.8F;
        Vec3 direction = Vec3.directionFromRotation(nextPitch + (random.nextFloat() - .5F) * spread * 2,
                nextYaw + (random.nextFloat() - .5F) * spread * 2);
        shotAt = age + interval;
        if (kind() == Kind.WHEELSON)
            TacticalRuntime.explosiveFire(this, WheelsonMount.muzzle(this, nextYaw, nextPitch), direction, range,
                    damage, 2, 6);
        else
            TacticalRuntime.fire(this, getEyePosition(), direction, range + targetingLift(), damage, incendiary);
        int burstLength = kind() == Kind.VTOL ? 8 : kind() == Kind.SENTRY ? 7 : 6;
        if (++burstShots >= burstLength
                && !(kind() == Kind.SENTRY && ResearchEffects.has(this, "sentry_gun.continuous"))) {
            burstShots = 0;
            burstPauseUntil = age + (kind() == Kind.SENTRY ? 14 : 20);
            lockTicks = Math.max(requiredLock - 4, 0);
        }
    }

    private boolean validAutomaticTarget(LivingEntity target, ServerPlayer operator, double range) {
        if (target == null || target.level() != level() || !TacticalRuntime.enemy(operator, target)
                || !TacticalRuntime.inTargetRange(this, target, range))
            return false;
        if (kind() == Kind.SENTRY && target.position().subtract(position()).multiply(1, 0, 1).normalize()
                .dot(Vec3.directionFromRotation(0, heading)) < 0)
            return false;
        return TacticalRuntime.clear(level(), getEyePosition(), target.getEyePosition(), this) && TacticalRuntime
                .clear(level(), getEyePosition(), target.position().add(0, target.getBbHeight() * .68, 0), this);
    }

    private boolean poweredProjectile() {
        return kind() == Kind.BOMB && (cargo.equals("sae_parent") || cargo.equals("mortar"))
                || kind() == Kind.ROCKET && (cargo.equals("guided") || cargo.equals("105mm")
                        || cargo.equals("helo_rocket") || cargo.equals("vtol_rocket"));
    }

    private void releaseSaeBomblets(Vec3 release) {
        if (!(level() instanceof ServerLevel l) || owner() == null)
            return;

        // 子弹药从实际释放位置下方散开，提前撞到屋顶时不能让碎片绕过屋顶继续追向原落点。
        Vec3 dispersionCenter = TacticalRuntime.ground(l, release);
        double phase = l.random.nextDouble() * Math.PI * 2;
        SaeEffects.release(l, this, release);
        for (int i = 0; i < 19; i++) {
            double radius = i == 0 ? 0 : Math.sqrt(i / 18D) * SAE_CLUSTER_RADIUS, angle = phase + i * 2.399963229728653;
            Vec3 target = TacticalRuntime.ground(l,
                    dispersionCenter.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius));
            Vec3 from = release.add((l.random.nextDouble() - .5) * .6, 0, (l.random.nextDouble() - .5) * .6);
            Vec3 delta = target.subtract(from);
            Vec3 motion = new Vec3(delta.x / 10, Math.min(-.3, delta.y / 10 + .4), delta.z / 10);
            TacticalRuntime.spawnChild(this, Kind.BOMB, from, dispersionCenter, 100, heading, 0, "sae_bomblet", motion);
        }
    }

    private void orientProjectile(Vec3 velocity) {
        if (velocity.lengthSqr() <= .001)
            return;
        setYRot((float) (Math.atan2(-velocity.x, velocity.z) * 180 / Math.PI));
        setXRot((float) (-Math.atan2(velocity.y, velocity.horizontalDistance()) * 180 / Math.PI));
        yBodyRot = getYRot();
        yHeadRot = getYRot();
    }

    /**
     * Sweep the whole tick's path so a fast shell cannot skip a roof or a thin wall.
     * 检查整个游戏刻的运动路径，防止高速炮弹越过屋顶或薄墙而漏检碰撞。
     */
    private Vec3 projectileImpact(ServerLevel level, Vec3 from, Vec3 to) {
        if (from.distanceToSqr(to) < .00001)
            return null;
        BlockHitResult block = level
                .clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
        Vec3 impact = block.getType() == HitResult.Type.MISS ? null
                : block.getLocation().add(Vec3.atLowerCornerOf(block.getDirection().getNormal()).scale(.12));
        double closest = block.getType() == HitResult.Type.MISS ? from.distanceToSqr(to)
                : from.distanceToSqr(block.getLocation());
        ServerPlayer operator = owner();
        if (operator != null)
            for (Entity target : StreakCombat.targets(level, operator, this, new AABB(from, to).inflate(.4), false)) {
                var hit = StreakCombat.intercept(target, from, to, .2);
                if (hit.isPresent() && from.distanceToSqr(hit.get()) < closest) {
                    closest = from.distanceToSqr(hit.get());
                    impact = hit.get();
                }
            }
        return impact;
    }

    private void projectileBlast(Vec3 at) {
        if (cargo.equals("sae_parent")) {
            releaseSaeBomblets(at);
            discard();
            return;
        }
        float radius, damage;
        switch (cargo) {
            case "sae" -> {
                radius = 10;
                damage = 80;
            }
            case "sae_bomblet" -> {
                radius = SAE_BLAST_RADIUS;
                damage = SAE_BLAST_DAMAGE;
            }
            case "mortar" -> {
                radius = MortarStrikeFlight.BLAST_RADIUS;
                damage = MortarStrikeFlight.BLAST_DAMAGE;
            }
            case "stealth" -> {
                radius = 12;
                damage = 100;
            }
            case "vtol", "vtol_rocket" -> {
                radius = 7;
                damage = 55;
            }
            case "105mm" -> {
                radius = 10;
                damage = 100;
            }
            case "helo_rocket" -> {
                radius = 6;
                damage = 60;
            }
            case "guided" -> {
                radius = 8;
                damage = 55;
            }
            default -> {
                radius = kind() == Kind.ROCKET ? 6 : 5;
                damage = 32;
            }
        }
        TacticalRuntime.blast(this, at, radius, damage);
        discard();
    }

    private void projectileTrail(ServerLevel level, Vec3 from, Vec3 to) {
        if (AircraftFireEffects.projectileTrail(this, from, to))
            return;

        // 原游戏 VTOL 的 tag_fx 位于弹体原点后方 27.917 英寸处。
        Vec3 exhaust = getDeltaMovement().normalize().scale(stage() == 10 ? -.7090918 : -.5);
        int samples = Mth.clamp((int) Math.ceil(from.distanceTo(to) / .65), 2, 8);
        for (int i = 0; i < samples; i++) {
            Vec3 point = from.lerp(to, (i + 1D) / samples).add(exhaust);
            for (ServerPlayer observer : level.players())
                if (observer == owner() || observer.position().distanceToSqr(point) < 192 * 192) {

                    // 即使操作者本体留在地面，强制发送的粒子消息也应能在飞机摄像机视角中看到。
                    level.sendParticles(observer, ParticleTypes.FLAME, true, point.x, point.y, point.z, 2, .05, .05,
                            .05, .006);
                    if (i % 2 == 0)
                        level.sendParticles(observer, ParticleTypes.SMOKE, true, point.x, point.y, point.z, 1, .06, .06,
                                .06, .01);
                }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag n) {
        super.addAdditionalSaveData(n);
        n.putInt("UnitKind", kind().ordinal());
        if (ownerId != null)
            n.putUUID("Operator", ownerId);
        if (kind() == Kind.VTOL) {
            n.putInt("VtolFlightFormat", 3);
            if (vtolSuccessor != null)
                n.putUUID("VtolSuccessor", vtolSuccessor);
        }
        n.putDouble("TargetX", center.x);
        n.putDouble("TargetY", center.y);
        n.putDouble("TargetZ", center.z);
        n.putFloat("Heading", heading);
        n.putInt("Age", age);
        n.putInt("Life", lifetime);
        n.putInt("Delay", delay);
        n.putString("Cargo", cargo);
        n.putBoolean("Auto", autopilot);
        n.putInt("Rockets", rockets);
        n.putInt("UnitStage", stage());
        n.putInt("NextShot", shotAt);
        n.putDouble("ProjectileSpeed", projectileSpeed);
        n.putInt("FlightLift", flightLift);
        n.putFloat("FlightYaw", flightYaw());
        n.putFloat("FlightPitch", flightPitch());
        n.putBoolean("Departing", departing());
        n.putInt("DepartureAge", departureAge);
        n.putFloat("DepartureYaw", departureYaw);
        n.putFloat("DeparturePitch", departurePitch);
        n.putDouble("DepartureX", departureStart.x);
        n.putDouble("DepartureY", departureStart.y);
        n.putDouble("DepartureZ", departureStart.z);
        n.putBoolean("DepartureVoiceSent", departureVoiceSent);
        n.putInt("MarkerOwnerEntityId", markerOwnerEntityId);
        n.putInt("MarkerLandedAt", markerLandedAt);
        n.putBoolean("CrateLanded", crateLanded);
        n.putInt("ClusterPlantedAt", clusterPlantedAt);
        n.putInt("ClusterReleased", clusterReleased);
        n.put("ResearchNodes", ResearchCatalog.saveNodes(researchUpgrades));
        n.putString("ResearchSource", researchSource);
        n.putInt("ResearchStarted", researchStarted);
        n.putInt("ResearchSonicRounds", researchSonicRounds);
        n.putInt("ResearchMineGeneration", researchMineGeneration);
        if (delayedMotion != null) {
            n.putDouble("LaunchX", delayedMotion.x);
            n.putDouble("LaunchY", delayedMotion.y);
            n.putDouble("LaunchZ", delayedMotion.z);
        }
        AircraftCrash.save(this, n);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag n) {
        super.readAdditionalSaveData(n);
        researchUpgrades = ResearchCatalog.loadNodes(n.getList("ResearchNodes", net.minecraft.nbt.Tag.TAG_STRING));
        researchSource = n.getString("ResearchSource");
        researchStarted = n.contains("ResearchStarted") ? n.getInt("ResearchStarted") : -1;
        researchSonicRounds = Math.clamp(n.getInt("ResearchSonicRounds"), 0, 3);
        researchMineGeneration = Math.clamp(n.getInt("ResearchMineGeneration"), 0, 1);
        flightLift = Mth.clamp(n.getInt("FlightLift"), 0, level().getMaxBuildHeight() - level().getMinBuildHeight());
        kind(Kind.values()[Mth.clamp(n.getInt("UnitKind"), 0, Kind.values().length - 1)]);
        if (n.hasUUID("Operator"))
            ownerId = n.getUUID("Operator");
        center = new Vec3(n.getDouble("TargetX"), n.getDouble("TargetY"), n.getDouble("TargetZ"));
        heading = n.getFloat("Heading");
        age = n.getInt("Age");
        lifetime = Math.max(1, n.getInt("Life"));
        delay = n.getInt("Delay");
        cargo = n.getString("Cargo");
        autopilot = n.getBoolean("Auto");
        rockets = n.getInt("Rockets");
        stage(n.getInt("UnitStage"));
        shotAt = n.getInt("NextShot");
        if (kind() == Kind.VTOL && n.getInt("VtolFlightFormat") < 2 && !n.getBoolean("Departing")) {
            if (stage() == 1) {
                cargo = "vtol_patrol";
                age = Math.max(0, age - delay - NativeVtolFlight.SOURCE_TICKS) + NativeVtolFlight.SECOND_INGRESS_TICKS;
                lifetime = Math.max(age + 1,
                        lifetime - NativeVtolFlight.SOURCE_TICKS + NativeVtolFlight.SECOND_INGRESS_TICKS);
                delay = 0;
            } else
                lifetime += NativeVtolFlight.INGRESS_TICKS - NativeVtolFlight.SOURCE_TICKS;
        }
        if (vtolPatrol() && age < delay)
            stage(-12);
        if (n.hasUUID("VtolSuccessor"))
            vtolSuccessor = n.getUUID("VtolSuccessor");
        if (vtolPatrol() && n.getInt("VtolFlightFormat") < 3 && !n.getBoolean("Departing")) {

            // 在客户端开始跟踪实体之前，先迁移旧版本的返航曲线数据。
            Vec3 migrated = airPosition(NativeVtolFlight.secondPosition(center, heading, Math.max(0, age - delay)));
            setPos(migrated.x, Mth.clamp(migrated.y, level().getMinBuildHeight() + 3, level().getMaxBuildHeight() - 4),
                    migrated.z);
            setOldPosAndRot();
        }
        if (kind() == Kind.BOMB && cargo.equals("mortar"))
            stage(age < delay ? -3 : 3);
        if (kind() == Kind.BOMB && cargo.equals("stealth"))
            stage(age < delay ? -6 : 6);
        if (kind() == Kind.ROCKET)
            stage(cargo.equals("105mm") ? 8 : cargo.equals("vtol_rocket") ? 10 : 7);
        if (kind() == Kind.PRECISION && age < delay)
            stage(-9);
        setDiscardFriction(poweredProjectile());
        projectileSpeed = n.getDouble("ProjectileSpeed");
        if (n.contains("LaunchX"))
            delayedMotion = new Vec3(n.getDouble("LaunchX"), n.getDouble("LaunchY"), n.getDouble("LaunchZ"));
        trackedTarget = null;
        lockTicks = 0;
        burstShots = 0;
        burstPauseUntil = 0;
        targetLockAnnounced = false;
        if (independentFlightPose())
            flightOrientation(n.contains("FlightYaw") ? n.getFloat("FlightYaw") : getYRot(),
                    n.contains("FlightPitch") ? n.getFloat("FlightPitch") : getXRot());
        if (AircraftFlight.aircraft(kind()))
            entityData.set(NATIVE_CLOCK,
                    kind() == Kind.VTOL ? NativeVtolFlight.animationClock(vtolPatrol(), age - delay)
                            : Math.max(0, age - delay));
        entityData.set(DEPARTING, n.getBoolean("Departing"));
        departureAge = n.getInt("DepartureAge");
        departureYaw = n.getFloat("DepartureYaw");
        departurePitch = n.getFloat("DeparturePitch");
        departureStart = new Vec3(n.getDouble("DepartureX"), n.getDouble("DepartureY"), n.getDouble("DepartureZ"));
        departureVoiceSent = n.getBoolean("DepartureVoiceSent");
        markerOwnerEntityId = n.getInt("MarkerOwnerEntityId");
        markerLandedAt = n.getInt("MarkerLandedAt");
        crateLanded = n.contains("CrateLanded") ? n.getBoolean("CrateLanded") : onGround();
        clusterPlantedAt = n.contains("ClusterPlantedAt") ? n.getInt("ClusterPlantedAt") : -1;
        clusterReleased = Mth.clamp(n.getInt("ClusterReleased"), 0, 4);
        if (kind() == Kind.CLUSTER && clusterPlantedAt >= 0)
            setNoGravity(true);
        if (departing() && kind() == Kind.VTOL)
            entityData.set(NATIVE_CLOCK, Math.max(0, age - departureAge));
        AircraftCrash.load(this, n);
    }
}
