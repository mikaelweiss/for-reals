package dev.mikael.forreals.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mikael.forreals.climate.BiomeClimate;
import dev.mikael.forreals.time.Calendar;
import org.junit.jupiter.api.Test;

class SnowpackTest {
	private static final WeatherField FIELD = new WeatherField(42);
	private static final BiomeClimate FOREST = BiomeClimate.fromVanilla(0.7, 0.8);
	private static final BiomeClimate JUNGLE = BiomeClimate.fromVanilla(0.95, 0.9);
	private static final BiomeClimate SNOWY_TAIGA = BiomeClimate.fromVanilla(-0.5, 0.4);
	private static final long MIDSUMMER = (long)(0.375 * Calendar.TICKS_PER_YEAR);
	private static final long MIDWINTER = (long)(0.875 * Calendar.TICKS_PER_YEAR);

	private static double deepestOverTwoWeeks(final BiomeClimate climate, final long from, final int blocksAboveSea) {
		double deepest = 0;
		for (int day = 0; day < 14; day++) {
			deepest = Math.max(deepest, Snowpack.over(FIELD, climate, 0, 0, from + (long)day * Calendar.TICKS_PER_DAY).layersAt(blocksAboveSea));
		}

		return deepest;
	}

	@Test
	void forestsGetSnowInWinterAndNoneInSummer() {
		assertTrue(deepestOverTwoWeeks(FOREST, MIDWINTER, 0) >= 1);
		assertEquals(0, deepestOverTwoWeeks(FOREST, MIDSUMMER, 0));
	}

	@Test
	void junglesNeverGetSnow() {
		assertEquals(0, deepestOverTwoWeeks(JUNGLE, MIDWINTER, 0));
	}

	@Test
	void frozenLandKeepsItsSnow() {
		assertTrue(Snowpack.over(FIELD, SNOWY_TAIGA, 0, 0, MIDWINTER).layersAt(0) >= 1);
	}

	@Test
	void peaksHoldMoreSnowThanTheValley() {
		Snowpack snowpack = Snowpack.over(FIELD, FOREST, 0, 0, MIDWINTER);
		assertTrue(snowpack.layersAt(120) >= snowpack.layersAt(0));
		assertTrue(deepestOverTwoWeeks(FOREST, MIDSUMMER, 200) > 0, "a high peak lost its snow cap in summer");
	}
}
