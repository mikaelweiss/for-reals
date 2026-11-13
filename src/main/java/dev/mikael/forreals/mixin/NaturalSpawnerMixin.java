package dev.mikael.forreals.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.mikael.forreals.animals.SpeciesTypes;
import dev.mikael.forreals.climate.Climate;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.MobSpawnSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(NaturalSpawner.class)
abstract class NaturalSpawnerMixin {
	@ModifyExpressionValue(
		method = "spawnMobsForChunkGeneration",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/biome/MobSpawnSettings;getMobsToSpawn(Lnet/minecraft/world/entity/MobCategory;)Lnet/minecraft/util/random/WeightedList;"
		)
	)
	private static WeightedList<MobSpawnSettings.SpawnerData> forreals$newLandComesWithoutHerdAnimals(
		final WeightedList<MobSpawnSettings.SpawnerData> spawns,
		final ServerLevelAccessor level,
		final BlockPos sourcePos,
		final ChunkPos chunkPos,
		final RandomSource random
	) {
		return Climate.hasClimate(level.getLevel()) ? SpeciesTypes.withoutHerdAnimals(spawns) : spawns;
	}
}
