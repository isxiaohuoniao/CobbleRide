package cobbleride.riding;

import com.cobblemon.mod.common.api.riding.RidingStyle;
import com.cobblemon.mod.common.api.riding.behaviour.RidingBehaviourSettings;
import com.cobblemon.mod.common.api.riding.behaviour.RidingController;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Map;

public final class RideControlActivator {
	private RideControlActivator() {
	}

	public static void activateIfNeeded(PokemonEntity entity, Player rider) {
		activateIfNeeded(entity, rider, null);
	}

	public static void activateIfNeeded(PokemonEntity entity, Player rider, RidingStyle preferredOverride) {
		RidingController controller = entity.getRidingController();
		if (controller == null || controller.getContext() != null) {
			return;
		}

		Map<RidingStyle, RidingBehaviourSettings> behaviours = controller.getBehaviours();
		if (behaviours == null || behaviours.isEmpty()) {
			return;
		}

		RidingStyle preferred = preferredOverride != null && behaviours.get(preferredOverride) != null
				? preferredOverride
				: preferredStyle(entity, rider, behaviours);
		RidingBehaviourSettings settings = preferred == null ? null : behaviours.get(preferred);
		if (settings == null) {
			settings = behaviours.values().stream().filter(value -> value != null).findFirst().orElse(null);
		}
		if (settings != null && settings.getKey() != null) {
			controller.changeBehaviour(settings.getKey());
		}
	}

	private static RidingStyle preferredStyle(
			PokemonEntity entity,
			Player rider,
			Map<RidingStyle, RidingBehaviourSettings> behaviours
	) {
		if (entity.isFlying() && behaviours.get(RidingStyle.AIR) != null) {
			return RidingStyle.AIR;
		}
		if (!rider.onGround() && !rider.isInWaterOrBubble() && !rider.isInLava()
				&& behaviours.get(RidingStyle.AIR) != null) {
			return RidingStyle.AIR;
		}
		if ((rider.isInWaterOrBubble() || rider.isInLava()) && behaviours.get(RidingStyle.LIQUID) != null) {
			return RidingStyle.LIQUID;
		}
		if (behaviours.get(RidingStyle.LAND) != null) {
			return RidingStyle.LAND;
		}
		if (behaviours.get(RidingStyle.AIR) != null) {
			return RidingStyle.AIR;
		}
		return behaviours.get(RidingStyle.LIQUID) != null ? RidingStyle.LIQUID : null;
	}
}
