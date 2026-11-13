package dev.mikael.forreals.animals;

import java.util.EnumSet;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/** Keeps an animal near the middle of its herd, or near where the herd is heading. Before a storm it closes right in. */
final class StayWithHerdGoal extends Goal {
	private static final double LOOSE = 10;
	private static final double TIGHT = 3;

	private final PathfinderMob animal;

	StayWithHerdGoal(final PathfinderMob animal) {
		this.animal = animal;
		this.setFlags(EnumSet.of(Flag.MOVE));
	}

	private double leash(final Herd herd) {
		return Herds.mood(herd).bunching ? TIGHT : LOOSE;
	}

	private static Vec3 anchor(final Herd herd) {
		Vec3 heading = Herds.mood(herd).heading;
		return heading != null ? heading : new Vec3(herd.x(), 0, herd.z());
	}

	private double distanceFromAnchor(final Herd herd) {
		Vec3 anchor = anchor(herd);
		return Math.hypot(this.animal.getX() - anchor.x, this.animal.getZ() - anchor.z);
	}

	@Override
	public boolean canUse() {
		Herd herd = Herds.herdOf(this.animal);
		return herd != null && this.distanceFromAnchor(herd) > this.leash(herd);
	}

	@Override
	public void start() {
		Vec3 anchor = anchor(Herds.herdOf(this.animal));
		this.animal.getNavigation().moveTo(anchor.x, this.animal.getY(), anchor.z, 1.1);
	}

	@Override
	public boolean canContinueToUse() {
		Herd herd = Herds.herdOf(this.animal);
		return herd != null && !this.animal.getNavigation().isDone() && this.distanceFromAnchor(herd) > this.leash(herd) / 2;
	}
}
