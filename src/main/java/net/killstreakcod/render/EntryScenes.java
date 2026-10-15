package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.killstreaks.NativeCruiseFlight;
import net.killstreakcod.killstreaks.TacticalUnitEntity;
import net.killstreakcod.render.compat.OptionalShaderCompat;
import net.killstreakcod.universal.SceneEntryLibrary;

import java.util.Map;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/**
 * Cinematic actors use the world render projection and source tag_camera, never the hand pass.
 * 过场角色使用世界渲染投影和源资源 tag_camera，不在手部渲染阶段绘制。
 */
@EventBusSubscriber(modid = KillstreakcodMod.MODID, value = Dist.CLIENT)
public final class EntryScenes {
    private static PlayerModel<net.minecraft.client.player.AbstractClientPlayer> avatar, slimAvatar;
    private static float heading;
    private static int sceneLight;
    private static final ResourceLocation FLAME = ResourceLocation.withDefaultNamespace("textures/particle/flame.png");
    private static final ResourceLocation HULL = ResourceLocation.fromNamespaceAndPath(KillstreakcodMod.MODID,
            "textures/entity/tactical_atlas.png");
    public static final int GUNSHIP_FLYPAST = SceneEntryLibrary.GUNSHIP_EXTERIOR;

    private EntryScenes() {}

    public static void begin(float bodyHeading) {
        heading = bodyHeading;
    }

    public static void reset() {
        heading = 0;
    }

    public static boolean active() {
        return AircraftCrashClient.active() || StreakPresentation.transitioning()
                && StreakPresentation.currentStage().equals("entry") && (switch (StreakPresentation.currentReward()) {
                    case "gunship" -> StreakPresentation.elapsed() < SceneEntryLibrary.GUNSHIP_ENTRY;
                    case "chopper_gunner", "cruise_missile" -> true;
                    default -> false;
                });
    }

