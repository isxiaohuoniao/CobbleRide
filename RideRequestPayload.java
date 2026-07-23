package cobbleride.network;

import cobbleride.CobbleRide;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

public record RideRequestPayload(UUID pokemonId) implements CustomPacketPayload {
	public static final Type<RideRequestPayload> TYPE = new Type<>(CobbleRide.id("ride_request"));
	public static final StreamCodec<RegistryFriendlyByteBuf, RideRequestPayload> STREAM_CODEC = StreamCodec.of(
			(buffer, payload) -> buffer.writeUUID(payload.pokemonId),
			buffer -> new RideRequestPayload(buffer.readUUID())
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
