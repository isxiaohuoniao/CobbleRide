package cobbleride.network;

import cobbleride.CobbleRide;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record QuickRidePayload() implements CustomPacketPayload {
	public static final QuickRidePayload INSTANCE = new QuickRidePayload();
	public static final Type<QuickRidePayload> TYPE = new Type<>(CobbleRide.id("quick_ride"));
	public static final StreamCodec<RegistryFriendlyByteBuf, QuickRidePayload> STREAM_CODEC = StreamCodec.of(
			(buffer, payload) -> {
			},
			buffer -> INSTANCE
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
