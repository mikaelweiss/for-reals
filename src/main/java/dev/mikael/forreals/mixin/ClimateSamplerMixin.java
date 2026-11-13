package dev.mikael.forreals.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Climate.Sampler.class)
abstract class ClimateSamplerMixin {
	/** Climate values are whole numbers, ten thousand to the unit. */
	private static final long UNIT = 10000;
	private static final long NEAR_THE_COAST = UNIT / 10;
	private static final double DRIER_PER_UNIT_INLAND = 0.3;

	// Rain comes off the sea, so the deeper inland a place is, the drier it reads when biomes are chosen.
	@ModifyReturnValue(method = "sample", at = @At("RETURN"))
	private Climate.TargetPoint forreals$coastsAreWetAndInteriorsDry(final Climate.TargetPoint point) {
		long humidity = point.humidity() - (long)(DRIER_PER_UNIT_INLAND * (point.continentalness() - NEAR_THE_COAST));
		return new Climate.TargetPoint(point.temperature(), humidity, point.continentalness(), point.erosion(), point.depth(), point.weirdness());
	}
}
