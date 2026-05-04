package top.bobixuan

import com.google.gson.GsonBuilder
import net.minecraft.util.Identifier
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.ServiceLoader

object OneEnoughConfig {
	private val logger = LoggerFactory.getLogger("${OneEnoughMod.RESOURCE_NAMESPACE}/config")
	private val gson = GsonBuilder()
		.disableHtmlEscaping()
		.setPrettyPrinting()
		.create()

	class FileModel(
		var autoDetect: Boolean = true,
		var scanRoots: MutableList<String> = OneEnoughItemTags.defaultScanRoots.toMutableList(),
		var scanBareNamespaceTags: Boolean = false,
		var excludedBareNamespaces: MutableList<String> = mutableListOf("minecraft", OneEnoughMod.RESOURCE_NAMESPACE, "c", "forge"),
		var whitelistTags: MutableList<String> = mutableListOf(),
		var blacklistTags: MutableList<String> = mutableListOf(),
		var whitelistGroups: MutableList<String> = mutableListOf(),
		var blacklistGroups: MutableList<String> = mutableListOf(),
		var itemBlacklist: MutableList<String> = mutableListOf(),
		var groupAliases: MutableMap<String, String> = LinkedHashMap(OneEnoughItemTags.defaultGroupAliases),
		var publicTagNames: MutableMap<String, String> = LinkedHashMap(OneEnoughItemTags.defaultPublicTagNames),
		var rewriteRecipes: Boolean = true,
		var recipeBlacklist: MutableList<String> = mutableListOf(),
		var itemGroupOverrides: MutableMap<String, String> = LinkedHashMap(),
		var cacheEnabled: Boolean = true,
		var cacheExpiryMins: Int = 0,
	)

	data class ResolvedConfig(
		val autoDetect: Boolean,
		val cacheEnabled: Boolean,
		val cacheExpiryMins: Int,
		val scanRoots: Set<String>,
		val scanBareNamespaceTags: Boolean,
		val excludedBareNamespaces: Set<String>,
		val whitelistTags: Set<Identifier>,
		val blacklistTags: Set<Identifier>,
		val whitelistGroups: Set<String>,
		val blacklistGroups: Set<String>,
		val itemBlacklist: Set<Identifier>,
		val groupAliases: Map<String, String>,
		val publicTagNames: Map<String, String>,
		val rewriteRecipes: Boolean,
		val recipeBlacklist: Set<Identifier>,
		val itemGroupOverrides: Map<Identifier, String>,
	) {
		fun shouldScanTag(tagId: Identifier): Boolean {
			if (tagId in blacklistTags) {
				return false
			}

			if (tagId in whitelistTags) {
				return true
			}

			val fullId = tagId.toString()
			if (scanRoots.any(fullId::startsWith)) {
				return true
			}

			return scanBareNamespaceTags && '/' !in tagId.path && tagId.namespace !in excludedBareNamespaces
		}

		fun isGroupAllowed(name: String): Boolean {
			if (name in OneEnoughItemTags.genericGroupNameBlacklist) {
				return false
			}

			if (name in blacklistGroups) {
				return false
			}

			return whitelistGroups.isEmpty() || name in whitelistGroups
		}

		fun isRecipeAllowed(recipeId: Identifier): Boolean = rewriteRecipes && recipeId !in recipeBlacklist
	}

	@Volatile
	private var cached: ResolvedConfig? = null
	private val platformPaths: OneEnoughPlatformPaths by lazy {
		ServiceLoader.load(OneEnoughPlatformPaths::class.java, OneEnoughPlatformPaths::class.java.classLoader)
			.findFirst()
			.orElseThrow {
				IllegalStateException("No OneEnoughPlatformPaths implementation was found for the active loader")
			}
	}

	@JvmStatic
	fun current(): ResolvedConfig = cached ?: synchronized(this) {
		cached ?: loadResolved().also { cached = it }
	}

