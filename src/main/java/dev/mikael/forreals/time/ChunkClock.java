package dev.mikael.forreals.time;

import com.mojang.serialization.Codec;
import dev.mikael.forreals.ForReals;
import dev.mikael.forreals.Switch;
import dev.mikael.forreals.climate.Climate;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Lets each overworld chunk know how long it was away. A chunk is stamped with the calendar time when it unloads.
 * When it loads again, everything that should have happened in between is caught up.
 */
public final class ChunkClock {
	private static final AttachmentType<Long> LAST_ACTIVE = AttachmentRegistry.createPersistent(
		Identifier.fromNamespaceAndPath(ForReals.MOD_ID, "last_active"), Codec.LONG
	);

	private static final LongSet LOADED = new LongOpenHashSet();
	private static final List<CatchUp> CATCH_UPS = new ArrayList<>();

	private ChunkClock() {
	}

	public interface CatchUp {
		/** @param awayTicks calendar ticks the chunk missed, or empty if this world never recorded when it was last active */
		void catchUp(ServerLevel level, LevelChunk chunk, OptionalLong awayTicks);
	}

	public static void onCatchUp(final CatchUp catchUp) {
		CATCH_UPS.add(catchUp);
	}

	public static void chunkLoaded(final ServerLevel level, final LevelChunk chunk) {
		if (!Climate.hasClimate(level)) {
			return;
		}

		LOADED.add(chunk.getPos().pack());
		Long lastActive = chunk.getAttached(LAST_ACTIVE);
		long now = WorldTime.ticks(level.getServer());
		OptionalLong away = lastActive == null ? OptionalLong.empty() : OptionalLong.of(Math.max(0, now - lastActive));
		// Changing blocks in a chunk that is still loading can stall the server, so the work runs on the next tick.
		level.getServer().execute(() -> {
			if (level.getChunkSource().getChunkNow(chunk.getPos().x(), chunk.getPos().z()) == chunk) {
				CATCH_UPS.forEach(catchUp -> catchUp.catchUp(level, chunk, away));
			}
		});
	}

	public static void chunkUnloaded(final ServerLevel level, final LevelChunk chunk) {
		if (Climate.hasClimate(level)) {
			LOADED.remove(chunk.getPos().pack());
			chunk.setAttached(LAST_ACTIVE, WorldTime.ticks(level.getServer()));
		}
	}

	/** Chunks still loaded when the server stops never get an unload, so they are stamped here. */
	public static void serverStopping(final MinecraftServer server) {
		if (!Switch.isOn()) {
			return;
		}

		ServerLevel overworld = server.overworld();
		long now = WorldTime.ticks(server);
		LOADED.forEach(packed -> {
			LevelChunk chunk = overworld.getChunkSource().getChunkNow(ChunkPos.getX(packed), ChunkPos.getZ(packed));
			if (chunk != null) {
				chunk.setAttached(LAST_ACTIVE, now);
			}
		});
		LOADED.clear();
	}
}
