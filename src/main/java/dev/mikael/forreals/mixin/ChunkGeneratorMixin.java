package dev.mikael.forreals.mixin;

import dev.mikael.forreals.animals.SpeciesTypes;
import dev.mikael.forreals.climate.Climate;
import dev.mikael.forreals.monsters.SurfaceSpawns;
import net.minecraft.core.BlockPos;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkGenerator;
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
}
