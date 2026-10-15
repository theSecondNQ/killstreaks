package net.killstreakcod.mixin;

import net.killstreakcod.render.TacticalClient;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Extend only visible, already tracked remote targets; frustum and normal depth occlusion remain.
 * 只扩展已被跟踪且可见的遥控目标渲染距离，保留视锥裁剪与正常深度遮挡。
 */
@Mixin(Entity.class)
public abstract class RemoteLivingRenderDistanceMixin {
    @Inject(method = "shouldRenderAtSqrDistance", at = @At("RETURN"), cancellable = true)
    private void killstreak$remoteTarget(double distance, CallbackInfoReturnable<Boolean> result) {
        if (result.getReturnValue() || !TacticalClient.remote() || distance > 256 * 256)
            return;
        Entity self = (Entity) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        if (!(self instanceof LivingEntity living) || mc.level == null || mc.player == null || !living.isAlive()
                || living.isRemoved() || living.isInvisibleTo(mc.player) || mc.level.getEntity(self.getId()) != self)
            return;
        Vec3 eye = living.getEyePosition(), camera = mc.gameRenderer.getMainCamera().getPosition();
        if (mc.level.clip(new ClipContext(camera, eye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, self))
                .getType() != HitResult.Type.MISS)
            return;
        int top = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, living.blockPosition().getX(),
                living.blockPosition().getZ());
        Vec3 above = new Vec3(eye.x, Math.max(top + 1, eye.y + .25), eye.z);
        if (mc.level.clip(new ClipContext(above, eye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, self))
                .getType() == HitResult.Type.MISS)
            result.setReturnValue(true);
    }
}
