package cobbleride.client;

import cobbleride.riding.RideEligibility;
import cobbleride.riding.RideControlActivator;
import cobbleride.network.QuickRidePayload;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class CobbleRideClient implements ClientModInitializer {
	private static final int HOLD_TICKS_TO_OPEN_WHEEL = 5;
	public static final KeyMapping RIDE_WHEEL_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.cobbleride.ride_wheel",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_V,
			"key.categories.cobbleride"
	));
	private boolean rideWheelKeyWasDown;
	private boolean wheelOpenedForCurrentPress;
	private boolean currentPressEligible;
	private int rideWheelHeldTicks;

	@Override
	public void onInitializeClient() {
		ClientTickEvents.START_CLIENT_TICK.register(this::activateMountedControls);
		ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
	}

	private void activateMountedControls(Minecraft client) {
		if (client.player != null && client.player.getVehicle() instanceof PokemonEntity pokemonEntity) {
			RideControlActivator.activateIfNeeded(pokemonEntity, client.player);
		}
	}

	private void onClientTick(Minecraft client) {
		boolean rideWheelKeyDown = isRideWheelKeyPhysicallyDown(client);
		if (client.player == null || client.level == null) {
			rideWheelKeyWasDown = rideWheelKeyDown;
			wheelOpenedForCurrentPress = false;
			currentPressEligible = false;
			rideWheelHeldTicks = 0;
			return;
		}

		if (rideWheelKeyDown && !rideWheelKeyWasDown) {
			wheelOpenedForCurrentPress = false;
			currentPressEligible = client.screen == null;
			rideWheelHeldTicks = 0;
		}

		if (client.screen instanceof RideWheelScreen wheel) {
			if (!rideWheelKeyDown) {
				wheel.confirmAndClose();
				currentPressEligible = false;
			}
			rideWheelKeyWasDown = rideWheelKeyDown;
			return;
		}

		// KeyMapping.isDown() is released by Minecraft while a Screen owns keyboard
		// input. Read GLFW state instead and open only on the physical up -> down
		// edge, so key-repeat events cannot close and reopen the wheel every tick.
		if (rideWheelKeyDown && currentPressEligible && !wheelOpenedForCurrentPress) {
			rideWheelHeldTicks++;
		}
		if (client.screen == null && rideWheelKeyDown && currentPressEligible
				&& !wheelOpenedForCurrentPress && rideWheelHeldTicks >= HOLD_TICKS_TO_OPEN_WHEEL) {
			List<Pokemon> rideablePokemon = new ArrayList<>();
			for (Pokemon pokemon : CobblemonClient.INSTANCE.getStorage().getParty()) {
				if (RideEligibility.isRideable(pokemon)) {
					rideablePokemon.add(pokemon);
				}
			}

			client.setScreen(new RideWheelScreen(rideablePokemon));
			wheelOpenedForCurrentPress = true;
		}

		if (!rideWheelKeyDown && rideWheelKeyWasDown) {
			if (currentPressEligible && !wheelOpenedForCurrentPress && client.screen == null) {
				ClientPlayNetworking.send(QuickRidePayload.INSTANCE);
			}
			currentPressEligible = false;
			rideWheelHeldTicks = 0;
		}
		rideWheelKeyWasDown = rideWheelKeyDown;
	}

	private static boolean isRideWheelKeyPhysicallyDown(Minecraft client) {
		InputConstants.Key boundKey = InputConstants.getKey(RIDE_WHEEL_KEY.saveString());
		long window = client.getWindow().getWindow();
		if (boundKey.getType() == InputConstants.Type.MOUSE) {
			return GLFW.glfwGetMouseButton(window, boundKey.getValue()) == GLFW.GLFW_PRESS;
		}
		return InputConstants.isKeyDown(window, boundKey.getValue());
	}
}
