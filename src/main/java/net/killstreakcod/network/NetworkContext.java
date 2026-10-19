package net.killstreakcod.network;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

public record NetworkContext(NetworkEvent.Context context) {
    public Player player() {
        return context.getSender();
    }

    public void enqueueWork(Runnable action) {
        context.enqueueWork(action);
    }
}
