package dev.mikael.forreals.seasons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SeasonalLookTest {
	private static void assertNear(final int expected, final int actual) {
		for (int shift = 0; shift <= 16; shift += 8) {
			int difference = Math.abs((expected >> shift & 0xFF) - (actual >> shift & 0xFF));
			assertTrue(difference <= 12, String.format("expected about %06X, got %06X", expected, actual));
		}
	}

	@Test
	void summerColorsMatchTheGamesOwn() {
		assertNear(0x91BD59, SummerColors.grass(0.8, 0.4));
		assertNear(0x79C05A, SummerColors.grass(0.7, 0.8));
		assertNear(0x59C93C, SummerColors.grass(0.95, 0.9));
		assertNear(0xBFB755, SummerColors.grass(2.0, 0.0));
		assertNear(0x86B783, SummerColors.grass(0.25, 0.8));
		assertNear(0x77AB2F, SummerColors.foliage(0.8, 0.4));
		assertNear(0x59AE30, SummerColors.foliage(0.7, 0.8));
		assertNear(0x30BB0B, SummerColors.foliage(0.95, 0.9));
	}

	@Test
	void aYearRunsWinterSpringSummerFallWinter() {
		assertEquals(LookStage.WINTER, LookStage.at(0, true, 0.5));
		assertEquals(LookStage.SPRING, LookStage.at(8, true, 0.5));
		assertEquals(LookStage.SUMMER, LookStage.at(20, true, 0.5));
		assertEquals(LookStage.SUMMER, LookStage.at(20, false, 0.5));
		assertEquals(LookStage.FALL_GOLD, LookStage.at(10, false, 0.5));
		assertEquals(LookStage.FALL_RED, LookStage.at(10, false, 0.1));
		assertEquals(LookStage.WINTER, LookStage.at(2, false, 0.5));
	}

	@Test
	void summerLeavesTheColorAloneAndFallTurnsLeavesWarm() {
		int green = SummerColors.foliage(0.7, 0.8);
		assertEquals(green, LookStage.SUMMER.foliage(green, 1));
		int fall = LookStage.FALL_RED.foliage(green, 1);
		assertTrue((fall >> 16 & 0xFF) > (fall >> 8 & 0xFF), "fall leaves should be more red than green");
	}

	@Test
	void aPlaceWithoutSeasonsKeepsItsColor() {
		int green = SummerColors.foliage(0.95, 0.9);
		assertEquals(green, LookStage.WINTER.foliage(green, 0));
	}
}
