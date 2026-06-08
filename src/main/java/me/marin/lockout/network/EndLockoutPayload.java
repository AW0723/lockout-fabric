package me.marin.lockout.network;

import me.marin.lockout.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record EndLockoutPayload(int[] winners, long time) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<EndLockoutPayload> ID = new CustomPacketPayload.Type<>(Constants.END_LOCKOUT_PACKET);
    public static final StreamCodec<RegistryFriendlyByteBuf, EndLockoutPayload> CODEC = StreamCodec.composite(
            StreamCodec.of((buf, winners) -> buf.writeVarIntArray(winners), buf -> buf.readVarIntArray()),
            EndLockoutPayload::winners,
            ByteBufCodecs.VAR_LONG,
            EndLockoutPayload::time,
            EndLockoutPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
