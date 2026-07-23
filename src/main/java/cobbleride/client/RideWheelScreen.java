package cobbleride.client;

import cobbleride.network.RideRequestPayload;
import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class RideWheelScreen extends Screen {
	private static final double START_ANGLE = -Math.PI / 2.0D;
	private static final int TILE_HALF_WIDTH = 42;
	private static final int TILE_HALF_HEIGHT = 44;
	private static final int DEAD_ZONE = 28;

	private final List<Pokemon> pokemon;
	private final List<PokemonEntity> previews = new ArrayList<>();
	private int selectedIndex = -1;
	private boolean confirmed;

	public RideWheelScreen(List<Pokemon> pokemon) {
		super(Component.translatable("screen.cobbleride.ride_wheel"));
		this.pokemon = List.copyOf(pokemon);
	}

	@Override
	protected void init() {
		previews.clear();
		Minecraft client = Minecraft.getInstance();
		for (Pokemon partyPokemon : pokemon) {
			try {
				Pokemon copy = partyPokemon.clone(false, client.level.registryAccess());
				PokemonEntity preview = new PokemonEntity(client.level, copy, CobblemonEntities.POKEMON);
				preview.setYRot(25.0F);
				preview.setYHeadRot(25.0F);
				previews.add(preview);
			} catch (RuntimeException exception) {
				previews.add(null);
			}
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, width, height, 0x88000000);
		updateSelection(mouseX, mouseY);

		int centerX = width / 2;
		int centerY = height / 2;
		if (pokemon.isEmpty()) {
			graphics.drawCenteredString(font, Component.translatable("screen.cobbleride.no_rideable"), centerX, centerY - 5, 0xFFFFFF);
			graphics.drawCenteredString(font, Component.translatable("screen.cobbleride.release_to_close"), centerX, centerY + 13, 0xB8C3D1);
			return;
		}

		double radius = Math.max(92.0D, Math.min(154.0D, Math.min(width, height) * 0.29D));
		for (int i = 0; i < pokemon.size(); i++) {
			double angle = itemAngle(i);
			int x = centerX + (int) Math.round(Math.cos(angle) * radius);
			int y = centerY + (int) Math.round(Math.sin(angle) * radius);
			renderPokemonTile(graphics, mouseX, mouseY, i, x, y, delta);
		}

		graphics.fill(centerX - DEAD_ZONE, centerY - 1, centerX + DEAD_ZONE, centerY + 1, 0x99DDEBFF);
		graphics.fill(centerX - 1, centerY - DEAD_ZONE, centerX + 1, centerY + DEAD_ZONE, 0x99DDEBFF);
		Component prompt = selectedIndex >= 0
				? Component.translatable("screen.cobbleride.release_to_ride", pokemon.get(selectedIndex).getDisplayName(false))
				: Component.translatable("screen.cobbleride.choose");
		graphics.drawCenteredString(font, prompt, centerX, centerY + DEAD_ZONE + 8, 0xFFFFFF);
	}

	private void renderPokemonTile(GuiGraphics graphics, int mouseX, int mouseY, int index, int x, int y, float delta) {
		boolean selected = index == selectedIndex;
		int border = selected ? 0xFFE7C75F : 0xCC6E7F96;
		int background = selected ? 0xE03B4858 : 0xD0252D39;
		graphics.fill(x - TILE_HALF_WIDTH - 2, y - TILE_HALF_HEIGHT - 2, x + TILE_HALF_WIDTH + 2, y + TILE_HALF_HEIGHT + 2, border);
		graphics.fill(x - TILE_HALF_WIDTH, y - TILE_HALF_HEIGHT, x + TILE_HALF_WIDTH, y + TILE_HALF_HEIGHT, background);

		PokemonEntity preview = previews.size() > index ? previews.get(index) : null;
		if (preview != null) {
			float largestDimension = Math.max(preview.getBbWidth(), preview.getBbHeight());
			int scale = Math.max(13, Math.min(36, Math.round(35.0F / Math.max(0.7F, largestDimension))));
			InventoryScreen.renderEntityInInventoryFollowsMouse(
					graphics,
					x - TILE_HALF_WIDTH + 4,
					y - TILE_HALF_HEIGHT + 3,
					x + TILE_HALF_WIDTH - 4,
					y + 20,
					scale,
					0.0F,
					(float) x - mouseX,
					(float) y - mouseY,
					preview
			);
		}

		Pokemon partyPokemon = pokemon.get(index);
		Component displayName = partyPokemon.getDisplayName(false);
		graphics.drawCenteredString(font, displayName, x, y + 24, selected ? 0xFFF1A8 : 0xFFFFFF);
		graphics.drawCenteredString(font, Component.translatable("screen.cobbleride.level", partyPokemon.getLevel()), x, y + 35, 0xB9C6D8);
	}

	private void updateSelection(double mouseX, double mouseY) {
		if (pokemon.isEmpty()) {
			selectedIndex = -1;
			return;
		}

		double dx = mouseX - width / 2.0D;
		double dy = mouseY - height / 2.0D;
		if (dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE) {
			selectedIndex = -1;
			return;
		}

		double angle = normalizeAngle(Math.atan2(dy, dx) - START_ANGLE);
		double segment = Math.PI * 2.0D / pokemon.size();
		selectedIndex = Math.floorMod((int) Math.round(angle / segment), pokemon.size());
	}

	private double itemAngle(int index) {
		return START_ANGLE + Math.PI * 2.0D * index / pokemon.size();
	}

	private static double normalizeAngle(double angle) {
		double fullCircle = Math.PI * 2.0D;
		return (angle % fullCircle + fullCircle) % fullCircle;
	}

	public void confirmAndClose() {
		if (confirmed) {
			return;
		}
		confirmed = true;
		if (selectedIndex >= 0 && selectedIndex < pokemon.size()) {
			ClientPlayNetworking.send(new RideRequestPayload(pokemon.get(selectedIndex).getUuid()));
		}
		onClose();
	}

	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		if (CobbleRideClient.RIDE_WHEEL_KEY.matches(keyCode, scanCode)) {
			confirmAndClose();
			return true;
		}
		return super.keyReleased(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return true;
	}
}
