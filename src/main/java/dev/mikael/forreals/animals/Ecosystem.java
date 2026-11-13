package dev.mikael.forreals.animals;

import dev.mikael.forreals.time.Calendar;
import dev.mikael.forreals.time.Season;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

/**
 * Every herd and every range of grazing land, and the rules that keep them in balance: grass grows, grazers eat it
 * and breed, predators eat grazers, and all of them starve when there is not enough. It needs no loaded world.
 *
 * <p>Land that is not in the ledger is unexplored. It never runs out of animals, so emptied land next to it refills.
 */
public final class Ecosystem {
	public static final int HOUR_TICKS = Calendar.TICKS_PER_DAY / 24;

	private static final int HOURS_PER_DAY = 24;
	private static final double HOURS_PER_YEAR = HOURS_PER_DAY * Calendar.DAYS_PER_YEAR;
	private static final double HOURS_PER_SPRING = HOURS_PER_DAY * Calendar.DAYS_PER_SEASON;
	private static final double GRAZER_HUNGER_PER_UNFED_HOUR = 1.0 / 48;
	private static final double GRAZER_RECOVERY_PER_FED_HOUR = 1.0 / 12;
	private static final double PREDATOR_HUNGER_PER_HOUR = 1.0 / (4 * HOURS_PER_DAY);
	static final double DAYS_OF_FOOD_IN_A_FULL_BELLY = 4;
	private static final double STARVING_SHARE_LOST_PER_HOUR = 0.01;
	private static final double STARVING = 0.9;
	private static final double PREDATOR_STARVES_ONE_PER_HOURS = 6;
	private static final double TOO_HUNGRY_TO_BREED = 0.3;
	private static final double THIN_PASTURE = 0.25;
	private static final double RICH_PASTURE = 0.7;
	private static final double HUNTS_WHEN_HUNGRIER_THAN = 0.35;
	private static final double KILL_CHANCE_PER_HOUR = 0.12;
	private static final double FEAR_PER_KILL = 0.15;
	private static final double FEAR_KEPT_PER_HOUR = 1 - 1.0 / (7 * HOURS_PER_DAY);
	private static final double TRAVEL_BLOCKS_PER_HOUR = 24;
	private static final int HUNT_REACH_RANGES = 2;
	private static final int TRACK_REACH_RANGES = 4;
	private static final double WORTH_MOVING_FOR = 1.25;
	private static final double UNEXPLORED_LAND_SCORE = 5;
	private static final double WRONG_HABITAT = 0.1;
	private static final double CAPACITY_PER_SETTLED_HERD = 70;
	private static final double WOLVES_SETTLE_CHANCE = 0.2;
	private static final double FOXES_SETTLE_CHANCE = 0.3;
	private static final double GRAZERS_ARRIVE_CHANCE_PER_DAY = 0.3;
	private static final double PREDATORS_ARRIVE_CHANCE_PER_DAY = 0.1;
	private static final double LAND_WORTH_ARRIVING_FOR = 0.5;

	private final Map<Long, Range> ranges = new HashMap<>();
	private final List<Herd> herds = new ArrayList<>();
	private int nextHerdId;

	public Collection<Herd> herds() {
		return Collections.unmodifiableList(this.herds);
	}

	public Map<Long, Range> ranges() {
		return Collections.unmodifiableMap(this.ranges);
	}

	public Range range(final long key) {
		return this.ranges.get(key);
	}

	public Herd herd(final int id) {
		for (Herd herd : this.herds) {
			if (herd.id() == id) {
				return herd;
			}
		}

		return null;
	}

	public Herd addHerd(final Species species, final double x, final double z, final int count) {
		Herd herd = new Herd(this.nextHerdId++, species, x, z, count, 0, 0);
		this.herds.add(herd);
		return herd;
	}

	/** Puts back a range read from disk. */
	public void restoreRange(final long key, final Range range) {
		this.ranges.put(key, range);
	}

	/** Puts back a herd read from disk. */
	public void restoreHerd(final Herd herd) {
		this.herds.add(herd);
		this.nextHerdId = Math.max(this.nextHerdId, herd.id() + 1);
	}

