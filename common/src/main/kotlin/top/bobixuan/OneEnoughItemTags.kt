package top.bobixuan

import net.minecraft.item.Item
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.TagKey
import net.minecraft.util.Identifier

object OneEnoughItemTags {
	private val trailingNamePattern = Regex("(?:^|/)([a-z0-9_]+)$")
	private val invalidNameCharsPattern = Regex("[^a-z0-9_]+")
	private val vowels = setOf('a', 'e', 'i', 'o', 'u')

	val defaultScanRoots = setOf(
		"c:crops/",
		"c:vegetables/",
		"forge:crops/",
		"forge:vegetables/",
		"rusticdelight:crops/",
		"rusticdelight:vegetables/",
		"rusticdelight:food/",
		"veggiesdelight:crops/",
		"veggiesdelight:vegetables/",
		"veggiesdelight:food/",
		"croptopia:crops/",
		"croptopia:vegetables/",
		"croptopia:food/",
	)

	val defaultGroupAliases = linkedMapOf(
		"tomatoes" to "tomato",
		"potatoes" to "potato",
		// Normalize compound-word vs underscore-separated variants to the same group
		"bellpepper" to "bell_pepper",
		"hotpepper" to "hot_pepper",
		"sweetpotato" to "sweet_potato",
		"greenonion" to "green_onion",
		"greenbean" to "green_bean",
		"sweetcorn" to "sweet_corn",
		// Normalize color-before-name → color-after-name patterns
		// e.g. "yellow_bell_pepper" → "bell_pepper_yellow"
		"yellow_bell_pepper" to "bell_pepper_yellow",
		"green_bell_pepper" to "bell_pepper_green",
		"red_bell_pepper" to "bell_pepper_red",
		"yellow_bellpepper" to "bell_pepper_yellow",
		"green_bellpepper" to "bell_pepper_green",
		"red_bellpepper" to "bell_pepper_red",
		// Rustic Delight: uncolored "bell_pepper" = yellow variant
		// Handled via itemGroupOverrides config for per-item precision
	)

	val defaultPublicTagNames = linkedMapOf(
		"rice" to "rice",
	)

	val genericGroupNameBlacklist = setOf(
		"all", "every", "any", "none",
		"crop", "vegetable", "food", "fruit", "grain",
		"block", "item", "ingredient", "material", "resource",
		"component", "element", "thing", "stuff",
		"compressed", "dense", "tier", "level", "grade",
		"mix", "mixed", "combined", "general", "common",
		"various", "multiple", "misc", "other",
		"edible", "plant", "seed", "essence",
		"raw", "cooked", "processed", "crafted",
		"output", "input", "result", "product",
		"base", "basic", "simple", "generic",
	)

	val recipeIngredientFields = setOf(
		"ingredient",
		"ingredients",
		"key",
		"base",
		"addition",
		"template",
	)

	fun itemTag(id: Identifier): TagKey<Item> = TagKey.of(RegistryKeys.ITEM, id)

	fun hubTagId(name: String): Identifier = Identifier(OneEnoughMod.RESOURCE_NAMESPACE, name)

	fun commonTagId(path: String): Identifier = Identifier("c", path)

	fun forgeTagId(path: String): Identifier = Identifier("forge", path)

	fun commonPluralTagId(name: String, overrides: Map<String, String> = emptyMap()): Identifier =
		commonTagId(publicTagPath(name, overrides))

	fun forgePluralTagId(name: String, overrides: Map<String, String> = emptyMap()): Identifier =
		forgeTagId(publicTagPath(name, overrides))

	fun categoryTagIds(name: String, categories: Set<String>): Set<Identifier> = buildSet {
		categories.forEach { category ->
			add(commonTagId("$category/$name"))
			add(forgeTagId("$category/$name"))
		}
	}

	fun normalizeGroupName(raw: String, aliases: Map<String, String> = emptyMap()): String? {
		val match = trailingNamePattern.find(raw.lowercase()) ?: return null
		val candidate = invalidNameCharsPattern.replace(match.groupValues[1], "_").trim('_')
		if (candidate.isEmpty()) {
			return null
		}

		return aliases[candidate] ?: defaultGroupAliases[candidate] ?: singularize(candidate)
	}

	fun publicTagPath(name: String, overrides: Map<String, String> = emptyMap()): String =
		overrides[name] ?: defaultPublicTagNames[name] ?: pluralize(name)

	private fun singularize(name: String): String = when {
		name.endsWith("ies") && name.length > 3 -> name.dropLast(3) + "y"
		name.endsWith("oes") && name.length > 3 -> name.dropLast(2)
		// Skip words ending in -us/-ous/-ss to avoid cactus→cactu, asparagus→asparagu
		name.endsWith("us") || name.endsWith("ss") -> name
		name.endsWith("s") && name.length > 1 -> name.dropLast(1)
		else -> name
	}

	private fun pluralize(name: String): String = when {
		name.endsWith("y") && name.length > 1 && name[name.length - 2] !in vowels -> name.dropLast(1) + "ies"
		name.endsWith("ch") || name.endsWith("sh") || name.endsWith("s") || name.endsWith("x") || name.endsWith("z") -> "$name" + "es"
		name.endsWith("o") -> "$name" + "es"
		else -> "$name" + "s"
	}

	val foodRelatedPathKeywords = setOf(
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
		"knife", "cutting_board", "skillet", "pot", "pan",
		"rolling_pin", "cooking", "kitchen", "bottle", "jar",
		"bowl", "plate", "cup", "mug", "basket", "crate", "box",
	)
}