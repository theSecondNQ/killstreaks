package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.render.compat.OptionalShaderCompat;
import net.killstreakcod.universal.NativeFxPayload;
import net.killstreakcod.universal.NativePresentationConfig;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/**
 * Source sprites, lifetimes and debris, adapted to Minecraft. IW module IDs are not emulated.
 * 将源精灵、生命周期和碎片适配到 Minecraft，不模拟 IW 的模块 ID。
 */
@EventBusSubscriber(modid = KillstreakcodMod.MODID, value = Dist.CLIENT)
public final class NativeFxRenderer {
    private record Layer(ResourceLocation texture, boolean glow, int columns, int rows, float minLife, float maxLife,
            String material, int delay, int textureWidth, int textureHeight, int tint, boolean variants) {
    }

    private record Batch(ResourceLocation texture, boolean glow) {
    }

    private record Sprite(Vec3 origin, Vec3 velocity, Layer layer, long born, int life, float size, float rotation,
            int tint, float opacity, boolean groundDust) {
    }

    private static final class Debris {
        final String model;
        final long born;
        final int life;
        final float scale, spin;
        Vec3 previous, at, velocity;

        Debris(String model, Vec3 at, Vec3 velocity, long born, int life, float scale, float spin) {
            this.model = model;
            this.previous = this.at = at;
            this.velocity = velocity;
            this.born = born;
            this.life = life;
            this.scale = scale;
            this.spin = spin;
        }
    }

    private static final Map<String, List<Layer>> PROFILES = new HashMap<>();
    private static final Map<String, List<String>> CRASH_DEBRIS = new HashMap<>();
    private static final ArrayDeque<Sprite> SPRITES = new ArrayDeque<>();
    private static final ArrayList<Debris> DEBRIS = new ArrayList<>();
    private static ClientLevel world;
    private static Player pawn;
    private static ShaderInstance shader;
    private static BufferBuilder storage, debrisStorage;
    private static boolean loaded;

    private NativeFxRenderer() {}

