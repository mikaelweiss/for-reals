package dev.mikael.forreals.test;

import dev.mikael.forreals.time.Calendar;
import dev.mikael.forreals.weather.SnowAndIce;
import dev.mikael.forreals.weather.Snowpack;
import java.util.HashSet;
import java.util.OptionalLong;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

public class WeatherTests {
	private static final BlockPos OPEN_GROUND = new BlockPos(2, 1, 2);
	private static final BlockPos POND = new BlockPos(5, 1, 5);
	private static final BlockPos ROOFED = new BlockPos(6, 1, 1);
	private static final long MIDSUMMER = (long)(0.375 * Calendar.TICKS_PER_YEAR);
	private static final long WINTER_STARTS = (long)(0.75 * Calendar.TICKS_PER_YEAR);

	private static void buildYard(final GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
				helper.setBlock(x, 1, z, Blocks.STONE);
			}
		}

		helper.setBlock(POND, Blocks.WATER);
		helper.setBlock(ROOFED.above(3), Blocks.STONE);
	}

	/** The yard can straddle a chunk border, so every chunk under it is settled. */
	private static void settleYard(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Set<LevelChunk> chunks = new HashSet<>();
		for (int x = 0; x < 8; x += 7) {
			for (int z = 0; z < 8; z += 7) {
				chunks.add(level.getChunkAt(helper.absolutePos(new BlockPos(x, 0, z))));
			}
		}

		chunks.forEach(chunk -> SnowAndIce.catchUp(level, chunk, OptionalLong.empty()));
	}

	@GameTest(environment = TestClimate.ENVIRONMENT, skyAccess = true)
	public void rainOnlyFallsUnderOpenSkyAndOnlyWhenTheSkySaysSo(final GameTestHelper helper) {
		buildYard(helper);
		ServerLevel level = helper.getLevel();
		TestClimate.pin(helper, TestClimate.TEMPERATE);
		TestClimate.setCalendar(helper, MIDSUMMER);
		level.getServer().setWeatherParameters(0, 1_000_000, true, false);
		TestClimate.forgetCachedWeather();
		helper.assertTrue(level.isRainingAt(helper.absolutePos(OPEN_GROUND.above())), "no rain under open sky in a forced storm");
		helper.assertFalse(level.isRainingAt(helper.absolutePos(ROOFED.above())), "rain fell through a roof");

		level.getServer().setWeatherParameters(1_000_000, 0, false, false);
		TestClimate.forgetCachedWeather();
		helper.assertFalse(level.isRainingAt(helper.absolutePos(OPEN_GROUND.above())), "rain fell from a forced clear sky");
		level.getServer().setWeatherParameters(0, 0, false, false);
		helper.succeed();
	}

	@GameTest(environment = TestClimate.ENVIRONMENT, skyAccess = true)
	public void snowAndIceComeWithWinterAndLeaveWithSummer(final GameTestHelper helper) {
		buildYard(helper);
		ServerLevel level = helper.getLevel();
		TestClimate.pin(helper, TestClimate.FROZEN);
		BlockPos ground = helper.absolutePos(OPEN_GROUND.above());
		int aboveSea = ground.getY() - level.getSeaLevel();

		long snowyMoment = -1;
		for (long moment = WINTER_STARTS; moment < Calendar.TICKS_PER_YEAR; moment += Snowpack.STEP_TICKS) {
			TestClimate.setCalendar(helper, moment);
			if (SnowAndIce.snowpack(level, ground).layersAt(aboveSea) >= 2) {
				snowyMoment = moment;
				break;
			}
		}

		helper.assertTrue(snowyMoment >= 0, "a frozen land saw no real snow all winter");
		int wanted = (int)Math.round(SnowAndIce.snowpack(level, ground).layersAt(aboveSea));
		settleYard(helper);
		BlockState snow = level.getBlockState(ground);
		helper.assertTrue(snow.is(Blocks.SNOW) && snow.getValue(SnowLayerBlock.LAYERS) == wanted, "expected " + wanted + " snow layers, found " + snow);
		helper.assertTrue(level.getBlockState(helper.absolutePos(POND)).is(Blocks.ICE), "the pond did not freeze");
		helper.assertTrue(level.getBlockState(helper.absolutePos(ROOFED.above())).isAir(), "snow settled under a roof");
		helper.assertTrue(level.precipitationAt(ground.above(8)) != Biome.Precipitation.RAIN, "it rained in a hard frost");

		TestClimate.pin(helper, TestClimate.TEMPERATE);
		TestClimate.setCalendar(helper, MIDSUMMER);
		settleYard(helper);
		helper.assertTrue(level.getBlockState(ground).isAir(), "snow survived midsummer");
		helper.assertTrue(level.getBlockState(helper.absolutePos(POND)).is(Blocks.WATER), "the pond did not thaw");
		helper.succeed();
	}
}
