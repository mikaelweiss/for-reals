package dev.mikael.forreals.terrain;

import dev.mikael.forreals.ForReals;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * The biomes that fill the gaps between vanilla ones. Each is made from the vanilla biomes it sits between: their
 * plants, with some left out, under a climate of its own. Nothing here needs a new block.
 */
public final class NewBiomes {
	public static final ResourceKey<Biome> STEPPE = key("steppe");
	public static final ResourceKey<Biome> SCRUBLAND = key("scrubland");
	public static final ResourceKey<Biome> PRAIRIE = key("prairie");
	public static final ResourceKey<Biome> MARSH = key("marsh");
	public static final ResourceKey<Biome> MIXED_FOREST = key("mixed_forest");
	public static final ResourceKey<Biome> DRY_WOODLAND = key("dry_woodland");
	public static final ResourceKey<Biome> TEMPERATE_RAINFOREST = key("temperate_rainforest");
	public static final ResourceKey<Biome> TUNDRA = key("tundra");

	private static final Predicate<String> EVERYTHING = feature -> true;
	private static final Predicate<String> NOTHING = feature -> false;
	private static final Predicate<String> NO_TREES = feature -> !feature.startsWith("trees_") && !feature.equals("patch_bush") && !feature.equals("patch_pumpkin");

	/**
	 * @param base the vanilla biome this one copies its sky, water, animals, and terrain features from
	 * @param keep which of the base's features stay, by name
	 * @param other a second vanilla biome to borrow features from
	 * @param borrow which of the other's features come along, by name
	 */
	private record Recipe(
		ResourceKey<Biome> made, ResourceKey<Biome> base, Predicate<String> keep, ResourceKey<Biome> other, Predicate<String> borrow, float temperature, float downfall
	) {
	}

	private static final List<Recipe> RECIPES = List.of(
		new Recipe(STEPPE, Biomes.PLAINS, NO_TREES, Biomes.PLAINS, NOTHING, 0.5F, 0.2F),
		new Recipe(PRAIRIE, Biomes.PLAINS, NO_TREES, Biomes.FLOWER_FOREST, feature -> feature.contains("flower"), 0.85F, 0.55F),
		new Recipe(MIXED_FOREST, Biomes.FOREST, EVERYTHING, Biomes.TAIGA, feature -> feature.equals("trees_taiga") || feature.contains("fern") || feature.contains("berry"), 0.45F, 0.8F),
		new Recipe(DRY_WOODLAND, Biomes.FOREST, feature -> !feature.equals("forest_flowers"), Biomes.SAVANNA, feature -> feature.equals("patch_tall_grass"), 0.9F, 0.3F),
		new Recipe(SCRUBLAND, Biomes.SAVANNA, EVERYTHING, Biomes.DESERT, feature -> feature.contains("dead_bush") || feature.contains("dry_grass"), 1.8F, 0.1F),
		new Recipe(MARSH, Biomes.SWAMP, feature -> !feature.startsWith("trees_"), Biomes.SWAMP, NOTHING, 0.5F, 0.9F),
		new Recipe(TEMPERATE_RAINFOREST, Biomes.OLD_GROWTH_SPRUCE_TAIGA, EVERYTHING, Biomes.FOREST, feature -> feature.equals("patch_bush"), 0.55F, 1.0F),
		new Recipe(TUNDRA, Biomes.SNOWY_PLAINS, NO_TREES, Biomes.OLD_GROWTH_SPRUCE_TAIGA, feature -> feature.equals("forest_rock"), 0.05F, 0.3F)
	);

	private NewBiomes() {
	}

	private static ResourceKey<Biome> key(final String name) {
		return ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(ForReals.MOD_ID, name));
	}

	/**
	 * Adds the new biomes to a biome registry that has just finished loading. They are made last so that their feature
	 * lists can follow one order that agrees with every biome already there, including any a datapack brought.
	 */
	public static void addTo(final WritableRegistry<Biome> registry) {
		List<List<List<Holder<PlacedFeature>>>> everyBiomesSteps = registry.listElements()
			.filter(Holder.Reference::isBound)
			.map(biome -> biome.value().getGenerationSettings().features().stream().map(step -> step.stream().toList()).toList())
			.toList();
		for (Recipe recipe : RECIPES) {
			Optional<Biome> base = loaded(registry, recipe.base);
			Optional<Biome> other = loaded(registry, recipe.other);
			if (base.isPresent() && other.isPresent() && loaded(registry, recipe.made).isEmpty()) {
				registry.register(recipe.made, make(recipe, base.get(), other.get(), everyBiomesSteps), RegistrationInfo.BUILT_IN);
			}
		}
	}

	/**
	 * A biome that has really been registered. While registries load, the game reserves an empty slot for any biome
	 * something else has asked for by name, so a name being present does not mean the biome is.
	 */
	public static Optional<Biome> loaded(final Registry<Biome> registry, final ResourceKey<Biome> key) {
		return registry.get(key).filter(Holder.Reference::isBound).map(Holder.Reference::value);
	}

	private static Biome make(final Recipe recipe, final Biome base, final Biome other, final List<List<List<Holder<PlacedFeature>>>> everyBiomesSteps) {
		BiomeGenerationSettings.PlainBuilder features = new BiomeGenerationSettings.PlainBuilder();
		base.getGenerationSettings().getCarvers().forEach(features::addCarver);
		List<HolderSet<PlacedFeature>> baseSteps = base.getGenerationSettings().features();
		List<HolderSet<PlacedFeature>> otherSteps = other.getGenerationSettings().features();
		for (int step = 0; step < Math.max(baseSteps.size(), otherSteps.size()); step++) {
			int index = step;
			List<Holder<PlacedFeature>> kept = chosen(baseSteps, step, recipe.keep);
			List<Holder<PlacedFeature>> borrowed = chosen(otherSteps, step, recipe.borrow);
			List<List<Holder<PlacedFeature>>> thisStepEverywhere = everyBiomesSteps.stream().filter(steps -> index < steps.size()).map(steps -> steps.get(index)).toList();
			for (Holder<PlacedFeature> feature : FeatureOrder.agreeingWith(thisStepEverywhere)) {
				if (kept.contains(feature) || borrowed.contains(feature)) {
					features.addFeature(step, feature);
				}
			}
		}

		return new Biome.BiomeBuilder()
			.hasPrecipitation(true)
			.temperature(recipe.temperature)
			.downfall(recipe.downfall)
			.putAttributes(base.getAttributes())
			.specialEffects(base.getSpecialEffects())
			.generationSettings(features.build())
			.build();
	}

	private static List<Holder<PlacedFeature>> chosen(final List<HolderSet<PlacedFeature>> steps, final int step, final Predicate<String> wanted) {
		if (step >= steps.size()) {
			return List.of();
		}

		return steps.get(step).stream().filter(feature -> feature.unwrapKey().map(key -> wanted.test(key.identifier().getPath())).orElse(false)).toList();
	}
}
