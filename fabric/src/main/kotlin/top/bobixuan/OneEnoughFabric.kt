package top.bobixuan

import net.fabricmc.api.ModInitializer

object OneEnoughFabric : ModInitializer {
	override fun onInitialize() {
		OneEnoughMod.init()
	}
}