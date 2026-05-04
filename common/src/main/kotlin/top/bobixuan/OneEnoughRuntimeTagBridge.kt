package top.bobixuan

import net.minecraft.item.Item
import net.minecraft.registry.Registries
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.util.Identifier
import org.slf4j.LoggerFactory

object OneEnoughRuntimeTagBridge {
	private val logger = LoggerFactory.getLogger("${OneEnoughMod.RESOURCE_NAMESPACE}/runtime-tags")
	private const val CROPS = "crops"
	private const val VEGETABLES = "vegetables"

	data class DynamicIngredientGroup(
		val name: String,
		val sourceTagIds: Set<Identifier>,
		val memberItemIds: Set<Identifier>,
		val publicTagIds: Set<Identifier>,
		val categoryHints: Set<String>,
		val recipeTagId: Identifier,
	) {
		val hubTagId: Identifier = OneEnoughItemTags.hubTagId(name)
	}

	data class RuntimeSnapshot(
		val groups: Map<String, DynamicIngredientGroup>,
		val itemToGroup: Map<Identifier, DynamicIngredientGroup>,
		val ambiguousItems: Set<Identifier>,
	) {
		companion object {
			val EMPTY = RuntimeSnapshot(
				groups = emptyMap(),
				itemToGroup = emptyMap(),
				ambiguousItems = emptySet(),
			)
		}
	}

	data class MergeSummary(
		val mergedGroups: Int,
		val discoveredSourceTags: Int,
		val touchedTags: Int,
		val rewritableItems: Int,
		val fromCache: Boolean = false,
	)

	data class ScanMatch(
		val groupName: String,
		val categoryHints: Set<String>,
	)

	data class MutableDiscoveredGroup(
		val name: String,
		val sourceTags: LinkedHashSet<Identifier> = linkedSetOf(),
		val categoryHints: LinkedHashSet<String> = linkedSetOf(),
		val items: LinkedHashSet<Any> = linkedSetOf(),
		val itemIds: LinkedHashSet<Identifier> = linkedSetOf(),
	)

	@Volatile
	private var snapshot: RuntimeSnapshot = RuntimeSnapshot.EMPTY

	@JvmStatic
	fun currentSnapshot(): RuntimeSnapshot = snapshot

	@JvmStatic
	fun mergeBuiltItemTags(
		builtTags: MutableMap<Identifier, MutableCollection<Any>>,
	): MergeSummary {
		val config = OneEnoughConfig.current()
		val cacheStateHash = if (config.cacheEnabled) computeStateHash(builtTags, config) else null

		// === Phase 1: Attempt cache load ===
		if (cacheStateHash != null) {
			val cachedPayload = OneEnoughCache.tryLoad(cacheStateHash)
			if (cachedPayload != null) {
				return applyCachedGroups(builtTags, cachedPayload, config)
			}
		}

		// === Phase 2: Auto-detect or rule-based classification ===
		return if (config.autoDetect) {
			val classification = OneEnoughTagClassifier.classifyAllTags(
				allTags = builtTags.mapValues { it.value as Collection<Any> },
				config = config,
			)

			// Save to cache for next startup
			if (cacheStateHash != null) {
				OneEnoughCache.save(classification.groups, cacheStateHash)
			}

			applyGroupsAndBuildSnapshot(builtTags, classification.groups, config, fromCache = false)
		} else {
			// Legacy manual mode: use scanRoots and bare-tag config
			legacyMergeFromScannedTags(builtTags, config)
		}
	}

