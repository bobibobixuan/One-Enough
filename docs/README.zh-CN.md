# One Enough — 中文

## 1. 项目概述

One Enough 是一个面向食物整合包和农作物附属生态的兼容模组。它解决的问题很具体：多个 Farmer's Delight 风格模组会各自注册名字接近、用途相同、但物品 id 不同的作物或蔬菜，导致标签不统一、配方无法互通、玩家需要同时携带多套"本质一样"的材料。

这个项目当前不再依赖手写固定食材名单，而是改成运行时按命名规则和证据规则扫描已加载的 item tags，自动识别哪些标签像"原始作物来源"，把它们合并成统一兼容标签，并在需要时把硬编码物品输入的配方改写为标签输入。

## 2. 核心能力

项目当前的核心能力有 6 个：

1. 自动扫描已加载的 item tags，而不是维护一份硬编码食材表。
2. 按"来源可信度 + 成员证据 + 接受阈值"的规则保守分类。
3. 为每个识别出的食材创建内部 hub 标签和公开兼容标签。
4. 在标签加载阶段把来源标签成员并回统一后的标签集合。
5. 在配方加载阶段把简单的硬编码 `{"item":"..."}` 输入改写为 `{"tag":"..."}`。
6. 通过状态哈希缓存分类结果，避免每次启动都完全重算。

## 3. 仓库结构

这个项目采用 `common + fabric + forge` 的三段结构：

- `common/`：放共享 Kotlin 逻辑、Mixin、共享资源、公共标签模板。
- `fabric/`：放 Fabric 入口 `OneEnoughFabric`、`fabric.mod.json` 和 Fabric 平台路径实现。
- `forge/`：放 Forge 入口 `OneEnoughForge`、`mods.toml` 和 Forge 平台路径实现。
- `analysis/`：放生态采样脚本和调规则时生成的分析结果。

这样做的好处是：运行时核心逻辑只写一份，平台层只保留最薄的启动和路径适配代码。

## 4. 运行时接入点

项目启动后，Fabric 和 Forge 最终都会调用 `OneEnoughMod.init()`。真正的运行时行为通过两个 Mixin 挂载到 Minecraft 加载链路里：

1. `TagGroupLoaderMixin`：在物品标签组构建完成后，触发运行时标签合并。
2. `RecipeManagerMixin`：在配方应用阶段，触发运行时配方输入改写。

也就是说，One Enough 的两条主链路分别是：

- 标签链路：扫描、分类、建组、发布 snapshot、合并标签
- 配方链路：读取 snapshot、判断是否可安全改写、替换成公共标签输入

## 5. 自动分类如何工作

当前自动分类的目标不是"尽量多收"，而是"只在证据足够时接纳"。

它的大致流程是：

1. 先筛掉明显结构性、辅助性、加工型和用途型标签。
2. 判断来源标签是强来源还是弱来源。
3. 检查每个成员物品是否真的支持该组名。
4. 按不同阈值决定是否接纳整个来源标签。
5. 把通过的结果汇总成最终兼容组，并写入缓存。

当前默认重点扫描的目录前缀是：

- `c:crops/*`
- `c:vegetables/*`
- `forge:crops/*`
- `forge:vegetables/*`

同时，系统已经明确排除很多危险来源或辅助来源，例如：

- `seeds`
- `storage_blocks`
- `crate` / `bag`
- `slice` / `soup` / `jam`
- `ingredient` / `ingredients`
- `jei_display_results/*`
- `display_results/*`
- `can_*`
- `flat_on_*`
- `*_food`
- `*_snack`
- `*_plantable`
- `*_feedable`

这样做是为了避免把"种子、容器、展示结果、喂食关系、用途标签"错当成原始食材来源。

更完整的分类规则说明见 [classification-rules.md](../classification-rules.md)。

## 6. 统一标签是怎么生成的

当系统识别出一个稳定食材组后，运行时会同时生成三类标签：

1. 原始来源标签本身会被补齐成员。
2. 内部 hub 标签会建在 `one-enough-mod:*` 命名空间下。
3. 公开兼容标签会发布到 `c:*` 和 `forge:*`，并在有类别提示时补出 `c:crops/...`、`forge:vegetables/...` 这类路径。

举例来说，`garlic` 组可能会关联：

- `one-enough-mod:garlic`
- `c:garlics`
- `forge:garlics`
- `c:crops/garlic` 或 `c:vegetables/garlic`
- `forge:crops/garlic` 或 `forge:vegetables/garlic`

