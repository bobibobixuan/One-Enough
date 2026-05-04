# One Enough

## 中文介绍

One Enough 现在按 common + fabric + forge 的结构管理，同一套运行时标签桥接和配方改写逻辑会同时产出 Fabric 和 Forge 两个版本。

其中：

- `common/` 放共享逻辑、Mixin 和通用资源
- `fabric/` 放 Fabric 入口和 `fabric.mod.json`
- `forge/` 放 Forge 入口和 `mods.toml`

Fabric 侧刻意不依赖 Fabric API，只保留 Fabric Loader、Fabric Language Kotlin 和 Mixin，方便在标准 Fabric 环境使用，也更接近信雅互联 / Connector 这类桥接环境的兼容要求。

One Enough 的目标是在游戏启动和数据包重载时，自动识别不同模组声明的农作物标签，动态建出统一标签，并在需要时直接改写配方输入。

当前实现不再依赖 Kotlin 里手写的固定食材名单，而是按命名约定扫描所有已经加载的 item tags。

默认扫描规则：

- `c:crops/*`
- `c:vegetables/*`
- `forge:crops/*`
- `forge:vegetables/*`

默认只扫描上述有目录前缀的标签。如果你有类似 `someaddon:garlic` 这种无目录前缀的 addon 自定义标签，可以在配置文件中将 `scanBareNamespaceTags` 设为 `true` 来启用扫描，并可通过 `excludedBareNamespaces` 排除不希望扫描的命名空间。

系统会从标签路径末尾动态提取食材名，例如：

- `c:crops/garlic` -> `garlic`
- `c:vegetables/tomatoes` -> `tomato`
- `someaddon:onion` -> `onion`

识别成功后，运行时会做三件事：

1. 为每个食材创建一个内部 hub 标签，例如 `one-enough-mod:garlic`
2. 自动生成公共兼容标签，例如 `c:garlics`、`forge:garlics`，以及按类别补出的 `c:crops/garlic`、`forge:vegetables/garlic` 之类标签
3. 把所有来源标签里的物品合并回这些标签，并反向写回原始来源标签，让不同模组的标签成员统一

如果某个配方 JSON 把输入写死成了具体物品 id，而不是标签，这一版还会在 `RecipeManager` 载入配方时直接改写常见输入字段，把它替换成动态公共标签。当前会处理的字段包括：

- `ingredient`
- `ingredients`
- `key`
- `base`
- `addition`
- `template`

这意味着就算别的模组把输入写成了 `mod_a:corn`，只要这个物品已经在运行时被识别进了 `corn` 组，配方也能被重写成使用 `c:corns` 这类公共标签。

## English Summary

One Enough now ships from a common + fabric + forge layout so the same runtime tag bridge can be built for both mod loaders.

The Fabric variant intentionally avoids Fabric API and only depends on Fabric Loader, Fabric Language Kotlin, and Mixins, which keeps it closer to Connector-friendly expectations.

One Enough is a compatibility mod for food and farming packs that ship duplicate crops under different item ids.

Instead of maintaining a hardcoded crop list, the mod now scans loaded item tags by naming convention, extracts a canonical ingredient name, builds runtime hub tags under `one-enough-mod:*`, and publishes shared compatibility tags such as `c:garlics` and `forge:garlics`.

It also intercepts recipe loading and rewrites hardcoded ingredient item ids into the generated shared tag when the item can be mapped to one dynamic ingredient group.

## How It Works

At tag load time, the mod inspects the item tag groups that were already loaded by Minecraft and other mods. Matching tags are grouped by the extracted ingredient name and merged into:

- the original source tags
- a private runtime hub tag under `one-enough-mod:*`
- public shared tags under `c:*` and `forge:*`

At recipe load time, the mod walks through common ingredient-bearing JSON fields and swaps `{"item": "mod:id"}` objects into `{"tag": "c:plural_name"}` when that item was already mapped to one unambiguous dynamic ingredient group.

## Config File

The mod writes a config file to `config/one-enough-mod.json` on first launch.

This file provides the safety net for false positives and naming edge cases. Important fields:

- `scanRoots`: tag prefixes to scan
- `scanBareNamespaceTags`: whether bare tags like `someaddon:garlic` should be considered (default `false`)
- `excludedBareNamespaces`: namespaces excluded from bare-tag scanning (default `["minecraft", "one-enough-mod", "c", "forge"]`)
- `whitelistTags` / `blacklistTags`: exact tag id overrides
- `whitelistGroups` / `blacklistGroups`: allowlist or blocklist canonical ingredient names
- `itemBlacklist`: items that must never be pulled into a dynamic group
- `groupAliases`: normalize names such as `tomatoes -> tomato`
- `publicTagNames`: override the generated public tag path for special cases such as `rice -> rice`
- `rewriteRecipes`: global on/off switch for recipe interception
- `recipeBlacklist`: recipes that must never be rewritten

## Notes

- The runtime tag scan and recipe rewrite paths are implemented in Kotlin and injected through Mixins during data pack reload.
- Shared runtime code now lives in `common/`, while loader bootstraps live in `fabric/` and `forge/`.
- The Forge build uses Kotlin for Forge and `mods.toml`; the Fabric build uses `fabric.mod.json` and no Fabric API dependency.
- Ambiguous items are intentionally not rewritten in recipes. If multiple dynamic groups claim the same item, recipe replacement skips it until you resolve the overlap in the config.

## Build

Build both variants with:

```powershell
\.\gradlew.bat build
```

Build only the Fabric variant with:

```powershell
\.\gradlew.bat buildFabric
```

Build only the Forge variant with:

```powershell
\.\gradlew.bat buildForge
```

If you only want to validate compilation, use:

```powershell
\.\gradlew.bat :fabric:classes
\.\gradlew.bat :forge:classes
```

Build outputs:

- `fabric/build/libs/one-enough-mod-fabric-<version>.jar`
- `forge/build/libs/one-enough-mod-forge-<version>.jar`

## License

This project is available under the CC0 license.
