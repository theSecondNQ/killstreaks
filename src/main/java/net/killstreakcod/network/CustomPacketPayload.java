package net.killstreakcod.network;

import net.minecraft.resources.ResourceLocation;

/** Stable message identifiers shared by the Forge network registrations. */
public interface CustomPacketPayload {
    Type<? extends CustomPacketPayload> type();

    record Type<T extends CustomPacketPayload>(Class<T> messageClass, ResourceLocation id) {}
}
