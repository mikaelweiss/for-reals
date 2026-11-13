package dev.mikael.forreals.mixin;

import dev.mikael.forreals.animals.SpeciesTypes;
import dev.mikael.forreals.climate.Climate;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.mikael.forreals.monsters.SurfaceSpawns;
import dev.mikael.forreals.terrain.Vegetation;
import net.minecraft.core.BlockPos;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.placement.FeaturePlacer;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkGenerator.class)
abstract class ChunkGeneratorMixin {
	// TAIL is the biome spawn list. Structure spawn lists return earlier, so outposts and witch huts keep their mobs.
	@Inject(method = "getMobsAt", at = @At("TAIL"), cancellable = true)
	private void forreals$surfaceSpawnRules(
		final Level level,
		final StructureManager structureManager,
		final MobCategory mobCategory,
		final BlockPos pos,
		final CallbackInfoReturnable<WeightedList<MobSpawnSettings.SpawnerData>> cir
	) {
		if (SurfaceSpawns.isBlocked(level, mobCategory, pos)) {
			cir.setReturnValue(WeightedList.of());
		} else if (mobCategory == MobCategory.CREATURE && Climate.hasClimate(level)) {
			cir.setReturnValue(SpeciesTypes.withoutHerdAnimals(cir.getReturnValue()));
		}
	}

	@WrapOperation(
		method = "applyBiomeDecoration",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/levelgen/placement/FeaturePlacer;placeWithBiomeCheck(Lnet/minecraft/world/level/levelgen/placement/PlacedFeature;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z"
		)
	)
	private boolean forreals$richGroundPlantsTwice(
		final FeaturePlacer placer,
		final PlacedFeature feature,
		final RandomSource random,
		final BlockPos origin,
		final Operation<Boolean> original,
		@Local(argsOnly = true) final WorldGenLevel level
	) {
		boolean placed = false;
		for (int planting = Vegetation.plantings(level, feature, origin); planting > 0; planting--) {
			placed |= original.call(placer, feature, random, origin);
		}

		return placed;
	}
}
