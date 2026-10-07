package abyssalmadness.network;

import abyssalmadness.AbyssalMadness;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// S2C: the decay trail's config. Separate from the blackout packet because the trail
// the client has recorded outlives any number of blackout changes.
//
// wipe is an edge, not a level: true only in the packet carrying the order.
public record DecayPayload(boolean enabled, int radius, int reach, boolean wipe) implements CustomPacketPayload {
    public static final Type<DecayPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(AbyssalMadness.MOD_ID, "decay"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DecayPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, DecayPayload::enabled,
                    ByteBufCodecs.VAR_INT, DecayPayload::radius,
                    ByteBufCodecs.VAR_INT, DecayPayload::reach,
                    ByteBufCodecs.BOOL, DecayPayload::wipe,
                    DecayPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
