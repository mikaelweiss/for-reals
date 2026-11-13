package dev.mikael.forreals.terrain;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import dev.mikael.forreals.ForReals;
import java.util.function.Function;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;

/**
 * The overworld's biome layout with the new biomes in it. It is a second layout beside the vanilla one. The mod's data
 * pack points the overworld at it, so a world with the pack off keeps the vanilla layout untouched.
 */
public final class FilledOverworld {
	private static final MultiNoiseBiomeSourceParameterList.Preset PRESET = new MultiNoiseBiomeSourceParameterList.Preset(
		Identifier.fromNamespaceAndPath(ForReals.MOD_ID, "overworld"), new MultiNoiseBiomeSourceParameterList.Preset.SourceProvider() {
			@Override
			public <T> Climate.ParameterList<T> apply(final Function<ResourceKey<Biome>, T> lookup) {
				ImmutableList.Builder<Pair<Climate.ParameterPoint, T>> placements = ImmutableList.builder();
				OverworldBiomeBuilder vanilla = new OverworldBiomeBuilder();
				((GapFilling)(Object)vanilla).forreals$fillTheGapsBetweenBiomes();
				vanilla.addBiomes(placement -> placements.add(placement.mapSecond(lookup)));
				return new Climate.ParameterList<>(placements.build());
			}
		}
	);

	private FilledOverworld() {
	}

	public static void register() {
		MultiNoiseBiomeSourceParameterList.Preset.BY_NAME.put(PRESET.id(), PRESET);
	}
}
