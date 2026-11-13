package dev.mikael.forreals.weather;

import dev.mikael.forreals.climate.BiomeClimate;
import dev.mikael.forreals.climate.ClimateModel;
import dev.mikael.forreals.time.Calendar;

/**
 * The weather over one place at one moment: what the {@link WeatherField} sends, filtered through the local climate.
 *
 * @param airMass degrees the air mass adds to the usual temperature
 * @param cloudCover 0 for clear sky, 1 for overcast
 * @param precipitation 0 for dry, 1 for a downpour
 * @param thunder 0 for none, 1 for a full thunderstorm
 */
public record Sky(double airMass, double cloudCover, double precipitation, double thunder) {
	public static final Sky CLEAR = new Sky(0, 0, 0, 0);

	private static final double WETTEST_THRESHOLD = 0.45;
	private static final double DRYNESS_PENALTY = 1.15;
	private static final double WINTER_WETNESS = 0.12;
	private static final double WETTEST_TIME_OF_YEAR = 0.75;
	private static final double CLOUD_LEAD = 0.3;
	private static final double EDGE = 0.8;
	private static final double THUNDER_FROM = 0.75;
	private static final double SHOWER_HEAT_FROM = 18;
	private static final double SHOWER_HEAT_FULL = 28;
	private static final double SHOWER_STRENGTH = 2.2;
	private static final double SHOWER_THRESHOLD = 0.35;
	private static final int SHOWER_PEAK_TIME_OF_DAY = 9000;
	private static final int SHOWER_HALF_WIDTH = 4000;

	public static Sky over(final WeatherField field, final BiomeClimate climate, final double x, final double z, final long ticks) {
		double airMass = field.airMass(x, z, ticks);
		double excess = field.stormSignal(x, z, ticks) - threshold(climate, ticks);
		double steady = Math.clamp(excess / EDGE, 0, 1);
		double shower = showers(field, climate, airMass, x, z, ticks);
		double precipitation = Math.max(steady, shower);
		double cloudCover = Math.max(smoothstep((excess + CLOUD_LEAD) / CLOUD_LEAD), shower);
		double thunder = Math.clamp((precipitation - THUNDER_FROM) / (1 - THUNDER_FROM), 0, 1);
		return new Sky(airMass, cloudCover, precipitation, thunder);
	}

	/** How strong a storm system must be before it rains here. Dry climates demand more. The cold half of the year asks for less. */
	private static double threshold(final BiomeClimate climate, final long ticks) {
		double dryness = 1 - climate.wetness();
		double byWetness = WETTEST_THRESHOLD + DRYNESS_PENALTY * dryness * dryness;
		return byWetness - WINTER_WETNESS * Math.cos(2 * Math.PI * (Calendar.yearFraction(ticks) - WETTEST_TIME_OF_YEAR));
	}

	/**
	 * Afternoon cloudbursts need heat and humidity, so they belong to warm wet places in the warm half of the year.
	 * Below zero means no shower, and how far below says how far off one is.
	 */
	private static double showers(final WeatherField field, final BiomeClimate climate, final double airMass, final double x, final double z, final long ticks) {
		double seaLevelTemperature = ClimateModel.openAir(climate, ticks, 0, airMass, 0);
		double heat = Math.clamp((seaLevelTemperature - SHOWER_HEAT_FROM) / (SHOWER_HEAT_FULL - SHOWER_HEAT_FROM), 0, 1);
		double afternoon = Math.max(0, 1 - Math.abs(Calendar.timeOfDay(ticks) - SHOWER_PEAK_TIME_OF_DAY) / (double)SHOWER_HALF_WIDTH);
		double potential = heat * afternoon * climate.wetness();
		return field.showerSignal(x, z, ticks) * potential * SHOWER_STRENGTH - SHOWER_THRESHOLD;
	}

	private static double smoothstep(final double x) {
		double t = Math.clamp(x, 0, 1);
		return t * t * (3 - 2 * t);
	}
}
