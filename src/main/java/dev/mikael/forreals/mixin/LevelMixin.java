package dev.mikael.forreals.mixin;

import dev.mikael.forreals.climate.Climate;
import dev.mikael.forreals.weather.Weather;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
abstract class LevelMixin {
	@Inject(method = "precipitationAt", at = @At("HEAD"), cancellable = true)
	private void forreals$rainFallsWhereTheStormIs(final BlockPos pos, final CallbackInfoReturnable<Biome.Precipitation> cir) {
		if ((Object)this instanceof ServerLevel level && Climate.hasClimate(level)) {
			cir.setReturnValue(Weather.precipitationAt(level, pos));
		}
	}
}