    private static void load() {
        if (loaded)
            return;
        loaded = true;
        for (String file : new String[] { "native-fx", "aircraft-crash-fx", "csj21-fx", "aircraft-fire-fx" }) {
            var resource = Minecraft.getInstance().getResourceManager()
                    .getResource(ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "mw/" + file + ".json"));
            if (resource.isEmpty())
                continue;
            try (var stream = resource.get().open();
                    var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                var root = JsonParser.parseReader(reader).getAsJsonObject();
                root.getAsJsonObject("profiles").entrySet().forEach(entry -> {
                    List<Layer> layers = new ArrayList<>();
                    for (var item : entry.getValue().getAsJsonObject().getAsJsonArray("layers")) {
                        var row = item.getAsJsonObject();
                        var life = row.getAsJsonArray("lifeSeconds");
                        if (row.has("material") && row.get("material").getAsString().toLowerCase(java.util.Locale.ROOT)
                                .contains("debug"))
                            continue;
                        int grid = Mth.clamp(row.get("grid").getAsInt(), 1, 16),
                                columns = row.has("columns") ? Mth.clamp(row.get("columns").getAsInt(), 1, 16) : grid,
                                rows = row.has("rows") ? Mth.clamp(row.get("rows").getAsInt(), 1, 16) : grid;
                        layers.add(new Layer(ResourceLocation.parse(row.get("texture").getAsString()),
                                row.get("blend").getAsString().equals("glow"), columns, rows,
                                Mth.clamp(life.get(0).getAsFloat(), .1F, 6),
                                Mth.clamp(life.get(1).getAsFloat(), .1F, 6),
                                row.has("material") ? row.get("material").getAsString() : "",
                                row.has("sourceDelaySeconds")
                                        ? Mth.clamp(Math.round(row.get("sourceDelaySeconds").getAsFloat() * 20), 0, 400)
                                        : 0,
                                row.has("textureWidth") ? Mth.clamp(row.get("textureWidth").getAsInt(), 1, 8192) : 2048,
                                row.has("textureHeight") ? Mth.clamp(row.get("textureHeight").getAsInt(), 1, 8192)
                                        : 2048,
                                row.has("tint") ? row.get("tint").getAsInt() & 0xffffff : 0xffffff,
                                row.has("staticVariant") && row.get("staticVariant").getAsBoolean()));
                    }
                    PROFILES.put(entry.getKey(), List.copyOf(layers));
                });
                if (root.has("debris"))
                    root.getAsJsonObject("debris").entrySet().forEach(entry -> {
                        List<String> pieces = new ArrayList<>();
                        for (var item : entry.getValue().getAsJsonArray())
                            pieces.add(item.getAsString());
                        CRASH_DEBRIS.put(entry.getKey(), List.copyOf(pieces));
                    });
            }
            catch (Exception failure) {
                com.mojang.logging.LogUtils.getLogger().warn("Recovered FX catalog unavailable", failure);
            }
        }
    }

    public static void accept(NativeFxPayload payload) {
        var mc = Minecraft.getInstance();
        Vec3 at = payload.at();
        if (mc.level == null || mc.player == null || !mc.player.isAlive()
                || !mc.level.dimension().location().equals(payload.dimension()) || !Double.isFinite(at.x)
                || !Double.isFinite(at.y) || !Double.isFinite(at.z) || !Float.isFinite(payload.size()))
            return;
        if (world != mc.level || pawn != mc.player) {
            clear();
            world = mc.level;
            pawn = mc.player;
        }
        load();
        var layers = PROFILES.get(payload.profile());
        if (layers == null)
            return;
        Random random = new Random(payload.seed());
        long born = world.getGameTime();
        float size = Mth.clamp(payload.size(), .2F, 24);
        if (payload.profile().equals("landing")) {

            // 原资源落地效果是柔和地面扬尘，不是爆炸；已检查的薄雾图像有 8×8 帧，旧 grid=1 会同时显示 64 团烟。
            // 省略不透明泥土填充及自发光模糊碎片，因为所需 IW 材质语义尚不可用。
            for (Layer recovered : layers) {
                if (!recovered.material.contains("vfx_vol_smoke_wispy_swirl_loop_1"))
                    continue;
                Layer layer = new Layer(recovered.texture, false, 8, 8, recovered.minLife, recovered.maxLife,
                        recovered.material, 0, recovered.textureWidth, recovered.textureHeight, recovered.tint, false);
                for (int i = 0; i < 3; i++) {
                    while (SPRITES.size() >= NativePresentationConfig.FX_LIMIT.get())
                        SPRITES.removeFirst();
                    double angle = (i + random.nextDouble()) * Math.PI * 2 / 3;
                    Vec3 radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                    Vec3 atGround = at.add(radial.scale(size * .18)).add(0, .12, 0);
                    int life = (int) (Mth.lerp(random.nextFloat(), layer.minLife, layer.maxLife) * 20);
                    SPRITES.addLast(new Sprite(atGround, radial.scale(.035).add(0, .008, 0), layer, born, life,
                            size * .8F, 0, 0xd1c3ab, .48F, true));
                }
            }
            return;
        }
        boolean smoke = payload.profile().equals("smoke"), flares = payload.profile().equals("flares");
        float spriteSize = size * ExplosionVisualScale.multiplier(payload.profile());
        for (Layer layer : layers) {
            boolean aircraftFire = payload.profile().startsWith("aircraft25_");
            int count = aircraftFire ? 1 : smoke ? 1 : flares ? 5 : layer.glow ? 2 : 3;
            for (int i = 0; i < count; i++) {
                while (SPRITES.size() >= NativePresentationConfig.FX_LIMIT.get())
                    SPRITES.removeFirst();
                Vec3 offset = new Vec3((random.nextDouble() - .5) * size * (aircraftFire ? .2 : .6),
                        random.nextDouble() * size * (aircraftFire ? .12 : .3),
                        (random.nextDouble() - .5) * size * (aircraftFire ? .2 : .6));
                Vec3 velocity = flares
                        ? new Vec3((random.nextDouble() - .5) * .8, -.05, (random.nextDouble() - .5) * .8)
                        : new Vec3((random.nextDouble() - .5) * .025, layer.glow ? .01 : .025,
                                (random.nextDouble() - .5) * .025);
                int life = (int) (Mth.lerp(random.nextFloat(), layer.minLife, layer.maxLife) * 20);
                SPRITES.addLast(new Sprite(at.add(offset), velocity, layer, born + layer.delay, Math.max(2, life),
                        spriteSize * (smoke ? .65F : layer.glow ? .8F : .55F), random.nextFloat() * 6.2832F, layer.tint,
                        1, false));
            }
        }
        if (!smoke && !flares && !payload.profile().equals("tracer")
                && (size >= 3 || payload.profile().startsWith("crash_") || payload.profile().equals("m13421_brass")
                        || payload.profile().equals("aircraft25_apache_brass"))) {
            boolean canopy = payload.profile().equals("crash_1ca4755f343f089c");
            boolean apacheBrass = payload.profile().equals("aircraft25_apache_brass"),
                    brass = payload.profile().equals("m13421_brass") || apacheBrass;
            for (int i = 0; i < (apacheBrass ? 1
                    : brass ? 2 : canopy ? 1 : Math.min(6, Math.max(1, (int) size))); i++) {
                if (DEBRIS.size() >= 24)
                    DEBRIS.remove(0);
                String model = debrisModel(payload.profile(), random);
                if (model == null)
                    continue;

                // 负尺寸表示保留原资源弹壳和弹链节的厘米尺寸。
                DEBRIS.add(new Debris(model, at.add(0, brass ? 0 : .25, 0),
                        new Vec3((random.nextDouble() - .5) * .6, canopy ? .75 : .18 + random.nextDouble() * .4,
                                (random.nextDouble() - .5) * .6),
                        born, canopy ? 80 : 30 + random.nextInt(35),
                        brass ? -1F : canopy ? 4.2F : .35F + random.nextFloat() * .4F, random.nextFloat() * 360));
            }
        }
    }

    private static String debrisModel(String profile, Random random) {
        if (profile.startsWith("crash_") || profile.equals("sae21_impact") || profile.equals("m13421_brass")
                || profile.startsWith("aircraft25_")) {
            List<String> pieces = CRASH_DEBRIS.get(profile);
            return pieces == null || pieces.isEmpty() ? null : pieces.get(random.nextInt(pieces.size()));
        }
        return switch (profile) {
            case "helicopter" -> "fx_veh8_mil_air_ahotel64_"
                    + new String[] { "blade_1", "dst_engine", "dst_landing_gear", "dst_rear_rotor",
                            "dst_sensor" }[random
                                    .nextInt(5)];
            case "equipment", "sentry" -> random.nextBoolean() ? "fx_metal_piece0" + (1 + random.nextInt(2))
                    : "fx_metal_scrap_debris_" + String.format(java.util.Locale.ROOT, "%02d", 7 + random.nextInt(5));
            case "cruise" -> random.nextInt(3) == 0 ? "fx_vfx_detachable_missile_shell"
                    : "fx_vfx_debris_metal_shrapnel_a_0" + (1 + random.nextInt(4));
            case "mine" -> "fx_vfx_debris_metal_shrapnel_a_0" + (1 + random.nextInt(4));
            default -> "fx_vfx_debris_metal_a_" + String.format(java.util.Locale.ROOT, "%02d", 1 + random.nextInt(10));
        };
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
        if (world == null)
            return;
        var mc = Minecraft.getInstance();
        if (mc.level != world || mc.player != pawn || !pawn.isAlive()) {
            clear();
            return;
        }
        if (mc.isPaused())
            return;
        long now = world.getGameTime();
        SPRITES.removeIf(p -> now - p.born >= p.life);
        DEBRIS.removeIf(p -> now - p.born >= p.life);
        for (Debris piece : DEBRIS) {
            piece.previous = piece.at;
            Vec3 next = piece.at.add(piece.velocity);
            var hit = world
                    .clip(new ClipContext(piece.at, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, pawn));
            if (hit.getType() == HitResult.Type.MISS) {
                piece.at = next;
                piece.velocity = piece.velocity.scale(.96).add(0, -.035, 0);
            } else {
                piece.at = hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(.025));
                piece.velocity = Vec3.ZERO;
            }
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || shader == null
                || OptionalShaderCompat.shadowPass() || world == null
                || EntryScenes.active() && !AircraftCrashClient.active() || (SPRITES.isEmpty() && DEBRIS.isEmpty()))
            return;
        var mc = Minecraft.getInstance();
        if (mc.level != world || mc.player != pawn || !pawn.isAlive()) {
            clear();
            return;
        }
        float partial = event.getPartialTick();
        double now = world.getGameTime() + partial;
        Vec3 eye = event.getCamera().getPosition();
        List<Sprite> ordered = new ArrayList<>(SPRITES);
        ordered.sort(Comparator.comparingDouble((Sprite p) -> p.origin.distanceToSqr(eye)).reversed());
        Map<Batch, List<Sprite>> batches = new LinkedHashMap<>();
        for (Sprite p : ordered)
            batches.computeIfAbsent(new Batch(p.layer.texture, p.layer.glow), k -> new ArrayList<>()).add(p);
        if (storage == null)
            storage = new BufferBuilder(65536);
        try (ThermalRenderState ignored = new ThermalRenderState()) {
            var matrix = RenderSystem.getModelViewStack();
            matrix.pushPose();
            try {
                matrix.last().pose().set(event.getPoseStack().last().pose());
                RenderSystem.applyModelViewMatrix();
                RenderSystem.setProjectionMatrix(event.getProjectionMatrix(), VertexSorting.DISTANCE_TO_ORIGIN);
                RenderSystem.setShader(() -> shader);
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
                RenderSystem.depthMask(false);
                GL11.glDisable(GL11.GL_STENCIL_TEST);
                RenderSystem.disableScissor();
                RenderSystem.disableCull();
                RenderSystem.disablePolygonOffset();
                RenderSystem.enableBlend();
                RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
                RenderSystem.colorMask(true, true, true, true);
                for (var group : batches.entrySet()) {
                    storage.discard();
                    BufferBuilder out = storage;
                    out.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                    for (Sprite p : group.getValue())
                        quad(out, p, now, eye, event);
                    BufferBuilder.RenderedBuffer mesh = out.endOrDiscardIfEmpty();
                    try {
                        if (mesh == null)
                            continue;
                        shader.setSampler("DiffuseSampler",
                                mc.getTextureManager().getTexture(group.getKey().texture).getId());
                        RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA,
                                group.getKey().glow ? GL11.GL_ONE : GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE,
                                GL11.GL_ONE_MINUS_SRC_ALPHA);
                        BufferUploader.drawWithShader(mesh);
                        mesh = null;
                    } finally {
                        if (mesh != null)
                            mesh.release();
                    }
                }
                RenderSystem.depthMask(true);
                RenderSystem.defaultBlendFunc();
                renderDebris(event, eye, partial);
            } finally {
                matrix.popPose();
                RenderSystem.applyModelViewMatrix();
            }
        }
        catch (RuntimeException | LinkageError failure) {
            shader = null;
            com.mojang.logging.LogUtils.getLogger().warn("Recovered FX renderer disabled until resource reload",
                    failure);
        } finally {
            storage.discard();
        }
    }

    private static void quad(BufferBuilder out, Sprite p, double now, Vec3 eye, RenderLevelStageEvent event) {
        double age = now - p.born;
        if (age < 0 || age >= p.life)
            return;
        float progress = (float) (age / p.life);
        Vec3 center = p.origin.add(p.velocity.scale(age));
        double distance = center.distanceTo(eye);
        if (distance < .6 || distance > 512)
            return;
        float width = (float) Math.min(p.size * (p.layer.glow ? 1 + progress * .4 : .55 + progress), distance * .45);
        if (!event.getFrustum().isVisible(new AABB(center, center).inflate(width)))
            return;
        float fade = (p.life <= 3 ? 1 : Math.min(1, (float) age / 3)) * Math.min(1, (1 - progress) * 5)
                * (p.layer.glow ? .9F : .5F) * p.opacity;
        int frames = p.layer.columns * p.layer.rows,
                frame = Math.min(frames - 1, (int) ((p.layer.variants ? p.rotation / 6.2832F : progress) * frames));
        float cellU = 1F / p.layer.columns, cellV = 1F / p.layer.rows;

        // 只采样一个动画帧，并向内收缩采样边界，防止过滤时混入相邻帧而露出整张图集。
        float u = (frame % p.layer.columns) * cellU, v = (frame / p.layer.columns) * cellV,
                insetU = Math.min(cellU * .1F, .75F / p.layer.textureWidth),
                insetV = Math.min(cellV * .1F, .75F / p.layer.textureHeight);
        Vector3f right = event.getCamera().rotation().transform(new Vector3f(1, 0, 0)),
                up = event.getCamera().rotation().transform(new Vector3f(0, 1, 0));
        float angle = p.groundDust ? 0 : p.rotation + progress * .3F, c = (float) Math.cos(angle),
                s = (float) Math.sin(angle);
        Vector3f a = new Vector3f(right).mul(c).add(new Vector3f(up).mul(s)).mul(width * .5F),
                b = new Vector3f(up).mul(c).sub(new Vector3f(right).mul(s)).mul(width * .5F);
        float aspect = (p.layer.textureWidth * cellU) / (p.layer.textureHeight * cellV);
        a.mul(Math.min(1, aspect));
        b.mul(Math.min(1, 1 / aspect));
        if (p.groundDust)
            b.mul(.35F);
        Vec3 origin = center.subtract(eye), r = new Vec3(a.x, a.y, a.z), t = new Vec3(b.x, b.y, b.z);
        int color = Math.round(fade * 255) << 24 | p.tint;
        vertex(out, origin.subtract(r).subtract(t), u + insetU, v + cellV - insetV, color);
        vertex(out, origin.add(r).subtract(t), u + cellU - insetU, v + cellV - insetV, color);
        vertex(out, origin.add(r).add(t), u + cellU - insetU, v + insetV, color);
        vertex(out, origin.subtract(r).add(t), u + insetU, v + insetV, color);
    }

    private static void vertex(BufferBuilder out, Vec3 p, float u, float v, int color) {
        out.vertex((float) p.x, (float) p.y, (float) p.z).uv(u, v).color(color).endVertex();
    }

    private static void renderDebris(RenderLevelStageEvent event, Vec3 eye, float partial) {
        if (DEBRIS.isEmpty())
            return;
        if (debrisStorage == null)
            debrisStorage = new BufferBuilder(131072);
        var buffers = MultiBufferSource.immediate(debrisStorage);
        var pose = new PoseStack();
        for (Debris piece : DEBRIS) {
            Vec3 at = piece.previous.lerp(piece.at, partial);
            if (at.distanceToSqr(eye) > 96 * 96 || !event.getFrustum().isVisible(new AABB(at, at).inflate(1)))
                continue;
            var model = ImportedCastModel.get(piece.model + "_lod_far");
            if (model == null)
                model = ImportedCastModel.get(piece.model);
            if (model == null)
                continue;
            pose.pushPose();
            try {
                Vec3 relative = at.subtract(eye);
                pose.translate(relative.x, relative.y, relative.z);
                pose.mulPose(Axis.YP.rotationDegrees(piece.spin + (world.getGameTime() - piece.born + partial) * 6));
                float span = model.boundsMax().sub(model.boundsMin()).length();
                float fit = piece.scale < 0 ? 1 : piece.scale / Math.max(.1F, span);
                pose.scale(fit, fit, fit);
                model.render(pose, buffers, net.minecraft.client.renderer.LevelRenderer.getLightColor(world,
                        net.minecraft.core.BlockPos.containing(at)), OverlayTexture.NO_OVERLAY, Map.of());
            } finally {
                pose.popPose();
            }
        }
        buffers.endBatch();
        debrisStorage.discard();
    }

    public static void clear() {
        SPRITES.clear();
        DEBRIS.clear();
        world = null;
        pawn = null;
        if (storage != null) {
            storage.clear();
            storage = null;
        }
        if (debrisStorage != null) {
            debrisStorage.clear();
            debrisStorage = null;
        }
    }

    static void reload() {
        clear();
        PROFILES.clear();
        CRASH_DEBRIS.clear();
        loaded = false;
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    @EventBusSubscriber(modid = KillstreakcodMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class NativeFxShaderEvents {
        @SubscribeEvent
        public static void shaders(RegisterShadersEvent event) {
            try {
                event.registerShader(new ShaderInstance(event.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "native_fx"),
                        DefaultVertexFormat.POSITION_TEX_COLOR), loadedShader -> shader = loadedShader);
            }
            catch (java.io.IOException | RuntimeException | LinkageError failure) {
                shader = null;
                com.mojang.logging.LogUtils.getLogger()
                        .warn("Recovered FX shader unavailable; vanilla effects remain active", failure);
            }
        }

    }
}
