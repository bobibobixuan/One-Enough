package top.bobixuan

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider
import net.minecraft.registry.RegistryWrapper
import java.util.concurrent.CompletableFuture

class OneEnoughItemTagProvider(
	output: FabricDataOutput,
	registriesFuture: CompletableFuture<RegistryWrapper.WrapperLookup>,
) : FabricTagProvider.ItemTagProvider(output, registriesFuture) {
	override fun configure(wrapperLookup: RegistryWrapper.WrapperLookup) {
		// The compatibility model is now runtime-driven via OneEnoughRuntimeTagBridge
		// and Mixin injection, so no static tag generation is needed here.
		// This provider is kept as a placeholder for potential future datagen requirements.
	}
}