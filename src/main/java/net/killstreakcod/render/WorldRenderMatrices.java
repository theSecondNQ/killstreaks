package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.render.compat.OptionalShaderCompat;

import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Retains the actual world view before Forge 1.20.1 dispatches AFTER_LEVEL with its projection pose stack. */
@EventBusSubscriber(modid = KillstreakcodMod.MODID, value = Dist.CLIENT)
public final class WorldRenderMatrices {
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Quaternionf BILLBOARD = new Quaternionf();
    private static ClientLevel world;
    private static Camera camera;
    private static int tick;
    private static float partial;

    private WorldRenderMatrices() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void capture(RenderLevelStageEvent event) {
        if ((event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY
                && event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES
                && event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) || OptionalShaderCompat.shadowPass())
            return;
        VIEW.set(event.getPoseStack().last().pose());
        billboardRotation(VIEW, BILLBOARD);
        world = Minecraft.getInstance().level;
        camera = event.getCamera();
        tick = event.getRenderTick();
        partial = event.getPartialTick();
    }

    /** Forge viewport angles and roll can differ from the quaternion retained by Camera. */
    static Quaternionf billboardRotation(RenderLevelStageEvent event) {
        Matrix4f view = view(event);
        return view == VIEW ? BILLBOARD : billboardRotation(view, new Quaternionf());
    }

    private static Quaternionf billboardRotation(Matrix4f view, Quaternionf rotation) {
        // Keep the 1.20.1 billboard texture basis (+Z forward), using the effective rendered view.
        return view.getUnnormalizedRotation(rotation).conjugate().rotateY((float) Math.PI).normalize();
    }

    static Matrix4f view(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL)
            return event.getPoseStack().last().pose();
        if (world == Minecraft.getInstance().level && camera == event.getCamera()
                && tick == event.getRenderTick() && partial == event.getPartialTick())
            return VIEW;
        return new Matrix4f().rotate(Axis.XP.rotationDegrees(event.getCamera().getXRot()))
                .rotate(Axis.YP.rotationDegrees(event.getCamera().getYRot() + 180));
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        world = null;
        camera = null;
    }
}
