package top.bobixuan.mixin;

import net.minecraft.registry.tag.TagGroupLoader;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.bobixuan.OneEnoughRuntimeTagBridge;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@Mixin(TagGroupLoader.class)
public abstract class TagGroupLoaderMixin<T> {
	@Shadow @Final private Function<Identifier, Optional<? extends T>> registryGetter;
	@Shadow @Final private String dataType;

	private static final Logger ONE_ENOUGH_LOGGER = LoggerFactory.getLogger("one-enough-mod/runtime-tags");

	@Inject(method = "buildGroup", at = @At("RETURN"), cancellable = true)
	private void oneEnough$mergeRuntimeCompat(
		Map<Identifier, List<TagGroupLoader.TrackedEntry>> tags,
		CallbackInfoReturnable<Map<Identifier, Collection<T>>> cir
	) {
		if (!"tags/items".equals(this.dataType)) {
			return;
		}

		Map<Identifier, Collection<T>> originalTags = cir.getReturnValue();
		Map<Identifier, Collection<T>> mutableTags = new LinkedHashMap<>();
		originalTags.forEach((tagId, values) -> mutableTags.put(tagId, new LinkedHashSet<>(values)));

		// Item tag values can be wrapped registry entries on some runtimes, so keep the original objects.
		@SuppressWarnings("unchecked")
		Map<Identifier, Collection<Object>> itemTags = (Map<Identifier, Collection<Object>>) (Map<?, ?>) mutableTags;

		OneEnoughRuntimeTagBridge.MergeSummary summary = OneEnoughRuntimeTagBridge.mergeBuiltItemTags(
			itemTags
		);

		if (summary.getMergedGroups() > 0) {
			ONE_ENOUGH_LOGGER.info(
				"Runtime tag merge touched {} tags across {} ingredient groups, {} discovered source tags, and {} rewritable items",
				summary.getTouchedTags(),
				summary.getMergedGroups(),
				summary.getDiscoveredSourceTags(),
				summary.getRewritableItems()
			);
		}

		cir.setReturnValue(new LinkedHashMap<>(mutableTags));
	}
}