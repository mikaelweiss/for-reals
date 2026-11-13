package dev.mikael.forreals.animals;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;

/** Which game animal each herd species is. */
public final class SpeciesTypes {
	private static final Map<Species, EntityType<?>> TYPE_BY_SPECIES = new EnumMap<>(Map.ofEntries(
		Map.entry(Species.COW, EntityTypes.COW),
		Map.entry(Species.SHEEP, EntityTypes.SHEEP),
		Map.entry(Species.HORSE, EntityTypes.HORSE),
		Map.entry(Species.DONKEY, EntityTypes.DONKEY),
		Map.entry(Species.PIG, EntityTypes.PIG),
		Map.entry(Species.GOAT, EntityTypes.GOAT),
		Map.entry(Species.LLAMA, EntityTypes.LLAMA),
		Map.entry(Species.RABBIT, EntityTypes.RABBIT),
		Map.entry(Species.CHICKEN, EntityTypes.CHICKEN),
		Map.entry(Species.WOLF, EntityTypes.WOLF),
		Map.entry(Species.FOX, EntityTypes.FOX)
	));
	private static final Map<EntityType<?>, Species> SPECIES_BY_TYPE = new IdentityHashMap<>();
	private static final Map<WeightedList<MobSpawnSettings.SpawnerData>, WeightedList<MobSpawnSettings.SpawnerData>> WITHOUT_HERD_ANIMALS = new IdentityHashMap<>();

	static {
		TYPE_BY_SPECIES.forEach((species, type) -> SPECIES_BY_TYPE.put(type, species));
	}

	private SpeciesTypes() {
	}

	public static EntityType<?> typeOf(final Species species) {
		return TYPE_BY_SPECIES.get(species);
	}

	/** Null for an animal that does not live in herds. */
	public static Species speciesOf(final EntityType<?> type) {
		return SPECIES_BY_TYPE.get(type);
	}

	/** The herd species a biome's own spawn list says belong in it. */
	public static Set<Species> nativeTo(final Holder<Biome> biome) {
		MobSpawnSettings spawns = biome.value().getAttributes().applyModifier(EnvironmentAttributes.NATURAL_MOB_SPAWNS, MobSpawnSettings.EMPTY);
		Set<Species> natives = EnumSet.noneOf(Species.class);
		for (Weighted<MobSpawnSettings.SpawnerData> spawn : spawns.getMobsToSpawn(MobCategory.CREATURE).unwrap()) {
			Species species = speciesOf(spawn.value().type());
			if (species != null) {
				natives.add(species);
			}
		}

		return natives;
	}

	/** A spawn list with the herd animals taken out. Herds place those animals themselves. */
	public static synchronized WeightedList<MobSpawnSettings.SpawnerData> withoutHerdAnimals(final WeightedList<MobSpawnSettings.SpawnerData> spawns) {
		return WITHOUT_HERD_ANIMALS.computeIfAbsent(
			spawns, list -> WeightedList.of(list.unwrap().stream().filter(spawn -> speciesOf(spawn.value().type()) == null).toList())
		);
	}
}