	private fun computeStateHash(
		builtTags: Map<Identifier, Collection<Any>>,
		config: OneEnoughConfig.ResolvedConfig,
	): Long {
		var hash = 17L

		fun add(value: String) {
			hash = 31L * hash + value.hashCode()
		}

		fun addAll(values: Iterable<String>) {
			values.forEach(::add)
		}

		add(config.autoDetect.toString())
		add(config.scanBareNamespaceTags.toString())
		addAll(config.scanRoots.sorted())
		addAll(config.excludedBareNamespaces.sorted())
		addAll(config.whitelistTags.map(Identifier::toString).sorted())
		addAll(config.blacklistTags.map(Identifier::toString).sorted())
		addAll(config.whitelistGroups.sorted())
		addAll(config.blacklistGroups.sorted())
		addAll(config.itemBlacklist.map(Identifier::toString).sorted())
		addAll(config.groupAliases.entries.sortedBy { it.key }.map { "${it.key}=${it.value}" })
		addAll(config.itemGroupOverrides.entries.sortedBy { it.key.toString() }.map { "${it.key}=${it.value}" })

		builtTags.entries
			.sortedBy { it.key.toString() }
			.forEach { (tagId, values) ->
				add(tagId.toString())
				val memberIds = values
					.mapNotNull(::resolveItemId)
					.map(Identifier::toString)
					.sorted()
				add(memberIds.size.toString())
				addAll(memberIds)
			}

		return hash
	}

	private fun applyCachedGroups(
		builtTags: MutableMap<Identifier, MutableCollection<Any>>,
		cachedPayload: OneEnoughCache.CachePayload,
		config: OneEnoughConfig.ResolvedConfig,
	): MergeSummary {
		val groups = LinkedHashMap(cachedPayload.groups)

		val totalSourceTags = groups.values.sumOf { it.sourceTags.size }
		val totalItems = groups.values.sumOf { it.members.size }
		logger.info(
			"Loaded {} groups ({} items, {} source tags) from cache",
			groups.size, totalItems, totalSourceTags,
		)

		return applyGroupsAndBuildSnapshot(builtTags, groups, config, fromCache = true)
	}

	private fun applyGroupsAndBuildSnapshot(
		builtTags: MutableMap<Identifier, MutableCollection<Any>>,
		cachedGroups: Map<String, OneEnoughCache.CachedGroup>,
		config: OneEnoughConfig.ResolvedConfig,
		fromCache: Boolean,
	): MergeSummary {
		val touchedTags = linkedSetOf<Identifier>()
		val frozenGroups = linkedMapOf<String, DynamicIngredientGroup>()
		val groupNameByItem = linkedMapOf<Identifier, String>()
		val ambiguousItems = linkedSetOf<Identifier>()
		val itemCandidateGroups = linkedMapOf<Identifier, LinkedHashSet<String>>()

		val existingItemMap = buildExistingItemMap(builtTags)

		cachedGroups.forEach { (groupName, cached) ->
			val sourceTagIds = cached.sourceTags.mapNotNull { Identifier.tryParse(it) }.toSet()
			val memberItemIds = cached.members.mapNotNull { Identifier.tryParse(it) }.toSet()

			if (memberItemIds.isEmpty()) return@forEach

			val publicTagIds = linkedSetOf<Identifier>()
			publicTagIds += OneEnoughItemTags.commonPluralTagId(groupName, config.publicTagNames)
			publicTagIds += OneEnoughItemTags.forgePluralTagId(groupName, config.publicTagNames)
			publicTagIds += OneEnoughItemTags.categoryTagIds(groupName, cached.categoryHints)

			val targetTags = linkedSetOf<Identifier>()
			targetTags += sourceTagIds
			targetTags += OneEnoughItemTags.hubTagId(groupName)
			targetTags += publicTagIds

			val memberItems = memberItemIds.mapNotNull { id ->
				existingItemMap[id] ?: (Registries.ITEM.getOrEmpty(id).orElse(null) as? Any)
			}

			targetTags.forEach { tagId ->
				mergeInto(builtTags, tagId, memberItems)
				touchedTags += tagId
			}

			val dynamicGroup = DynamicIngredientGroup(
				name = groupName,
				sourceTagIds = sourceTagIds,
				memberItemIds = memberItemIds,
				publicTagIds = publicTagIds,
				categoryHints = cached.categoryHints,
				recipeTagId = OneEnoughItemTags.commonPluralTagId(groupName, config.publicTagNames),
			)

			frozenGroups[groupName] = dynamicGroup

			memberItemIds.forEach { itemId ->
				itemCandidateGroups.getOrPut(itemId) { linkedSetOf() } += groupName
			}
		}

		// Resolve ambiguous items
		itemCandidateGroups.forEach { (itemId, candidates) ->
			val resolved = pickBestGroup(itemId, candidates.toList())
			if (resolved != null) {
				groupNameByItem[itemId] = resolved
			} else {
				ambiguousItems += itemId
			}
		}

		snapshot = RuntimeSnapshot(
			groups = frozenGroups,
			itemToGroup = groupNameByItem
				.filterKeys { it !in ambiguousItems }
				.mapValues { (_, groupName) -> frozenGroups.getValue(groupName) },
			ambiguousItems = ambiguousItems,
		)

		val sourceLabel = if (fromCache) "cache" else "auto-detect"
		logger.info(
			"Applied {} ingredient groups from {} ({} source tags, {} items)",
			frozenGroups.size, sourceLabel,
			cachedGroups.values.sumOf { it.sourceTags.size },
			groupNameByItem.size,
		)

		return MergeSummary(
			mergedGroups = frozenGroups.size,
			discoveredSourceTags = cachedGroups.values.sumOf { it.sourceTags.size },
			touchedTags = touchedTags.size,
			rewritableItems = groupNameByItem.size,
			fromCache = fromCache,
		)
	}

