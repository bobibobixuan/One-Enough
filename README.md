# One Enough

[中文](docs/README.zh-CN.md) | [English](docs/README.en-US.md) | [Français](docs/README.fr-FR.md) | [Español](docs/README.es-ES.md) | [Русский](docs/README.ru-RU.md) | [العربية](docs/README.ar-SA.md) | [Português](docs/README.pt-BR.md) | [हिन्दी](docs/README.hi-IN.md)

One Enough 是一个面向 Minecraft 食物整合包和 Farmer's Delight 风格农作物附属生态的兼容模组。它的目标不是去改原模组内容，也不是给整合包硬塞一套手写补丁，而是尽量做成一个保守、可控、可重复的运行时兼容层：自动识别相同语义但不同物品 id 的作物 / 蔬菜标签，统一公共标签，并在安全前提下把部分硬编码配方输入改写为标签输入。

如果你的整合包里同时装了多个食材附属，结果出现“名字差不多、用途差不多、就是不能互通”的情况，这个项目就是为这种问题准备的。

## 项目定位

这个项目当前的定位很明确：

- 不直接修改其他模组 jar。
- 不依赖一份越写越长的手动食材白名单。
- 不追求“尽可能多收”，而是优先保证保守接纳和低误判。
- 通过运行时标签桥接和配方改写，让重复作物材料尽量互通。
- 同时维护 Fabric 和 Forge 两个 loader 版本，并共享同一套核心逻辑。

## 它解决什么问题

在 Farmer's Delight 及其附属生态里，经常会出现这类情况：

- 模组 A 有自己的 `garlic`。
- 模组 B 也有自己的 `garlic`。
- 两边物品实际功能接近，但 item id 不同。
- 某些配方写死了具体 item，而不是 tag。
- 玩家最后要带多套“本质相同”的材料，或者某些附属之间根本不互通。

One Enough 会在运行时尝试把这些来源标签整理成统一组，并发布公共兼容标签，比如 `c:garlics`、`forge:garlics`。如果某些配方只是简单写了 `{"item":"mod:id"}`，它还会在确认安全后改写为 `{"tag":"..."}`，从而让配方真正互通。

## 核心功能

- 自动扫描已加载的 item tags，而不是维护一份固定食材表。
- 按“强来源 / 弱来源 + 成员证据 + 接纳阈值”做保守分类。
- 为每个识别出的食材建立内部 hub 标签和公开兼容标签。
- 在标签加载阶段把来源标签成员并回统一后的集合。
- 在配方加载阶段改写简单的硬编码物品输入。
- 使用状态哈希缓存分类结果，减少重复计算。
- 共享 `common/` 核心逻辑，同时产出 Fabric 和 Forge 两个版本。

## 适合的使用场景

- 你的整合包里同时用了多个 Farmer's Delight 风格附属。
- 不同模组都提供了蒜、洋葱、番茄、甜椒之类重复作物，但互不兼容。
- 你不想给每个模组手写大量 datapack / KubeJS / CraftTweaker 补丁。
- 你想保留原模组结构，只额外加一层兼容逻辑。
- 你需要同时支持 Fabric 和 Forge，且希望核心逻辑保持一致。

## 项目快照

| Field | Value |
| --- | --- |
| Project Type | Minecraft compatibility mod |
| Purpose | Unify duplicate crop / vegetable ingredients across food mods |
| Minecraft | 1.20.1 |
| Loaders | Fabric and Forge |
| Java Target | 17 |
| Kotlin | 2.0.0 |
| Fabric Loader | 0.19.2 |
| Fabric Language Kotlin | 1.11.0+kotlin.2.0.0 |
| Forge | 47.4.20 |
| Kotlin for Forge | 4.11.0 |
| License | CC0-1.0 |

## 运行时是怎么工作的

项目启动后，两个 loader 最终都会进入同一套核心入口和运行时链路：

1. `OneEnoughMod.init()` 初始化配置和基础状态。
2. `TagGroupLoaderMixin` 在物品标签组构建完成后触发运行时标签桥接。
3. 分类器按命名规则和成员证据判断哪些标签像“原始作物来源”。
4. 系统发布内部 hub 标签与公开兼容标签。
5. `RecipeManagerMixin` 在配方应用阶段读取 snapshot，并尝试改写安全的普通 ingredient。

