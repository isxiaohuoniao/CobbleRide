package cobbleride.riding;

import cobbleride.CobbleRide;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.activestate.ActivePokemonState;
import com.cobblemon.mod.common.pokemon.activestate.InactivePokemonState;
import com.cobblemon.mod.common.pokemon.activestate.ShoulderedState;
import kotlin.Unit;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RideSummonService {
	private static final Set<UUID> PENDING_SUMMONS = ConcurrentHashMap.newKeySet();
	private static final java.util.Map<UUID, UUID> LAST_RIDDEN_POKEMON = new ConcurrentHashMap<>();
	private static final Map<UUID, PendingSwitch> PENDING_SWITCHES = new ConcurrentHashMap<>();
	private static final int SWITCH_TIMEOUT_TICKS = 200;

	private RideSummonService() {
	}

	public static void quickRide(ServerPlayer player) {
		if (PENDING_SUMMONS.contains(player.getUUID())) {
			return;
		}

		if (player.getVehicle() instanceof PokemonEntity currentMount) {
			Pokemon partyPokemon = findPartyPokemon(player, currentMount.getPokemon().getUuid());
			if (partyPokemon == null) {
				return;
			}

			LAST_RIDDEN_POKEMON.put(player.getUUID(), partyPokemon.getUuid());
			player.stopRiding();
			player.fallDistance = 0.0F;
			currentMount.recallWithAnimation();
			return;
		}

		Pokemon target = null;
		UUID lastRidden = LAST_RIDDEN_POKEMON.get(player.getUUID());
		if (lastRidden != null) {
			Pokemon remembered = findPartyPokemon(player, lastRidden);
			if (RideEligibility.isRideable(remembered)) {
				target = remembered;
			}
		}
		if (target == null) {
			for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
				if (RideEligibility.isRideable(pokemon)) {
					target = pokemon;
					break;
				}
			}
		}

		if (target == null) {
			player.displayClientMessage(Component.translatable("screen.cobbleride.no_rideable"), true);
			return;
		}
		summonAndMount(player, target.getUuid());
	}

	public static void trackMountedPokemon(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.getVehicle() instanceof PokemonEntity mount
					&& findPartyPokemon(player, mount.getPokemon().getUuid()) != null) {
				LAST_RIDDEN_POKEMON.put(player.getUUID(), mount.getPokemon().getUuid());
			}
		}
		processPendingSwitches(server);
	}

	public static void summonAndMount(ServerPlayer player, UUID pokemonId) {
		Pokemon pokemon = findPartyPokemon(player, pokemonId);
		if (pokemon == null) {
			return;
		}
		if (!RideEligibility.isRideable(pokemon)) {
			player.displayClientMessage(Component.translatable("message.cobbleride.not_rideable"), true);
			return;
		}

		PokemonEntity currentMount = player.getVehicle() instanceof PokemonEntity entity ? entity : null;
		if (currentMount != null && currentMount.getPokemon().getUuid().equals(pokemonId)) {
			return;
		}
		if (!PENDING_SUMMONS.add(player.getUUID())) {
			return;
		}
		if (currentMount != null && currentMount.isBattling()) {
			PENDING_SUMMONS.remove(player.getUUID());
			player.displayClientMessage(Component.translatable("message.cobbleride.in_battle"), true);
			return;
		}

		PokemonEntity existingEntity = pokemon.getEntity();
		if (existingEntity != null && existingEntity.isBattling()) {
			PENDING_SUMMONS.remove(player.getUUID());
			player.displayClientMessage(Component.translatable("message.cobbleride.in_battle"), true);
			return;
		}

		// A sent-out Pokémon is recalled immediately so the same party member can be
		// summoned beside its rider even across dimensions. Shoulder send-out has a
		// dedicated Cobblemon animation and is handled by sendOutWithAnimation below.
		if (pokemon.getState() instanceof ActivePokemonState activeState
				&& !(activeState instanceof ShoulderedState)) {
			activeState.recall();
			pokemon.setState(new InactivePokemonState());
		}

		ServerLevel level = player.serverLevel();
		WaterSurface waterSurface = RideEligibility.canFly(pokemon) ? findWaterSurface(player) : null;
		boolean waterFlightSummon = currentMount == null && waterSurface != null;
		SwitchPlan switchPlan = currentMount == null ? null : createSwitchPlan(player, pokemon);
		Vec3 requestedPosition = switchPlan != null
				? switchPlan.spawnPosition()
				: waterFlightSummon ? createWaterFlightPosition(player, waterSurface, 2.5D) : player.position();
		try {
			pokemon.sendOutWithAnimation(
					player,
					level,
					requestedPosition,
					null,
					true,
					null,
					entity -> {
						prepareSummonedPokemon(player, entity, pokemon, switchPlan, requestedPosition, waterFlightSummon);
						return Unit.INSTANCE;
					}
			).whenComplete((entity, error) -> player.server.execute(() -> {
				if (error != null) {
					PENDING_SUMMONS.remove(player.getUUID());
					CobbleRide.LOGGER.warn("Failed to summon ride Pokémon {} for {}", pokemonId, player.getGameProfile().getName(), error);
					return;
				}
				if (switchPlan != null && player.getVehicle() instanceof PokemonEntity) {
					beginSwitchApproach(player, pokemonId, entity, switchPlan);
				} else {
					PENDING_SUMMONS.remove(player.getUUID());
					finishMount(player, pokemonId, entity);
				}
			}));
		} catch (RuntimeException exception) {
			PENDING_SUMMONS.remove(player.getUUID());
			throw exception;
		}
	}

	private static void prepareSummonedPokemon(
			ServerPlayer player,
			PokemonEntity entity,
			Pokemon pokemon,
			SwitchPlan switchPlan,
			Vec3 requestedPosition,
			boolean waterFlightSummon
	) {
		boolean startFlying = RideEligibility.canFly(pokemon)
				&& (isAirborne(player) || waterFlightSummon || switchPlan != null && switchPlan.forceFlying());
		Vec3 position;
		if (switchPlan == null && !waterFlightSummon) {
			double y = player.getY() - Math.min(0.75D, entity.getBbHeight() * 0.35D);
			position = new Vec3(player.getX(), y, player.getZ());
		} else {
			position = requestedPosition;
		}
		entity.setPos(position.x, position.y, position.z);
		entity.setYRot(player.getYRot());
		entity.setYHeadRot(player.getYRot());
		entity.setXRot(player.getXRot() * 0.35F);
		entity.setDeltaMovement(player.getDeltaMovement());
		entity.fallDistance = 0.0F;
		entity.setNoGravity(switchPlan != null && switchPlan.hoveringCatch());
		entity.refreshRiding();
		if (startFlying) {
			entity.setFlying(true);
		}
		RideControlActivator.activateIfNeeded(
				entity,
				player,
				startFlying ? com.cobblemon.mod.common.api.riding.RidingStyle.AIR : null
		);
	}

	private static void beginSwitchApproach(ServerPlayer player, UUID pokemonId, PokemonEntity newMount, SwitchPlan plan) {
		if (newMount == null || newMount.isRemoved()) {
			PENDING_SUMMONS.remove(player.getUUID());
			return;
		}

		if (plan.groundCatch()) {
			Entity oldVehicle = player.getVehicle();
			player.stopRiding();
			player.fallDistance = 0.0F;
			if (oldVehicle instanceof PokemonEntity oldPokemonMount && oldPokemonMount != newMount) {
				oldPokemonMount.recallWithAnimation();
			}
		}

		PENDING_SWITCHES.put(player.getUUID(), new PendingSwitch(
				pokemonId,
				newMount,
				plan.groundCatch(),
				plan.hoveringCatch(),
				player.serverLevel().getGameTime()
		));
	}

	private static void processPendingSwitches(MinecraftServer server) {
		for (Map.Entry<UUID, PendingSwitch> entry : PENDING_SWITCHES.entrySet()) {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			PendingSwitch pending = entry.getValue();
			PokemonEntity newMount = pending.newMount();
			if (player == null || newMount.isRemoved() || findPartyPokemon(player, pending.pokemonId()) == null) {
				clearPendingSwitch(entry.getKey(), pending);
				continue;
			}

			long age = player.serverLevel().getGameTime() - pending.startedAt();
			boolean timedOut = age >= SWITCH_TIMEOUT_TICKS;
			if (pending.groundCatch()) {
				player.fallDistance = 0.0F;
				updateGroundCatch(player, pending);
				if (isPlayerCloseEnoughToCatch(player, newMount) || timedOut) {
					if (timedOut) {
						newMount.setPos(player.getX(), player.getY() - newMount.getBbHeight() * 0.35D, player.getZ());
					}
					completePendingSwitch(player, pending);
				}
			} else {
				updateApproachMovement(player, newMount, age);
				double contactDistance = 2.25D + (player.getBbWidth() + newMount.getBbWidth()) * 0.5D;
				if (newMount.distanceToSqr(player) <= contactDistance * contactDistance || timedOut) {
					if (timedOut) {
						newMount.setPos(player.getX() + 1.0D, player.getY(), player.getZ());
					}
					completePendingSwitch(player, pending);
				}
			}
		}
	}

	private static void updateApproachMovement(ServerPlayer player, PokemonEntity newMount, long pursuitTicks) {
		if ((isAirborne(player) || findWaterSurface(player) != null)
				&& RideEligibility.canFly(newMount.getPokemon())) {
			newMount.setFlying(true);

			Entity targetVehicle = player.getVehicle();
			Vec3 targetVelocity = targetVehicle == null ? player.getDeltaMovement() : targetVehicle.getDeltaMovement();
			double targetSpeed = targetVelocity.length();
			double distance = newMount.distanceTo(player);
			double accelerationRamp = Math.min(1.0D, pursuitTicks / 20.0D) * 0.65D;
			double distanceBoost = Math.min(1.5D, distance * 0.035D);
			double pursuitSpeed = Mth.clamp(
					Math.max(0.9D + accelerationRamp, targetSpeed * 1.45D + 0.35D + distanceBoost),
					0.9D,
					3.5D
			);

			// Aim ahead of a fast mount instead of chasing its previous position. The
			// lead shrinks near contact so the incoming Pokémon does not overshoot.
			double leadTicks = Mth.clamp(distance / Math.max(0.4D, pursuitSpeed), 1.5D, 10.0D);
			Vec3 interceptionPoint = player.position().add(targetVelocity.scale(leadTicks));
			Vec3 direction = interceptionPoint.subtract(newMount.position());
			if (direction.lengthSqr() > 0.01D) {
				Vec3 desiredVelocity = direction.normalize().scale(pursuitSpeed);
				double steering = Math.min(0.9D, 0.58D + pursuitTicks * 0.015D);
				newMount.setDeltaMovement(
						newMount.getDeltaMovement().scale(1.0D - steering).add(desiredVelocity.scale(steering))
				);
			}
		} else {
			newMount.getNavigation().moveTo(player, 1.35D);
		}
	}

	private static void updateGroundCatch(ServerPlayer player, PendingSwitch pending) {
		PokemonEntity newMount = pending.newMount();
		GroundTarget target = findGroundBelow(player.serverLevel(), player.position());
		if (target.foundGround()) {
			newMount.setNoGravity(false);
			newMount.getNavigation().moveTo(target.position().x, target.position().y, target.position().z, 1.5D);
		} else if (pending.hoveringCatch()) {
			newMount.setNoGravity(true);
			newMount.setDeltaMovement(new Vec3(
					(player.getX() - newMount.getX()) * 0.2D,
					0.0D,
					(player.getZ() - newMount.getZ()) * 0.2D
			));
		}
	}

	private static boolean isPlayerCloseEnoughToCatch(ServerPlayer player, PokemonEntity newMount) {
		double dx = player.getX() - newMount.getX();
		double dz = player.getZ() - newMount.getZ();
		double horizontalLimit = 2.5D + (player.getBbWidth() + newMount.getBbWidth()) * 0.5D;
		double verticalGap = player.getY() - newMount.getBoundingBox().maxY;
		return dx * dx + dz * dz <= horizontalLimit * horizontalLimit
				&& verticalGap <= 2.5D && verticalGap >= -1.5D;
	}

	private static void completePendingSwitch(ServerPlayer player, PendingSwitch pending) {
		clearPendingSwitch(player.getUUID(), pending);
		PokemonEntity newMount = pending.newMount();
		newMount.setNoGravity(false);
		if (pending.groundCatch()) {
			player.fallDistance = 0.0F;
			player.setDeltaMovement(player.getDeltaMovement().x, 0.0D, player.getDeltaMovement().z);
			newMount.setDeltaMovement(Vec3.ZERO);
		}
		finishMount(player, pending.pokemonId(), newMount);
	}

	private static void clearPendingSwitch(UUID playerId, PendingSwitch pending) {
		PENDING_SWITCHES.remove(playerId, pending);
		PENDING_SUMMONS.remove(playerId);
		if (pending.hoveringCatch() && !pending.newMount().isRemoved()) {
			pending.newMount().setNoGravity(false);
		}
	}

	private static SwitchPlan createSwitchPlan(ServerPlayer player, Pokemon pokemon) {
		WaterSurface waterSurface = RideEligibility.canFly(pokemon) ? findWaterSurface(player) : null;
		if (waterSurface != null) {
			return new SwitchPlan(createWaterFlightPosition(player, waterSurface, 6.0D), false, false, true);
		}

		boolean groundCatch = isAirborne(player) && !RideEligibility.canFly(pokemon);
		if (groundCatch) {
			GroundTarget ground = findGroundBelow(player.serverLevel(), player.position());
			Vec3 catchPosition = ground.foundGround()
					? ground.position()
					: player.position().add(0.0D, -6.0D, 0.0D);
			return new SwitchPlan(catchPosition, true, !ground.foundGround(), false);
		}

		Vec3 look = player.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
		if (look.lengthSqr() < 0.01D) {
			look = new Vec3(0.0D, 0.0D, 1.0D);
		} else {
			look = look.normalize();
		}
		Vec3 offsetPosition = player.position().add(look.scale(6.0D));
		if (isAirborne(player) && RideEligibility.canFly(pokemon)) {
			offsetPosition = offsetPosition.add(0.0D, -1.5D, 0.0D);
		} else {
			GroundTarget ground = findGroundBelow(player.serverLevel(), offsetPosition.add(0.0D, 4.0D, 0.0D));
			if (ground.foundGround()) {
				offsetPosition = ground.position();
			}
		}
		return new SwitchPlan(offsetPosition, false, false, isAirborne(player) && RideEligibility.canFly(pokemon));
	}

	private static WaterSurface findWaterSurface(ServerPlayer player) {
		Entity reference = player.getVehicle() == null ? player : player.getVehicle();
		ServerLevel level = player.serverLevel();
		int x = Mth.floor(reference.getX());
		int z = Mth.floor(reference.getZ());
		int startY = Math.min(Mth.floor(reference.getBoundingBox().maxY) + 2, level.getMaxBuildHeight() - 1);
		int lowestY = Math.max(level.getMinBuildHeight(), startY - 10);
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, startY, z);
		for (int y = startY; y >= lowestY; y--) {
			cursor.setY(y);
			if (level.getFluidState(cursor).is(FluidTags.WATER)
					&& !level.getFluidState(cursor.above()).is(FluidTags.WATER)) {
				double surfaceY = y + level.getFluidState(cursor).getHeight(level, cursor);
				if (reference.getBoundingBox().minY - surfaceY <= 3.5D) {
					return new WaterSurface(new Vec3(reference.getX(), surfaceY, reference.getZ()));
				}
			}
		}
		return null;
	}

	private static Vec3 createWaterFlightPosition(ServerPlayer player, WaterSurface surface, double distance) {
		Vec3 look = player.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
		if (look.lengthSqr() < 0.01D) {
			look = new Vec3(0.0D, 0.0D, 1.0D);
		} else {
			look = look.normalize();
		}
		return new Vec3(
				player.getX() + look.x * distance,
				surface.position().y + 1.35D,
				player.getZ() + look.z * distance
		);
	}

	private static GroundTarget findGroundBelow(ServerLevel level, Vec3 origin) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(
				Mth.floor(origin.x),
				Math.min(Mth.floor(origin.y), level.getMaxBuildHeight() - 2),
				Mth.floor(origin.z)
		);
		for (int y = cursor.getY(); y >= level.getMinBuildHeight(); y--) {
			cursor.setY(y);
			if (!level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty()
					&& level.getBlockState(cursor.above()).getCollisionShape(level, cursor.above()).isEmpty()) {
				return new GroundTarget(Vec3.atBottomCenterOf(cursor.above()), true);
			}
		}
		return new GroundTarget(origin.add(0.0D, -6.0D, 0.0D), false);
	}

	private static void finishMount(ServerPlayer player, UUID pokemonId, PokemonEntity newMount) {
		if (player.isRemoved() || newMount == null || newMount.isRemoved()) {
			return;
		}

		Pokemon selected = findPartyPokemon(player, pokemonId);
		if (selected == null || selected.getEntity() != newMount || !RideEligibility.isRideable(selected)) {
			newMount.recallWithAnimation();
			return;
		}

		Entity oldVehicle = player.getVehicle();
		PokemonEntity oldPokemonMount = oldVehicle instanceof PokemonEntity pokemonEntity ? pokemonEntity : null;
		Vec3 inheritedVelocity = oldVehicle == null ? player.getDeltaMovement() : oldVehicle.getDeltaMovement();

		// Keep the old mount until the new Pokémon has fully appeared. Only then is
		// the player transferred and the previous party member recalled.
		if (oldVehicle != null) {
			player.stopRiding();
		}
		newMount.setDeltaMovement(inheritedVelocity);
		newMount.fallDistance = 0.0F;
		player.fallDistance = 0.0F;
		newMount.tryRidingPokemon(player);
		RideControlActivator.activateIfNeeded(newMount, player);

		if (player.getVehicle() != newMount) {
			if (oldVehicle != null && !oldVehicle.isRemoved()) {
				player.startRiding(oldVehicle, true);
			}
			newMount.recallWithAnimation();
			return;
		}
		LAST_RIDDEN_POKEMON.put(player.getUUID(), selected.getUuid());

		if (oldPokemonMount != null && oldPokemonMount != newMount && !oldPokemonMount.isRemoved()) {
			oldPokemonMount.recallWithAnimation();
		}
	}

	private static boolean isAirborne(ServerPlayer player) {
		return !player.onGround() && !player.isInWaterOrBubble() && !player.isInLava();
	}

	private static Pokemon findPartyPokemon(ServerPlayer player, UUID pokemonId) {
		for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
			if (pokemon != null && pokemon.getUuid().equals(pokemonId)) {
				return pokemon;
			}
		}
		return null;
	}

	private record SwitchPlan(Vec3 spawnPosition, boolean groundCatch, boolean hoveringCatch, boolean forceFlying) {
	}

	private record WaterSurface(Vec3 position) {
	}

	private record GroundTarget(Vec3 position, boolean foundGround) {
	}

	private record PendingSwitch(
			UUID pokemonId,
			PokemonEntity newMount,
			boolean groundCatch,
			boolean hoveringCatch,
			long startedAt
	) {
	}
}
