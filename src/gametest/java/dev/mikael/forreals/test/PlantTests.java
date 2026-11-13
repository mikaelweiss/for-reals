package dev.mikael.forreals.test;

import dev.mikael.forreals.climate.BiomeClimate;
import dev.mikael.forreals.plants.Crops;
import dev.mikael.forreals.plants.WildPlants;
import dev.mikael.forreals.time.Calendar;
import java.util.OptionalLong;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;

public class PlantTests {
	private static final long MIDSUMMER = (long)(0.375 * Calendar.TICKS_PER_YEAR);
	private static final long MIDWINTER_NIGHT = (long)(0.875 * Calendar.TICKS_PER_YEAR) + 20000;
	private static final long TWO_DAYS = 2L * Calendar.TICKS_PER_DAY;
	private static final BlockPos CROP = new BlockPos(3, 2, 3);
	private static final int YARD = 8;

	private static void floor(final GameTestHelper helper, final Block block) {
		for (int x = 0; x < YARD; x++) {
			for (int z = 0; z < YARD; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
				helper.setBlock(x, 1, z, block);
			}
		}
	}

	private static void season(final GameTestHelper helper, final BiomeClimate climate, final long calendarTicks) {
		TestClimate.pin(helper, climate);
		TestClimate.setCalendar(helper, calendarTicks);
		TestClimate.clearSkies(helper);
	}

	private static void randomTick(final GameTestHelper helper, final BlockPos relative, final int times) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(relative);
		for (int i = 0; i < times; i++) {
			level.getBlockState(pos).randomTick(level, pos, level.getRandom());
		}
	}

	private static int count(final GameTestHelper helper, final int y, final Predicate<BlockState> matches) {
		int found = 0;
		for (int x = 0; x < YARD; x++) {
			for (int z = 0; z < YARD; z++) {
				found += matches.test(helper.getBlockState(new BlockPos(x, y, z))) ? 1 : 0;
			}
		}

		return found;
	}

	@GameTest(environment = TestClimate.ENVIRONMENT, skyAccess = true)
	public void wheatRipensInSummerAndDiesInHardFrost(final GameTestHelper helper) {
		floor(helper, Blocks.FARMLAND);
		helper.setBlock(CROP, Blocks.WHEAT);
		season(helper, TestClimate.TEMPERATE, MIDSUMMER);
		randomTick(helper, CROP, 400);
		helper.assertTrue(helper.getBlockState(CROP).getValue(CropBlock.AGE) == CropBlock.MAX_AGE, "wheat did not ripen in summer: " + helper.getBlockState(CROP));

		season(helper, TestClimate.FROZEN, MIDWINTER_NIGHT);
		randomTick(helper, CROP, 1);
		helper.assertTrue(helper.getBlockState(CROP).isAir(), "ripe wheat survived a hard frost in the open");
		helper.succeed();
	}

	@GameTest(environment = TestClimate.ENVIRONMENT, skyAccess = true)
	public void aRoofAndAFireCarryWheatThroughAWinterNight(final GameTestHelper helper) {
		floor(helper, Blocks.FARMLAND);
		helper.setBlock(CROP, Blocks.WHEAT);
		helper.setBlock(CROP.east(), Blocks.CAMPFIRE);
		for (int x = 0; x < YARD; x++) {
			for (int z = 0; z < YARD; z++) {
				helper.setBlock(x, 5, z, Blocks.GLASS);
			}
		}

		season(helper, TestClimate.TEMPERATE, MIDWINTER_NIGHT);
		randomTick(helper, CROP, 50);
		helper.assertTrue(helper.getBlockState(CROP).is(Blocks.WHEAT), "wheat under glass beside a fire died in winter");
		helper.succeed();
	}

	@GameTest(environment = TestClimate.ENVIRONMENT, skyAccess = true)
	public void cropsGrowWhileTheirChunkIsAway(final GameTestHelper helper) {
		floor(helper, Blocks.FARMLAND);
		for (int x = 0; x < YARD; x++) {
			for (int z = 0; z < YARD; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE));
				helper.setBlock(x, 2, z, Blocks.WHEAT);
			}
		}

		season(helper, TestClimate.TEMPERATE, MIDSUMMER + TWO_DAYS);
		ServerLevel level = helper.getLevel();
		Crops.catchUp(level, level.getChunkAt(helper.absolutePos(CROP)), OptionalLong.of(TWO_DAYS));
		helper.assertTrue(helper.getBlockState(CROP).getValue(CropBlock.AGE) > 0, "wheat did not grow during two summer days away");

		for (int x = 0; x < YARD; x++) {
			for (int z = 0; z < YARD; z++) {
				helper.setBlock(x, 2, z, Blocks.WHEAT);
			}
		}

		season(helper, TestClimate.FROZEN, MIDWINTER_NIGHT);
		Crops.catchUp(level, level.getChunkAt(helper.absolutePos(CROP)), OptionalLong.of(TWO_DAYS));
		helper.assertTrue(helper.getBlockState(CROP).isAir(), "wheat left out through a frozen winter was still there");
		helper.succeed();
	}

	@GameTest(environment = TestClimate.ENVIRONMENT, skyAccess = true)
	public void meadowGreensInSummerAndDiesBackInWinter(final GameTestHelper helper) {
		floor(helper, Blocks.GRASS_BLOCK);
		season(helper, TestClimate.GROWING_WEATHER, MIDSUMMER);
		ServerLevel level = helper.getLevel();
		for (int visit = 0; visit < 40; visit++) {
			for (int x = 0; x < YARD; x++) {
				for (int z = 0; z < YARD; z++) {
					WildPlants.tend(level, helper.absolutePos(new BlockPos(x, 0, z)));
				}
			}
		}

		int grown = count(helper, 2, state -> !state.isAir());
		helper.assertTrue(grown > 5 && grown < YARD * YARD, "a summer meadow grew " + grown + " plants on " + YARD * YARD + " blocks");

		season(helper, TestClimate.FROZEN, MIDWINTER_NIGHT);
		for (int visit = 0; visit < 20; visit++) {
			for (int x = 0; x < YARD; x++) {
				for (int z = 0; z < YARD; z++) {
					WildPlants.tend(level, helper.absolutePos(new BlockPos(x, 0, z)));
				}
			}
		}

		helper.assertTrue(count(helper, 2, WildPlants::diesBack) == 0, "grass and flowers stood through a hard winter");
		helper.succeed();
	}

	@GameTest(environment = TestClimate.ENVIRONMENT, skyAccess = true)
	public void treesSeedTheOpenGroundAroundThem(final GameTestHelper helper) {
		floor(helper, Blocks.GRASS_BLOCK);
		BlockPos canopy = new BlockPos(3, 5, 3);
		helper.setBlock(canopy, Blocks.OAK_LEAVES);
		season(helper, TestClimate.GROWING_WEATHER, MIDSUMMER);
		ServerLevel level = helper.getLevel();
		for (int visit = 0; visit < 4000; visit++) {
			WildPlants.tend(level, helper.absolutePos(canopy));
		}

		int saplings = count(helper, 2, state -> state.is(Blocks.OAK_SAPLING));
		helper.assertTrue(saplings >= 1, "an oak dropped no saplings");
		helper.assertTrue(saplings <= 4, "saplings crowded each other: " + saplings + " in an 8 by 8 yard");
		helper.succeed();
	}
}
