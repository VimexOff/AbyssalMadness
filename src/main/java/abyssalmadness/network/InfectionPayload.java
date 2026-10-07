package abyssalmadness.network;

import abyssalmadness.AbyssalMadness;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

// S2C: a player's infection level, 0..100, and which light it burns with. Sent for
// every player so it shows on others too.
//
// The palette rides along with the level rather than travelling in a packet of its
// own because the two are always set together and always read together - a level
// with no colour, or a colour arriving a tick after the level, would both show up
// on screen as a figure flickering the wrong shade.
public record InfectionPayload(UUID player, int level, boolean warm) implements CustomPacketPayload {
    public static final Type<InfectionPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(AbyssalMadness.MOD_ID, "infection"));

    public static final StreamCodec<RegistryFriendlyByteBuf, InfectionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, InfectionPayload::player,
                    ByteBufCodecs.VAR_INT, InfectionPayload::level,
                    ByteBufCodecs.BOOL, InfectionPayload::warm,
                    InfectionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
