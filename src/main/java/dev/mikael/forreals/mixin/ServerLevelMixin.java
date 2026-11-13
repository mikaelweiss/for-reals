package dev.mikael.forreals.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.mikael.forreals.Switch;
import dev.mikael.forreals.climate.Climate;
import dev.mikael.forreals.plants.WildPlants;
import dev.mikael.forreals.time.WorldTime;
import dev.mikael.forreals.weather.SnowAndIce;
import dev.mikael.forreals.weather.Weather;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.SleepStatus;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
abstract class ServerLevelMixin {
	// Answering "not enough sleepers" keeps vanilla from jumping the clock to morning and clearing the weather.
	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/SleepStatus;areEnoughSleeping(I)Z"))
	private boolean forreals$sleepPassesTime(final SleepStatus sleepers, final int percentage, final Operation<Boolean> original) {
		if (!Switch.isOn()) {
			return original.call(sleepers, percentage);
		}

		ServerLevel level = (ServerLevel)(Object)this;
		if (level.dimension() == Level.OVERWORLD) {
			WorldTime.setSleeping(original.call(sleepers, percentage) && sleepers.areEnoughDeepSleeping(percentage, level.players()));
		}

		return false;
	}

	@ModifyVariable(method = "tickChunk", at = @At("HEAD"), argsOnly = true)
	private int forreals$plantsGrowThroughSleep(final int randomTickSpeed) {
		return randomTickSpeed * WorldTime.speedUp();
	}

	@Inject(method = "advanceWeatherCycle", at = @At("HEAD"), cancellable = true)
	private void forreals$weatherIsLocal(final CallbackInfo ci) {
		ServerLevel level = (ServerLevel)(Object)this;
		if (Climate.hasClimate(level)) {
			Weather.tickForcedWeather(level);
			ci.cancel();
		}
	}

	@Inject(method = "tickPrecipitation", at = @At("HEAD"), cancellable = true)
	private void forreals$climateDecidesSnowAndIce(final BlockPos pos, final CallbackInfo ci) {
		if (Climate.hasClimate((ServerLevel)(Object)this)) {
			ci.cancel();
		}
	}

	@Inject(method = "tickChunk", at = @At("HEAD"))
	private void forreals$tendTheGround(final LevelChunk chunk, final int randomTickSpeed, final CallbackInfo ci) {
		ServerLevel level = (ServerLevel)(Object)this;
		if (Climate.hasClimate(level)) {
			SnowAndIce.tickChunk(level, chunk);
			WildPlants.tickChunk(level, chunk);
		}
	}

	@Inject(method = "tickThunder", at = @At("HEAD"), cancellable = true)
	private void forreals$lightningFollowsTheStorm(final LevelChunk chunk, final CallbackInfo ci) {
		ServerLevel level = (ServerLevel)(Object)this;
		if (Climate.hasClimate(level)) {
			Weather.strikeLightning(level, chunk);
			ci.cancel();
		}
	}
}
