package top.bobixuan

import net.minecraft.item.Item
import net.minecraft.registry.Registries
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.util.Identifier
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

object OneEnoughTagClassifier {
	private val logger = LoggerFactory.getLogger("${OneEnoughMod.RESOURCE_NAMESPACE}/classifier")
	private const val CROPS = "crops"
	private const val VEGETABLES = "vegetables"
	private const val FOODS = "foods"
	private val excludedTagSegmentPattern = Regex(
		"(?:^|_)(seed|seeds|storage|storage_block|storage_blocks|crate|crates|bag|bags|slice|slices|soup|stew|salad|jam|jelly|juice|powder|meal|flour|oil|syrup|sauce|roasted|cooked|smoked|baked|fried|stuffed|pickled|fermented|dried|pie|cake|cookie|sandwich|burger|taco|dumpling|roll|rolls|pasta|ingredient|ingredients|mix|mixture|bottle|jar|bowl|plate|cup|mug|box|boxes)(?:$|_)",
	)
	private val excludedItemPathPattern = Regex(
		"(?:^|_)(seed|seeds|seed_bag|seeds_bag|crate|crates|bag|bags|slice|slices|soup|stew|salad|jam|jelly|juice|powder|meal|flour|oil|syrup|sauce|roasted|cooked|smoked|baked|fried|stuffed|pickled|fermented|dried|pie|cake|cookie|sandwich|burger|taco|dumpling|roll|rolls|pasta|ingredient|ingredients|mix|mixture|bottle|jar|bowl|plate|cup|mug|box|boxes)(?:$|_)",
	)
	private val auxiliaryTagPathPrefixes = listOf(
		"jei_display_results/",
		"display_results/",
	)
	private val auxiliaryTagSegmentPrefixes = listOf(
		"can_",
		"flat_on_",
		"used_in_",
		"works_with_",
		"supports_",
	)
	private val auxiliaryTagSegmentSuffixes = listOf(
		"_food",
		"_foods",
		"_snack",
		"_snacks",
		"_plantable",
		"_feedable",
	)
	private val allowedVariantSuffixes = setOf(
		"red", "green", "yellow", "white", "black", "purple", "orange", "brown", "pink", "blue", "gold", "golden",
	)

	private enum class SourceTagConfidence {
		STRONG,
		WEAK,
	}

	private enum class TagCategory(val id: String) {
		CROPS("crops"),
		VEGETABLES("vegetables"),
		FOODS("foods"),
	}

	private data class SourceTagCandidate(
		val tagId: Identifier,
		val groupName: String,
		val categoryHints: Set<TagCategory>,
		val confidence: SourceTagConfidence,
	)

	private data class ClassifiedAssignment(
		val sourceTagId: Identifier,
		val itemId: Identifier,
		val groupName: String,
		val categoryHints: Set<TagCategory>,
	)

	private data class TagEvaluation(
		val assignments: List<ClassifiedAssignment>,
		val consideredMembers: Int,
		val acceptedMembers: Int,
	)

	private data class GroupAccumulator(
		val name: String,
		val sourceTagIds: LinkedHashSet<Identifier> = linkedSetOf(),
		val memberItemIds: LinkedHashSet<Identifier> = linkedSetOf(),
		val categoryHints: LinkedHashSet<TagCategory> = linkedSetOf(),
	)

	private val knownFoodItems = ConcurrentHashMap.newKeySet<Identifier>()

	fun clearFoodItemsCache() {
		knownFoodItems.clear()
	}

	fun isFoodItem(item: Item): Boolean = item.isFood

