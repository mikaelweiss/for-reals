package dev.mikael.forreals.mixin;

import dev.mikael.forreals.Switch;
import dev.mikael.forreals.seasons.SeasonalLook;
import dev.mikael.forreals.terrain.NewBiomes;
import java.util.stream.Stream;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryLoadTask;
import net.minecraft.resources.ResourceManagerRegistryLoadTask;
import net.minecraft.world.level.biome.Biome;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RegistryLoadTask.class)
abstract class RegistryLoadTaskMixin<T> {
	@Shadow
	@Final
	private WritableRegistry<T> registry;

	// Runs once every biome file has been read, while the registry can still be added to. A client that receives its
	// registries from a server gets these biomes sent to it, so only a registry read from files is added to, and only
	// for a world that has the mod's data pack on.
	@SuppressWarnings("unchecked")
	@Inject(
		method = "registerElements",
		at = @At(value = "FIELD", target = "Lnet/minecraft/resources/RegistryLoadTask;elementsRegistered:Z", opcode = Opcodes.PUTFIELD)
	)
	private void forreals$addBiomesMadeFromOthers(final Stream<?> elements, final CallbackInfo ci) {
		if ((Object)this instanceof ResourceManagerRegistryLoadTask<?> fromFiles && this.registry.key().equals(Registries.BIOME) && Switch.isOnIn(fromFiles.resourceManager)) {
			WritableRegistry<Biome> biomes = (WritableRegistry<Biome>)this.registry;
			NewBiomes.addTo(biomes);
			SeasonalLook.addTo(biomes);
		}
	}
}
