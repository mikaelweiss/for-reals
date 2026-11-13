package dev.mikael.forreals.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.mikael.forreals.Switch;
import dev.mikael.forreals.terrain.Vegetation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BiomeFilter.class)
abstract class BiomeFilterMixin {
	@Inject(method = "shouldPlace", at = @At("HEAD"), cancellable = true)
	private void forreals$plantsGrowInPatches(final PlacementContext context, final RandomSource random, final BlockPos origin, final CallbackInfoReturnable<Boolean> cir) {
		if (Switch.isOn() && context.topFeature().isPresent() && !Vegetation.grows(context.getLevel(), context.topFeature().get(), origin, random)) {
			cir.setReturnValue(false);
		}
	}

	@WrapOperation(
		method = "shouldPlace",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/WorldGenLevel;getBiome(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;")
	)
	private Holder<Biome> forreals$bordersBlend(
		final WorldGenLevel level, final BlockPos origin, final Operation<Holder<Biome>> original, @Local(argsOnly = true) final PlacementContext context
	) {
		if (!Switch.isOn()) {
			return original.call(level, origin);
		}

		return original.call(level, context.topFeature().map(feature -> Vegetation.biomeLookup(level, feature, origin)).orElse(origin));
	}
}
