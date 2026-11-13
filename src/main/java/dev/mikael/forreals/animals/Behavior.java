package dev.mikael.forreals.animals;

import dev.mikael.forreals.time.Calendar;
import dev.mikael.forreals.time.Season;
import dev.mikael.forreals.time.WorldTime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NonTameRandomTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariants;

/** How herd animals and owned animals act and stay alive while they walk the world. */
final class Behavior {
	private static final double SECONDS_PER_DAY = Calendar.TICKS_PER_DAY / 20.0;
	private static final int STARVING_HURTS_EVERY_SECONDS = 10;
	private static final float WELL_FED = 0.7F;
	private static final double SECONDS_PER_SPRING = Calendar.DAYS_PER_SEASON * SECONDS_PER_DAY;
	private static final float PACK_GROWS_RESTLESS_BELOW = 0.5F;
	private static final int WARNING_ODDS_PER_SECOND = 40;
	private static final double WARNING_REACHES_PLAYERS_WITHIN = 48;
	private static final float WARNING_VOLUME = 4;
	private static final float WARNING_PITCH = 0.6F;

	private Behavior() {
	}

	/** Gives an animal the goals of its kind. Owned animals graze but neither flee, follow a herd, nor hunt. */
	static void install(final Mob animal, final Species species, final boolean wild) {
		if (!(animal instanceof PathfinderMob walker)) {
			return;
		}

		if (!species.isPredator()) {
			walker.goalSelector.addGoal(4, new GrazeGoal(walker));
		}

		if (!wild) {
			return;
		}

		if (species.flightDistance() > 0) {
			walker.goalSelector.addGoal(1, new FleeGoal(walker, species));
		}

		if (species.largestHerd() > 1) {
			walker.goalSelector.addGoal(5, new StayWithHerdGoal(walker));
		}

		if (species.isPredator()) {
			// Vanilla predators attack prey at random. Here hunger decides.
			walker.targetSelector.removeAllGoals(goal -> goal instanceof NonTameRandomTargetGoal || species == Species.FOX && goal instanceof NearestAttackableTargetGoal);
			walker.targetSelector.addGoal(3, new HuntGoal(walker, species));
		}
	}

	static void becomeOwned(final Mob animal) {
		animal.goalSelector.removeAllGoals(goal -> goal instanceof FleeGoal || goal instanceof StayWithHerdGoal);
		animal.targetSelector.removeAllGoals(goal -> goal instanceof HuntGoal);
	}

	/**
	 * One second of an animal's life: it gets hungrier, starves if it has nothing left, and in spring a well-fed adult
	 * looks for a mate.
	 *
	 * @param herd the animal's herd, or null for an owned animal
	 */
	static void liveASecond(final Mob animal, final Species species, final Herd herd) {
		ServerLevel level = (ServerLevel)animal.level();
		double daysInABelly = species.isPredator() ? Ecosystem.DAYS_OF_FOOD_IN_A_FULL_BELLY : Grazing.DAYS_IN_A_FULL_BELLY;
		float energy = Herds.energyOf(animal) - (float)(WorldTime.speedUp() / (daysInABelly * SECONDS_PER_DAY));
		Herds.setEnergy(animal, energy);
		long second = level.getGameTime() / 20;
		if (energy <= 0 && second % STARVING_HURTS_EVERY_SECONDS == 0) {
			animal.hurtServer(level, level.damageSources().starve(), 1);
		}

		boolean roomToGrow = herd == null || herd.count() < 2 * species.largestHerd();
		if (animal instanceof Animal adult
			&& energy > WELL_FED
			&& roomToGrow
			&& Calendar.season(WorldTime.ticks(level.getServer())) == Season.SPRING
			&& adult.getAge() == 0
			&& adult.canFallInLove()
			&& animal.getRandom().nextDouble() < WorldTime.speedUp() * 2 * species.youngPerAdultPerSpring() / SECONDS_PER_SPRING) {
			adult.setInLove(null);
		}

		if (herd != null && species == Species.WOLF && energy < PACK_GROWS_RESTLESS_BELOW) {
			warnOfHungryWolves(animal, level);
		}

		// Only wild animals die of old age. An owned animal gives no sign of its age, so losing one to it would be unfair.
		double secondsInALife = species.lifespanYears() * Calendar.DAYS_PER_YEAR * SECONDS_PER_DAY;
		if (herd != null && animal.getRandom().nextDouble() < WorldTime.speedUp() / secondsInALife) {
			animal.kill(level);
		}
	}

	/** A hungry pack is heard before it is seen. The game has no howl, so it is a deep bark that carries. */
	private static void warnOfHungryWolves(final Mob wolf, final ServerLevel level) {
		if (level.isDarkOutside()
			&& wolf.getRandom().nextInt(WARNING_ODDS_PER_SECOND) == 0
			&& level.getNearestPlayer(wolf, WARNING_REACHES_PLAYERS_WITHIN) != null) {
			level.playSound(
				null,
				wolf.blockPosition(),
				SoundEvents.WOLF_SOUNDS.get(WolfSoundVariants.SoundSet.CLASSIC).adultSounds().ambientSound().value(),
				SoundSource.NEUTRAL,
				WARNING_VOLUME,
				WARNING_PITCH
			);
		}
	}
}
