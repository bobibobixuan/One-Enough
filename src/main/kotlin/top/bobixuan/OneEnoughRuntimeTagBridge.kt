package top.bobixuan

import net.minecraft.item.Item
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import org.slf4j.LoggerFactory

object OneEnoughRuntimeTagBridge {
	private val logger = LoggerFactory.getLogger("${OneEnoughMod.MOD_ID}/runtime-tags")
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
	)

	private data class ScanMatch(
		val groupName: String,
		val categoryHints: Set<String>,
	)

	private data class MutableDiscoveredGroup(
		val name: String,
		val sourceTags: LinkedHashSet<Identifier> = linkedSetOf(),
		val categoryHints: LinkedHashSet<String> = linkedSetOf(),
		val items: LinkedHashSet<Item> = linkedSetOf(),
		val itemIds: LinkedHashSet<Identifier> = linkedSetOf(),
	)

	@Volatile
	private var snapshot: RuntimeSnapshot = RuntimeSnapshot.EMPTY

	@JvmStatic
	fun currentSnapshot(): RuntimeSnapshot = snapshot

	@JvmStatic
	fun mergeBuiltItemTags(
		builtTags: MutableMap<Identifier, MutableCollection<Item>>,
	): MergeSummary {
		val config = OneEnoughConfig.current()
		val discoveredGroups = linkedMapOf<String, MutableDiscoveredGroup>()

		builtTags.forEach { (tagId, values) ->
			val scanMatch = scanTag(tagId, config) ?: return@forEach
			if (!config.isGroupAllowed(scanMatch.groupName)) {
				return@forEach
			}

			val group = discoveredGroups.getOrPut(scanMatch.groupName) {
				MutableDiscoveredGroup(scanMatch.groupName)
			}

			group.sourceTags += tagId
			group.categoryHints += scanMatch.categoryHints

			values.forEach { item ->
				val itemId = Registries.ITEM.getId(item)
				if (itemId !in config.itemBlacklist) {
					group.items += item
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

		discoveredGroups.values.forEach { group ->
			if (group.items.isEmpty()) {
				return@forEach
			}

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
				val previousGroup = groupNameByItem.putIfAbsent(itemId, dynamicGroup.name)
				if (previousGroup != null && previousGroup != dynamicGroup.name) {
					ambiguousItems += itemId
				}
			}

			if (group.sourceTags.size > 1) {
				logger.info(
					"Merged dynamic ingredient group '{}' from {} discovered source tags",
					group.name,
					group.sourceTags.size,
				)
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

	private fun scanTag(
		tagId: Identifier,
		config: OneEnoughConfig.ResolvedConfig,
	): ScanMatch? {
		if (!config.shouldScanTag(tagId)) {
			return null
		}

		val categoryHints = linkedSetOf<String>()
		if (tagId.path.startsWith("$CROPS/")) {
			categoryHints += CROPS
		}
		if (tagId.path.startsWith("$VEGETABLES/")) {
			categoryHints += VEGETABLES
		}

		val groupName = OneEnoughItemTags.normalizeGroupName(tagId.path, config.groupAliases) ?: return null
		return ScanMatch(groupName = groupName, categoryHints = categoryHints)
	}

	private fun mergeInto(
		builtTags: MutableMap<Identifier, MutableCollection<Item>>,
		tagId: Identifier,
		values: Collection<Item>,
	) {
		val merged = linkedSetOf<Item>()
		builtTags[tagId]?.let(merged::addAll)
		merged.addAll(values)
		builtTags[tagId] = merged
	}
}