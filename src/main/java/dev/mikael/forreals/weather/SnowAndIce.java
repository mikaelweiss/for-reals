package dev.mikael.forreals.weather;

import dev.mikael.forreals.climate.Climate;
import dev.mikael.forreals.climate.ClimateModel;
import dev.mikael.forreals.time.WorldTime;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.OptionalLong;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;

/** Puts snow and ice on the ground where the climate says they belong, and takes them away when it warms. */
public final class SnowAndIce {
	/** Snow is worked out once per square of this many chunks. Weather barely changes across it. */
	private static final int CELL_CHUNKS_SHIFT = 2;
	private static final int COLUMN_ODDS = 16;
	private static final int BRIGHT_ENOUGH_TO_MELT = 10;
	private static final double THAW_ABOVE = 1;
	private static final long LONG_ENOUGH_AWAY_TO_REDO_SNOW = Snowpack.STEP_TICKS;
	private static final int MAX_CACHED_CELLS = 4096;
	private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	private static final Long2ObjectOpenHashMap<CachedSnowpack> SNOWPACK_BY_CELL = new Long2ObjectOpenHashMap<>();

	private SnowAndIce() {
	}

	private record CachedSnowpack(Snowpack snowpack, long step) {
	}

	public static void forgetWorld() {
		SNOWPACK_BY_CELL.clear();
	}

	public static Snowpack snowpack(final ServerLevel level, final BlockPos pos) {
		int cellX = pos.getX() >> 4 >> CELL_CHUNKS_SHIFT;
		int cellZ = pos.getZ() >> 4 >> CELL_CHUNKS_SHIFT;
		long ticks = WorldTime.ticks(level.getServer());
		long key = ChunkPos.pack(cellX, cellZ);
		CachedSnowpack cached = SNOWPACK_BY_CELL.get(key);
		if (cached == null || cached.step != Snowpack.stepOf(ticks)) {
			if (SNOWPACK_BY_CELL.size() >= MAX_CACHED_CELLS) {
				SNOWPACK_BY_CELL.clear();
			}

			int centerChunkX = (cellX << CELL_CHUNKS_SHIFT) + (1 << CELL_CHUNKS_SHIFT >> 1);
			int centerChunkZ = (cellZ << CELL_CHUNKS_SHIFT) + (1 << CELL_CHUNKS_SHIFT >> 1);
			Snowpack snowpack = Snowpack.over(
				Weather.field(level), Climate.base(level, centerChunkX, centerChunkZ), centerChunkX << 4, centerChunkZ << 4, ticks
			);
			cached = new CachedSnowpack(snowpack, Snowpack.stepOf(ticks));
			SNOWPACK_BY_CELL.put(key, cached);
		}

		return cached.snowpack;
	}

	/** Runs every tick for every ticking chunk. Each run may nudge one random column a step toward how it should look. */
	public static void tickChunk(final ServerLevel level, final LevelChunk chunk) {
		ChunkPos chunkPos = chunk.getPos();
		for (int i = 0; i < WorldTime.speedUp(); i++) {
			if (level.getRandom().nextInt(COLUMN_ODDS) == 0) {
				settleColumn(level, level.getBlockRandomPos(chunkPos.getMinBlockX(), 0, chunkPos.getMinBlockZ(), 15), false);
			}
		}
	}

	/** A chunk that was away long enough for the weather to change gets all of its snow and ice put right at once. */
	public static void catchUp(final ServerLevel level, final LevelChunk chunk, final OptionalLong awayTicks) {
		if (awayTicks.isPresent() && awayTicks.getAsLong() < LONG_ENOUGH_AWAY_TO_REDO_SNOW) {
			return;
		}

		ChunkPos chunkPos = chunk.getPos();
		BlockPos.MutableBlockPos column = new BlockPos.MutableBlockPos();
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				settleColumn(level, column.set(chunkPos.getMinBlockX() + x, 0, chunkPos.getMinBlockZ() + z), true);
			}
		}
	}

	private static void settleColumn(final ServerLevel level, final BlockPos column, final boolean allAtOnce) {
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
		BlockPos below = top.below();
		Sky sky = Weather.sky(level, top);
		double temperature = Climate.openAir(level, top, sky, WorldTime.ticks(level.getServer()));

		if (temperature < ClimateModel.FREEZING) {
			if (canFreeze(level, below)) {
				level.setBlock(below, Blocks.ICE.defaultBlockState(), allAtOnce ? QUIET : Block.UPDATE_ALL);
			}
		} else if (temperature > THAW_ABOVE && level.getBlockState(below).is(Blocks.ICE)) {
			level.setBlock(below, IceBlock.meltsInto(), allAtOnce ? QUIET : Block.UPDATE_ALL);
			return;
		}

		settleSnow(level, top, temperature, allAtOnce);
		if (!allAtOnce) {
			Biome.Precipitation falling = Weather.precipitationAt(level, top);
			if (falling != Biome.Precipitation.NONE) {
				BlockState ground = level.getBlockState(below);
				ground.getBlock().handlePrecipitation(ground, level, below, falling);
			}
		}
	}

	private static void settleSnow(final ServerLevel level, final BlockPos top, final double temperature, final boolean allAtOnce) {
		BlockPos snowPos = level.getBlockState(top.below()).is(Blocks.SNOW) ? top.below() : top;
		BlockState state = level.getBlockState(snowPos);
		int layers = state.is(Blocks.SNOW) ? state.getValue(SnowLayerBlock.LAYERS) : 0;
		int wanted = (int)Math.round(snowpack(level, top).layersAt(top.getY() - level.getSeaLevel()));
		if (layers == 0 && wanted > 0 && !(temperature < Weather.SNOW_BELOW + THAW_ABOVE && canHoldSnow(level, snowPos, state))) {
			return;
		}

		int next = allAtOnce ? wanted : layers + Integer.signum(wanted - layers);
		if (next == layers) {
			return;
		}

		BlockState nextState = next == 0 ? Blocks.AIR.defaultBlockState() : Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, next);
		if (next > layers && layers > 0) {
			Block.pushEntitiesUp(state, nextState, level, snowPos);
		}

		level.setBlock(snowPos, nextState, allAtOnce ? QUIET : Block.UPDATE_ALL);
	}

	/** Snow lands on bare ground and flattens grass. It will not settle near a torch or on something it cannot rest on. */
	private static boolean canHoldSnow(final ServerLevel level, final BlockPos pos, final BlockState state) {
		return level.isInsideBuildHeight(pos.getY())
			&& level.getBrightness(LightLayer.BLOCK, pos) < BRIGHT_ENOUGH_TO_MELT
			&& (state.isAir() || state.is(Blocks.SHORT_GRASS) || state.is(Blocks.FERN))
			&& Blocks.SNOW.defaultBlockState().canSurvive(level, pos);
	}

	/** Still water freezes from its edges inward, away from light. */
	private static boolean canFreeze(final ServerLevel level, final BlockPos pos) {
		if (!level.isInsideBuildHeight(pos.getY()) || level.getBrightness(LightLayer.BLOCK, pos) >= BRIGHT_ENOUGH_TO_MELT) {
			return false;
		}

		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof LiquidBlock) || !level.getFluidState(pos).is(Fluids.WATER)) {
			return false;
		}

		return !(level.isWaterAt(pos.west()) && level.isWaterAt(pos.east()) && level.isWaterAt(pos.north()) && level.isWaterAt(pos.south()));
	}
}
