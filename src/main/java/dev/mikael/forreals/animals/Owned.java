package dev.mikael.forreals.animals;

import com.mojang.serialization.Codec;
import dev.mikael.forreals.ForReals;
import dev.mikael.forreals.time.Calendar;
import dev.mikael.forreals.time.WorldTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Animals that belong to a player: fed, leashed, named, tamed, or found beside a fence. They are saved with the world
 * like any vanilla animal and stay where they are put. They still get hungry and need grass or hay.
 */
public final class Owned {
	private static final AttachmentType<Boolean> OWNED = AttachmentRegistry.createPersistent(Identifier.fromNamespaceAndPath(ForReals.MOD_ID, "owned"), Codec.BOOL);
	private static final AttachmentType<Long> LAST_SEEN = AttachmentRegistry.createPersistent(Identifier.fromNamespaceAndPath(ForReals.MOD_ID, "last_seen"), Codec.LONG);

	private static final int FENCE_REACH = 8;
	private static final int FENCE_REACH_VERTICAL = 3;
	private static final float FED_BY_HAND = 0.3F;
	private static final int FEED_REACH = 6;
	private static final int MOST_CATCH_UP_BITES = 8;
	private static final float EATS_BELOW = 0.6F;
	/** However long an owned animal went unfed while nobody was near, it comes back starving, not dead. */
	private static final float WEAKEST_ON_RETURN = 0.1F;

	private static final Set<Mob> NEARBY = new HashSet<>();

	private Owned() {
	}

	static void forgetWorld() {
		NEARBY.clear();
	}

	public static boolean isOwned(final Entity animal) {
		return animal.hasAttached(OWNED);
	}

	static List<Mob> nearby() {
		NEARBY.removeIf(Entity::isRemoved);
		return List.copyOf(NEARBY);
	}

	/** True for an animal a player has plainly claimed. */
	static boolean showsOwnership(final Mob animal) {
		return animal.hasCustomName()
			|| animal instanceof Leashable leashable && leashable.isLeashed()
			|| animal instanceof TamableAnimal pet && pet.isTame()
			|| animal instanceof AbstractHorse horse && horse.isTamed();
	}

	/** In a world that predates the herds, an animal standing by a fence is taken to be somebody's. */
	static boolean isBesideAFence(final Mob animal) {
		ServerLevel level = (ServerLevel)animal.level();
		for (BlockPos pos : BlockPos.withinClippedManhattan(animal.blockPosition(), FENCE_REACH, FENCE_REACH_VERTICAL, FENCE_REACH)) {
			LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
			if (chunk == null) {
				continue;
			}

			BlockState state = chunk.getBlockState(pos);
			if (state.is(BlockTags.FENCES) || state.is(BlockTags.WALLS) || state.is(BlockTags.FENCE_GATES)) {
				return true;
			}
		}

		return false;
	}

	/** Takes an animal out of the wild for good. */
	static void claim(final Mob animal) {
		Herds.leaveHerd(animal);
		Behavior.becomeOwned(animal);
		animal.setAttached(OWNED, true);
		NEARBY.add(animal);
	}

	public static void fedByPlayer(final Mob animal) {
		if (SpeciesTypes.speciesOf(animal.getType()) == null) {
			return;
		}

		if (!isOwned(animal)) {
			claim(animal);
		}

		Herds.setEnergy(animal, Herds.energyOf(animal) + FED_BY_HAND);
	}

	/** An owned animal back in a loaded world is as hungry as its time away made it, less whatever it could reach to eat. */
	static void returned(final Mob animal, final Species species) {
		NEARBY.add(animal);
		Behavior.install(animal, species, false);
		Long lastSeen = animal.getAttached(LAST_SEEN);
		if (lastSeen == null) {
			return;
		}

		long away = Math.max(0, WorldTime.ticks(animal.level().getServer()) - lastSeen);
		float energy = Herds.energyOf(animal) - (float)(away / (Grazing.DAYS_IN_A_FULL_BELLY * Calendar.TICKS_PER_DAY));
		Herds.setEnergy(animal, Math.max(energy, WEAKEST_ON_RETURN));
		if (!species.isPredator()) {
			eatWhatIsInReach(animal);
		}
	}

	private static void eatWhatIsInReach(final Mob animal) {
		ServerLevel level = (ServerLevel)animal.level();
		int bites = 0;
		for (BlockPos pos : BlockPos.withinClippedManhattan(animal.blockPosition(), FEED_REACH, 2, FEED_REACH)) {
			if (bites >= MOST_CATCH_UP_BITES || Herds.energyOf(animal) >= EATS_BELOW) {
				return;
			}

			LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
			if (chunk != null && Grazing.isWorthWalkingTo(chunk.getBlockState(pos))) {
				Grazing.eat(animal, pos.immutable());
				bites++;
			}
		}
	}

	static void liveASecond(final Mob animal, final Species species) {
		Behavior.liveASecond(animal, species, null);
		animal.setAttached(LAST_SEEN, WorldTime.ticks(animal.level().getServer()));
	}
}
