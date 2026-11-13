package dev.mikael.forreals.animals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mikael.forreals.climate.BiomeClimate;
import dev.mikael.forreals.time.Calendar;
import java.util.EnumSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EcosystemTest {
	private static final BiomeClimate MEADOW = BiomeClimate.fromVanilla(0.7, 0.8);
	private static final BiomeClimate FROZEN = BiomeClimate.fromVanilla(-0.5, 0.8);
	private static final BiomeClimate TROPICS = BiomeClimate.fromVanilla(0.95, 0.9);
	private static final Set<Species> SHEEP_COUNTRY = EnumSet.of(Species.SHEEP);
	private static final Set<Species> SHEEP_AND_WOLVES = EnumSet.of(Species.SHEEP, Species.WOLF);
	private static final long MIDSUMMER = (long)(0.375 * Calendar.TICKS_PER_YEAR);
	private static final long MIDWINTER = (long)(0.875 * Calendar.TICKS_PER_YEAR);
	private static final int HOURS_PER_YEAR = 24 * Calendar.DAYS_PER_YEAR;

	private static Ecosystem land(final int size, final BiomeClimate climate, final Set<Species> habitat, final double forage) {
		Ecosystem ecosystem = new Ecosystem();
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				ecosystem.restoreRange(Range.key(x, z), new Range(climate, habitat, forage));
			}
		}

		return ecosystem;
	}

	private static void run(final Ecosystem ecosystem, final long fromTicks, final int hours, final Random random) {
		for (int hour = 0; hour < hours; hour++) {
			ecosystem.advanceHour(fromTicks + (long)hour * Ecosystem.HOUR_TICKS, random, herd -> false);
		}
	}

	private static int animals(final Ecosystem ecosystem, final Species species) {
		return ecosystem.herds().stream().filter(herd -> herd.species() == species).mapToInt(Herd::count).sum();
	}

	@Test
	void grassGrowsInSummerAndNotInWinter() {
		Range summer = new Range(MEADOW, SHEEP_COUNTRY, 0.3);
		Range winter = new Range(MEADOW, SHEEP_COUNTRY, 0.3);
		for (int hour = 0; hour < 24 * 6; hour++) {
			summer.growForAnHour(MIDSUMMER + (long)hour * Ecosystem.HOUR_TICKS);
			winter.growForAnHour(MIDWINTER + (long)hour * Ecosystem.HOUR_TICKS);
		}

		assertTrue(summer.forage() > 0.5);
		assertEquals(0.3, winter.forage(), 1e-9);
	}

	@Test
	void snowBuriesMostOfTheGrass() {
		Range range = new Range(MEADOW, SHEEP_COUNTRY, 1);
		assertTrue(range.shareInReach(MIDWINTER) < range.shareInReach(MIDSUMMER) / 2);
	}

	@Test
	void aFlockGrowsUntilTheLandHoldsItBack() {
		Ecosystem ecosystem = land(3, MEADOW, SHEEP_COUNTRY, 0.8);
		ecosystem.addHerd(Species.SHEEP, Range.center(1), Range.center(1), 6);
		Random random = new Random(1);
		int most = 0;
		int yearsWithSheep = 0;
		for (int year = 0; year < 12; year++) {
			run(ecosystem, (long)year * Calendar.TICKS_PER_YEAR, HOURS_PER_YEAR, random);
			most = Math.max(most, animals(ecosystem, Species.SHEEP));
			yearsWithSheep += animals(ecosystem, Species.SHEEP) > 0 ? 1 : 0;
		}

		assertTrue(most > 30, "the flock never grew past " + most);
		assertTrue(most < 200, "nothing held the flock back: " + most + " sheep on nine ranges");
		assertTrue(yearsWithSheep >= 11, "the land stood empty for " + (12 - yearsWithSheep) + " of 12 years");
	}

	@Test
	void tooManyGrazersStarveOrLeave() {
		Ecosystem ecosystem = land(1, MEADOW, SHEEP_COUNTRY, 0.5);
		for (int herd = 0; herd < 20; herd++) {
			ecosystem.addHerd(Species.COW, Range.center(0), Range.center(0), 8);
		}

		run(ecosystem, MIDSUMMER, 24 * 20, new Random(2));
		assertTrue(animals(ecosystem, Species.COW) < 100, "160 cows stayed on one range: " + animals(ecosystem, Species.COW));
	}

	@Test
	void herdsLeaveSnowedInLandForOpenLand() {
		Ecosystem ecosystem = new Ecosystem();
		ecosystem.restoreRange(Range.key(0, 0), new Range(FROZEN, SHEEP_COUNTRY, 0.6));
		ecosystem.restoreRange(Range.key(1, 0), new Range(TROPICS, SHEEP_COUNTRY, 0.6));
		Herd herd = ecosystem.addHerd(Species.SHEEP, Range.center(0), Range.center(0), 8);
		run(ecosystem, MIDWINTER, 24 * 4, new Random(3));
		assertEquals(Range.key(1, 0), herd.range());
	}

	@Test
	void wolvesAndSheepBothLast() {
		Ecosystem ecosystem = land(6, MEADOW, SHEEP_AND_WOLVES, 0.8);
		Random random = new Random(4);
		for (int x = 0; x < 6; x++) {
			for (int z = 0; z < 6; z++) {
				ecosystem.addHerd(Species.SHEEP, Range.center(x), Range.center(z), 6);
			}
		}

		ecosystem.addHerd(Species.WOLF, Range.center(2), Range.center(2), 4);
		ecosystem.addHerd(Species.WOLF, Range.center(4), Range.center(4), 4);
		int yearsWithWolves = 0;
		int fewestSheep = Integer.MAX_VALUE;
		for (int year = 0; year < 15; year++) {
			run(ecosystem, (long)year * Calendar.TICKS_PER_YEAR, HOURS_PER_YEAR, random);
			yearsWithWolves += animals(ecosystem, Species.WOLF) > 0 ? 1 : 0;
			fewestSheep = Math.min(fewestSheep, animals(ecosystem, Species.SHEEP));
		}

		assertTrue(fewestSheep > 0, "wolves ate every sheep");
		assertTrue(yearsWithWolves >= 12, "wolves were gone for " + (15 - yearsWithWolves) + " of 15 years");
	}

	@Test
	void emptiedLandRefillsFromUnexploredLand() {
		Ecosystem ecosystem = land(3, MEADOW, SHEEP_COUNTRY, 0.8);
		run(ecosystem, 0, HOURS_PER_YEAR, new Random(5));
		assertTrue(animals(ecosystem, Species.SHEEP) > 0);
	}

	@Test
	void landWithNoUnexploredNeighborOnlyRefillsFromItsNeighbors() {
		Ecosystem ecosystem = land(5, MEADOW, SHEEP_COUNTRY, 0.8);
		run(ecosystem, 0, 24 * 6, new Random(6));
		boolean centerSettledFirst = ecosystem.herds().stream().anyMatch(herd -> herd.range() == Range.key(2, 2));
		assertTrue(!centerSettledFirst, "a herd appeared in the middle of explored land from nowhere");
	}

	@Test
	void exploringLandFindsHerdsAlreadyLivingThere() {
		Ecosystem ecosystem = new Ecosystem();
		Random random = new Random(7);
		for (int x = 0; x < 10; x++) {
			ecosystem.explore(Range.key(x, 0), new Range(MEADOW, SHEEP_AND_WOLVES, 0.8), random);
		}

		assertTrue(animals(ecosystem, Species.SHEEP) >= 10 * Species.SHEEP.smallestHerd());
		int before = ecosystem.herds().size();
		ecosystem.explore(Range.key(0, 0), new Range(MEADOW, SHEEP_AND_WOLVES, 0.8), random);
		assertEquals(before, ecosystem.herds().size());
	}
}