你可以把它理解成两条主链路：

- 标签链路：扫描、分类、建组、合并、发布 snapshot。
- 配方链路：读取 snapshot、判断是否安全、替换为公共 tag 输入。

## 自动分类规则摘要

当前自动分类的原则不是激进，而是保守：只有证据足够时才接纳。

默认重点扫描的目录前缀包括：

- `c:crops/*`
- `c:vegetables/*`
- `forge:crops/*`
- `forge:vegetables/*`

同时，系统会明确排除很多高风险来源，例如：

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

这样做是为了避免把种子、容器、展示结果、用途标签、喂食关系标签误当成作物来源，重新引入错误归类和反向合成漏洞。

更完整的规则说明见 [classification-rules.md](classification-rules.md)。

## 配方改写策略

One Enough 不会去碰所有配方。当前它只会改写“纯普通物品输入对象”，也就是只有一个 `item` 字段的 JSON 对象。

当前会处理的常见字段包括：

- `ingredient`
- `ingredients`
- `key`
- `base`
- `addition`
- `template`

当前不会改写的情况包括：

- 已经是 `tag` 的 ingredient。
- 带 `type`、NBT、数量或其他额外字段的自定义 payload。
- 同时命中多个动态组、存在歧义的物品。
- 被 `recipeBlacklist` 明确排除的配方。

这意味着它优先考虑安全，不会为了“多改几个配方”而去破坏复杂的 loader / 模组自定义 ingredient 结构。

## 快速开始

### 1. 运行环境

- Minecraft 1.20.1
- Java 17
- Fabric 或 Forge 运行环境

### 2. 构建项目

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

### 3. 首次运行后的配置

项目首次运行会在配置目录写出：

```text
config/one-enough-mod.json
```

这个文件用于控制：

- 扫描根前缀
- 裸标签扫描开关
- 白名单 / 黑名单
- 组别名归一
- 公开标签名覆盖
- 配方改写开关
- 显式物品归组覆盖
- 分类缓存开关

## 仓库结构

```text
common/   # 共享 Kotlin 逻辑、Mixin、共享资源、标签模板
fabric/   # Fabric 入口、metadata、平台路径实现
forge/    # Forge 入口、metadata、平台路径实现
analysis/ # 生态扫描脚本、样本数据、调规则产物
docs/     # 多语言完整文档
```

这个结构的重点是：运行时核心逻辑只写一份，平台层只做最薄的启动和路径适配。

## 文档导航

### 完整说明文档

| Language | File |
| --- | --- |
| 中文 | [docs/README.zh-CN.md](docs/README.zh-CN.md) |
| English | [docs/README.en-US.md](docs/README.en-US.md) |
| Français | [docs/README.fr-FR.md](docs/README.fr-FR.md) |
| Español | [docs/README.es-ES.md](docs/README.es-ES.md) |
| Русский | [docs/README.ru-RU.md](docs/README.ru-RU.md) |
| العربية | [docs/README.ar-SA.md](docs/README.ar-SA.md) |
| Português | [docs/README.pt-BR.md](docs/README.pt-BR.md) |
| हिन्दी | [docs/README.hi-IN.md](docs/README.hi-IN.md) |

### 其他文档

- [classification-rules.md](classification-rules.md)：自动分类规则的详细设计。
- [review.md](review.md)：这轮结构整理、漏洞修复和规则收敛的过程记录。
- [analysis/github-delight-scan/README.md](analysis/github-delight-scan/README.md)：GitHub 公共附属扫描说明。

## 当前边界与注意事项

- 配置是进程级缓存，改完配置后通常需要重启游戏。
- 配方改写只覆盖纯 `{"item":"..."}` 输入对象。
- 歧义物品会被故意跳过，避免错误替换。
- 分类器是保守型设计，所以不会接纳所有语义模糊标签。
- 这是兼容层，不是内容模组本体；它依赖其他模组提供相对合理的源标签。

## License

This project is available under the CC0-1.0 license.
