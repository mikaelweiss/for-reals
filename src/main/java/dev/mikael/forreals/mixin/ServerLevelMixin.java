package dev.mikael.forreals.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.mikael.forreals.time.WorldTime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.SleepStatus;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ServerLevel.class)
abstract class ServerLevelMixin {
	// Answering "not enough sleepers" keeps vanilla from jumping the clock to morning and clearing the weather.
	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/SleepStatus;areEnoughSleeping(I)Z"))
	private boolean forreals$sleepPassesTime(final SleepStatus sleepers, final int percentage, final Operation<Boolean> original) {
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
}
