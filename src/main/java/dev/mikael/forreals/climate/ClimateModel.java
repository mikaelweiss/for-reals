package dev.mikael.forreals.climate;

import dev.mikael.forreals.time.Calendar;

/** Turns a place's yearly climate into the air temperature at one moment, in Celsius. */
public final class ClimateModel {
	public static final double FREEZING = 0;

	/** The air lags the sun. The hottest day comes an eighth of a year after the longest one. */
	private static final double SEASONAL_LAG = 0.125;
	private static final int WARMEST_TIME_OF_DAY = 8000;
	private static final double BLOCKS_PER_DEGREE = 10;
	private static final double BLOCKS_DOWN_PER_DEGREE = 16;

	private ClimateModel() {
	}

	/**
	 * @param airMass degrees the passing air mass adds or removes
	 * @param cloudCover 0 for clear sky, 1 for overcast. Clouds flatten the swing between day and night.
	 */
	public static double openAir(final BiomeClimate climate, final long ticks, final int blocksAboveSea, final double airMass, final double cloudCover) {
		return climate.yearlyMean()
			+ seasonalOffset(climate, ticks)
			+ dailyOffset(climate, ticks) * (1 - 0.6 * cloudCover)
			+ altitudeOffset(blocksAboveSea)
			+ airMass;
	}

	/** Under cover the ground holds the yearly average, as a cave does. Deep rock is warmer. */
	public static double underCover(final BiomeClimate climate, final int blocksAboveSea) {
		return climate.yearlyMean() + altitudeOffset(blocksAboveSea) + Math.max(0, -blocksAboveSea) / BLOCKS_DOWN_PER_DEGREE;
	}

	public static double seasonalOffset(final BiomeClimate climate, final long ticks) {
		return climate.seasonalSwing() * Math.sin(2 * Math.PI * (Calendar.yearFraction(ticks) - SEASONAL_LAG));
	}

	public static double dailyOffset(final BiomeClimate climate, final long ticks) {
		double sinceWarmest = (double)(Calendar.timeOfDay(ticks) - WARMEST_TIME_OF_DAY) / Calendar.TICKS_PER_DAY;
		return climate.dailySwing() * Math.cos(2 * Math.PI * sinceWarmest);
	}

	public static double altitudeOffset(final int blocksAboveSea) {
		return -Math.max(0, blocksAboveSea) / BLOCKS_PER_DEGREE;
	}
}
