package dev.mikael.forreals.animals;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * A wild animal runs from players and from the predators that hunt its kind. One animal seeing a threat alarms the
 * whole herd. A herd that has lost members to hunters runs sooner.
 */
final class FleeGoal extends Goal {
	private static final int LOOK_EVERY_TICKS = 10;
	private static final int ALARM_TICKS = 100;
	private static final double ALARM_REACH = 32;
	private static final double SNEAKING_SHARE = 0.5;
	private static final double SPEED = 1.8;

	private final PathfinderMob animal;
	private final Species species;
	private int lookAgainAt;

	FleeGoal(final PathfinderMob animal, final Species species) {
		this.animal = animal;
		this.species = species;
		this.setFlags(EnumSet.of(Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		Herd herd = Herds.herdOf(this.animal);
		if (herd == null) {
			return false;
		}

		HerdMood mood = Herds.mood(herd);
		long now = this.animal.level().getGameTime();
		if (this.animal.tickCount >= this.lookAgainAt) {
			this.lookAgainAt = this.animal.tickCount + LOOK_EVERY_TICKS;
			LivingEntity threat = this.threat(this.species.flightDistance() * (1 + 2 * herd.fear()));
			if (threat != null) {
				mood.alarmedBy = threat.position();
				mood.alarmedUntil = now + ALARM_TICKS;
			}
		}

		return mood.isAlarmed(now) && this.animal.position().distanceTo(mood.alarmedBy) < ALARM_REACH;
	}

	private LivingEntity threat(final double distance) {
		Player player = this.animal.level()
			.getNearestPlayer(
				this.animal.getX(),
				this.animal.getY(),
				this.animal.getZ(),
				distance,
				entity -> entity instanceof Player seen
					&& !seen.isCreative()
					&& !seen.isSpectator()
					&& (!seen.isDiscrete() || seen.distanceTo(this.animal) < distance * SNEAKING_SHARE)
			);
		if (player != null) {
			return player;
		}

		return this.animal.level()
			.getEntitiesOfClass(Mob.class, this.animal.getBoundingBox().inflate(distance), this::hunts)
			.stream()
			.findFirst()
			.orElse(null);
	}

	private boolean hunts(final Mob other) {
		Species theirs = SpeciesTypes.speciesOf(other.getType());
		return theirs != null && theirs.prey().contains(this.species) && !(other instanceof TamableAnimal pet && pet.isTame());
	}

	@Override
	public void start() {
		this.runFrom(Herds.mood(Herds.herdOf(this.animal)).alarmedBy);
	}

	@Override
	public void tick() {
		Herd herd = Herds.herdOf(this.animal);
		if (herd != null && this.animal.getNavigation().isDone()) {
			this.runFrom(Herds.mood(herd).alarmedBy);
		}
	}

	private void runFrom(final Vec3 threat) {
		Vec3 away = DefaultRandomPos.getPosAway(this.animal, 16, 7, threat);
		if (away != null) {
			this.animal.getNavigation().moveTo(away.x, away.y, away.z, SPEED);
		}
	}

	@Override
	public boolean canContinueToUse() {
		Herd herd = Herds.herdOf(this.animal);
		return herd != null && Herds.mood(herd).isAlarmed(this.animal.level().getGameTime());
	}
}