	fun classifyAsFood(itemId: Identifier, item: Item): Boolean {
		if (item.isFood) {
			knownFoodItems.add(itemId)
			return true
		}

		val path = itemId.path.lowercase()
		if (excludedItemPathPattern.containsMatchIn(path)) {
			return false
		}

		val foodKeywords = listOf(
			"crop", "vegetable", "fruit", "food", "seed", "berry",
			"meat", "fish", "egg", "milk", "cheese", "butter", "cream",
			"bread", "cake", "pie", "soup", "stew", "salad", "rice",
			"wheat", "corn", "potato", "carrot", "tomato", "onion",
			"garlic", "pepper", "lettuce", "cabbage", "beet", "bean",
			"apple", "banana", "orange", "grape", "melon", "pumpkin",
			"mushroom", "tea", "coffee", "juice", "wine", "beer",
			"sugar", "salt", "spice", "herb", "oil", "vinegar",
			"flour", "dough", "pasta", "noodle", "dumpling", "taco",
			"burger", "sandwich", "pizza", "sushi", "kimchi", "tofu",
			"jam", "jelly", "honey", "syrup", "chocolate", "candy",
			"cookie", "biscuit", "muffin", "pancake", "waffle",
			"roast", "grill", "fry", "bake", "cook", "smoke",
			"delight", "tasty", "yummy", "delicious", "cuisine",
			"edible", "nutrition", "meal", "snack", "dish",
		)

		for (keyword in foodKeywords) {
			if (keyword in path) {
				return true
			}
		}

		return false
	}

	data class ClassificationResult(
		val groups: Map<String, OneEnoughCache.CachedGroup>,
		val totalSourceTags: Int,
		val totalItems: Int,
	)

	fun classifyAllTags(
		allTags: Map<Identifier, Collection<Any>>,
		config: OneEnoughConfig.ResolvedConfig,
	): ClassificationResult {
		logger.info("Starting auto-classification of {} item tags", allTags.size)

		val relevantTags = filterRelevantTags(allTags, config)

		logger.info("Found {} relevant food/crop tags out of {} total", relevantTags.size, allTags.size)

		val assignments = classifyAssignments(relevantTags, config)
		val groups = buildGroups(assignments)

		val totalSourceTags = groups.values.sumOf { it.sourceTags.size }
		val totalItems = groups.values.sumOf { it.members.size }

		logger.info(
			"Auto-classification complete: {} groups, {} source tags, {} unique items",
			groups.size, totalSourceTags, totalItems,
		)

		return ClassificationResult(
			groups = groups,
			totalSourceTags = totalSourceTags,
			totalItems = totalItems,
		)
	}

	private fun filterRelevantTags(
		allTags: Map<Identifier, Collection<Any>>,
		config: OneEnoughConfig.ResolvedConfig,
	): Map<Identifier, Collection<Any>> {
		val configWhitelist = config.whitelistTags.associateWith { true }
		val configBlacklist = config.blacklistTags.associateWith { true }

		return allTags.filter { (tagId, values) ->
			// Config whitelist always wins
			if (tagId in configWhitelist) return@filter true
			// Config blacklist always excludes
			if (tagId in configBlacklist) return@filter false

			// Skip known structural or helper tags before content-scanning.
			if (isStructuralTag(tagId) || isAuxiliaryTag(tagId) || isExcludedSourceTag(tagId)) return@filter false

			// If auto-detect is on, evaluate by item content
			if (config.autoDetect) {
				return@filter isFoodRelatedTag(values)
			}

			// Manual mode: use scanRoots and bare-tag config
			return@filter config.shouldScanTag(tagId)
		}
	}

	private fun isStructuralTag(tagId: Identifier): Boolean {
		val path = tagId.path.lowercase()
		val structuralPrefixes = listOf(
			"mineable/", "needs_", "fences", "fence_gates", "walls",
			"stairs", "slabs", "doors", "trapdoors", "buttons",
			"pressure_plates", "signs", "banners", "beds",
		)

		for (prefix in structuralPrefixes) {
			if (path.startsWith(prefix)) return true
		}

		return false
	}

	private fun isAuxiliaryTag(tagId: Identifier): Boolean {
		val path = tagId.path.lowercase()
		if (auxiliaryTagPathPrefixes.any(path::startsWith)) {
			return true
		}

		return path
			.split('/')
			.any { segment ->
				auxiliaryTagSegmentPrefixes.any(segment::startsWith) ||
					auxiliaryTagSegmentSuffixes.any(segment::endsWith)
			}
	}

