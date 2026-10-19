package net.killstreakcod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public final class ByteBufCodecs {
    public static final StreamCodec<FriendlyByteBuf, Boolean> BOOL =
            StreamCodec.of((buffer, value) -> buffer.writeBoolean(value), FriendlyByteBuf::readBoolean);
    public static final StreamCodec<FriendlyByteBuf, Integer> VAR_INT =
            StreamCodec.of((buffer, value) -> buffer.writeVarInt(value), FriendlyByteBuf::readVarInt);
    public static final StreamCodec<FriendlyByteBuf, String> STRING_UTF8 =
            StreamCodec.of((buffer, value) -> buffer.writeUtf(value), FriendlyByteBuf::readUtf);
    public static final StreamCodec<FriendlyByteBuf, ResourceLocation> RESOURCE_LOCATION =
            StreamCodec.of((buffer, value) -> buffer.writeResourceLocation(value), FriendlyByteBuf::readResourceLocation);

    private ByteBufCodecs() {}
}
