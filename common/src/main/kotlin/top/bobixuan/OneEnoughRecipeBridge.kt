package top.bobixuan

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import net.minecraft.util.Identifier
import org.slf4j.LoggerFactory

object OneEnoughRecipeBridge {
	private val logger = LoggerFactory.getLogger("${OneEnoughMod.RESOURCE_NAMESPACE}/runtime-recipes")

	data class RewriteSummary(
		val rewrittenRecipes: Int,
		val rewrittenIngredients: Int,
		val skippedAmbiguousIngredients: Int,
	)

	private data class RewriteCounters(
		var rewrittenIngredients: Int = 0,
		var skippedAmbiguousIngredients: Int = 0,
	)

	@JvmStatic
	fun rewriteRecipes(preparedRecipes: Map<Identifier, JsonElement>): RewriteSummary {
		val config = OneEnoughConfig.current()
		if (!config.rewriteRecipes) {
			return RewriteSummary(0, 0, 0)
		}

		val snapshot = OneEnoughRuntimeTagBridge.currentSnapshot()
		if (snapshot.groups.isEmpty()) {
			return RewriteSummary(0, 0, 0)
		}

		var rewrittenRecipes = 0
		val counters = RewriteCounters()

		preparedRecipes.forEach { (recipeId, recipeJson) ->
			if (!config.isRecipeAllowed(recipeId)) {
				return@forEach
			}

			val recipeObject = recipeJson as? JsonObject ?: return@forEach
			val rewrittenBefore = counters.rewrittenIngredients
			rewriteRecipeObject(recipeObject, snapshot, counters)
			if (counters.rewrittenIngredients > rewrittenBefore) {
				rewrittenRecipes += 1
			}
		}

		if (counters.rewrittenIngredients > 0) {
			logger.info(
				"Rewrote {} hardcoded ingredient entries across {} recipes",
				counters.rewrittenIngredients,
				rewrittenRecipes,
			)
		}

		return RewriteSummary(
			rewrittenRecipes = rewrittenRecipes,
			rewrittenIngredients = counters.rewrittenIngredients,
			skippedAmbiguousIngredients = counters.skippedAmbiguousIngredients,
		)
	}

	private fun rewriteRecipeObject(
		recipeObject: JsonObject,
		snapshot: OneEnoughRuntimeTagBridge.RuntimeSnapshot,
		counters: RewriteCounters,
	) {
		recipeObject.entrySet().forEach { (fieldName, fieldValue) ->
			when (fieldName) {
				"ingredient", "ingredients", "base", "addition", "template" -> rewriteIngredientElement(fieldValue, snapshot, counters)
				"key" -> rewriteKeyObject(fieldValue, snapshot, counters)
			}
		}
	}

	private fun rewriteKeyObject(
		fieldValue: JsonElement,
		snapshot: OneEnoughRuntimeTagBridge.RuntimeSnapshot,
		counters: RewriteCounters,
	) {
		val keyObject = fieldValue as? JsonObject ?: return
		keyObject.entrySet().forEach { (_, ingredientValue) ->
			rewriteIngredientElement(ingredientValue, snapshot, counters)
		}
	}

	private fun rewriteIngredientElement(
		element: JsonElement,
		snapshot: OneEnoughRuntimeTagBridge.RuntimeSnapshot,
		counters: RewriteCounters,
	) {
		when {
			element.isJsonArray -> element.asJsonArray.forEach { child ->
				rewriteIngredientElement(child, snapshot, counters)
			}
			element.isJsonObject -> rewriteIngredientObject(element.asJsonObject, snapshot, counters)
		}
	}

	private fun rewriteIngredientObject(
		ingredientObject: JsonObject,
		snapshot: OneEnoughRuntimeTagBridge.RuntimeSnapshot,
		counters: RewriteCounters,
	) {
		if (!ingredientObject.has("item") || ingredientObject.has("tag")) {
			return
		}

		// Only rewrite plain vanilla-style item ingredients. Custom ingredient payloads
		// frequently carry a type, NBT, amount, or other loader-specific fields that
		// would become invalid if we blindly replaced `item` with `tag`.
		if (ingredientObject.entrySet().any { (key, _) -> key != "item" }) {
			return
		}

		val itemElement = ingredientObject.get("item")
		if (!itemElement.isJsonPrimitive || !itemElement.asJsonPrimitive.isString) {
			return
		}

		val itemId = Identifier.tryParse(itemElement.asString) ?: return
		if (itemId in snapshot.ambiguousItems) {
			counters.skippedAmbiguousIngredients += 1
			return
		}

		val group = snapshot.itemToGroup[itemId] ?: return
		ingredientObject.remove("item")
		ingredientObject.addProperty("tag", group.recipeTagId.toString())
		counters.rewrittenIngredients += 1
	}
}