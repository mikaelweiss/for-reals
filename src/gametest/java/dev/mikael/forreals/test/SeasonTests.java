package dev.mikael.forreals.test;

import dev.mikael.forreals.seasons.SeasonalLook;
import dev.mikael.forreals.time.Calendar;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

public class SeasonTests {
	private static final long MIDSUMMER_NOON = (long)(0.375 * Calendar.TICKS_PER_YEAR) + 6000;
	private static final long MIDFALL = (long)(0.625 * Calendar.TICKS_PER_YEAR);
	private static final long MIDWINTER_NIGHT = (long)(0.875 * Calendar.TICKS_PER_YEAR) + 20000;
	private static final BlockPos SPOT = new BlockPos(3, 2, 3);

	/** Height changes the season, and a test world's sea level is far below its tests. */
	private static BlockPos atSeaLevel(final GameTestHelper helper) {
		return helper.absolutePos(SPOT).atY(helper.getLevel().getSeaLevel());
	}

	private static Holder<Biome> look(final GameTestHelper helper, final long calendarTicks) {
		TestClimate.setCalendar(helper, calendarTicks);
		TestClimate.clearSkies(helper);
		return SeasonalLook.lookAt(helper.getLevel(), atSeaLevel(helper));
	}

	@GameTest(environment = TestClimate.ENVIRONMENT)
	public void playersAreShownTheSeasonNotTheRawBiome(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Holder<Biome> real = level.getBiome(atSeaLevel(helper));
		if (real.is(Biomes.THE_VOID)) {
			helper.succeed();
			return;
		}

		TestClimate.pin(helper, TestClimate.TEMPERATE);
		Holder<Biome> summer = look(helper, MIDSUMMER_NOON);
		Holder<Biome> fall = look(helper, MIDFALL);
		Holder<Biome> winter = look(helper, MIDWINTER_NIGHT);
		String realName = real.unwrapKey().orElseThrow().identifier().getPath();
		helper.assertTrue(summer.getRegisteredName().endsWith(realName + "/summer_rain"), "midsummer showed " + summer.getRegisteredName());
		helper.assertTrue(fall.getRegisteredName().contains(realName + "/fall_"), "midfall showed " + fall.getRegisteredName());
		helper.assertTrue(winter.getRegisteredName().endsWith(realName + "/winter_snow"), "a midwinter night showed " + winter.getRegisteredName());

		helper.assertTrue(summer.value().warmEnoughToRain(BlockPos.ZERO, level.getSeaLevel()), "the summer look would draw snow");
		helper.assertTrue(winter.value().coldEnoughToSnow(BlockPos.ZERO, level.getSeaLevel()), "the winter look would draw rain");
		int summerLeaves = summer.value().getFoliageColor();
		int fallLeaves = fall.value().getFoliageColor();
		helper.assertTrue(summerLeaves != fallLeaves, "leaves are the same color in summer and fall");
		helper.assertTrue((fallLeaves >> 16 & 0xFF) > (summerLeaves >> 16 & 0xFF), "fall leaves are no redder than summer leaves");
		helper.assertTrue(level.getBiome(atSeaLevel(helper)) == real, "the world's own biome changed");
		helper.succeed();
	}
}
