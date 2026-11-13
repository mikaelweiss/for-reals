package dev.mikael.forreals.animals;

import java.util.Comparator;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A predator hunts only when it is hungry, and stops when it has eaten. A starving wolf will hunt a player, unless
 * the player is beside a fire.
 */
final class HuntGoal extends Goal {
	private static final float HUNTS_BELOW = 0.5F;
	private static final float FULL_AT = 0.9F;
	private static final float DESPERATE_BELOW = 0.2F;
	private static final int LOOK_EVERY_TICKS = 20;
	private static final double SIGHT = 24;
	private static final double PLAYER_SIGHT = 20;
	private static final int FIRE_REACH = 6;

	private final Mob predator;
	private final Species species;
	private int lookAgainAt;

	HuntGoal(final Mob predator, final Species species) {
		this.predator = predator;
		this.species = species;
		this.setFlags(EnumSet.of(Flag.TARGET));
	}

	@Override
	public boolean canUse() {
		// Goals are only asked this every other tick, so the wait is counted from the last look, not by tick number.
		if (Herds.energyOf(this.predator) >= HUNTS_BELOW || this.predator.tickCount < this.lookAgainAt) {
			return false;
		}

		this.lookAgainAt = this.predator.tickCount + LOOK_EVERY_TICKS;
		return this.predator.getTarget() == null && this.findPrey() != null;
	}

	private LivingEntity findPrey() {
		LivingEntity prey = this.predator.level()
			.getEntitiesOfClass(LivingEntity.class, this.predator.getBoundingBox().inflate(SIGHT), this::isPrey)
			.stream()
			.min(Comparator.comparingDouble(this.predator::distanceToSqr))
			.orElse(null);
		if (prey == null && this.species == Species.WOLF && Herds.energyOf(this.predator) < DESPERATE_BELOW) {
			return this.predator.level()
				.getNearestPlayer(
					this.predator.getX(),
					this.predator.getY(),
					this.predator.getZ(),
					PLAYER_SIGHT,
					entity -> entity instanceof Player player && !player.isCreative() && !player.isSpectator() && !isBesideFire(player)
				);
		}

		return prey;
	}

	private boolean isPrey(final LivingEntity other) {
		Species theirs = SpeciesTypes.speciesOf(other.getType());
		return other.isAlive() && theirs != null && this.species.prey().contains(theirs) && !(other instanceof TamableAnimal pet && pet.isTame());
	}

	static boolean isBesideFire(final LivingEntity living) {
		for (BlockPos pos : BlockPos.withinClippedManhattan(living.blockPosition(), FIRE_REACH, 2, FIRE_REACH)) {
			BlockState state = living.level().getBlockState(pos);
			if (state.is(BlockTags.FIRE) || state.is(BlockTags.CAMPFIRES) && state.getValue(CampfireBlock.LIT)) {
				return true;
			}
		}

		return false;
	}

	@Override
	public void start() {
		this.predator.setTarget(this.findPrey());
	}

	@Override
	public boolean canContinueToUse() {
		LivingEntity target = this.predator.getTarget();
		return target != null
			&& target.isAlive()
			&& Herds.energyOf(this.predator) < FULL_AT
			&& !(target instanceof Player player && (player.isCreative() || isBesideFire(player)));
	}

	@Override
	public void stop() {
		this.predator.setTarget(null);
	}
}
