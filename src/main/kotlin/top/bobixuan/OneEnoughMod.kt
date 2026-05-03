package top.bobixuan

import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

object OneEnoughMod : ModInitializer {
	const val MOD_ID = "one-enough-mod"

	private val logger = LoggerFactory.getLogger(MOD_ID)

	override fun onInitialize() {
		val config = OneEnoughConfig.current()
		logger.info(
			"Enabled dynamic runtime tag compatibility across {} scan roots; recipe rewrite={}",
			config.scanRoots.size,
			config.rewriteRecipes,
		)
	}
}