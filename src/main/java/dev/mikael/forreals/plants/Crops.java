package dev.mikael.forreals.plants;

import dev.mikael.forreals.climate.Climate;
import dev.mikael.forreals.climate.ClimateModel;
import dev.mikael.forreals.time.WorldTime;
import java.util.Map;
import java.util.OptionalLong;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.gamerules.GameRules;

/** Makes planted things answer to the climate: growth by temperature, death by frost, and growth while nobody is near. */
public final class Crops {
	private static final Map<Block, GrowingRange> RANGE_BY_BLOCK = Map.ofEntries(
		Map.entry(Blocks.WHEAT, GrowingRange.COOL_CROP),
		Map.entry(Blocks.CARROTS, GrowingRange.COOL_CROP),
		Map.entry(Blocks.POTATOES, GrowingRange.COOL_CROP),
		Map.entry(Blocks.BEETROOTS, GrowingRange.COOL_CROP),
		Map.entry(Blocks.MELON_STEM, GrowingRange.WARM_CROP),
		Map.entry(Blocks.PUMPKIN_STEM, GrowingRange.WARM_CROP),
		Map.entry(Blocks.TORCHFLOWER_CROP, GrowingRange.WARM_CROP),
		Map.entry(Blocks.PITCHER_CROP, GrowingRange.WARM_CROP),
		Map.entry(Blocks.COCOA, GrowingRange.TROPICAL_FRUIT),
		Map.entry(Blocks.SUGAR_CANE, GrowingRange.TROPICAL),
		Map.entry(Blocks.BAMBOO, GrowingRange.TROPICAL),
		Map.entry(Blocks.BAMBOO_SAPLING, GrowingRange.TROPICAL),
		Map.entry(Blocks.CACTUS, GrowingRange.TROPICAL),
		Map.entry(Blocks.SWEET_BERRY_BUSH, GrowingRange.HARDY)
	);

	private static final long SHORTEST_ABSENCE_WORTH_CATCHING_UP = 1200;
	private static final int MOST_MISSED_GROWTH_TICKS = 52;
	private static final int BLOCKS_PER_SECTION = 4096;
	/** Set while a chunk catches up, so plants grow by the weather of the time they missed instead of this moment. */
	private static long growingAsOf = Long.MIN_VALUE;

	private Crops() {
	}

	public static GrowingRange rangeOf(final BlockState state) {
		GrowingRange range = RANGE_BY_BLOCK.get(state.getBlock());
		return range == null && state.is(BlockTags.SAPLINGS) ? GrowingRange.TREE : range;
	}

	/**
	 * Called before a block's random tick, which is how plants grow.
	 *
	 * @return true if the climate stops this tick from happening
	 */
	public static boolean climateStopsGrowth(final ServerLevel level, final BlockPos pos, final BlockState state, final RandomSource random) {
		if (!Climate.hasClimate(level)) {
			return false;
		}

		GrowingRange range = rangeOf(state);
		if (range == null) {
			return false;
		}

		double temperature = temperatureFor(level, pos);
		if (range.diesAt(temperature)) {
			level.removeBlock(pos, false);
			return true;
		}

		return random.nextDouble() >= range.growthAt(temperature);
	}

	private static double temperatureFor(final ServerLevel level, final BlockPos pos) {
		if (growingAsOf == Long.MIN_VALUE) {
			return Climate.feltAt(level, pos);
		}

		double typicalThen = Climate.base(level, pos).yearlyMean()
			+ ClimateModel.seasonalOffset(Climate.base(level, pos), growingAsOf)
			+ ClimateModel.altitudeOffset(pos.getY() - level.getSeaLevel());
		return Climate.feltAt(level, pos, typicalThen);
	}

	/** Gives every plant in a returning chunk the growth it missed, up to a limit, by the season it missed it in. */
	public static void catchUp(final ServerLevel level, final LevelChunk chunk, final OptionalLong awayTicks) {
		if (awayTicks.isEmpty() || awayTicks.getAsLong() < SHORTEST_ABSENCE_WORTH_CATCHING_UP) {
			return;
		}

		long away = awayTicks.getAsLong();
		double missedTicksPerBlock = Math.min(
			MOST_MISSED_GROWTH_TICKS, (double)away * level.getGameRules().get(GameRules.RANDOM_TICK_SPEED) / BLOCKS_PER_SECTION
		);
		growingAsOf = WorldTime.ticks(level.getServer()) - away / 2;
		try {
			LevelChunkSection[] sections = chunk.getSections();
			for (int index = 0; index < sections.length; index++) {
				if (sections[index].maybeHas(state -> rangeOf(state) != null)) {
					growSection(level, chunk, index, missedTicksPerBlock);
				}
			}
		} finally {
			growingAsOf = Long.MIN_VALUE;
		}
	}

	private static void growSection(final ServerLevel level, final LevelChunk chunk, final int sectionIndex, final double missedTicksPerBlock) {
		LevelChunkSection section = chunk.getSections()[sectionIndex];
		int minY = chunk.getSectionYFromSectionIndex(sectionIndex) << 4;
		RandomSource random = level.getRandom();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = 0; y < 16; y++) {
			for (int z = 0; z < 16; z++) {
				for (int x = 0; x < 16; x++) {
					if (rangeOf(section.getBlockState(x, y, z)) == null) {
						continue;
					}

					pos.set(chunk.getPos().getMinBlockX() + x, minY + y, chunk.getPos().getMinBlockZ() + z);
					int missed = (int)missedTicksPerBlock + (random.nextDouble() < missedTicksPerBlock % 1 ? 1 : 0);
					for (int i = 0; i < missed; i++) {
						BlockState state = level.getBlockState(pos);
						if (rangeOf(state) == null || !state.isRandomlyTicking()) {
							break;
						}

						state.randomTick(level, pos, random);
					}
				}
			}
		}
	}
}
