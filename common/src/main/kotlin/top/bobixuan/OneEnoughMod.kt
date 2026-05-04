package top.bobixuan

import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

object OneEnoughMod {
	const val MOD_ID = "one_enough_mod"
	const val FABRIC_MOD_ID = "one-enough-mod"
	const val RESOURCE_NAMESPACE = "one-enough-mod"

	private val logger = LoggerFactory.getLogger(RESOURCE_NAMESPACE)
	private val initialized = AtomicBoolean(false)

	@JvmStatic
	fun init() {
		if (!initialized.compareAndSet(false, true)) {
			return
		}

		val config = OneEnoughConfig.current()
		logger.info(
			"Enabled dynamic runtime tag compatibility: autoDetect={}, scanRoots={}, cache={}, recipe rewrite={}",
			config.autoDetect,
			config.scanRoots.size,
			config.cacheEnabled,
			config.rewriteRecipes,
		)
	}
}