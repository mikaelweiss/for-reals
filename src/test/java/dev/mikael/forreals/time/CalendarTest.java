package dev.mikael.forreals.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CalendarTest {
	private static final long DAY = Calendar.TICKS_PER_DAY;
	private static final long SEASON = DAY * Calendar.DAYS_PER_SEASON;
	private static final long SUMMER_SOLSTICE = SEASON;
	private static final long WINTER_SOLSTICE = 3 * SEASON;

	@Test
	void yearStartsInSpringAndWraps() {
		assertEquals(Season.SPRING, Calendar.season(0));
		assertEquals(Season.SUMMER, Calendar.season(SEASON));
		assertEquals(Season.FALL, Calendar.season(2 * SEASON));
		assertEquals(Season.WINTER, Calendar.season(3 * SEASON));
		assertEquals(Season.WINTER, Calendar.season(Calendar.TICKS_PER_YEAR - 1));
		assertEquals(Season.SPRING, Calendar.season(Calendar.TICKS_PER_YEAR));
	}

	@Test
	void clockSetBeforeTheEpochStillHasASeason() {
		assertEquals(Season.WINTER, Calendar.season(-1));
		assertEquals(Calendar.DAYS_PER_YEAR - 1, Calendar.dayOfYear(-1));
	}

	@Test
	void daysAreLongestAtTheSummerSolstice() {
		assertEquals(0.5, Calendar.daylightShare(0), 1e-9);
		assertEquals(0.625, Calendar.daylightShare(SUMMER_SOLSTICE), 1e-9);
		assertEquals(0.375, Calendar.daylightShare(WINTER_SOLSTICE), 1e-9);
	}

	@Test
	void summerSunCrossesTheSkySlowly() {
		long noon = SUMMER_SOLSTICE + DAY / 4;
		long midnight = SUMMER_SOLSTICE + 3 * DAY / 4;
		assertTrue(Calendar.clockRate(noon) < 1);
		assertTrue(Calendar.clockRate(midnight) > 1);
	}

	@Test
	void aFullDayAlwaysTakesTheSameRealTime() {
		for (long start : new long[] {0, SUMMER_SOLSTICE, WINTER_SOLSTICE, 5 * DAY}) {
			double realTicks = DAY / 2.0 / Calendar.clockRate(start) + DAY / 2.0 / Calendar.clockRate(start + DAY / 2);
			assertEquals(DAY, realTicks, DAY / 100.0);
		}
	}
}
