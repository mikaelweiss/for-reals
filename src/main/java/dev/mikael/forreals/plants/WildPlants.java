package dev.mikael.forreals.plants;

import dev.mikael.forreals.climate.Climate;
import dev.mikael.forreals.time.WorldTime;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.placement.VegetationPlacements;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * Keeps wild ground alive. Grass and flowers come up when it is warm and die back in hard frost. Trees seed the open
 * ground around them.
 */
public final class WildPlants {
	private static final Map<Block, Block> SAPLING_BY_LEAVES = Map.of(
		Blocks.OAK_LEAVES, Blocks.OAK_SAPLING,
		Blocks.BIRCH_LEAVES, Blocks.BIRCH_SAPLING,
		Blocks.SPRUCE_LEAVES, Blocks.SPRUCE_SAPLING,
		Blocks.JUNGLE_LEAVES, Blocks.JUNGLE_SAPLING,
		Blocks.ACACIA_LEAVES, Blocks.ACACIA_SAPLING,
		Blocks.CHERRY_LEAVES, Blocks.CHERRY_SAPLING,
		Blocks.MANGROVE_LEAVES, Blocks.MANGROVE_PROPAGULE
	);

	private static final int COLUMN_ODDS = 16;
	private static final double DIES_BACK_BELOW = -2;
	private static final double GROWS_ABOVE = 8;
	private static final double DIE_BACK_CHANCE = 0.5;
	private static final double SPROUT_CHANCE = 0.15;
	private static final double FLOWER_SHARE = 0.12;
	private static final double FERN_SHARE_IN_TAIGA = 0.5;
	private static final int MOST_NEIGHBORING_PLANTS = 2;
	/** Lit ground stays clear, the same way a torch keeps snow off. */
	private static final int TOO_BRIGHT_TO_SPROUT = 10;
	private static final int SEED_ODDS = 40;
	private static final int SEED_REACH = 5;
	private static final int SAPLING_NEEDS_LIGHT = 9;
	private static final int TRUNK_SPACING = 3;
	private static final long COLUMN_VISIT_INTERVAL = 16 * 256;
	private static final int MOST_CATCH_UP_PASSES = 4;

	private WildPlants() {
	}

	public static void tickChunk(final ServerLevel level, final LevelChunk chunk) {
		ChunkPos chunkPos = chunk.getPos();
		for (int i = 0; i < WorldTime.speedUp(); i++) {
			if (level.getRandom().nextInt(COLUMN_ODDS) == 0) {
				tend(level, level.getBlockRandomPos(chunkPos.getMinBlockX(), 0, chunkPos.getMinBlockZ(), 15));
			}
		}
	}

	/** A returning chunk gets the visits it missed, up to a few rounds over every column. */
	public static void catchUp(final ServerLevel level, final LevelChunk chunk, final OptionalLong awayTicks) {
		long passes = Math.min(MOST_CATCH_UP_PASSES, awayTicks.orElse(0) / COLUMN_VISIT_INTERVAL);
		ChunkPos chunkPos = chunk.getPos();
		BlockPos.MutableBlockPos column = new BlockPos.MutableBlockPos();
		for (int pass = 0; pass < passes; pass++) {
			for (int x = 0; x < 16; x++) {
				for (int z = 0; z < 16; z++) {
					tend(level, column.set(chunkPos.getMinBlockX() + x, 0, chunkPos.getMinBlockZ() + z));
				}
			}
		}
	}

	/** One visit to one column: a plant may die back, sprout, or a tree overhead may drop a seed. */
	public static void tend(final ServerLevel level, final BlockPos column) {
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
		double temperature = Climate.openAir(level, top);
		BlockState state = level.getBlockState(top);
		RandomSource random = level.getRandom();
		if (temperature < DIES_BACK_BELOW) {
			if (diesBack(state) && random.nextDouble() < DIE_BACK_CHANCE) {
				level.removeBlock(top, false);
			}
		} else if (temperature > GROWS_ABOVE) {
			BlockState ground = level.getBlockState(top.below());
			if (ground.is(Blocks.GRASS_BLOCK)) {
				sprout(level, top, state, random);
			} else if (ground.is(BlockTags.LEAVES) && !ground.getValue(LeavesBlock.PERSISTENT) && random.nextInt(SEED_ODDS) == 0) {
				dropSeed(level, top.below(), ground, random);
			}
		}
	}

