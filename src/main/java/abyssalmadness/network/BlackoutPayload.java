package abyssalmadness.network;

import abyssalmadness.AbyssalMadness;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// S2C: the whole blackout config, resent in full on every change - any of these fields
// costs a chunk mesh rebuild anyway, so there is nothing to save by sending a delta.
// The palette is an index into the client's list; its blocks only exist there.
public record BlackoutPayload(boolean enabled, int radius, float noise, int palette,
                              boolean follow, boolean reanchor) implements CustomPacketPayload {
    public static final Type<BlackoutPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(AbyssalMadness.MOD_ID, "blackout"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BlackoutPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, BlackoutPayload::enabled,
                    ByteBufCodecs.VAR_INT, BlackoutPayload::radius,
                    ByteBufCodecs.FLOAT, BlackoutPayload::noise,
                    ByteBufCodecs.VAR_INT, BlackoutPayload::palette,
                    ByteBufCodecs.BOOL, BlackoutPayload::follow,
                    ByteBufCodecs.BOOL, BlackoutPayload::reanchor,
                    BlackoutPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
