package dev.mikael.forreals.animals;

import java.util.EnumSet;
import java.util.Set;

/**
 * The animals that live in herds. Food is counted in one unit: what a cow eats in a day. A body is worth that many
 * units to whatever eats it.
 */
public enum Species {
	COW(4, 8, 1.0, 8, 0.6, 5, 10),
	SHEEP(4, 8, 0.6, 4, 0.7, 4, 10),
	HORSE(3, 6, 1.0, 8, 0.4, 7, 14),
	DONKEY(2, 4, 0.8, 6, 0.4, 7, 12),
	PIG(3, 6, 0.6, 4, 1.0, 4, 8),
	GOAT(3, 6, 0.5, 3, 0.7, 4, 10),
	LLAMA(3, 6, 0.8, 5, 0.5, 5, 10),
	RABBIT(2, 5, 0.1, 0.5, 2.0, 2, 12),
	CHICKEN(3, 6, 0.1, 0.5, 1.5, 2, 8),
	WOLF(2, 6, 0.5, 2, 0.6, 4, 0),
	FOX(1, 3, 0.15, 1, 0.6, 3, 10);

	private final int smallestHerd;
	private final int largestHerd;
	private final double dailyFood;
	private final double bodyFood;
	private final double youngPerAdultPerSpring;
	private final double lifespanYears;
	private final double flightDistance;

	Species(
		final int smallestHerd,
		final int largestHerd,
		final double dailyFood,
		final double bodyFood,
		final double youngPerAdultPerSpring,
		final double lifespanYears,
		final double flightDistance
	) {
		this.smallestHerd = smallestHerd;
		this.largestHerd = largestHerd;
		this.dailyFood = dailyFood;
		this.bodyFood = bodyFood;
		this.youngPerAdultPerSpring = youngPerAdultPerSpring;
		this.lifespanYears = lifespanYears;
		this.flightDistance = flightDistance;
	}

	public boolean isPredator() {
		return this == WOLF || this == FOX;
	}

	/** What this predator hunts. Empty for grazers. */
	public Set<Species> prey() {
		return switch (this) {
			case WOLF -> EnumSet.of(SHEEP, GOAT, PIG, COW, HORSE, DONKEY, LLAMA, RABBIT, CHICKEN);
			case FOX -> EnumSet.of(RABBIT, CHICKEN);
			default -> EnumSet.noneOf(Species.class);
		};
	}

	public int smallestHerd() {
		return this.smallestHerd;
	}

	public int largestHerd() {
		return this.largestHerd;
	}

	public double dailyFood() {
		return this.dailyFood;
	}

	public double bodyFood() {
		return this.bodyFood;
	}

	public double youngPerAdultPerSpring() {
		return this.youngPerAdultPerSpring;
	}

	public double lifespanYears() {
		return this.lifespanYears;
	}

	/** Blocks at which a calm animal of this kind runs from a threat. 0 for one that never runs. */
	public double flightDistance() {
		return this.flightDistance;
	}
}
