package dev.mikael.forreals.plants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GrowingRangeTest {
	@Test
	void nothingGrowsBelowFreezing() {
		for (GrowingRange range : GrowingRange.values()) {
			assertEquals(0, range.growthAt(-5), range.name());
		}
	}

	@Test
	void wheatGrowsFullyInMildWeatherAndSlowlyInTheCold() {
		assertEquals(1, GrowingRange.COOL_CROP.growthAt(18));
		assertEquals(0.5, GrowingRange.COOL_CROP.growthAt(6), 1e-9);
		assertEquals(0, GrowingRange.COOL_CROP.growthAt(35));
	}

	@Test
	void melonsNeedMoreWarmthThanWheat() {
		assertTrue(GrowingRange.WARM_CROP.growthAt(9) < GrowingRange.COOL_CROP.growthAt(9));
	}

	@Test
	void hardFrostKillsCropsButNotTreesOrBerries() {
		assertTrue(GrowingRange.COOL_CROP.diesAt(-4));
		assertFalse(GrowingRange.COOL_CROP.diesAt(-1));
		assertFalse(GrowingRange.TREE.diesAt(-20));
		assertFalse(GrowingRange.HARDY.diesAt(-20));
	}
}