	private fun legacyMergeFromScannedTags(
		builtTags: MutableMap<Identifier, MutableCollection<Any>>,
		config: OneEnoughConfig.ResolvedConfig,
	): MergeSummary {
		val discoveredGroups = linkedMapOf<String, MutableDiscoveredGroup>()

		builtTags.forEach { (tagId, values) ->
			val scanMatch = scanTag(tagId, config) ?: return@forEach
			val tagName = scanMatch.groupName

			if (tagName in config.blacklistGroups) return@forEach
			if (config.whitelistGroups.isNotEmpty() && tagName !in config.whitelistGroups) return@forEach

			values.forEach { value ->
				val itemId = resolveItemId(value) ?: return@forEach
				if (itemId !in config.itemBlacklist) {
					val rawGroupName = OneEnoughItemTags.normalizeGroupName(itemId.path, config.groupAliases) ?: return@forEach
					val itemOwnName = config.itemGroupOverrides[itemId] ?: rawGroupName

					val effectiveGroupName = if (itemOwnName == tagName) tagName else itemOwnName

					if (!config.isGroupAllowed(effectiveGroupName)) return@forEach

					val group = discoveredGroups.getOrPut(effectiveGroupName) {
						MutableDiscoveredGroup(effectiveGroupName)
					}
					group.sourceTags += tagId
					group.categoryHints += scanMatch.categoryHints
					group.items += value
					group.itemIds += itemId
				}
			}
		}

		var mergedGroups = 0
		var discoveredSourceTags = 0
		val touchedTags = linkedSetOf<Identifier>()
		val frozenGroups = linkedMapOf<String, DynamicIngredientGroup>()
		val groupNameByItem = linkedMapOf<Identifier, String>()
		val ambiguousItems = linkedSetOf<Identifier>()
		val itemCandidateGroups = linkedMapOf<Identifier, LinkedHashSet<String>>()

		discoveredGroups.values.forEach { group ->
			if (group.items.isEmpty()) return@forEach

			mergedGroups += 1
			discoveredSourceTags += group.sourceTags.size

			val publicTagIds = linkedSetOf<Identifier>()
			publicTagIds += OneEnoughItemTags.commonPluralTagId(group.name, config.publicTagNames)
			publicTagIds += OneEnoughItemTags.forgePluralTagId(group.name, config.publicTagNames)
			publicTagIds += OneEnoughItemTags.categoryTagIds(group.name, group.categoryHints)

			val targetTags = linkedSetOf<Identifier>()
			targetTags += group.sourceTags
			targetTags += OneEnoughItemTags.hubTagId(group.name)
			targetTags += publicTagIds

			targetTags.forEach { tagId ->
				mergeInto(builtTags, tagId, group.items)
				touchedTags += tagId
			}

			val dynamicGroup = DynamicIngredientGroup(
				name = group.name,
				sourceTagIds = group.sourceTags.toSet(),
				memberItemIds = group.itemIds.toSet(),
				publicTagIds = publicTagIds.toSet(),
				categoryHints = group.categoryHints.toSet(),
				recipeTagId = OneEnoughItemTags.commonPluralTagId(group.name, config.publicTagNames),
			)

			frozenGroups[group.name] = dynamicGroup

			dynamicGroup.memberItemIds.forEach { itemId ->
				itemCandidateGroups.getOrPut(itemId) { linkedSetOf() } += group.name
			}

			if (group.sourceTags.size > 1) {
				logger.info(
					"Merged dynamic ingredient group '{}' from {} discovered source tags",
					group.name, group.sourceTags.size,
				)
			}
		}

		itemCandidateGroups.forEach { (itemId, candidates) ->
			val resolved = pickBestGroup(itemId, candidates.toList())
			if (resolved != null) {
				groupNameByItem[itemId] = resolved
			} else {
				ambiguousItems += itemId
			}
		}

		snapshot = RuntimeSnapshot(
			groups = frozenGroups,
			itemToGroup = groupNameByItem
				.filterKeys { it !in ambiguousItems }
				.mapValues { (_, groupName) -> frozenGroups.getValue(groupName) },
			ambiguousItems = ambiguousItems,
		)

		return MergeSummary(
			mergedGroups = mergedGroups,
			discoveredSourceTags = discoveredSourceTags,
			touchedTags = touchedTags.size,
			rewritableItems = snapshot.itemToGroup.size,
		)
	}

