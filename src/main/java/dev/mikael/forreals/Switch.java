package dev.mikael.forreals;

import com.google.common.annotations.VisibleForTesting;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Whether a world uses the mod. The mod ships a data pack that is off unless a world turns it on, and Minecraft saves
 * that choice in the world. With the pack off, a world plays as vanilla.
 *
 * <p>The answer is read once when a world starts. Turning the pack on or off while a world is running takes effect
 * the next time it starts, because the biomes it adds can only be loaded then.
 */
public final class Switch {
	private static final Identifier PACK = Identifier.fromNamespaceAndPath(ForReals.MOD_ID, "core");
	/** A file only the pack has. A world with the pack on can see it. */
	private static final Identifier MARKER = Identifier.fromNamespaceAndPath(ForReals.MOD_ID, "switch.json");

	private static volatile boolean on;

	private Switch() {
	}

	public static boolean isOn() {
		return on;
	}

	/** True while a world's data is still loading, before {@link #isOn} has an answer. */
	public static boolean isOnIn(final ResourceManager worldData) {
		return worldData.getResource(MARKER).isPresent();
	}

	static void offerThePack() {
		ResourceLoader.registerBuiltinPack(
			PACK, FabricLoader.getInstance().getModContainer(ForReals.MOD_ID).orElseThrow(), Component.literal("For Reals"), PackActivationType.NORMAL
		);
	}

	static void worldStarting(final MinecraftServer server) {
		on = isOnIn(server.getResourceManager());
		ForReals.LOGGER.info("For Reals is {} for this world", on ? "on" : "off");
	}

	static void worldStopped() {
		on = false;
	}

	@VisibleForTesting
	public static void set(final boolean nowOn) {
		on = nowOn;
	}
}
