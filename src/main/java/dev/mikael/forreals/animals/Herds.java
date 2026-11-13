package dev.mikael.forreals.animals;

import com.mojang.serialization.Codec;
import dev.mikael.forreals.ForReals;
import dev.mikael.forreals.Switch;
import dev.mikael.forreals.climate.Climate;
import dev.mikael.forreals.time.WorldTime;
import dev.mikael.forreals.weather.Sky;
import dev.mikael.forreals.weather.Weather;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Joins the herd ledger to the running world. A herd whose land is loaded walks around as real animals. Those animals
 * are never written to disk: when their land unloads they fold back into the ledger, and it brings them out again.
 */
public final class Herds {
	static final AttachmentType<Integer> HERD = AttachmentRegistry.create(Identifier.fromNamespaceAndPath(ForReals.MOD_ID, "herd"));
	static final AttachmentType<Float> ENERGY = AttachmentRegistry.createPersistent(Identifier.fromNamespaceAndPath(ForReals.MOD_ID, "energy"), Codec.FLOAT);

	private static final int MOST_HOURS_PER_TICK = 24;
	private static final int HEAD_COUNT_TICKS = 40;
	private static final int BIOME_PROBE_ABOVE_SEA = 128;
	private static final double NEW_LAND_FORAGE = 0.8;
	private static final double SPREAD_BLOCKS = 6;
	private static final int PLACEMENT_TRIES = 8;
	private static final double NUDGE_BLOCKS = 16;
	private static final double FEAR_PER_MEMBER_KILLED_BY_A_PLAYER = 0.25;

	private static final int ADOPTS_INTO_HERDS_WITHIN = 48;
	private static final float HERD_MOVES_ON_WHEN_HUNGRIER_THAN = 0.35F;
	private static final double HEADING_BLOCKS = 24;
	private static final double ARRIVED_WITHIN = 6;
	private static final int HEADING_LASTS_TICKS = 1200;
	private static final int STORM_WARNING_TICKS = 3600;
	private static final double STORM_FROM = 0.4;
	private static final double SHARES_A_KILL_WITHIN = 16;

	private static final Map<Integer, Set<Mob>> WALKING = new HashMap<>();
	private static final Map<Integer, HerdMood> MOODS = new HashMap<>();

	private Herds() {
	}

	public static void forgetWorld() {
		WALKING.clear();
		MOODS.clear();
		Owned.forgetWorld();
	}

	static HerdMood mood(final Herd herd) {
		return MOODS.computeIfAbsent(herd.id(), id -> new HerdMood());
	}

	public static Ecosystem ecosystem(final MinecraftServer server) {
		return ledger(server).ecosystem();
	}

	private static HerdLedger ledger(final MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(HerdLedger.TYPE);
	}

	/** The herd a wild animal belongs to, or null for any other entity. */
	public static Herd herdOf(final Entity entity) {
		Integer id = entity.getAttached(HERD);
		return id == null ? null : ecosystem(entity.level().getServer()).herd(id);
	}

	public static boolean isWild(final Entity entity) {
		return entity.hasAttached(HERD);
	}

	public static boolean walksTheWorld(final Herd herd) {
		return WALKING.containsKey(herd.id());
	}

	public static Set<Mob> members(final Herd herd) {
		return WALKING.getOrDefault(herd.id(), Set.of());
	}

	public static void tick(final MinecraftServer server) {
		if (!Switch.isOn() || WorldTime.isPaused(server)) {
			return;
		}

		HerdLedger ledger = ledger(server);
		long seed = server.overworld().getSeed();
		for (int owed = ledger.hoursOwed(Math.floorDiv(WorldTime.ticks(server), (long)Ecosystem.HOUR_TICKS), MOST_HOURS_PER_TICK); owed > 0; owed--) {
			long hour = ledger.nextHour();
			ledger.ecosystem().advanceHour(hour * Ecosystem.HOUR_TICKS, new Random(seed ^ hour), Herds::walksTheWorld);
		}

		ServerLevel level = server.overworld();
		if (server.getTickCount() % 20 == 0) {
			liveASecond(ledger.ecosystem());
		}

		if (server.getTickCount() % HEAD_COUNT_TICKS == 0) {
			followWalkingHerds(level, ledger.ecosystem());
			if (level.getGameRules().get(GameRules.SPAWN_MOBS)) {
				bringHerdsIntoTheWorld(level);
			}
		}
	}

