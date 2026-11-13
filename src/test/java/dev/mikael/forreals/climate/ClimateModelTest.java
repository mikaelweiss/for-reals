package dev.mikael.forreals.climate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mikael.forreals.time.Calendar;
import org.junit.jupiter.api.Test;

class ClimateModelTest {
	private static final long DAY = Calendar.TICKS_PER_DAY;
	private static final long MIDSUMMER = (long)(0.375 * Calendar.TICKS_PER_YEAR);
	private static final long MIDWINTER = (long)(0.875 * Calendar.TICKS_PER_YEAR);
	private static final long AFTERNOON = 8000;
	private static final long SMALL_HOURS = 20000;

	private static final BiomeClimate PLAINS = BiomeClimate.fromVanilla(0.8, 0.4);
	private static final BiomeClimate JUNGLE = BiomeClimate.fromVanilla(0.95, 0.9);
	private static final BiomeClimate DESERT = BiomeClimate.fromVanilla(2.0, 0.0);
	private static final BiomeClimate SNOWY_TAIGA = BiomeClimate.fromVanilla(-0.5, 0.4);

	private static double seaLevel(final BiomeClimate climate, final long ticks) {
		return ClimateModel.openAir(climate, ticks, 0, 0, 0);
	}

	@Test
	void plainsFreezeOnWinterNightsAndAreWarmOnSummerDays() {
		assertTrue(seaLevel(PLAINS, MIDWINTER + SMALL_HOURS) < ClimateModel.FREEZING);
		assertTrue(seaLevel(PLAINS, MIDSUMMER + AFTERNOON) > 25);
	}

	@Test
	void jungleNeverFreezes() {
		assertTrue(seaLevel(JUNGLE, MIDWINTER + SMALL_HOURS) > 15);
	}

	@Test
	void desertNightsAreFarColderThanDesertDays() {
		assertTrue(seaLevel(DESERT, MIDSUMMER + AFTERNOON) - seaLevel(DESERT, MIDSUMMER + SMALL_HOURS) > 15);
	}

	@Test
	void frozenLandBarelyThawsInSummer() {
		assertTrue(seaLevel(SNOWY_TAIGA, MIDWINTER + AFTERNOON) < -15);
		assertTrue(seaLevel(SNOWY_TAIGA, MIDSUMMER + AFTERNOON) > ClimateModel.FREEZING);
	}

	@Test
	void mountainsAreColderThanTheValleyBelow() {
		long noon = MIDSUMMER + DAY / 4;
		assertEquals(10, seaLevel(PLAINS, noon) - ClimateModel.openAir(PLAINS, noon, 100, 0, 0), 1e-9);
	}

	@Test
	void cloudsKeepNightsWarmerAndDaysCooler() {
		assertTrue(ClimateModel.openAir(PLAINS, MIDSUMMER + SMALL_HOURS, 0, 0, 1) > seaLevel(PLAINS, MIDSUMMER + SMALL_HOURS));
		assertTrue(ClimateModel.openAir(PLAINS, MIDSUMMER + AFTERNOON, 0, 0, 1) < seaLevel(PLAINS, MIDSUMMER + AFTERNOON));
	}

	@Test
	void coverHoldsTheYearlyAverage() {
		assertEquals(PLAINS.yearlyMean(), ClimateModel.underCover(PLAINS, 0), 1e-9);
	}
}
