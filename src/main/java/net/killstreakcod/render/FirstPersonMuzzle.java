package net.killstreakcod.render;

import net.killstreakcod.render.tablet.TabletHandModel;

import java.util.Optional;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

/** 将实际手持层炮口重投影到同一帧的世界层，包含晃动、左右手和两套视野投影。 */
final class FirstPersonMuzzle {
    private static final Matrix4f WORLD_PROJECTION = new Matrix4f(), INVERSE_WORLD = new Matrix4f();
    private static ClientLevel world;
    private static Vec3 eye = Vec3.ZERO;
    private static boolean available;

    private FirstPersonMuzzle() {}

    static void world(Matrix4fc view, Matrix4fc projection, Vec3 position) {
        world = Minecraft.getInstance().level;
        eye = position;
        WORLD_PROJECTION.set(projection);
        INVERSE_WORLD.set(projection).mul(view).invert();
        available = world != null;
    }

    static Optional<Vec3> position(PoseStack pose, Matrix4f socket) {
        if (!available || world != Minecraft.getInstance().level || socket == null)
            return Optional.empty();
        return project(pose.last().pose(), RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(),
                socket, WORLD_PROJECTION, INVERSE_WORLD, eye);
    }

    /** 以手持投影确定屏幕位置，以世界投影确定深度；不能直接把英寸骨骼偏移加到摄像机上。 */
    static Optional<Vec3> project(Matrix4fc handPose, Matrix4fc handView, Matrix4fc handProjection, Matrix4f socket,
            Matrix4fc worldProjection, Matrix4fc inverseWorld, Vec3 eye) {
        Vector4f viewPoint = new Matrix4f(handView).mul(handPose).mul(TabletHandModel.sourceCameraMatrix(socket))
                .transform(new Vector4f(0, 0, 0, 1));
        Vector4f handClip = handProjection.transform(new Vector4f(viewPoint));
        Vector4f depth = worldProjection.transform(new Vector4f(0, 0, viewPoint.z / viewPoint.w, 1));
        if (handClip.w <= .001F || Math.abs(depth.w) <= .001F)
            return Optional.empty();
        Vector4f point = inverseWorld
                .transform(new Vector4f(handClip.x / handClip.w, handClip.y / handClip.w, depth.z / depth.w, 1));
        if (Math.abs(point.w) <= .001F)
            return Optional.empty();
        Vec3 offset = new Vec3(point.x / point.w, point.y / point.w, point.z / point.w);
        if (!Double.isFinite(offset.lengthSqr()) || offset.lengthSqr() > 64)
            return Optional.empty();
        return Optional.of(eye.add(offset));
    }

    static void reset() {
        available = false;
        world = null;
    }
}
