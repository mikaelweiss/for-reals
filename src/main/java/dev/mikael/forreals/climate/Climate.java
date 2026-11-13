package dev.mikael.forreals.climate;

import com.google.common.annotations.VisibleForTesting;
import dev.mikael.forreals.time.WorldTime;
import dev.mikael.forreals.weather.Sky;
import dev.mikael.forreals.weather.Weather;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** The temperature of places in a running world. Only the overworld has a climate. */
public final class Climate {
	/** Biomes are read far above the ground, where the surface biome reaches and cave biomes do not. */
	private static final int BIOME_PROBE_ABOVE_SEA = 128;
	private static final int BLEND_REACH_BLOCKS = 24;
	private static final int MAX_CACHED_CHUNKS = 16384;
	private static final int MAX_SKY_LIGHT = 15;
	private static final int HEAT_REACH = 3;
	private static final int HEAT_REACH_VERTICAL = 2;
	private static final double HEAT_FADE_DISTANCE = 4.5;
	private static final double MOST_HEAT = 30;

	private static final Long2ObjectOpenHashMap<BiomeClimate> BASE_BY_CHUNK = new Long2ObjectOpenHashMap<>();

	private Climate() {
	}

	public static boolean hasClimate(final Level level) {
		return level.dimension() == Level.OVERWORLD;
	}

	public static void forgetWorld() {
		BASE_BY_CHUNK.clear();
	}

	/**
	 * The yearly climate around a chunk: its own biome blended with its neighbors, so crossing a biome border or a
	 * river never jumps the temperature.
	 */
	public static BiomeClimate base(final ServerLevel level, final int chunkX, final int chunkZ) {
		if (!level.getServer().isSameThread()) {
			return blend(level, chunkX, chunkZ);
		}

		long key = ChunkPos.pack(chunkX, chunkZ);
		BiomeClimate cached = BASE_BY_CHUNK.get(key);
		if (cached == null) {
			if (BASE_BY_CHUNK.size() >= MAX_CACHED_CHUNKS) {
				BASE_BY_CHUNK.clear();
			}

			cached = blend(level, chunkX, chunkZ);
			BASE_BY_CHUNK.put(key, cached);
		}

		return cached;
	}

	/** Lets a test decide the climate of a chunk, whatever biome the test happens to run in. */
	@VisibleForTesting
	public static void pin(final int chunkX, final int chunkZ, final BiomeClimate climate) {
		BASE_BY_CHUNK.put(ChunkPos.pack(chunkX, chunkZ), climate);
	}

	public static BiomeClimate base(final ServerLevel level, final BlockPos pos) {
		return base(level, pos.getX() >> 4, pos.getZ() >> 4);
	}

	private static BiomeClimate blend(final ServerLevel level, final int chunkX, final int chunkZ) {
		int centerX = (chunkX << 4) + 8;
		int centerZ = (chunkZ << 4) + 8;
		int quartY = QuartPos.fromBlock(level.getSeaLevel() + BIOME_PROBE_ABOVE_SEA);
		BiomeClimate sum = new BiomeClimate(0, 0, 0, 0);
		double total = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				Holder<Biome> biome = level.getNoiseBiome(
					QuartPos.fromBlock(centerX + dx * BLEND_REACH_BLOCKS), quartY, QuartPos.fromBlock(centerZ + dz * BLEND_REACH_BLOCKS)
				);
				var climate = BiomeClimates.ownClimate(biome);
				if (climate.isPresent()) {
					double weight = (2 - Math.abs(dx)) * (2 - Math.abs(dz));
					sum = sum.plus(climate.get(), weight);
					total += weight;
				}
			}
		}

		if (total == 0) {
			return BiomeClimates.fromBiome(level.getNoiseBiome(QuartPos.fromBlock(centerX), quartY, QuartPos.fromBlock(centerZ)));
		}

		return sum.dividedBy(total);
	}

	/** Air temperature in the open at this spot, in Celsius. */
	public static double openAir(final ServerLevel level, final BlockPos pos) {
		return openAir(level, pos, Weather.sky(level, pos), WorldTime.ticks(level.getServer()));
	}

	public static double openAir(final ServerLevel level, final BlockPos pos, final Sky sky, final long ticks) {
		return ClimateModel.openAir(base(level, pos), ticks, pos.getY() - level.getSeaLevel(), sky.airMass(), sky.cloudCover());
	}

	/** Temperature felt by something standing at this spot. Cover holds the yearly average. Fires and lava add heat. */
	public static double feltAt(final ServerLevel level, final BlockPos pos) {
		return feltAt(level, pos, openAir(level, pos));
	}

	/** As {@link #feltAt(ServerLevel, BlockPos)}, for a given open-air temperature instead of the current one. */
	public static double feltAt(final ServerLevel level, final BlockPos pos, final double openAir) {
		double underCover = ClimateModel.underCover(base(level, pos), pos.getY() - level.getSeaLevel());
		return openAir + cover(level, pos) * (underCover - openAir) + heatAround(level, pos);
	}

	/**
	 * How sheltered a spot is, from 0 in the open to 1 deep inside. Anything overhead counts for half, glass
	 * included, so a greenhouse works. Darkness counts for the rest.
	 */
	private static double cover(final ServerLevel level, final BlockPos pos) {
		if (!hasRoof(level, pos)) {
			return 0;
		}

		return 0.5 + 0.5 * (1 - (double)level.getBrightness(LightLayer.SKY, pos) / MAX_SKY_LIGHT);
	}

	/** True when anything solid is overhead, glass and leaves included. */
	public static boolean hasRoof(final ServerLevel level, final BlockPos pos) {
		return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos).getY() > pos.getY();
	}

	private static double heatAround(final ServerLevel level, final BlockPos pos) {
		double heat = 0;
		BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
		for (int dx = -HEAT_REACH; dx <= HEAT_REACH; dx++) {
			for (int dy = -HEAT_REACH_VERTICAL; dy <= HEAT_REACH_VERTICAL; dy++) {
				for (int dz = -HEAT_REACH; dz <= HEAT_REACH; dz++) {
					double warmth = warmthOf(level.getBlockState(probe.setWithOffset(pos, dx, dy, dz)));
					if (warmth > 0) {
						heat += warmth * Math.max(0, 1 - Math.sqrt(dx * dx + dy * dy + dz * dz) / HEAT_FADE_DISTANCE);
					}
				}
			}
		}

		return Math.min(heat, MOST_HEAT);
	}

	/** Degrees a block adds to the air right beside it. */
	private static double warmthOf(final BlockState state) {
		if (state.isAir()) {
			return 0;
		} else if (state.getFluidState().is(FluidTags.LAVA)) {
			return 15;
		} else if (state.is(BlockTags.FIRE) || state.is(BlockTags.CAMPFIRES) && state.getValue(CampfireBlock.LIT)) {
			return 12;
		} else if (state.getBlock() instanceof AbstractFurnaceBlock && state.getValue(AbstractFurnaceBlock.LIT)) {
			return 8;
		} else if (state.is(Blocks.MAGMA_BLOCK)) {
			return 6;
		} else {
			return state.getLightEmission() >= 10 ? 1 : 0;
		}
	}
}
