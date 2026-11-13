package dev.mikael.forreals.body;

/**
 * How far a body has drifted from comfortable. Exposure runs from {@link #COLDEST} to {@link #HOTTEST} with 0 as
 * comfortable. Air outside the comfortable range pushes it out. Comfortable air brings it back.
 */
public final class BodyHeat {
	public static final double COLDEST = -100;
	public static final double HOTTEST = 100;

	private static final double BARE_COMFORT_LOW = 5;
	private static final double BARE_COMFORT_HIGH = 32;
	private static final double WARM_PIECE_DEGREES = 5;
	private static final double OTHER_PIECE_DEGREES = 2;
	private static final double EXPOSURE_PER_DEGREE_SECOND = 0.05;
	private static final double RECOVERY_PER_SECOND = 3;

	private BodyHeat() {
	}

	/**
	 * @param warmPieces worn pieces of warm clothing, such as leather
	 * @param otherPieces worn pieces of any other armor
	 */
	public record Clothing(int warmPieces, int otherPieces) {
		public static final Clothing NONE = new Clothing(0, 0);

		/** Coldest air this outfit stays comfortable in. */
		double comfortLow() {
			return BARE_COMFORT_LOW - this.warmPieces * WARM_PIECE_DEGREES - this.otherPieces * OTHER_PIECE_DEGREES;
		}

		/** Hottest air this outfit stays comfortable in. Everything worn makes heat harder to bear. */
		double comfortHigh() {
			return BARE_COMFORT_HIGH - this.warmPieces - this.otherPieces * 1.5;
		}
	}

	/** Ordered from coldest to hottest. */
	public enum Stage {
		HYPOTHERMIA("You are freezing to death!"),
		FREEZING("You are freezing. Get to a fire."),
		COLD("You are cold. Find fire or shelter."),
		CHILLY("You feel a chill."),
		COMFORTABLE("You feel comfortable again."),
		WARM("You feel warm."),
		HOT("You are hot. Find shade or water."),
		OVERHEATING("You are overheating. Get out of the heat."),
		HEATSTROKE("The heat is killing you!");

		private final String note;

		Stage(final String note) {
			this.note = note;
		}

		public String note() {
			return this.note;
		}

		public boolean isColdAtLeast(final Stage stage) {
			return this.compareTo(stage) <= 0;
		}

		public boolean isHotAtLeast(final Stage stage) {
			return this.compareTo(stage) >= 0;
		}

		public boolean isWorseThan(final Stage other) {
			return Math.abs(this.ordinal() - COMFORTABLE.ordinal()) > Math.abs(other.ordinal() - COMFORTABLE.ordinal());
		}
	}

	public static Stage stage(final double exposure) {
		if (exposure <= COLDEST) {
			return Stage.HYPOTHERMIA;
		} else if (exposure >= HOTTEST) {
			return Stage.HEATSTROKE;
		}

		int quartersFromComfort = (int)(Math.abs(exposure) / 25);
		return Stage.values()[Stage.COMFORTABLE.ordinal() + (int)Math.signum(exposure) * quartersFromComfort];
	}

	public static double next(final double exposure, final double feltTemperature, final Clothing clothing, final double seconds) {
		double change;
		if (feltTemperature < clothing.comfortLow()) {
			change = -(clothing.comfortLow() - feltTemperature) * EXPOSURE_PER_DEGREE_SECOND;
		} else if (feltTemperature > clothing.comfortHigh()) {
			change = (feltTemperature - clothing.comfortHigh()) * EXPOSURE_PER_DEGREE_SECOND;
		} else {
			change = -Math.signum(exposure) * Math.min(Math.abs(exposure) / seconds, RECOVERY_PER_SECOND);
		}

		return Math.clamp(exposure + change * seconds, COLDEST, HOTTEST);
	}
}
