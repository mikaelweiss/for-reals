package dev.mikael.forreals.weather;

import dev.mikael.forreals.time.Calendar;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * The sky over the whole map at any moment, computed from the seed alone. It holds no state, so the same place and
 * time always give the same weather, and tomorrow's weather can be read today.
 *
 * <p>Air masses and storm systems are noise patterns that drift with the wind and slowly change shape. Storms
 * strengthen where one air mass replaces another, so a swing in temperature comes with rain.
 */
public final class WeatherField {
	private static final double AIR_MASS_DEGREES = 8;
	private static final double AIR_MASS_SIZE = 3000;
	private static final double AIR_MASS_LIFETIME = 4 * Calendar.TICKS_PER_DAY;
	private static final double STORM_SIZE = 1800;
	private static final double STORM_LIFETIME = 2.5 * Calendar.TICKS_PER_DAY;
	private static final double SHOWER_SIZE = 900;
	private static final double SHOWER_LIFETIME = Calendar.TICKS_PER_DAY * 2 / 3.0;

	private static final double WIND_BLOCKS_PER_TICK = 0.07;
	private static final double WIND_WANDER_BLOCKS = 1500;
	private static final double WIND_WANDER_PERIOD = 5 * Calendar.TICKS_PER_DAY;

	private static final int FRONT_PROBE_TICKS = 600;
	private static final double FRONT_DEGREES_FOR_FULL_BOOST = 1.5;
	private static final double FRONT_BOOST = 0.45;

	private final SimplexNoise airMasses;
	private final SimplexNoise storms;
	private final SimplexNoise showers;
	private final double windX;
	private final double windZ;

	public WeatherField(final long seed) {
		RandomSource random = RandomSource.create(seed ^ 0x466F725265616C73L);
		this.airMasses = new SimplexNoise(random);
		this.storms = new SimplexNoise(random);
		this.showers = new SimplexNoise(random);
		double prevailing = random.nextDouble() * 2 * Math.PI;
		this.windX = Math.cos(prevailing) * WIND_BLOCKS_PER_TICK;
		this.windZ = Math.sin(prevailing) * WIND_BLOCKS_PER_TICK;
	}

	/** Degrees the air mass overhead adds to or takes from the usual temperature. */
	public double airMass(final double x, final double z, final long ticks) {
		return AIR_MASS_DEGREES
			* this.airMasses.get((x - this.driftX(ticks)) / AIR_MASS_SIZE, (z - this.driftZ(ticks)) / AIR_MASS_SIZE, ticks / AIR_MASS_LIFETIME);
	}

	/**
	 * How much wide storm systems are trying to rain here. Roughly -1 to 1.5. A climate decides how much of that it
	 * takes to actually rain.
	 */
	public double stormSignal(final double x, final double z, final long ticks) {
		double system = 1.6 * this.storms.get((x - this.driftX(ticks)) / STORM_SIZE, (z - this.driftZ(ticks)) / STORM_SIZE, ticks / STORM_LIFETIME);
		double change = Math.abs(this.airMass(x, z, ticks + FRONT_PROBE_TICKS) - this.airMass(x, z, ticks - FRONT_PROBE_TICKS));
		return system + FRONT_BOOST * Math.min(1, change / FRONT_DEGREES_FOR_FULL_BOOST);
	}

	/** Small short-lived showers that pop up on their own. 0 to 1. Heat and humidity decide whether they amount to anything. */
	public double showerSignal(final double x, final double z, final long ticks) {
		return Math.max(
			0, this.showers.get((x - this.driftX(ticks)) / SHOWER_SIZE, (z - this.driftZ(ticks)) / SHOWER_SIZE, ticks / SHOWER_LIFETIME)
		);
	}

	/** How far the wind has carried the sky since the start of the calendar. A steady prevailing wind plus a slow wander. */
	private double driftX(final long ticks) {
		return this.windX * ticks + WIND_WANDER_BLOCKS * Math.sin(2 * Math.PI * ticks / WIND_WANDER_PERIOD);
	}

	private double driftZ(final long ticks) {
		return this.windZ * ticks + WIND_WANDER_BLOCKS * Math.cos(2 * Math.PI * ticks / WIND_WANDER_PERIOD);
	}
}