	/** Brings a range into the ledger for the first time, with the herds that were living on it all along. */
	public void explore(final long key, final Range range, final Random random) {
		if (this.ranges.putIfAbsent(key, range) != null) {
			return;
		}

		List<Species> grazers = range.habitat().stream().filter(species -> !species.isPredator()).sorted().toList();
		int settled = grazers.isEmpty() ? 0 : (int)(range.capacity() / CAPACITY_PER_SETTLED_HERD + random.nextDouble());
		for (int i = 0; i < settled; i++) {
			this.settle(key, grazers.get(random.nextInt(grazers.size())), random);
		}

		if (range.habitat().contains(Species.WOLF) && random.nextDouble() < WOLVES_SETTLE_CHANCE) {
			this.settle(key, Species.WOLF, random);
		}

		if (range.habitat().contains(Species.FOX) && random.nextDouble() < FOXES_SETTLE_CHANCE) {
			this.settle(key, Species.FOX, random);
		}
	}

	private Herd settle(final long key, final Species species, final Random random) {
		int count = species.smallestHerd() + random.nextInt(species.largestHerd() - species.smallestHerd() + 1);
		double x = (Range.xOf(key) + 0.2 + 0.6 * random.nextDouble()) * Range.SIZE_BLOCKS;
		double z = (Range.zOf(key) + 0.2 + 0.6 * random.nextDouble()) * Range.SIZE_BLOCKS;
		return this.addHerd(species, x, z, count);
	}

	/**
	 * One hour of life for the whole ledger.
	 *
	 * @param livesInTheWorld herds whose animals are walking around a loaded world. Those animals eat, die, and breed
	 *     for themselves, so the ledger leaves them alone.
	 */
	public void advanceHour(final long ticks, final Random random, final Predicate<Herd> livesInTheWorld) {
		this.ranges.values().forEach(range -> range.growForAnHour(ticks));
		Map<Long, List<Herd>> herdsByRange = this.herdsByRange();
		for (Herd herd : List.copyOf(this.herds)) {
			if (herd.count() <= 0 || livesInTheWorld.test(herd)) {
				continue;
			}

			if (herd.species().isPredator()) {
				this.liveAsPredator(herd, herdsByRange, random, livesInTheWorld);
			} else {
				this.liveAsGrazer(herd, ticks, random);
			}

			this.breed(herd, ticks, random);
			this.growOld(herd, random);
			herd.setFear(herd.fear() * FEAR_KEPT_PER_HOUR);
			this.splitIfCrowded(herd, random);
		}

		// A herd that walks off the ledger has gone back to unexplored land.
		this.herds.removeIf(herd -> herd.count() <= 0 || !this.ranges.containsKey(herd.range()));
		if (Math.floorDiv(ticks, (long)HOUR_TICKS) % HOURS_PER_DAY == 0) {
			this.arriveFromUnexploredLand(random);
		}
	}

	private Map<Long, List<Herd>> herdsByRange() {
		Map<Long, List<Herd>> byRange = new HashMap<>();
		for (Herd herd : this.herds) {
			byRange.computeIfAbsent(herd.range(), key -> new ArrayList<>()).add(herd);
		}

		return byRange;
	}

	private void liveAsGrazer(final Herd herd, final long ticks, final Random random) {
		Range range = this.ranges.get(herd.range());
		if (range == null) {
			return;
		}

		double need = herd.count() * herd.species().dailyFood() / HOURS_PER_DAY;
		double eaten = Math.min(need * range.shareInReach(ticks), range.food());
		range.graze(eaten);

		// Hunger settles toward the share of appetite going unfed, so a lean winter leaves a herd hungry but alive.
		double unfed = 1 - eaten / need;
		double change = unfed - herd.hunger();
		herd.setHunger(herd.hunger() + Math.clamp(change, -GRAZER_RECOVERY_PER_FED_HOUR, GRAZER_HUNGER_PER_UNFED_HOUR));
		if (herd.hunger() >= STARVING) {
			herd.addMembers(-happens(herd.count() * STARVING_SHARE_LOST_PER_HOUR, random));
		}

		if (unfed > 0.2) {
			this.seekBetterLand(herd, ticks);
		}
	}

