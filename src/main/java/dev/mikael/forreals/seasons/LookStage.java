package dev.mikael.forreals.seasons;

/**
 * How the plants of a place look at this point in its year. The place's own temperature decides, not the date, so a
 * mountain turns gold while the valley below is still green, and jungle never turns at all.
 */
public enum LookStage {
	SPRING(0x7CD650, 0.2, 0x6ECD46, 0.2),
	SUMMER(0, 0, 0, 0),
	FALL_GOLD(0xB8A054, 0.35, 0xDEA830, 0.75),
	FALL_RED(0xB8A054, 0.35, 0xC45424, 0.75),
	WINTER(0x8E8668, 0.55, 0x7C684E, 0.7);

	private static final double SPRING_FROM = 4;
	private static final double SUMMER_FROM = 12;
	private static final double FALL_BELOW = 14;
	private static final double WINTER_BELOW = 6;
	private static final double RED_SHARE = 0.4;

	private final int grassTint;
	private final double grassStrength;
	private final int foliageTint;
	private final double foliageStrength;

	LookStage(final int grassTint, final double grassStrength, final int foliageTint, final double foliageStrength) {
		this.grassTint = grassTint;
		this.grassStrength = grassStrength;
		this.foliageTint = foliageTint;
		this.foliageStrength = foliageStrength;
	}

	/**
	 * @param typicalTemperature the usual temperature here for this time of year, in Celsius
	 * @param warming true in the half of the year that is getting warmer
	 * @param variety a fixed number from 0 to 1 for this spot, so neighboring trees turn different colors
	 */
	public static LookStage at(final double typicalTemperature, final boolean warming, final double variety) {
		if (warming) {
			return typicalTemperature < SPRING_FROM ? WINTER : typicalTemperature < SUMMER_FROM ? SPRING : SUMMER;
		} else if (typicalTemperature >= FALL_BELOW) {
			return SUMMER;
		} else if (typicalTemperature < WINTER_BELOW) {
			return WINTER;
		}

		return variety < RED_SHARE ? FALL_RED : FALL_GOLD;
	}

	/** @param seasonality 0 for a place with no seasons, 1 for one with full seasons */
	public int grass(final int summerColor, final double seasonality) {
		return blend(summerColor, this.grassTint, this.grassStrength * seasonality);
	}

	public int foliage(final int summerColor, final double seasonality) {
		return blend(summerColor, this.foliageTint, this.foliageStrength * seasonality);
	}

	private static int blend(final int from, final int to, final double share) {
		int red = channel(from >> 16, to >> 16, share);
		int green = channel(from >> 8, to >> 8, share);
		int blue = channel(from, to, share);
		return red << 16 | green << 8 | blue;
	}

	private static int channel(final int from, final int to, final double share) {
		return (int)Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * share);
	}
}
