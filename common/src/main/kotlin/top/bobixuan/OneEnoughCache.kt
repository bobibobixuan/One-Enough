package top.bobixuan

import com.google.gson.GsonBuilder
import net.minecraft.util.Identifier
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.ServiceLoader

object OneEnoughCache {
	private val logger = LoggerFactory.getLogger("${OneEnoughMod.RESOURCE_NAMESPACE}/cache")
	private val gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()
	private const val CURRENT_CACHE_VERSION = 3

	private val platformPaths: OneEnoughPlatformPaths by lazy {
		ServiceLoader.load(OneEnoughPlatformPaths::class.java, OneEnoughPlatformPaths::class.java.classLoader)
			.findFirst()
			.orElseThrow { IllegalStateException("No OneEnoughPlatformPaths implementation found") }
	}

	data class CachedGroup(
		val name: String,
		val members: Set<String>,
		val sourceTags: Set<String>,
		val categoryHints: Set<String>,
	)

	data class CachePayload(
		val version: Int = CURRENT_CACHE_VERSION,
		val stateHash: Long = 0,
		val groups: Map<String, CachedGroup> = emptyMap(),
	)

	@Volatile
	private var loaded: CachePayload? = null

	fun loadedPayload(): CachePayload? = loaded

	fun tryLoad(stateHash: Long): CachePayload? {
		val path = cachePath()
		if (!Files.exists(path)) {
			return null
		}

		return runCatching {
			val payload: CachePayload = Files.newBufferedReader(path).use { reader ->
				gson.fromJson(reader, CachePayload::class.java)
			}
			if (payload.version != CURRENT_CACHE_VERSION) {
				logger.info(
					"Ignoring cache at {} because version {} does not match expected {}",
					path,
					payload.version,
					CURRENT_CACHE_VERSION,
				)
				null
			} else if (payload.stateHash == 0L || payload.stateHash == stateHash) {
				logger.info("Loaded cached classification with {} groups (stateHash={})", payload.groups.size, stateHash)
				loaded = payload
				payload
			} else {
				logger.info("Cache stateHash mismatch (cached={}, current={}); will reclassify", payload.stateHash, stateHash)
				null
			}
		}.getOrElse { error ->
			logger.warn("Failed to load cache: {}", error.toString())
			null
		}
	}

	fun save(groups: Map<String, CachedGroup>, stateHash: Long) {
		val path = cachePath()
		Files.createDirectories(path.parent)

		val payload = CachePayload(
			version = CURRENT_CACHE_VERSION,
			stateHash = stateHash,
			groups = groups,
		)

		runCatching {
			Files.writeString(path, gson.toJson(payload))
			logger.info("Saved classification cache with {} groups to {}", groups.size, path)
		}.getOrElse { error ->
			logger.warn("Failed to write cache to {}: {}", path, error.toString())
		}
	}

	private fun cachePath(): Path =
		platformPaths.configDir().resolve("${OneEnoughMod.RESOURCE_NAMESPACE}-cache.json")
}
