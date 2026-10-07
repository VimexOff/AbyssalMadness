package abyssalmadness.network;

import abyssalmadness.AbyssalMadness;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// S2C: whether the Sleeping Stars event is currently awake.
public record StarStatePayload(boolean awake) implements CustomPacketPayload {
    public static final Type<StarStatePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(AbyssalMadness.MOD_ID, "star_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StarStatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, StarStatePayload::awake,
                    StarStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
