package abyssalmadness.network;

import abyssalmadness.AbyssalMadness;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// S2C: where the beacon stands and whether it is burning. The light itself is drawn
// entirely on the client, so this is all the server has to say about it.
public record BeaconPayload(BlockPos pos, boolean active) implements CustomPacketPayload {
    public static final Type<BeaconPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(AbyssalMadness.MOD_ID, "beacon"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BeaconPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, BeaconPayload::pos,
                    ByteBufCodecs.BOOL, BeaconPayload::active,
                    BeaconPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
