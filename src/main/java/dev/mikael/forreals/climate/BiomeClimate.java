package dev.mikael.forreals.climate;

/**
 * What a place is like over a whole year, before season, hour, height, and weather move it. Temperatures are in
 * Celsius. Swings are the distance from the middle to the peak.
 */
public record BiomeClimate(double yearlyMean, double seasonalSwing, double dailySwing, double wetness) {
	private static final double[][] MEAN_BY_VANILLA_TEMPERATURE = {
		{-0.7, -14}, {0.0, -4}, {0.15, 0}, {0.25, 2}, {0.5, 8}, {0.8, 12}, {0.95, 24}, {2.0, 26}
	};
	private static final double TEMPERATE_MEAN = 12;
	private static final double TROPICAL_MEAN = 24;
	private static final double TEMPERATE_SEASONAL_SWING = 13;

	/** Reads a climate out of the two numbers every biome carries. Wet tropics barely change through the year. */
	public static BiomeClimate fromVanilla(final double temperature, final double downfall) {
		double wetness = Math.clamp(downfall, 0, 1);
		double mean = interpolate(MEAN_BY_VANILLA_TEMPERATURE, temperature);
		double tropical = Math.clamp((mean - TEMPERATE_MEAN) / (TROPICAL_MEAN - TEMPERATE_MEAN), 0, 1);
		double tropicalSwing = 3 + 6 * (1 - wetness);
		double seasonalSwing = TEMPERATE_SEASONAL_SWING + tropical * (tropicalSwing - TEMPERATE_SEASONAL_SWING);
		return new BiomeClimate(mean, seasonalSwing, 3 + 6 * (1 - wetness), wetness);
	}

	public BiomeClimate plus(final BiomeClimate other, final double weight) {
		return new BiomeClimate(
			this.yearlyMean + other.yearlyMean * weight,
			this.seasonalSwing + other.seasonalSwing * weight,
			this.dailySwing + other.dailySwing * weight,
			this.wetness + other.wetness * weight
		);
	}

	public BiomeClimate dividedBy(final double total) {
		return new BiomeClimate(this.yearlyMean / total, this.seasonalSwing / total, this.dailySwing / total, this.wetness / total);
	}

	private static double interpolate(final double[][] points, final double x) {
		if (x <= points[0][0]) {
			return points[0][1];
		}

		for (int i = 1; i < points.length; i++) {
			if (x <= points[i][0]) {
				double share = (x - points[i - 1][0]) / (points[i][0] - points[i - 1][0]);
				return points[i - 1][1] + share * (points[i][1] - points[i - 1][1]);
			}
		}

		return points[points.length - 1][1];
	}
}
