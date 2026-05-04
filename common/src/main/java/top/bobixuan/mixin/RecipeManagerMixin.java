package top.bobixuan.mixin;

import com.google.gson.JsonElement;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.bobixuan.OneEnoughRecipeBridge;

import java.util.Map;

@Mixin(RecipeManager.class)
public class RecipeManagerMixin {
	private static final Logger ONE_ENOUGH_LOGGER = LoggerFactory.getLogger("one-enough-mod/runtime-recipes");

	@Inject(method = "apply", at = @At("HEAD"))
	private void oneEnough$rewriteDynamicIngredients(
		Map<Identifier, JsonElement> prepared,
		ResourceManager resourceManager,
		Profiler profiler,
		CallbackInfo ci
	) {
		OneEnoughRecipeBridge.RewriteSummary summary = OneEnoughRecipeBridge.rewriteRecipes(prepared);

		if (summary.getRewrittenIngredients() > 0 || summary.getSkippedAmbiguousIngredients() > 0) {
			ONE_ENOUGH_LOGGER.info(
				"Runtime recipe rewrite changed {} ingredient entries across {} recipes; skipped {} ambiguous ingredients",
				summary.getRewrittenIngredients(),
				summary.getRewrittenRecipes(),
				summary.getSkippedAmbiguousIngredients()
			);
		}
	}
}