package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.render.compat.OptionalShaderCompat;
import net.killstreakcod.universal.TracerDelivery;
import net.killstreakcod.universal.TracerPayload;

import java.io.IOException;
import java.util.ArrayDeque;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
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
 * Moving emissive ovals and a brief muzzle glint, in a single depth-tested draw.
 * 在一次开启深度检测的绘制中呈现移动的自发光椭圆及短暂炮口亮光。
 */
@EventBusSubscriber(modid = KillstreakcodMod.MODID, value = Dist.CLIENT)
public final class TracerEffects {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID,
            "textures/effects/tracer_oval.png");
    private static final ResourceLocation APACHE_TEXTURE = ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID,
            "textures/aircraft25/apache_tracer.png"),
            GUNSHIP25_TEXTURE = ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID,
                    "textures/aircraft25/gunship25_tracer.png"),
            GUNSHIP40_TEXTURE = ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID,
                    "textures/aircraft25/gunship40_tracer.png");
    private static final int MAX_ACTIVE = 192, MAX_QUADS = 1152;
    private static final double[] WIDTH = { .10, .18, .26, .25, .18, .18 }, LENGTH = { 2.4, 4.5, 6, 3.8, 4.5, 4.5 };
    private static final int[] TRACE_COUNT = { 4, 6, 3, 3, 6, 6 };
    private static final double[] TRACE_DELAY = { .18, .12, .24, 0, .12, .12 };
    private static final double END_FADE = .6;
    private static final int[] RGB = { 0xffd68c, 0xffe6ba, 0xffc378, 0xffcd87, 0xffffff, 0xffffff };
    private static final ArrayDeque<Shot> SHOTS = new ArrayDeque<>();
    private static ClientLevel world;
    private static Player pawn;
    private static boolean remote;
    private static ShaderInstance shader;
    private static BufferBuilder storage;

    private TracerEffects() {}

    /**
     * Client handler calls this on its enqueued Minecraft thread.
     * 客户端消息处理器在排入 Minecraft 主线程后调用。
     */
    public static void accept(TracerPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.player.isAlive()
                || !mc.level.dimension().location().equals(payload.dimension()))
            return;
        if (payload.profile() < 0 || payload.profile() > TracerDelivery.GUNSHIP25)
            return;
        Vec3 from = payload.from(), to = payload.to();
        if (!finite(from) || !finite(to) || from.distanceToSqr(to) < .0025 || from.distanceToSqr(to) > 1024D * 1024)
            return;
        if (world != mc.level || pawn != mc.player || remote != TacticalClient.remote()) {
            clear();
            world = mc.level;
            pawn = mc.player;
            remote = TacticalClient.remote();
        }
        if (payload.profile() == TracerDelivery.PROJECTILE)
            SHOTS.removeIf(shot -> shot.profile == TracerDelivery.PROJECTILE && shot.source == payload.sourceId());
        while (SHOTS.size() >= MAX_ACTIVE)
            SHOTS.removeFirst();
        double distance = from.distanceTo(to), flight = payload.profile() == TracerDelivery.PROJECTILE ? 1
                : Math.max(1.25, Math.min(6, distance / 48));
        SHOTS.addLast(new Shot(from, to, payload.sourceId(), payload.profile(), world.getGameTime(), flight,
                payload.profile() == TracerDelivery.PROJECTILE ? 2.5
                        : flight + (TRACE_COUNT[payload.profile()] - 1) * TRACE_DELAY[payload.profile()] + END_FADE));
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
        if (world == null)
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != world || mc.player != pawn || pawn == null || !pawn.isAlive()
                || remote != TacticalClient.remote()) {
            clear();
            return;
        }
        if (mc.isPaused())
            return;
        long time = world.getGameTime();
        SHOTS.removeIf(shot -> time - shot.born > shot.life + 1);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || SHOTS.isEmpty() || shader == null
                || OptionalShaderCompat.shadowPass())
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != world || mc.player != pawn || pawn == null || !pawn.isAlive()
                || remote != TacticalClient.remote()) {
            clear();
            return;
        }
        if (EntryScenes.active())
            return;
        if (storage == null)
            storage = new BufferBuilder(32768);
        var textures = new java.util.LinkedHashSet<ResourceLocation>();
        for (Shot shot : SHOTS)
            textures.add(texture(shot.profile));
        for (ResourceLocation texture : textures) {
            storage.discard();
            float partial = event.getPartialTick();
            double now = world.getGameTime() + partial;
            Vec3 eye = event.getCamera().getPosition();
            BufferBuilder vertices = storage;
            vertices.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            int count = 0;
            for (Shot shot : SHOTS) {
                if (!texture(shot.profile).equals(texture))
                    continue;
                double age = Math.max(0, now - shot.born);
                if (age > shot.life)
                    continue;
                if (TracerDelivery.distanceToSegmentSqr(eye, shot.from, shot.to) > 512D * 512)
                    continue;
                Vec3 direction = shot.to.subtract(shot.from).normalize();
                count += traces(vertices, eye, shot, direction, age, partial, event, MAX_QUADS - count);
                if (shot.profile != TracerDelivery.PROJECTILE && age < 1.1 && count < MAX_QUADS) {
                    Vec3 flash = shot.from.add(direction.scale(Math.min(.7, shot.from.distanceTo(shot.to) * .6)));
                    if (shot.source == mc.player.getId() && mc.getCameraEntity() == mc.player
                            && mc.options.getCameraType().isFirstPerson()) {
                        Vector3f right = event.getCamera().rotation().transform(new Vector3f(1, 0, 0));
                        Vector3f up = event.getCamera().rotation().transform(new Vector3f(0, 1, 0));
                        if (mc.player.getMainHandItem().is(KillstreakcodMod.M134_MINIGUN.get())) {
                            double side = mc.options.mainHand().get() == net.minecraft.world.entity.HumanoidArm.LEFT
                                    ? -.255
                                    : .255;
                            flash = shot.from.add(direction.scale(Math.min(1.57, shot.from.distanceTo(shot.to) * .6)))
                                    .add(right.x * side - up.x * .48, right.y * side - up.y * .48,
                                            right.z * side - up.z * .48);
                        } else
                            flash = flash.add(right.x * .16 - up.x * .17, right.y * .16 - up.y * .17,
                                    right.z * .16 - up.z * .17);
                    }

                    // 位于摄像机近裁剪面上的闪光不能变成覆盖全屏的发光矩形。
                    double viewDistance = flash.distanceTo(eye),
                            size = Math.min(.22 + WIDTH[shot.profile], viewDistance * .15);
                    if (viewDistance > .45 && size > .025
                            && event.getFrustum().isVisible(new AABB(flash, flash).inflate(size)))
                        count += billboard(vertices, eye, flash, size, size * .6, RGB[shot.profile],
                                (float) (.85 * (1 - age / 1.1)), event);
                }
                if (count >= MAX_QUADS)
                    break;
            }
            BufferBuilder.RenderedBuffer mesh = vertices.endOrDiscardIfEmpty();
            if (mesh == null) {
                storage.discard();
                continue;
            }
            try (ThermalRenderState ignored = new ThermalRenderState()) {
                var modelView = RenderSystem.getModelViewStack();
                modelView.pushPose();
                try {
                    modelView.last().pose().set(event.getPoseStack().last().pose());
                    RenderSystem.applyModelViewMatrix();
                    RenderSystem.setProjectionMatrix(event.getProjectionMatrix(), VertexSorting.DISTANCE_TO_ORIGIN);
                    RenderSystem.setShader(() -> shader);
                    shader.setSampler("DiffuseSampler", mc.getTextureManager().getTexture(texture).getId());
                    RenderSystem.enableDepthTest();
                    RenderSystem.depthFunc(GL11.GL_LEQUAL);
                    RenderSystem.depthMask(false);
                    GL11.glDisable(GL11.GL_STENCIL_TEST);
                    RenderSystem.disableScissor();
                    RenderSystem.disableCull();
                    RenderSystem.disablePolygonOffset();
                    RenderSystem.enableBlend();
                    RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
                    RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE,
                            GL11.GL_ONE_MINUS_SRC_ALPHA);
                    RenderSystem.colorMask(true, true, true, true);
                    BufferUploader.drawWithShader(mesh);
                    mesh = null;
                } finally {
                    modelView.popPose();
                    RenderSystem.applyModelViewMatrix();
                }
            }
            catch (RuntimeException | LinkageError failure) {
                shader = null;
                LogUtils.getLogger().warn("Killstreaks: tracer rendering disabled until resource reload", failure);
            } finally {
                if (mesh != null)
                    mesh.release();
                storage.discard();
            }
        }
    }

    private static ResourceLocation texture(int profile) {
        return profile == TracerDelivery.APACHE ? APACHE_TEXTURE
                : profile == TracerDelivery.GUNSHIP25 ? GUNSHIP25_TEXTURE
                        : profile == TracerDelivery.HEAVY ? GUNSHIP40_TEXTURE : TEXTURE;
    }

    /**
     * Expand one network event into a staggered visual train on the same collision segment.
     * 将一次网络事件展开为同一碰撞线段上错时出现的一串视觉弹迹。
     */
    private static int traces(BufferBuilder out, Vec3 eye, Shot shot, Vec3 direction, double age, float partial,
            RenderLevelStageEvent event, int budget) {
        if (budget <= 0)
            return 0;
        int count = 0, profile = shot.profile, copies = TRACE_COUNT[profile];
        double distance = shot.from.distanceTo(shot.to),
                start = shot.from.distanceToSqr(eye) < 1.5 * 1.5 ? Math.min(.75, distance) : 0;
        Vec3 projectileHead = null;
        if (profile == TracerDelivery.PROJECTILE) {
            Entity entity = world.getEntity(shot.source);
            projectileHead = entity != null && !entity.isRemoved() ? entity.getPosition(partial) : shot.to;
        }
        for (int i = 0; i < copies && count < budget; i++) {
            Vec3 head, tail;
            float fade;
            if (projectileHead != null) {

                // 在现有短尾迹内分开放置三个椭圆，共同跟随一枚真实炮弹。
                double cell = Math.min(LENGTH[profile], distance) / copies;
                head = projectileHead.subtract(direction.scale(cell * i));
                tail = head.subtract(direction.scale(cell * .72));
                fade = (float) Math.min(1, (shot.life - age) / 1.5) * (1 - i * .15F);
            } else {
                double copyAge = age - i * TRACE_DELAY[profile];
                if (copyAge < 0 || copyAge > shot.flight + END_FADE)
                    continue;
                fade = (float) Math.min(1, (shot.flight + END_FADE - copyAge) / END_FADE);
                double traveled = Math.max(start, Math.min(distance, distance * copyAge / shot.flight));
                head = shot.from.add(direction.scale(traveled));
                tail = shot.from.add(direction.scale(Math.max(start, traveled - LENGTH[profile])));
            }
            if (head.distanceToSqr(tail) > .004 && event.getFrustum().isVisible(new AABB(tail, head).inflate(1)))
                count += ribbon(out, eye, tail, head, WIDTH[profile], RGB[profile], fade, event);
        }
        return count;
    }

    private static int ribbon(BufferBuilder out, Vec3 eye, Vec3 tail, Vec3 head, double baseWidth, int rgb, float alpha,
            RenderLevelStageEvent event) {

        // 即使线段中心很远，接近摄像机的部分仍可能穿过近裁剪面。
        if (alpha <= 0 || TracerDelivery.distanceToSegmentSqr(eye, tail, head) < .45 * .45)
            return 0;
        Vec3 center = tail.lerp(head, .5);
        double distance = center.distanceTo(eye);
        if (distance < .45)
            return 0;
        double width = Math.min(baseWidth * 3, Math.max(baseWidth, distance * .00075));
        Vec3 line = head.subtract(tail), normal = eye.subtract(center).normalize(),
                across = line.normalize().cross(normal);
        if (across.lengthSqr() < .01)
            return billboard(out, eye, center, width * 1.8, width, rgb, alpha, event);
        across = across.normalize().scale(width * .5);

        // 端点跟随真实射击线段，不向碰撞表面后方延伸。
        quad(out, eye, tail.subtract(across), head.subtract(across), head.add(across), tail.add(across), rgb, alpha);
        return 1;
    }

    private static int billboard(BufferBuilder out, Vec3 eye, Vec3 center, double width, double height, int rgb,
            float alpha, RenderLevelStageEvent event) {
        if (alpha <= 0)
            return 0;
        Vector3f right = event.getCamera().rotation().transform(new Vector3f(1, 0, 0)),
                up = event.getCamera().rotation().transform(new Vector3f(0, 1, 0));
        Vec3 x = new Vec3(right.x, right.y, right.z).scale(width * .5),
                y = new Vec3(up.x, up.y, up.z).scale(height * .5);
        quad(out, eye, center.subtract(x).subtract(y), center.add(x).subtract(y), center.add(x).add(y),
                center.subtract(x).add(y), rgb, alpha);
        return 1;
    }

    private static void quad(BufferBuilder out, Vec3 eye, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int rgb, float alpha) {
        int color = Math.round(Mth.clamp(alpha, 0, 1) * 255) << 24 | rgb;
        vertex(out, a.subtract(eye), 0, 1, color);
        vertex(out, b.subtract(eye), 1, 1, color);
        vertex(out, c.subtract(eye), 1, 0, color);
        vertex(out, d.subtract(eye), 0, 0, color);
    }

    private static void vertex(BufferBuilder out, Vec3 at, float u, float v, int color) {
        out.vertex((float) at.x, (float) at.y, (float) at.z).uv(u, v).color(color).endVertex();
    }

    private static boolean finite(Vec3 point) {
        return Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }

    public static void clear() {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(TracerEffects::clear);
            return;
        }
        SHOTS.clear();
        world = null;
        pawn = null;
        remote = false;
        if (storage != null) {
            storage.clear();
            storage = null;
        }
    }

    private record Shot(Vec3 from, Vec3 to, int source, int profile, long born, double flight, double life) {
    }

    @EventBusSubscriber(modid = KillstreakcodMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class TracerShaderEvents {
        private TracerShaderEvents() {}

        @SubscribeEvent
        public static void shaders(RegisterShadersEvent event) {
            try {
                event.registerShader(new ShaderInstance(event.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID, "tracer_glow"),
                        DefaultVertexFormat.POSITION_TEX_COLOR), loaded -> shader = loaded);
            }
            catch (IOException | RuntimeException | LinkageError failure) {
                shader = null;
                LogUtils.getLogger().warn("Killstreaks: optional tracer shader unavailable", failure);
            }
        }

    }
}
