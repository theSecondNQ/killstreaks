package net.killstreakcod.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.audio.SupportAudioPayload;
import net.killstreakcod.killstreaks.TacticalUnitEntity;
import net.killstreakcod.render.compat.OptionalShaderCompat;
import net.killstreakcod.universal.AircraftFlight;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/**
 * Visual aircraft snapshots outside entity/chunk tracking; never added to the client world or combat.
 * 显示实体或区块跟踪范围外的飞机快照，不将其加入客户端世界，也不参与战斗。
 */
@EventBusSubscriber(modid = KillstreakcodMod.MODID, value = Dist.CLIENT)
public final class DistantPrecisionAircraft {
    private static final class Visual {
        final TacticalUnitEntity model;
        Vec3 at, velocity = Vec3.ZERO, correction = Vec3.ZERO;
        long updated;
        int clock, stage;
        float fromYaw, toYaw, fromPitch, toPitch;
        boolean departing;

        Visual(ClientLevel level, SupportAudioPayload.Source source) {
            model = new TacticalUnitEntity(KillstreakcodMod.TACTICAL_UNIT.get(), level);
            model.kind(TacticalUnitEntity.Kind.values()[source.kind()]);
            fromYaw = toYaw = source.flightYaw();
            fromPitch = toPitch = source.flightPitch();
            update(level, source);
        }

        void update(ClientLevel level, SupportAudioPayload.Source source) {
            long now = level.getGameTime();
            float p = net.minecraft.util.Mth.clamp((now - updated) / 10F, 0, 1);
            fromYaw = net.minecraft.util.Mth.rotLerp(p, fromYaw, toYaw);
            fromPitch = net.minecraft.util.Mth.lerp(p, fromPitch, toPitch);
            Vec3 next = new Vec3(source.x(), source.y(), source.z());
            correction = at == null ? Vec3.ZERO : position(now).subtract(next);
            if (correction.lengthSqr() > 64 * 64)
                correction = Vec3.ZERO;
            at = next;
            velocity = new Vec3(source.vx(), source.vy(), source.vz());
            updated = now;
            toYaw = source.flightYaw();
            toPitch = source.flightPitch();
            clock = source.clock();
            stage = source.stage();
            departing = source.departing();
        }

        void pose(double time) {
            float p = (float) Math.max(0, Math.min(1, (time - updated) / 10));
            model.visualSnapshot(clock + (int) Math.max(0, time - updated), stage,
                    net.minecraft.util.Mth.rotLerp(p, fromYaw, toYaw),
                    net.minecraft.util.Mth.lerp(p, fromPitch, toPitch), departing);
        }

        Vec3 position(double time) {
            double elapsed = Math.max(0, Math.min(15, time - updated));
            return at.add(velocity.scale(elapsed)).add(correction.scale(Math.max(0, 1 - elapsed / 10)));
        }
    }

    private static final Map<Integer, Visual> PLANES = new HashMap<>();
    private static ClientLevel world;
    private static com.mojang.blaze3d.vertex.BufferBuilder storage;
    private static boolean renderingEnabled = true;

    private DistantPrecisionAircraft() {}

    public static void accept(SupportAudioPayload payload) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.level.dimension().location().equals(payload.dimension()))
            return;
        if (world != mc.level) {
            PLANES.clear();
            world = mc.level;
            renderingEnabled = true;
        }
        Set<Integer> present = new HashSet<>();
        for (var source : payload.sources())
            if (source.kind() >= 0 && source.kind() < TacticalUnitEntity.Kind.values().length
                    && AircraftFlight.visible(TacticalUnitEntity.Kind.values()[source.kind()])
                    && Double.isFinite(source.x()) && Double.isFinite(source.y()) && Double.isFinite(source.z())
                    && Float.isFinite(source.flightYaw()) && Float.isFinite(source.flightPitch())) {
                if (!Float.isFinite(source.vx()) || !Float.isFinite(source.vy()) || !Float.isFinite(source.vz())
                        || Math.abs(source.vx()) > 16 || Math.abs(source.vy()) > 16 || Math.abs(source.vz()) > 16)
                    continue;
                present.add(source.entityId());
                Visual visual = PLANES.get(source.entityId());
                if (visual == null)
                    PLANES.put(source.entityId(), new Visual(world, source));
                else
                    visual.update(world, source);
            }
        PLANES.keySet().retainAll(present);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
        var mc = Minecraft.getInstance();
        if (mc.level != world || mc.player == null || !mc.player.isAlive()) {
            PLANES.clear();
            world = null;
        } else if (world != null)
            PLANES.values().removeIf(p -> world.getGameTime() - p.updated > 30);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || PLANES.isEmpty() || !renderingEnabled
                || OptionalShaderCompat.shadowPass())
            return;
        var mc = Minecraft.getInstance();
        if (mc.level != world || mc.player == null || !mc.player.isAlive() || mc.player.isEyeInFluid(FluidTags.WATER)
                || mc.player.isEyeInFluid(FluidTags.LAVA))
            return;
        if (PLANES.keySet().stream().allMatch(id -> world.getEntity(id) != null))
            return;
        float partial = event.getPartialTick();
        double clock = world.getGameTime() + partial;
        Vec3 eye = event.getCamera().getPosition();
        if (storage == null)
            storage = new com.mojang.blaze3d.vertex.BufferBuilder(65536);
        var buffers = net.minecraft.client.renderer.MultiBufferSource.immediate(storage);
        PoseStack pose = new PoseStack();
        float fogStart = RenderSystem.getShaderFogStart(), fogEnd = RenderSystem.getShaderFogEnd();
        try (ThermalRenderState ignored = new ThermalRenderState()) {
            var matrix = RenderSystem.getModelViewStack();
            matrix.pushPose();
            try {
                matrix.last().pose().set(event.getPoseStack().last().pose());
                RenderSystem.applyModelViewMatrix();
                RenderSystem.setProjectionMatrix(event.getProjectionMatrix(), VertexSorting.DISTANCE_TO_ORIGIN);
                RenderSystem.setShaderFogStart(512);
                RenderSystem.setShaderFogEnd(768);
                for (var entry : PLANES.entrySet()) {
                    if (world.getEntity(entry.getKey()) != null)
                        continue;
                    Visual visual = entry.getValue();
                    Vec3 at = visual.position(clock);
                    visual.model.setPos(at);
                    visual.pose(clock);
                    if (at.distanceToSqr(eye) > 512 * 512
                            || !event.getFrustum().isVisible(visual.model.getBoundingBox().inflate(24)))
                        continue;
                    pose.pushPose();
                    try {
                        Vec3 relative = at.subtract(eye);
                        pose.translate(relative.x, relative.y, relative.z);
                        ImportedUnits.render(visual.model, partial, pose, buffers, AircraftLighting.at(world, at));
                    } finally {
                        pose.popPose();
                    }
                }
                buffers.endBatch();
            } finally {
                RenderSystem.setShaderFogStart(fogStart);
                RenderSystem.setShaderFogEnd(fogEnd);
                matrix.popPose();
                RenderSystem.applyModelViewMatrix();
            }
        }
        catch (RuntimeException | LinkageError failure) {
            renderingEnabled = false;
            com.mojang.logging.LogUtils.getLogger().warn("Distant aircraft display disabled until next world", failure);
        } finally {
            storage.discard();
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        PLANES.clear();
        world = null;
        renderingEnabled = true;
        if (storage != null) {
            storage.clear();
            storage = null;
        }
    }
}
