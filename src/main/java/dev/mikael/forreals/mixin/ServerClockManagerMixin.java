package dev.mikael.forreals.mixin;

import dev.mikael.forreals.Switch;
import dev.mikael.forreals.time.WorldTime;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.clock.ServerClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerClockManager.class)
abstract class ServerClockManagerMixin {
	@Shadow
	private MinecraftServer server;

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void forreals$clocksStopOnAnEmptyServer(final CallbackInfo ci) {
		if (Switch.isOn() && WorldTime.isPaused(this.server)) {
			ci.cancel();
		}
	}
}
