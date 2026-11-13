package dev.mikael.forreals.animals;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;

/** A hungry grazer walks to the nearest thing it can eat and eats it. With nothing in reach it crops the turf it stands on. */
final class GrazeGoal extends Goal {
	private static final float HUNGRY_BELOW = 0.8F;
	private static final int REACH = 6;
	private static final int LOOK_AGAIN_TICKS = 80;
	private static final int GIVE_UP_TICKS = 200;
	private static final double CLOSE_ENOUGH_SQUARED = 4;

	private final PathfinderMob animal;
	private BlockPos food;
	private int lookAgainAt;
	private int giveUpAt;

	GrazeGoal(final PathfinderMob animal) {
		this.animal = animal;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (Herds.energyOf(this.animal) >= HUNGRY_BELOW || this.animal.tickCount < this.lookAgainAt) {
			return false;
		}

		this.lookAgainAt = this.animal.tickCount + LOOK_AGAIN_TICKS + this.animal.getRandom().nextInt(LOOK_AGAIN_TICKS);
		BlockPos here = this.animal.blockPosition();
		for (BlockPos pos : BlockPos.withinClippedManhattan(here, REACH, 2, REACH)) {
			if (Grazing.isWorthWalkingTo(this.animal.level().getBlockState(pos))) {
				this.food = pos.immutable();
				return true;
			}
		}

		this.food = here.below();
		return Grazing.foodIn(this.animal.level().getBlockState(this.food)) > 0;
	}

	@Override
	public void start() {
		this.giveUpAt = this.animal.tickCount + GIVE_UP_TICKS;
		this.animal.getNavigation().moveTo(this.food.getX() + 0.5, this.food.getY(), this.food.getZ() + 0.5, 1);
	}

	@Override
	public boolean canContinueToUse() {
		return this.food != null && this.animal.tickCount < this.giveUpAt && Grazing.foodIn(this.animal.level().getBlockState(this.food)) > 0;
	}

	@Override
	public void tick() {
		this.animal.getLookControl().setLookAt(this.food.getX() + 0.5, this.food.getY(), this.food.getZ() + 0.5);
		if (this.animal.distanceToSqr(this.food.getX() + 0.5, this.food.getY(), this.food.getZ() + 0.5) <= CLOSE_ENOUGH_SQUARED) {
			Grazing.eat(this.animal, this.food);
			this.food = null;
		}
	}

	@Override
	public void stop() {
		this.food = null;
		this.animal.getNavigation().stop();
	}
}
