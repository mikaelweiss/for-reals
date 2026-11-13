package dev.mikael.forreals.weather;

import dev.mikael.forreals.climate.BiomeClimate;
import dev.mikael.forreals.climate.ClimateModel;

/**
 * How much snow lies on the ground at a place, worked out by replaying the last days of weather. Nothing is stored,
 * so land nobody has visited for a season still has the right snow when someone arrives.
 */
public final class Snowpack {
	public static final int MAX_LAYERS = 6;
	public static final int STEP_TICKS = 1000;

	private static final int STEPS = 480;
	private static final double SNOWFALL_FROM = 0.05;
	private static final double LAYERS_PER_STEP_OF_HEAVY_SNOW = 0.15;
	private static final double LAYERS_MELTED_PER_DEGREE_STEP = 0.033;

	private final float[] precipitation = new float[STEPS];
	private final float[] seaLevelTemperature = new float[STEPS];

	private Snowpack() {
	}

	/** Steps are counted the same everywhere, so every caller within one step sees the same snowpack. */
	public static long stepOf(final long ticks) {
		return Math.floorDiv(ticks, (long)STEP_TICKS);
	}

	public static Snowpack over(final WeatherField field, final BiomeClimate climate, final double x, final double z, final long ticks) {
		Snowpack snowpack = new Snowpack();
		long newest = stepOf(ticks);
		for (int i = 0; i < STEPS; i++) {
			long moment = (newest - STEPS + 1 + i) * STEP_TICKS;
			Sky sky = Sky.over(field, climate, x, z, moment);
			snowpack.precipitation[i] = (float)sky.precipitation();
			snowpack.seaLevelTemperature[i] = (float)ClimateModel.openAir(climate, moment, 0, sky.airMass(), sky.cloudCover());
		}

		return snowpack;
	}

	/** Depth in snow layers, 0 to {@link #MAX_LAYERS}. Higher ground is colder, so it gathers more and keeps it longer. */
	public double layersAt(final int blocksAboveSea) {
		double altitude = ClimateModel.altitudeOffset(blocksAboveSea);
		double layers = 0;
		for (int i = 0; i < STEPS; i++) {
			double temperature = this.seaLevelTemperature[i] + altitude;
			if (temperature < Weather.SNOW_BELOW && this.precipitation[i] > SNOWFALL_FROM) {
				layers += this.precipitation[i] * LAYERS_PER_STEP_OF_HEAVY_SNOW;
			}

			if (temperature > ClimateModel.FREEZING) {
				layers -= temperature * LAYERS_MELTED_PER_DEGREE_STEP;
			}

			layers = Math.clamp(layers, 0, MAX_LAYERS);
		}

		return layers;
	}
}