	private static void liveASecond(final Ecosystem ecosystem) {
		for (Herd herd : ecosystem.herds()) {
			for (Mob member : List.copyOf(members(herd))) {
				if (Owned.showsOwnership(member)) {
					Owned.claim(member);
				} else {
					Behavior.liveASecond(member, herd.species(), herd);
				}
			}
		}

		for (Mob animal : Owned.nearby()) {
			Species species = SpeciesTypes.speciesOf(animal.getType());
			if (species != null) {
				Owned.liveASecond(animal, species);
			}
		}
	}

	/** The first chunk to load on a range brings that range, and the herds living on it, into the ledger. */
	public static void chunkLoaded(final ServerLevel level, final LevelChunk chunk) {
		if (!Climate.hasClimate(level)) {
			return;
		}

		Ecosystem ecosystem = ecosystem(level.getServer());
		long key = Range.keyAt(chunk.getPos().getMiddleBlockX(), chunk.getPos().getMiddleBlockZ());
		if (ecosystem.range(key) != null) {
			return;
		}

		int centerX = (int)Range.center(Range.xOf(key));
		int centerZ = (int)Range.center(Range.zOf(key));
		int quartY = QuartPos.fromBlock(level.getSeaLevel() + BIOME_PROBE_ABOVE_SEA);
		Set<Species> habitat = EnumSet.noneOf(Species.class);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				int x = centerX + dx * Range.SIZE_BLOCKS / 4;
				int z = centerZ + dz * Range.SIZE_BLOCKS / 4;
				habitat.addAll(SpeciesTypes.nativeTo(level.getNoiseBiome(QuartPos.fromBlock(x), quartY, QuartPos.fromBlock(z))));
			}
		}

		Range range = new Range(Climate.base(level, centerX >> 4, centerZ >> 4), habitat, NEW_LAND_FORAGE);
		ecosystem.explore(key, range, new Random(level.getSeed() ^ key * 0x9E3779B97F4A7C15L));
	}

	/**
	 * Moves each walking herd's ledger entry to where its animals actually are, records how well fed they are, and
	 * decides what the herd does next as a group.
	 */
	private static void followWalkingHerds(final ServerLevel level, final Ecosystem ecosystem) {
		WALKING.values().removeIf(Set::isEmpty);
		MOODS.keySet().retainAll(WALKING.keySet());
		long ticks = WorldTime.ticks(level.getServer());
		for (Herd herd : ecosystem.herds()) {
			Set<Mob> members = members(herd);
			if (members.isEmpty()) {
				continue;
			}

			double x = 0;
			double z = 0;
			double energy = 0;
			for (Mob member : members) {
				x += member.getX();
				z += member.getZ();
				energy += energyOf(member);
			}

			herd.moveTo(x / members.size(), z / members.size());
			herd.setHunger(1 - energy / members.size());
			decideAsAHerd(level, ecosystem, herd, ticks);
		}
	}

	/** A hungry herd picks a direction and walks it until it finds food. A herd that sees a storm coming pulls in tight. */
	private static void decideAsAHerd(final ServerLevel level, final Ecosystem ecosystem, final Herd herd, final long ticks) {
		HerdMood mood = mood(herd);
		BlockPos center = BlockPos.containing(herd.x(), level.getSeaLevel(), herd.z());
		mood.bunching = Weather.sky(level, center).precipitation() > STORM_FROM
			|| Sky.over(Weather.field(level), Climate.base(level, center), herd.x(), herd.z(), ticks + STORM_WARNING_TICKS).precipitation() > STORM_FROM;
		if (herd.hunger() <= HERD_MOVES_ON_WHEN_HUNGRIER_THAN) {
			mood.heading = null;
			return;
		}

		long now = level.getGameTime();
		boolean arrived = mood.heading != null && Math.hypot(mood.heading.x - herd.x(), mood.heading.z - herd.z()) < ARRIVED_WITHIN;
		if (mood.heading == null || arrived || now > mood.headingExpires) {
			long better = ecosystem.betterLandFor(herd, ticks);
			double direction = better == herd.range()
				? level.getRandom().nextDouble() * 2 * Math.PI
				: Math.atan2(Range.center(Range.zOf(better)) - herd.z(), Range.center(Range.xOf(better)) - herd.x());
			mood.heading = new Vec3(herd.x() + Math.cos(direction) * HEADING_BLOCKS, 0, herd.z() + Math.sin(direction) * HEADING_BLOCKS);
			mood.headingExpires = now + HEADING_LASTS_TICKS;
		}
	}

	/** Gives every herd on ticking land the animals the ledger says it has. */
	private static void bringHerdsIntoTheWorld(final ServerLevel level) {
		for (Herd herd : ecosystem(level.getServer()).herds()) {
			if (level.isPositionEntityTicking(BlockPos.containing(herd.x(), level.getSeaLevel(), herd.z()))) {
				bringIntoTheWorld(level, herd);
			}
		}
	}

	public static void bringIntoTheWorld(final ServerLevel level, final Herd herd) {
		for (int missing = herd.count() - members(herd).size(); missing > 0; missing--) {
			placeMember(level, herd);
		}
	}

	private static void placeMember(final ServerLevel level, final Herd herd) {
		RandomSource random = level.getRandom();
		for (int attempt = 0; attempt < PLACEMENT_TRIES; attempt++) {
			int x = (int)Math.floor(herd.x() + (random.nextDouble() * 2 - 1) * SPREAD_BLOCKS);
			int z = (int)Math.floor(herd.z() + (random.nextDouble() * 2 - 1) * SPREAD_BLOCKS);
			if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) {
				continue;
			}

			BlockPos spot = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
			if (!level.getFluidState(spot.below()).isEmpty() || !level.getBlockState(spot).getCollisionShape(level, spot).isEmpty()) {
				continue;
			}

			if (SpeciesTypes.typeOf(herd.species()).create(level, EntitySpawnReason.NATURAL) instanceof Mob member) {
				member.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, random.nextFloat() * 360, 0);
				member.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), EntitySpawnReason.NATURAL, null);
				enroll(member, herd);
				level.addFreshEntity(member);
			}

			return;
		}

		// Nowhere to stand, such as open water. The herd shifts and tries again at the next head count.
		herd.moveTo(herd.x() + (random.nextDouble() * 2 - 1) * NUDGE_BLOCKS, herd.z() + (random.nextDouble() * 2 - 1) * NUDGE_BLOCKS);
	}

	static void enroll(final Mob member, final Herd herd) {
		member.setAttached(HERD, herd.id());
		member.setAttached(ENERGY, (float)(1 - herd.hunger()));
		WALKING.computeIfAbsent(herd.id(), id -> new HashSet<>()).add(member);
		Behavior.install(member, herd.species(), true);
	}

	/** Takes a living animal out of its herd. It becomes an ordinary saved animal. */
	static void leaveHerd(final Mob animal) {
		Herd herd = herdOf(animal);
		Integer id = animal.removeAttached(HERD);
		if (id != null && WALKING.containsKey(id)) {
			WALKING.get(id).remove(animal);
		}

		if (herd != null) {
			herd.addMembers(-1);
		}
	}

	/**
	 * Sorts out an animal the herds did not place: an owned one coming back with its chunk, or one from before the
	 * herds existed. The second kind is owned if it looks owned, and otherwise joins a wild herd.
	 */
	public static void entityLoaded(final Entity entity, final ServerLevel level) {
		if (!(entity instanceof Mob animal) || !Climate.hasClimate(level) || isWild(animal)) {
			return;
		}

		Species species = SpeciesTypes.speciesOf(animal.getType());
		if (species == null) {
			return;
		}

		// Looking at the blocks around an entity while its chunk is still loading can stall the server.
		level.getServer().execute(() -> {
			if (animal.isRemoved() || isWild(animal)) {
				return;
			}

			if (Owned.isOwned(animal)) {
				Owned.returned(animal, species);
			} else if (Owned.showsOwnership(animal) || Owned.isBesideAFence(animal)) {
				Owned.claim(animal);
				Behavior.install(animal, species, false);
			} else {
				joinAWildHerd(level, animal, species);
			}
		});
	}

	private static void joinAWildHerd(final ServerLevel level, final Mob animal, final Species species) {
		Ecosystem ecosystem = ecosystem(level.getServer());
		Herd nearest = null;
		double nearestDistance = ADOPTS_INTO_HERDS_WITHIN;
		for (Herd herd : ecosystem.herds()) {
			double distance = Math.hypot(herd.x() - animal.getX(), herd.z() - animal.getZ());
			if (herd.species() == species && distance < nearestDistance && herd.count() < species.largestHerd()) {
				nearest = herd;
				nearestDistance = distance;
			}
		}

		if (nearest == null) {
			nearest = ecosystem.addHerd(species, animal.getX(), animal.getZ(), 0);
		}

		nearest.addMembers(1);
		enroll(animal, nearest);
	}

	public static float energyOf(final Entity animal) {
		return animal.getAttachedOrElse(ENERGY, 1F);
	}

	public static void setEnergy(final Entity animal, final float energy) {
		animal.setAttached(ENERGY, Math.clamp(energy, 0, 1));
	}

	/** An animal that leaves the world alive is still in its herd. One that leaves it dead is not. */
	public static void entityUnloaded(final Entity entity, final ServerLevel level) {
		Integer id = entity.getAttached(HERD);
		if (id == null) {
			return;
		}

		Set<Mob> members = WALKING.get(id);
		if (members != null) {
			members.remove(entity);
		}

		Herd herd = ecosystem(level.getServer()).herd(id);
		Entity.RemovalReason reason = entity.getRemovalReason();
		if (herd != null && reason != null && reason.shouldDestroy()) {
			herd.addMembers(-1);
		}
	}

	public static void died(final LivingEntity animal, final DamageSource cause) {
		Herd herd = herdOf(animal);
		if (herd != null && cause.getEntity() instanceof Player) {
			herd.setFear(herd.fear() + FEAR_PER_MEMBER_KILLED_BY_A_PLAYER);
		}

		Species eaten = SpeciesTypes.speciesOf(animal.getType());
		if (eaten != null && cause.getEntity() instanceof Mob killer) {
			shareTheKill(killer, eaten);
		}
	}

	/** A kill feeds the whole pack that is close enough to eat. */
	private static void shareTheKill(final Mob killer, final Species eaten) {
		Herd pack = herdOf(killer);
		if (pack == null || !pack.species().isPredator()) {
			return;
		}

		List<Mob> eaters = members(pack).stream().filter(member -> member.distanceTo(killer) <= SHARES_A_KILL_WITHIN).toList();
		float share = (float)(eaten.bodyFood() / (eaters.size() * pack.species().dailyFood() * Ecosystem.DAYS_OF_FOOD_IN_A_FULL_BELLY));
		eaters.forEach(eater -> setEnergy(eater, energyOf(eater) + share));
	}

	/** Young born to a wild animal join its herd. Young born to an owned animal are owned. */
	public static void childBorn(final Entity parent, final AgeableMob child) {
		Herd herd = herdOf(parent);
		if (herd != null) {
			herd.addMembers(1);
			enroll(child, herd);
		} else if (Owned.isOwned(parent)) {
			Owned.claim(child);
		}
	}
}