    /**
     * This is called after TacticalClient seeds the remote sensor angles.
     * 在 TacticalClient 设置遥控传感器初始角度之后调用。
     */
    public static boolean overrideAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!active())
            return false;
        Matrix4f camera = camera();
        if (camera == null)
            return false;

        // 源资源 tag_player 的绝对倾角叠加到陡峭导弹轨迹后，会越过竖直向下的极点，使入场中途偏航翻转 180 度；改用相对动作终点的运动，也能平滑衔接传感器朝向。
        Quaternionf source = !AircraftCrashClient.active()
                && StreakPresentation.currentReward().equals("cruise_missile")
                        ? NativeCruiseFlight.cameraRotation(camera)
                        : camera.getNormalizedRotation(new Quaternionf());
        Vector3f forward = worldDirection(source.transform(new Vector3f(1, 0, 0))),
                up = worldDirection(source.transform(new Vector3f(0, 0, 1)));
        event.setYaw((float) Math.toDegrees(Math.atan2(-forward.x, forward.z)));
        event.setPitch((float) -Math.toDegrees(Math.asin(Mth.clamp(forward.y, -1, 1))));
        Vector3f left = new Vector3f(0, 1, 0).cross(forward);
        float roll = 0;
        if (left.lengthSquared() > .001F) {
            left.normalize();
            Vector3f upright = new Vector3f(forward).cross(left).normalize();
            roll = (float) -Math.toDegrees(Math.atan2(up.dot(left), up.dot(upright)));
        }
        event.setRoll(roll * Minecraft.getInstance().options.screenEffectScale().get().floatValue());
        return true;
    }

    public static boolean overrideFov(ViewportEvent.ComputeFov event) {
        if (!active())
            return false;
        String reward = StreakPresentation.currentReward();
        event.setFOV(reward.equals("gunship") ? 80
                : reward.equals("chopper_gunner")
                        ? Mth.lerp(Mth.clamp((StreakPresentation.elapsed() - 80) / 2F, 0, 1), 75, TacticalClient.fov())
                        : 70);
        return true;
    }

    private static Quaternionf flightAttitude() {
        if (AircraftCrashClient.active())
            return AircraftCrashClient.attitude();
        var camera = Minecraft.getInstance().getCameraEntity();
        float yaw = heading, pitch = 0;
        if (camera instanceof TacticalUnitEntity unit && (unit.kind() == TacticalUnitEntity.Kind.CHOPPER
                || unit.kind() == TacticalUnitEntity.Kind.GUNSHIP || unit.kind() == TacticalUnitEntity.Kind.CRUISE)) {
            if (unit.kind() == TacticalUnitEntity.Kind.CRUISE && TacticalClient.remote()) {

                // 入场消息包含稳定的发射姿态；第一帧绘制时，实体数据插值可能仍未追上当前状态。
                yaw = TacticalClient.yaw();
                pitch = TacticalClient.pitch();
            } else {
                float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
                yaw = unit.renderFlightYaw(partial);
                pitch = unit.renderFlightPitch(partial);
            }
        }
        return new Quaternionf().rotateY((float) Math.toRadians(180 - yaw)).rotateX((float) Math.toRadians(-pitch));
    }

    private static Vector3f worldDirection(Vector3f source) {
        return flightAttitude().transform(new Vector3f(-source.y, source.z, -source.x)).normalize();
    }

    /**
     * Position and orientation use the same vehicle-local camera, after removing the source flight root.
     * 移除源资源的飞行根节点运动后，位置和方向共用同一个载具局部摄像机。
     */
    public static Vec3 cameraPosition(Camera camera) {
        if (AircraftCrashClient.active()) {
            Vector3f p = AircraftCrashClient.camera().getTranslation(new Vector3f()),
                    offset = AircraftCrashClient.attitude().transform(new Vector3f(-p.y, p.z, -p.x).mul(.01F));
            return AircraftCrashClient.origin().add(offset.x, offset.y, offset.z);
        }
        if (TacticalClient.remote() && TacticalClient.state.mode().equals("cruise")
                && camera.getEntity() instanceof TacticalUnitEntity unit)
            return NativeCruiseScene.camera(unit, StreakPresentation.elapsed(), active(),
                    Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true));
        if (!active())
            return null;
        var entity = camera.getEntity();
        Matrix4f source = camera();
        if (!(entity instanceof TacticalUnitEntity) || source == null)
            return null;
        Vector3f p = source.getTranslation(new Vector3f()),
                offset = flightAttitude().transform(new Vector3f(-p.y, p.z, -p.x).mul(.01F));
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        return entity.getPosition(partial).add(offset.x, offset.y, offset.z);
    }

    private static Matrix4f camera() {
        if (AircraftCrashClient.active())
            return AircraftCrashClient.camera();
        String reward = StreakPresentation.currentReward();
        float ticks = StreakPresentation.elapsed();
        if (reward.equals("gunship") || reward.equals("chopper_gunner"))
            return SceneEntryRenderer.camera(reward, ticks);
        return NativeCruiseFlight.camera(ticks);
    }

    @SubscribeEvent
    public static void world(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || !active()
                || OptionalShaderCompat.shadowPass())
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.player.isAlive())
            return;
        var buffers = mc.renderBuffers().bufferSource();
        buffers.endBatch();
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE),
                blend = GL11.glIsEnabled(GL11.GL_BLEND), depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        int[] factors = { GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA) };
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix().set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        float[] color = RenderSystem.getShaderColor().clone();
        RenderSystem.setProjectionMatrix(event.getProjectionMatrix(), VertexSorting.DISTANCE_TO_ORIGIN);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        try {

            // 将保留的源资源摄像机局部姿态转回世界相对顶点，使 Iris 在光照及最终处理前取得实体法线和深度；这里必须保留地形深度，清除它会抹掉实时世界的深度信息。
            PoseStack pose = new PoseStack();
            pose.mulPose(new Matrix4f(event.getModelViewMatrix()).invert());
            TacticalClient.cinematicShake(pose);
            float time = StreakPresentation.elapsed();
            sceneLight = AircraftLighting.at(mc.level, event.getCamera().getPosition());
            if (AircraftCrashClient.active())
                AircraftCrashRenderer.movie(time, pose, buffers, sceneLight);
            else
                switch (StreakPresentation.currentReward()) {
                    case "gunship", "chopper_gunner" ->
                        SceneEntryRenderer.draw(StreakPresentation.currentReward(), time, pose, buffers, sceneLight);
                    case "cruise_missile" -> NativeCruiseScene.movie(pose, buffers, sceneLight, time);
                }
        } finally {

            // 恢复常规世界矩阵前，先提交摄像机局部空间的顶点缓冲。
            try {
                buffers.endBatch();
            } finally {
                RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
                RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
                modelView.popMatrix();
                RenderSystem.applyModelViewMatrix();
                if (depth)
                    RenderSystem.enableDepthTest();
                else
                    RenderSystem.disableDepthTest();
                if (cull)
                    RenderSystem.enableCull();
                else
                    RenderSystem.disableCull();
                if (blend)
                    RenderSystem.enableBlend();
                else
                    RenderSystem.disableBlend();
                RenderSystem.depthFunc(depthFunc);
                RenderSystem.depthMask(depthWrite);
                RenderSystem.blendFuncSeparate(factors[0], factors[1], factors[2], factors[3]);
            }
        }
    }

    public static void renderPlayer(PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation skin,
            boolean slim, Map<String, Matrix4f> bones, Map<String, Matrix4f> rest) {
        if (avatar == null)
            avatar = new PlayerModel<>(
                    LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64).bakeRoot(),
                    false);
        if (slimAvatar == null)
            slimAvatar = new PlayerModel<>(
                    LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, true), 64, 64).bakeRoot(),
                    true);
        var model = slim ? slimAvatar : avatar;
        var out = buffers.getBuffer(RenderType.entityCutoutNoCull(skin));
        segment(model.body, model.jacket, "j_neck", "j_mainroot", .75F, pose, out, light, bones, rest);
        segment(model.leftArm, model.leftSleeve, "j_shoulder_le", "j_wrist_le", .75F, pose, out, light, bones, rest);
        segment(model.rightArm, model.rightSleeve, "j_shoulder_ri", "j_wrist_ri", .75F, pose, out, light, bones, rest);
        segment(model.leftLeg, model.leftPants, "j_hip_le", "j_ankle_le", .75F, pose, out, light, bones, rest);
        segment(model.rightLeg, model.rightPants, "j_hip_ri", "j_ankle_ri", .75F, pose, out, light, bones, rest);
        Matrix4f head = bones.get("j_head");
        if (head != null && position(head).length() > .38F) {
            Vector3f at = position(head);
            pose.pushPose();
            try {
                pose.translate(at.x, at.y, at.z);
                pose.mulPose(rotation("j_head", bones, rest));
                pose.mulPose(Axis.ZP.rotationDegrees(180));
                pose.scale(.7F, .7F, .7F);
                part(model.head, pose, out, light);
                part(model.hat, pose, out, light);
            } finally {
                pose.popPose();
            }
        }
    }

    private static void segment(ModelPart part, ModelPart layer, String start, String end, float length, PoseStack pose,
            com.mojang.blaze3d.vertex.VertexConsumer out, int light, Map<String, Matrix4f> bones,
            Map<String, Matrix4f> rest) {
        if (!bones.containsKey(start) || !bones.containsKey(end))
            return;
        Vector3f at = position(bones.get(start)), delta = position(bones.get(end)).sub(at);
        float distance = delta.length();
        if (distance < .01F)
            return;
        Vector3f down = delta.normalize(), back = rotation(start, bones, rest).transform(new Vector3f(0, 0, 1));
        back.sub(new Vector3f(down).mul(back.dot(down)));
        Quaternionf direction;
        if (back.lengthSquared() < .001F)
            direction = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), down);
        else {
            back.normalize();
            Vector3f right = new Vector3f(down).cross(back).normalize();
            direction = new Quaternionf()
                    .setFromNormalized(new Matrix3f().setColumn(0, right).setColumn(1, down).setColumn(2, back));
        }
        pose.pushPose();
        try {
            pose.translate(at.x, at.y, at.z);
            pose.mulPose(direction);
            pose.scale(.8F, distance / length, .8F);
            part(part, pose, out, light);
            part(layer, pose, out, light);
        } finally {
            pose.popPose();
        }
    }

    private static Quaternionf rotation(String bone, Map<String, Matrix4f> pose, Map<String, Matrix4f> rest) {
        if (!pose.containsKey(bone) || !rest.containsKey(bone))
            return new Quaternionf();
        Matrix4f delta = new Matrix4f().rotate(pose.get(bone).getNormalizedRotation(new Quaternionf()))
                .rotate(rest.get(bone).getNormalizedRotation(new Quaternionf()).invert());
        Matrix4f basis = new Matrix4f().m00(0).m01(0).m02(-1).m10(-1).m11(0).m12(0).m20(0).m21(1).m22(0);
        return new Matrix4f(basis).mul(delta).mul(new Matrix4f(basis).invert())
                .getNormalizedRotation(new Quaternionf());
    }

    private static Vector3f position(Matrix4f source) {
        Vector3f p = source.getTranslation(new Vector3f());
        return new Vector3f(-p.y, p.z, -p.x).mul(.01F);
    }

    private static void part(ModelPart part, PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer out, int light) {
        part.setPos(0, 0, 0);
        part.xRot = part.yRot = part.zRot = 0;
        part.render(pose, out, light, OverlayTexture.NO_OVERLAY);
    }
}
