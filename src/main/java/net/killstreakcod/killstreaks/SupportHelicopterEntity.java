package net.killstreakcod.killstreaks;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.Level;

public final class SupportHelicopterEntity extends Phantom {
    private static final EntityDataAccessor<Boolean> BOMB_DRONE = SynchedEntityData
            .defineId(SupportHelicopterEntity.class, EntityDataSerializers.BOOLEAN);

    public SupportHelicopterEntity(EntityType<? extends Phantom> type, Level level) {
        super(type, level);
        setNoAi(true);
        setNoGravity(true);
        setInvulnerable(true);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(BOMB_DRONE, false);
    }

    public boolean isBombDrone() {
        return getEntityData().get(BOMB_DRONE);
    }
}