	private fun loadResolved(): ResolvedConfig {
		val path = configPath()
		val fileModel = readOrCreate(path)

		return ResolvedConfig(
			autoDetect = fileModel.autoDetect,
			cacheEnabled = fileModel.cacheEnabled,
			cacheExpiryMins = fileModel.cacheExpiryMins,
			scanRoots = fileModel.scanRoots.map(String::trim).filter(String::isNotEmpty).toSet(),
			scanBareNamespaceTags = fileModel.scanBareNamespaceTags,
			excludedBareNamespaces = fileModel.excludedBareNamespaces.map(String::trim).filter(String::isNotEmpty).toSet(),
			whitelistTags = parseIdentifiers(fileModel.whitelistTags, "whitelistTags", path),
			blacklistTags = parseIdentifiers(fileModel.blacklistTags, "blacklistTags", path),
			whitelistGroups = fileModel.whitelistGroups.map(::normalizeName).filter(String::isNotEmpty).toSet(),
			blacklistGroups = fileModel.blacklistGroups.map(::normalizeName).filter(String::isNotEmpty).toSet(),
			itemBlacklist = parseIdentifiers(fileModel.itemBlacklist, "itemBlacklist", path),
			groupAliases = fileModel.groupAliases.entries.associate { normalizeName(it.key) to normalizeName(it.value) },
			publicTagNames = fileModel.publicTagNames.entries.associate { normalizeName(it.key) to normalizePath(it.value) },
			rewriteRecipes = fileModel.rewriteRecipes,
			recipeBlacklist = parseIdentifiers(fileModel.recipeBlacklist, "recipeBlacklist", path),
			itemGroupOverrides = parseItemGroupOverrides(fileModel.itemGroupOverrides, "itemGroupOverrides", path),
		)
	}

	private fun readOrCreate(path: Path): FileModel {
		Files.createDirectories(path.parent)

		if (!Files.exists(path)) {
			val defaults = FileModel()
			Files.writeString(path, gson.toJson(defaults))
			logger.info("Wrote default compatibility config to {}", path)
			return defaults
		}

		return runCatching {
			Files.newBufferedReader(path).use { reader ->
				gson.fromJson(reader, FileModel::class.java) ?: FileModel()
			}
		}.getOrElse { error ->
			logger.warn("Failed to read compatibility config from {}; using defaults", path, error)
			FileModel()
		}
	}

	private fun parseIdentifiers(values: Collection<String>, fieldName: String, path: Path): Set<Identifier> {
		val parsed = linkedSetOf<Identifier>()

		values.forEach { raw ->
			val trimmed = raw.trim()
			if (trimmed.isEmpty()) {
				return@forEach
			}

			val identifier = Identifier.tryParse(trimmed)
			if (identifier == null) {
				logger.warn("Ignoring invalid identifier '{}' in {} at {}", trimmed, fieldName, path)
				return@forEach
			}

			parsed += identifier
		}

		return parsed
	}

	private fun parseItemGroupOverrides(
		entries: Map<String, String>,
		fieldName: String,
		path: Path,
	): Map<Identifier, String> {
		return entries.mapNotNull { (rawItemId, rawGroupName) ->
			val itemId = Identifier.tryParse(rawItemId.trim())
			val groupName = normalizeName(rawGroupName)
			if (itemId == null || groupName.isEmpty()) {
				logger.warn("Ignoring invalid itemGroupOverrides entry '{}' → '{}' in {} at {}", rawItemId, rawGroupName, fieldName, path)
				null
			} else {
				itemId to groupName
			}
		}.toMap()
	}

	private fun normalizeName(value: String): String = value.trim().lowercase()

	private fun normalizePath(value: String): String = value.trim().lowercase().replace('-', '_')

	private fun configPath(): Path = platformPaths.configDir().resolve("${OneEnoughMod.RESOURCE_NAMESPACE}.json")
}