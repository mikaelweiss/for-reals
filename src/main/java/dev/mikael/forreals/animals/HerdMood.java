package dev.mikael.forreals.animals;

import net.minecraft.world.phys.Vec3;

/** What a walking herd is doing as a group right now. Lost when the herd folds back into the ledger. */
final class HerdMood {
	/** Where the herd is heading to find food, or null while it is content where it is. */
	Vec3 heading;
	long headingExpires;
	/** True shortly before and during a storm. The herd pulls in tight. */
	boolean bunching;
	Vec3 alarmedBy;
	long alarmedUntil;

	boolean isAlarmed(final long gameTime) {
		return this.alarmedBy != null && gameTime < this.alarmedUntil;
	}
}
