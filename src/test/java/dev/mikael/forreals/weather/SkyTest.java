package dev.mikael.forreals.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mikael.forreals.climate.BiomeClimate;
import dev.mikael.forreals.time.Calendar;
import java.util.function.ToDoubleFunction;
import org.junit.jupiter.api.Test;

class SkyTest {
	private static final WeatherField FIELD = new WeatherField(42);
	private static final BiomeClimate DESERT = BiomeClimate.fromVanilla(2.0, 0.0);
	private static final BiomeClimate PLAINS = BiomeClimate.fromVanilla(0.8, 0.4);
	private static final BiomeClimate FOREST = BiomeClimate.fromVanilla(0.7, 0.8);
	private static final int STEP = 500;

	private static double yearlyShare(final BiomeClimate climate, final double x, final ToDoubleFunction<Sky> measure) {
		double total = 0;
		for (long t = 0; t < Calendar.TICKS_PER_YEAR; t += STEP) {
			total += measure.applyAsDouble(Sky.over(FIELD, climate, x, 0, t));
		}

		return total * STEP / Calendar.TICKS_PER_YEAR;
	}

	private static double wet(final Sky sky) {
		return sky.precipitation() > 0.05 ? 1 : 0;
	}

	@Test
	void sameSeedPlaceAndTimeGiveTheSameSky() {
		assertEquals(Sky.over(FIELD, PLAINS, 100, -250, 123456), Sky.over(new WeatherField(42), PLAINS, 100, -250, 123456));
	}

	@Test
	void desertsAreDryAndForestsAreWet() {
		assertTrue(yearlyShare(DESERT, 0, SkyTest::wet) < 0.03);
		assertTrue(yearlyShare(PLAINS, 0, SkyTest::wet) > 0.06);
		assertTrue(yearlyShare(FOREST, 0, SkyTest::wet) > yearlyShare(PLAINS, 0, SkyTest::wet));
	}

	@Test
	void downpoursAreRarerThanRain() {
		assertTrue(yearlyShare(PLAINS, 0, sky -> sky.thunder() > 0 ? 1 : 0) < yearlyShare(PLAINS, 0, SkyTest::wet) / 3);
	}

	@Test
	void weatherIsRegional() {
		double differs = 0;
		for (long t = 0; t < Calendar.TICKS_PER_YEAR; t += STEP) {
			differs += wet(Sky.over(FIELD, FOREST, 0, 0, t)) != wet(Sky.over(FIELD, FOREST, 6000, 0, t)) ? 1 : 0;
		}

		assertTrue(differs * STEP / Calendar.TICKS_PER_YEAR > 0.1);
	}

	@Test
	void stormsBuildOverMinutesNotSeconds() {
		double biggestJump = 0;
		double before = Sky.over(FIELD, FOREST, 0, 0, 0).precipitation();
		for (long t = 100; t < 20 * Calendar.TICKS_PER_DAY; t += 100) {
			double now = Sky.over(FIELD, FOREST, 0, 0, t).precipitation();
			biggestJump = Math.max(biggestJump, Math.abs(now - before));
			before = now;
		}

		assertTrue(biggestJump < 0.1, "rain strength jumped " + biggestJump + " in five seconds");
	}

	@Test
	void rainComesWithASwingInTemperature() {
		double swingWhenWet = 0;
		double swingWhenDry = 0;
		int wet = 0;
		int dry = 0;
		for (long t = 1000; t < Calendar.TICKS_PER_YEAR; t += STEP) {
			double swing = Math.abs(FIELD.airMass(0, 0, t + 600) - FIELD.airMass(0, 0, t - 600));
			if (wet(Sky.over(FIELD, PLAINS, 0, 0, t)) == 1) {
				swingWhenWet += swing;
				wet++;
			} else {
				swingWhenDry += swing;
				dry++;
			}
		}

		assertTrue(swingWhenWet / wet > swingWhenDry / dry);
	}
}
