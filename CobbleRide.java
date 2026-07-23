package cobbleride;

import cobbleride.network.RideRequestPayload;
import cobbleride.network.QuickRidePayload;
import cobbleride.riding.RideSummonService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CobbleRide implements ModInitializer {
	public static final String MOD_ID = "cobbleride";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.playC2S().register(RideRequestPayload.TYPE, RideRequestPayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(QuickRidePayload.TYPE, QuickRidePayload.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(RideRequestPayload.TYPE, (payload, context) ->
				context.server().execute(() -> RideSummonService.summonAndMount(context.player(), payload.pokemonId()))
		);
		ServerPlayNetworking.registerGlobalReceiver(QuickRidePayload.TYPE, (payload, context) ->
				context.server().execute(() -> RideSummonService.quickRide(context.player()))
		);
		ServerTickEvents.END_SERVER_TICK.register(RideSummonService::trackMountedPokemon);

		LOGGER.info("CobbleRide initialized");
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