	/** A hungry herd walks toward whichever neighboring range offers clearly more food than the one it is on. */
	private void seekBetterLand(final Herd herd, final long ticks) {
		long target = this.betterLandFor(herd, ticks);
		if (target != herd.range()) {
			travelToward(herd, Range.center(Range.xOf(target)), Range.center(Range.zOf(target)));
		}
	}

	/** The neighboring range clearly worth leaving for, or the herd's own range if none is. */
	public long betterLandFor(final Herd herd, final long ticks) {
		long here = herd.range();
		double best = this.landScore(herd, here, ticks) * WORTH_MOVING_FOR;
		long target = here;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				long neighbor = Range.key(Range.xOf(here) + dx, Range.zOf(here) + dz);
				double score = this.landScore(herd, neighbor, ticks);
				if (score > best) {
					best = score;
					target = neighbor;
				}
			}
		}

		return target;
	}

	/** Only a starving herd gambles on land nobody has seen. */
	private double landScore(final Herd herd, final long key, final long ticks) {
		Range range = this.ranges.get(key);
		if (range == null) {
			return herd.hunger() >= STARVING ? UNEXPLORED_LAND_SCORE : 0;
		}

		return range.food() * range.shareInReach(ticks) * (range.habitat().contains(herd.species()) ? 1 : WRONG_HABITAT);
	}

	private static void travelToward(final Herd herd, final double x, final double z) {
		double dx = x - herd.x();
		double dz = z - herd.z();
		double distance = Math.sqrt(dx * dx + dz * dz);
		if (distance <= TRAVEL_BLOCKS_PER_HOUR) {
			herd.moveTo(x, z);
		} else {
			herd.moveTo(herd.x() + dx / distance * TRAVEL_BLOCKS_PER_HOUR, herd.z() + dz / distance * TRAVEL_BLOCKS_PER_HOUR);
		}
	}

	private void liveAsPredator(final Herd pack, final Map<Long, List<Herd>> herdsByRange, final Random random, final Predicate<Herd> livesInTheWorld) {
		pack.setHunger(pack.hunger() + PREDATOR_HUNGER_PER_HOUR);
		if (pack.hunger() >= 1 && random.nextDouble() < 1 / PREDATOR_STARVES_ONE_PER_HOURS) {
			pack.addMembers(-1);
		}

		if (pack.hunger() <= HUNTS_WHEN_HUNGRIER_THAN) {
			return;
		}

		Herd prey = nearestPrey(pack, herdsByRange, TRACK_REACH_RANGES, livesInTheWorld);
		if (prey == null) {
			double direction = random.nextDouble() * 2 * Math.PI;
			pack.moveTo(pack.x() + Math.cos(direction) * TRAVEL_BLOCKS_PER_HOUR, pack.z() + Math.sin(direction) * TRAVEL_BLOCKS_PER_HOUR);
			return;
		}

		travelToward(pack, prey.x(), prey.z());
		boolean inReach = Math.hypot(prey.x() - pack.x(), prey.z() - pack.z()) <= HUNT_REACH_RANGES * Range.SIZE_BLOCKS;
		if (inReach && random.nextDouble() < KILL_CHANCE_PER_HOUR * Math.min(1, pack.count() / 3.0)) {
			prey.addMembers(-1);
			prey.setFear(prey.fear() + FEAR_PER_KILL);
			pack.setHunger(pack.hunger() - prey.species().bodyFood() / (pack.count() * pack.species().dailyFood() * DAYS_OF_FOOD_IN_A_FULL_BELLY));
		}
	}

	private static Herd nearestPrey(final Herd pack, final Map<Long, List<Herd>> herdsByRange, final int reach, final Predicate<Herd> livesInTheWorld) {
		long here = pack.range();
		Herd nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				for (Herd herd : herdsByRange.getOrDefault(Range.key(Range.xOf(here) + dx, Range.zOf(here) + dz), List.of())) {
					double distance = Math.hypot(herd.x() - pack.x(), herd.z() - pack.z());
					if (herd.count() > 0 && distance < nearestDistance && pack.species().prey().contains(herd.species()) && !livesInTheWorld.test(herd)) {
						nearest = herd;
						nearestDistance = distance;
					}
				}
			}
		}

		return nearest;
	}

	/** Young are born through spring to herds that are eating well. Grazers on thin pasture bear fewer. */
	private void breed(final Herd herd, final long ticks, final Random random) {
		if (Calendar.season(ticks) != Season.SPRING || herd.hunger() >= TOO_HUNGRY_TO_BREED || herd.count() < 2) {
			return;
		}

		Range range = this.ranges.get(herd.range());
		double plenty = herd.species().isPredator() || range == null
			? 1
			: Math.clamp((range.forage() - THIN_PASTURE) / (RICH_PASTURE - THIN_PASTURE), 0, 1);
		herd.addMembers(happens(plenty * herd.count() * herd.species().youngPerAdultPerSpring() / HOURS_PER_SPRING, random));
	}

	private void growOld(final Herd herd, final Random random) {
		herd.addMembers(-happens(herd.count() / (herd.species().lifespanYears() * HOURS_PER_YEAR), random));
	}

	/** Turns an expected number of events into a whole number that averages out to it. */
	private static int happens(final double expected, final Random random) {
		int whole = (int)expected;
		return whole + (random.nextDouble() < expected - whole ? 1 : 0);
	}

	/** A herd that outgrows its kind's largest size splits, and the new half heads for other land. */
	private void splitIfCrowded(final Herd herd, final Random random) {
		if (herd.count() <= herd.species().largestHerd()) {
			return;
		}

		int leaving = herd.count() / 2;
		herd.addMembers(-leaving);
		double direction = random.nextDouble() * 2 * Math.PI;
		double distance = Range.SIZE_BLOCKS * (herd.species().isPredator() ? 2 : 0.75);
		Herd offshoot = this.addHerd(herd.species(), herd.x() + Math.cos(direction) * distance, herd.z() + Math.sin(direction) * distance, leaving);
		offshoot.setHunger(herd.hunger());
		offshoot.setFear(herd.fear());
	}

	/** Empty land that borders unexplored land draws new herds from it, and predators follow the herds. */
	private void arriveFromUnexploredLand(final Random random) {
		Map<Long, List<Herd>> herdsByRange = this.herdsByRange();
		for (Map.Entry<Long, Range> entry : List.copyOf(this.ranges.entrySet())) {
			long key = entry.getKey();
			Range range = entry.getValue();
			if (range.forage() < LAND_WORTH_ARRIVING_FOR || !this.bordersUnexploredLand(key)) {
				continue;
			}

			List<Species> grazers = range.habitat().stream().filter(species -> !species.isPredator()).sorted().toList();
			boolean grazed = herdsByRange.getOrDefault(key, List.of()).stream().anyMatch(herd -> !herd.species().isPredator());
			if (!grazers.isEmpty() && !grazed && random.nextDouble() < GRAZERS_ARRIVE_CHANCE_PER_DAY) {
				Species species = grazers.get(random.nextInt(grazers.size()));
				this.addHerd(species, Range.center(Range.xOf(key)), Range.center(Range.zOf(key)), species.smallestHerd());
			}

			List<Species> predators = range.habitat().stream().filter(Species::isPredator).sorted().toList();
			if (!predators.isEmpty()
				&& countNear(herdsByRange, key, 1, herd -> !herd.species().isPredator()) >= 2
				&& countNear(herdsByRange, key, 2, herd -> herd.species().isPredator()) == 0
				&& random.nextDouble() < PREDATORS_ARRIVE_CHANCE_PER_DAY) {
				Species species = predators.get(random.nextInt(predators.size()));
				this.addHerd(species, Range.center(Range.xOf(key)), Range.center(Range.zOf(key)), Math.max(2, species.smallestHerd()));
			}
		}
	}

	private boolean bordersUnexploredLand(final long key) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (!this.ranges.containsKey(Range.key(Range.xOf(key) + dx, Range.zOf(key) + dz))) {
					return true;
				}
			}
		}

		return false;
	}

	private static int countNear(final Map<Long, List<Herd>> herdsByRange, final long key, final int reach, final Predicate<Herd> matches) {
		int found = 0;
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				for (Herd herd : herdsByRange.getOrDefault(Range.key(Range.xOf(key) + dx, Range.zOf(key) + dz), List.of())) {
					found += matches.test(herd) ? 1 : 0;
				}
			}
		}

		return found;
	}
}
