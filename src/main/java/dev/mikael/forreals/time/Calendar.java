package dev.mikael.forreals.time;

/**
 * The year starts on the spring equinox. Ticks count from that moment.
 */
public final class Calendar {
	public static final int TICKS_PER_DAY = 24000;
	public static final int DAYS_PER_SEASON = 12;
	public static final int DAYS_PER_YEAR = DAYS_PER_SEASON * Season.values().length;
	public static final long TICKS_PER_YEAR = (long)TICKS_PER_DAY * DAYS_PER_YEAR;

	private static final int SUNSET = TICKS_PER_DAY / 2;
	private static final double DAYLIGHT_SWING = 0.125;

	private Calendar() {
	}

	public static double yearFraction(final long ticks) {
		return (double)Math.floorMod(ticks, TICKS_PER_YEAR) / TICKS_PER_YEAR;
	}

	public static int dayOfYear(final long ticks) {
		return (int)(Math.floorMod(ticks, TICKS_PER_YEAR) / TICKS_PER_DAY);
	}

	public static int timeOfDay(final long ticks) {
		return (int)Math.floorMod(ticks, (long)TICKS_PER_DAY);
	}

	public static Season season(final long ticks) {
		return Season.values()[dayOfYear(ticks) / DAYS_PER_SEASON];
	}

	/** Share of a full day that the sun is up. Longest on the summer solstice, a quarter into the year. */
	public static double daylightShare(final long ticks) {
		return 0.5 + DAYLIGHT_SWING * Math.sin(2 * Math.PI * yearFraction(ticks));
	}

	/**
	 * How fast the clock runs. The sun is up for the first half of the clock's day, so a slow first half is a long
	 * day. Both halves together always take one full day of real ticks.
	 */
	public static float clockRate(final long ticks) {
		double share = daylightShare(ticks);
		return (float)(0.5 / (timeOfDay(ticks) < SUNSET ? share : 1 - share));
	}
}