	private fun isExcludedSourceTag(tagId: Identifier): Boolean {
		return tagId.path
			.lowercase()
			.split('/')
			.any(excludedTagSegmentPattern::containsMatchIn)
	}

	private fun isFoodRelatedTag(values: Collection<Any>): Boolean {
		// A tag is food-related if a significant proportion of its entries are food items
		if (values.isEmpty()) return false

		var foodCount = 0
		var nonFoodCount = 0

		values.forEach { value ->
			val item = resolveItem(value)
			if (item != null) {
				val itemId = Registries.ITEM.getId(item)
				if (itemId in knownFoodItems || item.isFood || classifyAsFood(itemId, item)) {
					foodCount++
				} else {
					nonFoodCount++
				}
			}
		}

		val total = foodCount + nonFoodCount
		if (total == 0) return false

		return foodCount.toDouble() / total >= 0.3
	}

	private fun classifyAssignments(
		relevantTags: Map<Identifier, Collection<Any>>,
		config: OneEnoughConfig.ResolvedConfig,
	): List<ClassifiedAssignment> {
		val assignments = mutableListOf<ClassifiedAssignment>()

		relevantTags.forEach { (tagId, values) ->
			val sourceTag = classifySourceTag(tagId, config) ?: return@forEach
			val evaluation = evaluateSourceTag(sourceTag, values, config)
			if (shouldAcceptSourceTag(sourceTag, evaluation)) {
				assignments += evaluation.assignments
			}
		}

		return assignments
	}

	private fun classifySourceTag(
		tagId: Identifier,
		config: OneEnoughConfig.ResolvedConfig,
	): SourceTagCandidate? {
		val groupName = OneEnoughItemTags.normalizeGroupName(tagId.path, config.groupAliases) ?: return null
		if (!config.isGroupAllowed(groupName)) return null

		val categoryHints = categoryHintsForTag(tagId)
		val confidence = if (
			tagId in config.whitelistTags ||
			TagCategory.CROPS in categoryHints ||
			TagCategory.VEGETABLES in categoryHints
		) {
			SourceTagConfidence.STRONG
		} else {
			SourceTagConfidence.WEAK
		}

		return SourceTagCandidate(
			tagId = tagId,
			groupName = groupName,
			categoryHints = categoryHints,
			confidence = confidence,
		)
	}

	private fun evaluateSourceTag(
		sourceTag: SourceTagCandidate,
		values: Collection<Any>,
		config: OneEnoughConfig.ResolvedConfig,
	): TagEvaluation {
		val acceptedAssignments = mutableListOf<ClassifiedAssignment>()
		var consideredMembers = 0
		var acceptedMembers = 0

		values.forEach { value ->
			val itemId = resolveItemId(value) ?: return@forEach
			if (itemId in config.itemBlacklist) return@forEach

			consideredMembers += 1

			val overriddenGroup = config.itemGroupOverrides[itemId]
			if (overriddenGroup != null) {
				if (config.isGroupAllowed(overriddenGroup)) {
					acceptedAssignments += ClassifiedAssignment(
						sourceTagId = sourceTag.tagId,
						itemId = itemId,
						groupName = overriddenGroup,
						categoryHints = sourceTag.categoryHints,
					)
					acceptedMembers += 1
				}
				return@forEach
			}

			if (isExcludedGroupMember(itemId)) {
				return@forEach
			}

			val normalizedItemName = OneEnoughItemTags.normalizeGroupName(itemId.path, config.groupAliases) ?: return@forEach
			if (!isCompatibleRawMemberName(normalizedItemName, sourceTag.groupName)) {
				return@forEach
			}

			acceptedAssignments += ClassifiedAssignment(
				sourceTagId = sourceTag.tagId,
				itemId = itemId,
				groupName = sourceTag.groupName,
				categoryHints = sourceTag.categoryHints,
			)
			acceptedMembers += 1
		}

		return TagEvaluation(
			assignments = acceptedAssignments,
			consideredMembers = consideredMembers,
			acceptedMembers = acceptedMembers,
		)
	}

