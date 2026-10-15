package net.killstreakcod.killstreaks;

import net.killstreakcod.universal.KillstreakService;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.Level;

/**
 * A steerable pig-derived vehicle with a dedicated armored rover renderer.
 * 基于猪实体实现可驾驶载具，并使用专用的装甲无人车渲染器。
 */
public final class WheelsonEntity extends Pig {
    public WheelsonEntity(EntityType<? extends Pig> type, Level level) {
        super(type, level);
        setNoAi(true);
        addTag(KillstreakService.WHEELSON_TAG);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
        return null;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }
}
