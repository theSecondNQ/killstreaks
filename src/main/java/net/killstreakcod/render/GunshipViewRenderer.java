package net.killstreakcod.render;

import net.killstreakcod.KillstreakcodMod;
import net.killstreakcod.killstreaks.TacticalUnitEntity;
import net.killstreakcod.render.compat.OptionalShaderCompat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** 炮手使用机体作为摄像机实体时，补绘被原版第一人称筛选跳过的机体。 */
@EventBusSubscriber(modid = KillstreakcodMod.MODID, value = Dist.CLIENT)
public final class GunshipViewRenderer {
    private GunshipViewRenderer() {}

    /** 机外和座舱过场使用各自的模型，此处只接管正常炮手视角。 */
    static boolean visible(TacticalUnitEntity unit) {
        return unit.kind() == TacticalUnitEntity.Kind.GUNSHIP && TacticalClient.remote()
                && TacticalClient.state.entityId() == unit.getId() && unit.isAlive() && !unit.crashing()
                && !EntryScenes.active();
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || OptionalShaderCompat.shadowPass())
            return;
        Minecraft mc = Minecraft.getInstance();
        var camera = event.getCamera();
        if (mc.level == null || mc.player == null || !mc.player.isAlive() || camera.isDetached()
                || !(camera.getEntity() instanceof TacticalUnitEntity unit) || unit.level() != mc.level
                || !visible(unit))
            return;
        if (!(mc.getEntityRenderDispatcher().getRenderer(unit) instanceof TacticalUnitRenderer renderer))
            return;

        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 position = unit.getPosition(partial), relative = position.subtract(camera.getPosition());
        PoseStack pose = new PoseStack();
        pose.translate(relative.x, relative.y, relative.z);
        var buffers = mc.renderBuffers().bufferSource();

        // 先提交其他实体的剩余顶点，再使用本帧世界视图；不清除深度，也不改变摄像机位置。
        buffers.endBatch();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix().set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        try {
            renderer.render(unit, Mth.rotLerp(partial, unit.yRotO, unit.getYRot()), partial, pose, buffers,
                    AircraftLighting.at(mc.level, position));
        } finally {
            try {
                buffers.endBatch();
            } finally {
                modelView.popMatrix();
                RenderSystem.applyModelViewMatrix();
            }
        }
    }
}
