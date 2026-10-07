package abyssalmadness.network;

import abyssalmadness.AbyssalMadness;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// S2C: how close the Cleansing is to this player, 0 (nothing) to 1 (on top of them),
// and where it is right now.
//
// The position is here for the sound. The pass has to be heard travelling from one
// side to the other, and that means the client needs to move the source itself - a
// one-shot played at a fixed point only ever arrives from one direction.
public record CleansingPayload(boolean running, float intensity, float x, float y, float z)
        implements CustomPacketPayload {
    public static final Type<CleansingPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(AbyssalMadness.MOD_ID, "cleansing"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CleansingPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, CleansingPayload::running,
                    ByteBufCodecs.FLOAT, CleansingPayload::intensity,
                    ByteBufCodecs.FLOAT, CleansingPayload::x,
                    ByteBufCodecs.FLOAT, CleansingPayload::y,
                    ByteBufCodecs.FLOAT, CleansingPayload::z,
                    CleansingPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
