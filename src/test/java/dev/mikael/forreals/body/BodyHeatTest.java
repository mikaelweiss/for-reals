package dev.mikael.forreals.body;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mikael.forreals.body.BodyHeat.Clothing;
import dev.mikael.forreals.body.BodyHeat.Stage;
import org.junit.jupiter.api.Test;

class BodyHeatTest {
	private static final Clothing FULL_LEATHER = new Clothing(4, 0);
	private static final Clothing FULL_IRON = new Clothing(0, 4);

	private static double after(final double seconds, final double felt, final Clothing clothing) {
		double exposure = 0;
		for (int i = 0; i < seconds; i++) {
			exposure = BodyHeat.next(exposure, felt, clothing, 1);
		}

		return exposure;
	}

	@Test
	void mildAirNeverHurts() {
		assertEquals(0, after(3600, 18, Clothing.NONE));
	}

	@Test
	void aFrostyNightWarnsWithinAMinuteAndTakesMinutesToKill() {
		assertEquals(Stage.CHILLY, BodyHeat.stage(after(60, -8, Clothing.NONE)));
		assertTrue(BodyHeat.stage(after(120, -8, Clothing.NONE)).isWorseThan(Stage.CHILLY));
		assertTrue(BodyHeat.stage(after(120, -8, Clothing.NONE)) != Stage.HYPOTHERMIA);
		assertEquals(Stage.HYPOTHERMIA, BodyHeat.stage(after(600, -8, Clothing.NONE)));
	}

	@Test
	void leatherKeepsAFrostyNightComfortable() {
		assertEquals(0, after(3600, -8, FULL_LEATHER));
	}

	@Test
	void deepColdBeatsEvenLeather() {
		assertEquals(Stage.HYPOTHERMIA, BodyHeat.stage(after(600, -32, FULL_LEATHER)));
	}

	@Test
	void armorMakesDesertHeatWorse() {
		assertTrue(after(60, 40, FULL_IRON) > after(60, 40, Clothing.NONE));
	}

	@Test
	void comfortBringsABodyBackToNormal() {
		double frozen = after(600, -8, Clothing.NONE);
		assertEquals(0, BodyHeat.next(BodyHeat.next(frozen, 18, Clothing.NONE, 20), 18, Clothing.NONE, 20));
	}

	@Test
	void stagesRunFromHypothermiaToHeatstroke() {
		assertEquals(Stage.HYPOTHERMIA, BodyHeat.stage(-100));
		assertEquals(Stage.FREEZING, BodyHeat.stage(-80));
		assertEquals(Stage.COLD, BodyHeat.stage(-50));
		assertEquals(Stage.CHILLY, BodyHeat.stage(-25));
		assertEquals(Stage.COMFORTABLE, BodyHeat.stage(-24));
		assertEquals(Stage.COMFORTABLE, BodyHeat.stage(24));
		assertEquals(Stage.HOT, BodyHeat.stage(60));
		assertEquals(Stage.HEATSTROKE, BodyHeat.stage(100));
	}
}
