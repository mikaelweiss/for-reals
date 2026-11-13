package dev.mikael.forreals.plants;

/**
 * The temperatures a kind of plant grows in, in Celsius. Growth is full between the two best marks and fades to
 * nothing at the limits.
 */
public enum GrowingRange {
	COOL_CROP(2, 10, 24, 32, true),
	WARM_CROP(8, 18, 30, 40, true),
	TROPICAL(12, 22, 38, 48, false),
	TROPICAL_FRUIT(12, 22, 38, 48, true),
	HARDY(-2, 6, 24, 34, false),
	TREE(2, 10, 30, 40, false);

	public static final double HARD_FROST = -3;

	private final double coldLimit;
	private final double bestFrom;
	private final double bestTo;
	private final double hotLimit;
	private final boolean tender;

	GrowingRange(final double coldLimit, final double bestFrom, final double bestTo, final double hotLimit, final boolean tender) {
		this.coldLimit = coldLimit;
		this.bestFrom = bestFrom;
		this.bestTo = bestTo;
		this.hotLimit = hotLimit;
		this.tender = tender;
	}

	/** Share of normal growth at this temperature, 0 to 1. */
	public double growthAt(final double temperature) {
		if (temperature <= this.coldLimit || temperature >= this.hotLimit) {
			return 0;
		} else if (temperature < this.bestFrom) {
			return (temperature - this.coldLimit) / (this.bestFrom - this.coldLimit);
		} else if (temperature > this.bestTo) {
			return (this.hotLimit - temperature) / (this.hotLimit - this.bestTo);
		}

		return 1;
	}

	/** A tender plant dies in a hard frost. A hardy one only stops growing. */
	public boolean diesAt(final double temperature) {
		return this.tender && temperature < HARD_FROST;
	}
}
