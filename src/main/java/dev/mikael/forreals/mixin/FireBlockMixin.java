package dev.mikael.forreals.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.mikael.forreals.climate.Climate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.FireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FireBlock.class)
abstract class FireBlockMixin {
	// Fire asks "is it raining anywhere" before it asks "is it raining on me". Only the second question has an answer here.
	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;isRaining()Z"))
	private boolean forreals$askAboutThisSpot(final ServerLevel level, final Operation<Boolean> original) {
		return Climate.hasClimate(level) || original.call(level);
	}
}