	private fun shouldAcceptSourceTag(
		sourceTag: SourceTagCandidate,
		evaluation: TagEvaluation,
	): Boolean {
		if (evaluation.consideredMembers == 0 || evaluation.acceptedMembers == 0) {
			return false
		}

		val acceptedRatio = evaluation.acceptedMembers.toDouble() / evaluation.consideredMembers.toDouble()
		return when (sourceTag.confidence) {
			SourceTagConfidence.STRONG -> acceptedRatio >= 0.5
			SourceTagConfidence.WEAK -> evaluation.acceptedMembers == evaluation.consideredMembers
		}
	}

	private fun buildGroups(assignments: Collection<ClassifiedAssignment>): Map<String, OneEnoughCache.CachedGroup> {
		val accumulators = linkedMapOf<String, GroupAccumulator>()
		assignments.forEach { assignment ->
			val group = accumulators.getOrPut(assignment.groupName) {
				GroupAccumulator(assignment.groupName)
			}
			group.sourceTagIds += assignment.sourceTagId
			group.memberItemIds += assignment.itemId
			group.categoryHints.addAll(assignment.categoryHints)
		}

		val groups = linkedMapOf<String, OneEnoughCache.CachedGroup>()

		// Build final groups
		accumulators.values.forEach { group ->
			val categoryHints = inferCategoryHints(group)

			groups[group.name] = OneEnoughCache.CachedGroup(
				name = group.name,
				members = group.memberItemIds.map(Identifier::toString).toSortedSet(),
				sourceTags = group.sourceTagIds.map(Identifier::toString).toSortedSet(),
				categoryHints = categoryHints.map(TagCategory::id).toSortedSet(),
			)
		}

		return groups
	}

	private fun inferCategoryHints(group: GroupAccumulator): Set<TagCategory> {
		val categoryHints = group.categoryHints.toMutableSet()
		val lowerGroupName = group.name.replace("_", "")
		if ("crop" in lowerGroupName) categoryHints += TagCategory.CROPS
		if ("vegetable" in lowerGroupName || "veggie" in lowerGroupName) categoryHints += TagCategory.VEGETABLES
		if ("food" in lowerGroupName) categoryHints += TagCategory.FOODS

		group.memberItemIds.forEach { memberId ->
			val memberPath = memberId.path.lowercase()
			if ("crop" in memberPath) categoryHints += TagCategory.CROPS
			if ("vegetable" in memberPath) categoryHints += TagCategory.VEGETABLES
		}

		return categoryHints
	}

	private fun categoryHintsForTag(tagId: Identifier): Set<TagCategory> = buildSet {
		if (tagId.path.startsWith("$CROPS/")) add(TagCategory.CROPS)
		if (tagId.path.startsWith("$VEGETABLES/")) add(TagCategory.VEGETABLES)
		if (tagId.path.startsWith("$FOODS/")) add(TagCategory.FOODS)
	}

	private fun isCompatibleRawMemberName(
		normalizedItemName: String,
		groupName: String,
	): Boolean {
		if (normalizedItemName == groupName) {
			return true
		}

		if (!normalizedItemName.startsWith("${groupName}_")) {
			return false
		}

		val suffix = normalizedItemName.removePrefix("${groupName}_")
		if (suffix.isEmpty()) {
			return false
		}

		return suffix.split('_').all(allowedVariantSuffixes::contains)
	}

	private fun isExcludedGroupMember(itemId: Identifier): Boolean {
		return excludedItemPathPattern.containsMatchIn(itemId.path.lowercase())
	}

	private fun resolveItem(value: Any): Item? = when (value) {
		is Item -> value
		is RegistryEntry<*> -> value.value() as? Item
		else -> null
	}

	private fun resolveItemId(value: Any): Identifier? = resolveItem(value)?.let(Registries.ITEM::getId)
}
