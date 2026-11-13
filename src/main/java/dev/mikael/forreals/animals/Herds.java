package dev.mikael.forreals.animals;

import com.mojang.serialization.Codec;
import dev.mikael.forreals.ForReals;
import dev.mikael.forreals.climate.Climate;
import dev.mikael.forreals.time.WorldTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
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

	private static final Map<Integer, Set<Mob>> WALKING = new HashMap<>();

	private Herds() {
	}

	public static void forgetWorld() {
		WALKING.clear();
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
		if (WorldTime.isPaused(server)) {
			return;
		}

		HerdLedger ledger = ledger(server);
		long seed = server.overworld().getSeed();
		for (int owed = ledger.hoursOwed(Math.floorDiv(WorldTime.ticks(server), (long)Ecosystem.HOUR_TICKS), MOST_HOURS_PER_TICK); owed > 0; owed--) {
			long hour = ledger.nextHour();
			ledger.ecosystem().advanceHour(hour * Ecosystem.HOUR_TICKS, new Random(seed ^ hour), Herds::walksTheWorld);
		}

		if (server.getTickCount() % HEAD_COUNT_TICKS == 0) {
			ServerLevel level = server.overworld();
			followWalkingHerds(ledger.ecosystem());
			if (level.getGameRules().get(GameRules.SPAWN_MOBS)) {
				bringHerdsIntoTheWorld(level);
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

	/** Moves each walking herd's ledger entry to where its animals actually are, and records how well fed they are. */
	private static void followWalkingHerds(final Ecosystem ecosystem) {
		WALKING.values().removeIf(Set::isEmpty);
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
	}

	/** Young born to a wild animal join its herd. */
	public static void childBorn(final Entity parent, final AgeableMob child) {
		Herd herd = herdOf(parent);
		if (herd != null) {
			herd.addMembers(1);
			enroll(child, herd);
		}
	}
}
