# One Enough — English

## 1. Overview

One Enough is a compatibility mod for food and farming modpacks, especially packs that combine multiple Farmer's Delight-style addons. Its job is to make duplicate crops and vegetables behave like interchangeable ingredients even when different mods use different item ids.

Instead of maintaining a hardcoded ingredient registry, the mod scans loaded item tags at runtime, classifies likely raw-ingredient sources conservatively, publishes shared compatibility tags, and rewrites simple hardcoded recipe inputs into tag-based inputs when that rewrite is safe.

## 2. Core capabilities

The project currently provides six core capabilities:

1. runtime item-tag scanning instead of static hardcoded crop lists,
2. evidence-based classification using source confidence and member-name checks,
3. generation of private hub tags and public `c:*` / `forge:*` tags,
4. runtime merging of source-tag members into unified groups,
5. recipe JSON rewriting for simple hardcoded item ingredients,
6. state-hash-based caching of classification results.

## 3. Repository layout

- `common/`: shared Kotlin logic, Mixins, shared resources, common tag templates
- `fabric/`: Fabric bootstrap, metadata, platform-path implementation
- `forge/`: Forge bootstrap, metadata, platform-path implementation
- `analysis/`: ecosystem scans and rule-tuning research artifacts

This layout keeps the platform layers thin and the runtime logic shared.

## 4. Runtime integration points

Both loaders ultimately call `OneEnoughMod.init()`. The actual runtime behavior is injected through two Mixins:

1. `TagGroupLoaderMixin` triggers runtime tag merging after item-tag groups are built.
2. `RecipeManagerMixin` triggers recipe rewriting when recipes are applied.

That gives the mod two main runtime pipelines:

- tag pipeline: scan, classify, group, publish snapshot, merge tags
- recipe pipeline: read snapshot, verify safety, rewrite eligible ingredients

## 5. How automatic classification works

The classifier is designed to be conservative. Its goal is not to accept as much as possible, but to accept only when there is enough evidence.

The high-level flow is:

1. pre-filter obviously structural, helper, processed, and predicate-like tags,
2. classify a source tag as strong or weak,
3. validate whether member item names support the inferred group name,
4. apply different acceptance thresholds,
5. publish final groups and cache them.

Important default scan roots are:

- `c:crops/*`
- `c:vegetables/*`
- `forge:crops/*`
- `forge:vegetables/*`

Important excluded patterns include seeds, storage blocks, crates, bags, sliced or cooked products, helper predicates like `can_*`, display-result paths, and suffixes such as `*_food`, `*_snack`, `*_plantable`, and `*_feedable`.

For a detailed rule breakdown, see [classification-rules.md](../classification-rules.md).

## 6. Unified tag publication

Once a stable ingredient group is accepted, the runtime bridge publishes three categories of tags:

1. the original source tags, enriched with merged members,
2. a private hub tag under `one-enough-mod:*`,
3. public compatibility tags under `c:*` and `forge:*`, plus category-tag variants when applicable.

## 7. Recipe rewriting

Recipe rewriting is intentionally narrow. The mod only rewrites plain ingredient objects that contain a single `item` field.

Handled fields include:

- `ingredient`
- `ingredients`
- `key`
- `base`
- `addition`
- `template`

It deliberately skips:

- objects that already use `tag`,
- custom ingredient payloads with extra fields,
- ambiguous items claimed by multiple dynamic groups,
- blacklisted recipes.

## 8. Configuration

On first launch, the mod writes `config/one-enough-mod.json`.

Important fields include:

- `autoDetect`
- `scanRoots`
- `scanBareNamespaceTags`
- `excludedBareNamespaces`
- `whitelistTags` / `blacklistTags`
- `whitelistGroups` / `blacklistGroups`
- `itemBlacklist`
- `groupAliases`
- `publicTagNames`
- `rewriteRecipes`
- `recipeBlacklist`
- `itemGroupOverrides`
- `cacheEnabled`
- `cacheExpiryMins`

Note that `cacheExpiryMins` exists in the config model, but current cache invalidation still primarily depends on cache version and state hash rather than time-based expiry.

## 9. Cache behavior

Classification results are stored in `one-enough-mod-cache.json`. The cache is invalidated when the effective configuration, group rules, overrides, or loaded tag membership set changes.

## 10. Platform baseline

Current baseline:

- Minecraft 1.20.1
- Java 17
- Kotlin 2.0.0
- Fabric Loader 0.19.2
- Fabric Language Kotlin 1.11.0+kotlin.2.0.0
- Forge 47.4.20
- Kotlin for Forge 4.11.0

The Fabric side intentionally avoids Fabric API.

## 11. Build

Build both variants:

```powershell
.\gradlew.bat build
```

Build only Fabric:

```powershell
.\gradlew.bat buildFabric
```

Build only Forge:

```powershell
.\gradlew.bat buildForge
```

Compile-only validation:

```powershell
.\gradlew.bat :fabric:classes
.\gradlew.bat :forge:classes
```

Artifacts:

- `fabric/build/libs/one-enough-mod-fabric-<version>.jar`
- `forge/build/libs/one-enough-mod-forge-<version>.jar`

## 12. Current limitations

1. configuration is process-cached, so restart is usually required after edits,
2. recipe rewriting only covers plain `{"item":"..."}` ingredient objects,
3. ambiguous items are intentionally skipped,
4. the classifier is conservative by design,
5. the mod depends on other mods exposing meaningful source tags.

## 13. Related documents

- [classification-rules.md](../classification-rules.md)
- [review.md](../review.md)
- [analysis/github-delight-scan/README.md](../analysis/github-delight-scan/README.md)
