package dev.mikael.forreals.animals;

import dev.mikael.forreals.climate.BiomeClimate;
import dev.mikael.forreals.climate.ClimateModel;
import java.util.Set;

/**
 * A square of land that herds graze. It remembers its own climate and which animals belong there, so the herd ledger
 * can run without the world loaded.
 */
public final class Range {
	public static final int SIZE_BLOCKS = 128;

	private static final double GRASS_STARTS_GROWING = 3;
	private static final double GRASS_GROWS_FULLY = 10;
	private static final double GROWTH_PER_HOUR = 0.3 / 24;
	private static final double SEED_BANK = 0.02;
	private static final double BURIED_BELOW = -3;
	private static final double CLEAR_ABOVE = 3;
	private static final double SHARE_REACHABLE_UNDER_SNOW = 0.35;

	private final BiomeClimate climate;
	private final Set<Species> habitat;
	private double forage;

	public Range(final BiomeClimate climate, final Set<Species> habitat, final double forage) {
		this.climate = climate;
		this.habitat = Set.copyOf(habitat);
		this.forage = forage;
	}

	public static long keyAt(final double x, final double z) {
		return key(Math.floorDiv((long)Math.floor(x), SIZE_BLOCKS), Math.floorDiv((long)Math.floor(z), SIZE_BLOCKS));
	}

	public static long key(final long rangeX, final long rangeZ) {
		return rangeX & 0xFFFFFFFFL | (rangeZ & 0xFFFFFFFFL) << 32;
	}

	public static int xOf(final long key) {
		return (int)key;
	}

	public static int zOf(final long key) {
		return (int)(key >> 32);
	}

	public static double center(final int rangeCoordinate) {
		return (rangeCoordinate + 0.5) * SIZE_BLOCKS;
	}

	public BiomeClimate climate() {
		return this.climate;
	}

	public Set<Species> habitat() {
		return this.habitat;
	}

	/** Share of the plant food this land can hold that is standing on it now, 0 to 1. */
	public double forage() {
		return this.forage;
	}

	/** Food this land holds when fully grown, in cow-days. Wet land holds more. */
	public double capacity() {
		return 40 + 100 * this.climate.wetness();
	}

	/**
	 * Food standing on this land, in cow-days. The last of the forage is roots and seed that nothing can eat, which
	 * is what lets grazed-out land grow back.
	 */
	public double food() {
		return Math.max(0, this.forage - SEED_BANK) * this.capacity();
	}

	/** Share of its appetite a grazer can find at this hour, 0 to 1. Deep cold buries most of the food under snow. */
	public double shareInReach(final long ticks) {
		return Math.clamp((typicalTemperature(ticks) - BURIED_BELOW) / (CLEAR_ABOVE - BURIED_BELOW), SHARE_REACHABLE_UNDER_SNOW, 1);
	}

	public void graze(final double food) {
		this.forage = Math.max(0, this.forage - food / this.capacity());
	}

	/** Grass grows back fastest at half cover, only when it is warm, and never quite dies out. */
	public void growForAnHour(final long ticks) {
		double warmth = Math.clamp((typicalTemperature(ticks) - GRASS_STARTS_GROWING) / (GRASS_GROWS_FULLY - GRASS_STARTS_GROWING), 0, 1);
		this.forage = Math.clamp(this.forage + GROWTH_PER_HOUR * warmth * this.forage * (1 - this.forage), SEED_BANK, 1);
	}

	private double typicalTemperature(final long ticks) {
		return this.climate.yearlyMean() + ClimateModel.seasonalOffset(this.climate, ticks);
	}
}
