package cobbleride.riding;

import com.cobblemon.mod.common.api.riding.RidingProperties;
import com.cobblemon.mod.common.pokemon.Pokemon;

import java.util.List;
import java.util.Map;

public final class RideEligibility {
	private RideEligibility() {
	}

	public static boolean isRideable(Pokemon pokemon) {
		if (pokemon == null || pokemon.isFainted()) {
			return false;
		}

		if (pokemon.getForm() == null) {
			return false;
		}

		RidingProperties riding = pokemon.getForm().getRiding();
		if (riding == null) {
			return false;
		}

		// Custom species and partially synced client forms can legally deserialize
		// a riding object with either collection left null. Treat incomplete riding
		// data as non-rideable instead of crashing the client or server.
		List<?> seats = riding.getSeats();
		Map<?, ?> behaviours = riding.getBehaviours();
		return seats != null && !seats.isEmpty() && behaviours != null && !behaviours.isEmpty();
	}

	public static boolean canFly(Pokemon pokemon) {
		if (!isRideable(pokemon)) {
			return false;
		}

		Map<?, ?> behaviours = pokemon.getForm().getRiding().getBehaviours();
		return behaviours != null && behaviours.containsKey(com.cobblemon.mod.common.api.riding.RidingStyle.AIR);
	}
}