	public static boolean diesBack(final BlockState state) {
		return state.is(Blocks.SHORT_GRASS) || state.is(Blocks.FERN) || state.is(BlockTags.SMALL_FLOWERS) && !state.is(Blocks.WITHER_ROSE);
	}

	private static void sprout(final ServerLevel level, final BlockPos pos, final BlockState state, final RandomSource random) {
		double wetness = Climate.base(level, pos).wetness();
		if (!state.isAir()
			|| random.nextDouble() >= SPROUT_CHANCE * (0.2 + 0.8 * wetness)
			|| level.getBrightness(LightLayer.BLOCK, pos) >= TOO_BRIGHT_TO_SPROUT
			|| neighboringPlants(level, pos) > MOST_NEIGHBORING_PLANTS) {
			return;
		}

		if (random.nextDouble() < FLOWER_SHARE) {
			List<Feature> flowers = level.getBiome(pos).value().getGenerationSettings().getBoneMealFeatures();
			if (!flowers.isEmpty()) {
				Util.getRandom(flowers, random).place(level, level.getChunkSource().getGenerator(), random, pos);
				return;
			}
		}

		if (level.getBiome(pos).is(BiomeTags.IS_TAIGA) && random.nextDouble() < FERN_SHARE_IN_TAIGA) {
			level.setBlockAndUpdate(pos, Blocks.FERN.defaultBlockState());
		} else {
			level.registryAccess()
				.lookupOrThrow(Registries.PLACED_FEATURE)
				.get(VegetationPlacements.GRASS_BONEMEAL)
				.ifPresent(grass -> grass.value().place(level, level.getChunkSource().getGenerator(), random, pos));
		}
	}

	private static int neighboringPlants(final ServerLevel level, final BlockPos pos) {
		int plants = 0;
		BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if ((dx != 0 || dz != 0) && !level.getBlockState(probe.setWithOffset(pos, dx, 0, dz)).isAir()) {
					plants++;
				}
			}
		}

		return plants;
	}

	/**
	 * A sapling takes where a tree could grow: open, lit ground with no trunk close by. Dry climates take fewer, so
	 * forest creeps into grassland slowly and stops short of desert.
	 */
	private static void dropSeed(final ServerLevel level, final BlockPos leaves, final BlockState leavesState, final RandomSource random) {
		Block sapling = SAPLING_BY_LEAVES.get(leavesState.getBlock());
		if (sapling == null || random.nextDouble() >= Climate.base(level, leaves).wetness()) {
			return;
		}

		BlockPos landing = level.getHeightmapPos(
			Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
			leaves.offset(random.nextIntBetweenInclusive(-SEED_REACH, SEED_REACH), 0, random.nextIntBetweenInclusive(-SEED_REACH, SEED_REACH))
		);
		BlockState there = level.getBlockState(landing);
		if ((there.isAir() || diesBack(there))
			&& sapling.defaultBlockState().canSurvive(level, landing)
			&& level.getRawBrightness(landing, 0) >= SAPLING_NEEDS_LIGHT
			&& !hasTrunkNear(level, landing)) {
			level.setBlockAndUpdate(landing, sapling.defaultBlockState());
		}
	}

	private static boolean hasTrunkNear(final ServerLevel level, final BlockPos pos) {
		for (BlockPos near : BlockPos.betweenClosed(pos.offset(-TRUNK_SPACING, -1, -TRUNK_SPACING), pos.offset(TRUNK_SPACING, TRUNK_SPACING, TRUNK_SPACING))) {
			BlockState state = level.getBlockState(near);
			if (state.is(BlockTags.LOGS) || state.is(BlockTags.SAPLINGS)) {
				return true;
			}
		}

		return false;
	}
}
