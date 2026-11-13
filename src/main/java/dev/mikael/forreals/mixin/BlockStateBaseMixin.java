package dev.mikael.forreals.mixin;

import dev.mikael.forreals.plants.Crops;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class BlockStateBaseMixin {
	@Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
	private void forreals$plantsGrowByClimate(final ServerLevel level, final BlockPos pos, final RandomSource random, final CallbackInfo ci) {
		if (Crops.climateStopsGrowth(level, pos, (BlockState)(Object)this, random)) {
			ci.cancel();
		}
	}
}
