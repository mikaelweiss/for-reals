package dev.mikael.forreals.mixin;

import dev.mikael.forreals.animals.Herds;
import dev.mikael.forreals.animals.Owned;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Animal.class)
abstract class AnimalMixin {
	@Inject(method = "finalizeSpawnChildFromBreeding", at = @At("TAIL"))
	private void forreals$youngJoinTheHerd(final ServerLevel level, final Animal partner, final AgeableMob offspring, final CallbackInfo ci) {
		if (offspring != null) {
			Herds.childBorn((Animal)(Object)this, offspring);
		}
	}

	@Inject(method = "setInLove", at = @At("HEAD"))
	private void forreals$feedingAnAnimalMakesItYours(final Player player, final CallbackInfo ci) {
		if (player != null) {
			Owned.fedByPlayer((Animal)(Object)this);
		}
	}
}