	private fun pickBestGroup(itemId: Identifier, candidates: List<String>): String? {
		if (candidates.size == 1) return candidates.first()

		val normalized = candidates.associateWith { it.replace("_", "") }

		val dominated = mutableSetOf<String>()
		for (a in candidates) {
			for (b in candidates) {
				if (a == b) continue
				val na = normalized.getValue(a)
				val nb = normalized.getValue(b)
				if (na.contains(nb) && !nb.contains(na)) {
					dominated += b
				}
			}
		}

		val undominated = candidates.filter { it !in dominated }
		if (undominated.size == 1) return undominated.first()

		val itemPath = itemId.path.replace("_", "")
		fun score(groupName: String): Int {
			val ng = normalized.getValue(groupName)
			return when {
				itemPath == ng -> 1000
				itemPath.endsWith(ng) || itemPath.startsWith(ng) -> ng.length * 2
				ng.endsWith(itemPath) || ng.startsWith(itemPath) -> itemPath.length * 2
				itemPath.contains(ng) -> ng.length
				ng.contains(itemPath) -> itemPath.length
				else -> 0
			}
		}

		val best = undominated.maxByOrNull(::score) ?: return null
		return if (score(best) > 0) best else null
	}

	private fun scanTag(tagId: Identifier, config: OneEnoughConfig.ResolvedConfig): ScanMatch? {
		if (!config.shouldScanTag(tagId)) return null

		val categoryHints = linkedSetOf<String>()
		if (tagId.path.startsWith("$CROPS/")) categoryHints += CROPS
		if (tagId.path.startsWith("$VEGETABLES/")) categoryHints += VEGETABLES

		val groupName = OneEnoughItemTags.normalizeGroupName(tagId.path, config.groupAliases) ?: return null
		return ScanMatch(groupName = groupName, categoryHints = categoryHints)
	}

	private fun buildExistingItemMap(
		builtTags: MutableMap<Identifier, MutableCollection<Any>>,
	): Map<Identifier, Any> {
		val lookup = mutableMapOf<Identifier, Any>()
		builtTags.values.forEach { values ->
			values.forEach { value ->
				val itemId = resolveItemId(value)
				if (itemId != null) {
					lookup[itemId] = value
				}
			}
		}
		return lookup
	}

	private fun mergeInto(
		builtTags: MutableMap<Identifier, MutableCollection<Any>>,
		tagId: Identifier,
		values: Collection<Any>,
	) {
		val merged = linkedSetOf<Any>()
		builtTags[tagId]?.let(merged::addAll)
		merged.addAll(values)
		builtTags[tagId] = merged
	}

	private fun resolveItemId(value: Any): Identifier? {
		val item = when (value) {
			is Item -> value
			is RegistryEntry<*> -> value.value() as? Item
			else -> null
		} ?: return null
		return Registries.ITEM.getId(item)
	}

}