## 7. 配方改写如何工作

One Enough 不会盲目改写所有配方。当前配方桥接只改写"纯净的普通物品输入对象"，也就是只有一个 `item` 字段的 JSON 对象。

当前会遍历的常见字段包括：

- `ingredient`
- `ingredients`
- `key`
- `base`
- `addition`
- `template`

当前不会改写的情况包括：

- 已经是 `tag` 的输入
- 带 `type`、NBT、数量或其他额外字段的自定义 ingredient payload
- 映射到多个动态组、存在歧义的物品
- 被 `recipeBlacklist` 禁掉的配方

这意味着它偏向安全：宁可少改，也不去破坏 loader 特定或模组特定的复杂 ingredient 结构。

## 8. 配置文件

项目首次运行会写出 `config/one-enough-mod.json`。这个配置文件是处理误判和命名特例的主要安全阀。

主要字段包括：

- `autoDetect`：是否启用自动分类。
- `scanRoots`：目录前缀扫描白名单。
- `scanBareNamespaceTags`：是否扫描裸标签，例如 `someaddon:garlic`。
- `excludedBareNamespaces`：裸标签扫描时要排除的命名空间。
- `whitelistTags` / `blacklistTags`：按标签 id 强制允许或禁止。
- `whitelistGroups` / `blacklistGroups`：按规范化组名允许或禁止。
- `itemBlacklist`：绝不允许进入动态组的物品。
- `groupAliases`：命名别名归一，例如 `tomatoes -> tomato`。
- `publicTagNames`：公开标签名覆盖，例如 `rice -> rice`。
- `rewriteRecipes`：总开关，控制是否做配方改写。
- `recipeBlacklist`：永远不改写的配方列表。
- `itemGroupOverrides`：按物品 id 强制归组。
- `cacheEnabled`：是否启用分类缓存。
- `cacheExpiryMins`：字段存在，但当前主要失效逻辑仍依赖状态哈希和缓存版本，而不是时间到期。

## 9. 缓存机制

为了避免每次启动都完整重跑分类，项目会把结果写入 `one-enough-mod-cache.json`。当前缓存版本是 `3`，并且会把以下因素折入状态哈希：

- 配置开关
- 扫描根前缀
- 白黑名单
- 组别名和显式覆盖
- 当前已加载标签的成员集合

只要规则、配置、成员列表发生变化，缓存就会失效并重新分类。

## 10. 平台与依赖

这个项目当前的技术基线是：

- Minecraft 1.20.1
- Java 17
- Kotlin 2.0.0
- Fabric Loader 0.19.2
- Fabric Language Kotlin 1.11.0+kotlin.2.0.0
- Forge 47.4.20
- Kotlin for Forge 4.11.0

其中 Fabric 侧刻意不依赖 Fabric API，只保留 Fabric Loader、Fabric Language Kotlin 和 Mixin，使其更接近标准 Fabric 环境以及 Connector 之类桥接环境的兼容要求。

## 11. 构建方式

构建两个版本：

```powershell
.\gradlew.bat build
```

只构建 Fabric：

```powershell
.\gradlew.bat buildFabric
```

只构建 Forge：

```powershell
.\gradlew.bat buildForge
```

只验证编译：

```powershell
.\gradlew.bat :fabric:classes
.\gradlew.bat :forge:classes
```

输出产物：

- `fabric/build/libs/one-enough-mod-fabric-<version>.jar`
- `forge/build/libs/one-enough-mod-forge-<version>.jar`

## 12. 当前限制与注意事项

当前需要明确知道的边界有：

1. 配置文件是进程级缓存，改完配置后通常需要重启游戏，单纯 reload 不保证立即生效。
2. 配方改写只覆盖纯 `{"item":"..."}` 输入对象，不处理复杂自定义 ingredient payload。
3. 一个物品如果被多个动态组同时认领，系统会把它视为歧义物品并跳过配方改写。
4. 自动分类故意偏保守，所以某些语义不够明确的标签不会被自动接纳。
5. 这是兼容层，不是内容模组本体；它依赖外部模组已经声明了足够合理的标签。

## 13. 相关文档

- 分类机制详细说明：[classification-rules.md](../classification-rules.md)
- 结构调整与漏洞修复记录：[review.md](../review.md)
- GitHub 公共附属扫描说明：[analysis/github-delight-scan/README.md](../analysis/github-delight-scan/README.md)
