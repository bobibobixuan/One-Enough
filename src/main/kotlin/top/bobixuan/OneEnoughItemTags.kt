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
	)

	val defaultGroupAliases = linkedMapOf(
		"tomatoes" to "tomato",
		"potatoes" to "potato",
	)

	val defaultPublicTagNames = linkedMapOf(
		"rice" to "rice",
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

	fun hubTagId(name: String): Identifier = Identifier(OneEnoughMod.MOD_ID, name)

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
		name.endsWith("s") && !name.endsWith("ss") && name.length > 1 -> name.dropLast(1)
		else -> name
	}

	private fun pluralize(name: String): String = when {
		name.endsWith("y") && name.length > 1 && name[name.length - 2] !in vowels -> name.dropLast(1) + "ies"
		name.endsWith("ch") || name.endsWith("sh") || name.endsWith("s") || name.endsWith("x") || name.endsWith("z") -> "$name" + "es"
		name.endsWith("o") -> "$name" + "es"
		else -> "$name" + "s"
	}
}