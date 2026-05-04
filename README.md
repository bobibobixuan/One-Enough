# One Enough

## Project Snapshot

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

## Shared Technical Facts

- Repository layout:
	- `common/`: shared runtime logic, Mixins, common resources
	- `fabric/`: Fabric bootstrap, metadata, service registration
	- `forge/`: Forge bootstrap, metadata, service registration
	- `analysis/`: ecosystem sampling scripts and research outputs
- Main runtime entry points:
	- `top.bobixuan.OneEnoughMod`
	- `top.bobixuan.mixin.TagGroupLoaderMixin`
	- `top.bobixuan.mixin.RecipeManagerMixin`
- Main runtime components:
	- `OneEnoughConfig`: configuration loading and normalization
	- `OneEnoughTagClassifier`: evidence-based tag classification
	- `OneEnoughRuntimeTagBridge`: runtime tag merge and snapshot publication
	- `OneEnoughRecipeBridge`: recipe JSON rewrite pass
	- `OneEnoughCache`: state-hash-based cache storage
- Main supporting docs:
	- [classification-rules.md](classification-rules.md)
	- [review.md](review.md)
	- [analysis/github-delight-scan/README.md](analysis/github-delight-scan/README.md)

---

## 中文

### 1. 项目概述

One Enough 是一个面向食物整合包和农作物附属生态的兼容模组。它解决的问题很具体：多个 Farmer's Delight 风格模组会各自注册名字接近、用途相同、但物品 id 不同的作物或蔬菜，导致标签不统一、配方无法互通、玩家需要同时携带多套“本质一样”的材料。

这个项目当前不再依赖手写固定食材名单，而是改成运行时按命名规则和证据规则扫描已加载的 item tags，自动识别哪些标签像“原始作物来源”，把它们合并成统一兼容标签，并在需要时把硬编码物品输入的配方改写为标签输入。

### 2. 核心能力

项目当前的核心能力有 6 个：

1. 自动扫描已加载的 item tags，而不是维护一份硬编码食材表。
2. 按“来源可信度 + 成员证据 + 接受阈值”的规则保守分类。
3. 为每个识别出的食材创建内部 hub 标签和公开兼容标签。
4. 在标签加载阶段把来源标签成员并回统一后的标签集合。
5. 在配方加载阶段把简单的硬编码 `{"item":"..."}` 输入改写为 `{"tag":"..."}`。
6. 通过状态哈希缓存分类结果，避免每次启动都完全重算。

### 3. 仓库结构

这个项目采用 `common + fabric + forge` 的三段结构：

- `common/`：放共享 Kotlin 逻辑、Mixin、共享资源、公共标签模板。
- `fabric/`：放 Fabric 入口 `OneEnoughFabric`、`fabric.mod.json` 和 Fabric 平台路径实现。
- `forge/`：放 Forge 入口 `OneEnoughForge`、`mods.toml` 和 Forge 平台路径实现。
- `analysis/`：放生态采样脚本和调规则时生成的分析结果。

这样做的好处是：运行时核心逻辑只写一份，平台层只保留最薄的启动和路径适配代码。

### 4. 运行时接入点

项目启动后，Fabric 和 Forge 最终都会调用 `OneEnoughMod.init()`。真正的运行时行为通过两个 Mixin 挂载到 Minecraft 加载链路里：

1. `TagGroupLoaderMixin`：在物品标签组构建完成后，触发运行时标签合并。
2. `RecipeManagerMixin`：在配方应用阶段，触发运行时配方输入改写。

也就是说，One Enough 的两条主链路分别是：

- 标签链路：扫描、分类、建组、发布 snapshot、合并标签
- 配方链路：读取 snapshot、判断是否可安全改写、替换成公共标签输入

### 5. 自动分类如何工作

当前自动分类的目标不是“尽量多收”，而是“只在证据足够时接纳”。

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

这样做是为了避免把“种子、容器、展示结果、喂食关系、用途标签”错当成原始食材来源。

更完整的分类规则说明见 [classification-rules.md](classification-rules.md)。

### 6. 统一标签是怎么生成的

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

### 7. 配方改写如何工作

One Enough 不会盲目改写所有配方。当前配方桥接只改写“纯净的普通物品输入对象”，也就是只有一个 `item` 字段的 JSON 对象。

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

### 8. 配置文件

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

### 9. 缓存机制

为了避免每次启动都完整重跑分类，项目会把结果写入 `one-enough-mod-cache.json`。当前缓存版本是 `3`，并且会把以下因素折入状态哈希：

- 配置开关
- 扫描根前缀
- 白黑名单
- 组别名和显式覆盖
- 当前已加载标签的成员集合

只要规则、配置、成员列表发生变化，缓存就会失效并重新分类。

### 10. 平台与依赖

这个项目当前的技术基线是：

- Minecraft 1.20.1
- Java 17
- Kotlin 2.0.0
- Fabric Loader 0.19.2
- Fabric Language Kotlin 1.11.0+kotlin.2.0.0
- Forge 47.4.20
- Kotlin for Forge 4.11.0

其中 Fabric 侧刻意不依赖 Fabric API，只保留 Fabric Loader、Fabric Language Kotlin 和 Mixin，使其更接近标准 Fabric 环境以及 Connector 之类桥接环境的兼容要求。

### 11. 构建方式

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

### 12. 当前限制与注意事项

当前需要明确知道的边界有：

1. 配置文件是进程级缓存，改完配置后通常需要重启游戏，单纯 reload 不保证立即生效。
2. 配方改写只覆盖纯 `{"item":"..."}` 输入对象，不处理复杂自定义 ingredient payload。
3. 一个物品如果被多个动态组同时认领，系统会把它视为歧义物品并跳过配方改写。
4. 自动分类故意偏保守，所以某些语义不够明确的标签不会被自动接纳。
5. 这是兼容层，不是内容模组本体；它依赖外部模组已经声明了足够合理的标签。

### 13. 相关文档

- 分类机制详细说明：[classification-rules.md](classification-rules.md)
- 结构调整与漏洞修复记录：[review.md](review.md)
- GitHub 公共附属扫描说明：[analysis/github-delight-scan/README.md](analysis/github-delight-scan/README.md)

---

## English

### 1. Overview

One Enough is a compatibility mod for food and farming modpacks, especially packs that combine multiple Farmer's Delight-style addons. Its job is to make duplicate crops and vegetables behave like interchangeable ingredients even when different mods use different item ids.

Instead of maintaining a hardcoded ingredient registry, the mod scans loaded item tags at runtime, classifies likely raw-ingredient sources conservatively, publishes shared compatibility tags, and rewrites simple hardcoded recipe inputs into tag-based inputs when that rewrite is safe.

### 2. Core capabilities

The project currently provides six core capabilities:

1. runtime item-tag scanning instead of static hardcoded crop lists,
2. evidence-based classification using source confidence and member-name checks,
3. generation of private hub tags and public `c:*` / `forge:*` tags,
4. runtime merging of source-tag members into unified groups,
5. recipe JSON rewriting for simple hardcoded item ingredients,
6. state-hash-based caching of classification results.

### 3. Repository layout

- `common/`: shared Kotlin logic, Mixins, shared resources, common tag templates
- `fabric/`: Fabric bootstrap, metadata, platform-path implementation
- `forge/`: Forge bootstrap, metadata, platform-path implementation
- `analysis/`: ecosystem scans and rule-tuning research artifacts

This layout keeps the platform layers thin and the runtime logic shared.

### 4. Runtime integration points

Both loaders ultimately call `OneEnoughMod.init()`. The actual runtime behavior is injected through two Mixins:

1. `TagGroupLoaderMixin` triggers runtime tag merging after item-tag groups are built.
2. `RecipeManagerMixin` triggers recipe rewriting when recipes are applied.

That gives the mod two main runtime pipelines:

- tag pipeline: scan, classify, group, publish snapshot, merge tags
- recipe pipeline: read snapshot, verify safety, rewrite eligible ingredients

### 5. How automatic classification works

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

For a detailed rule breakdown, see [classification-rules.md](classification-rules.md).

### 6. Unified tag publication

Once a stable ingredient group is accepted, the runtime bridge publishes three categories of tags:

1. the original source tags, enriched with merged members,
2. a private hub tag under `one-enough-mod:*`,
3. public compatibility tags under `c:*` and `forge:*`, plus category-tag variants when applicable.

### 7. Recipe rewriting

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

### 8. Configuration

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

### 9. Cache behavior

Classification results are stored in `one-enough-mod-cache.json`. The cache is invalidated when the effective configuration, group rules, overrides, or loaded tag membership set changes.

### 10. Platform baseline

Current baseline:

- Minecraft 1.20.1
- Java 17
- Kotlin 2.0.0
- Fabric Loader 0.19.2
- Fabric Language Kotlin 1.11.0+kotlin.2.0.0
- Forge 47.4.20
- Kotlin for Forge 4.11.0

The Fabric side intentionally avoids Fabric API.

### 11. Build

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

### 12. Current limitations

1. configuration is process-cached, so restart is usually required after edits,
2. recipe rewriting only covers plain `{"item":"..."}` ingredient objects,
3. ambiguous items are intentionally skipped,
4. the classifier is conservative by design,
5. the mod depends on other mods exposing meaningful source tags.

### 13. Related documents

- [classification-rules.md](classification-rules.md)
- [review.md](review.md)
- [analysis/github-delight-scan/README.md](analysis/github-delight-scan/README.md)

---

## Français

### 1. Vue d'ensemble

One Enough est un mod de compatibilité destiné aux packs alimentaires et agricoles, en particulier ceux qui combinent plusieurs addons de type Farmer's Delight. Son objectif est d'unifier des ingrédients équivalents déclarés sous des identifiants d'objet différents.

### 2. Capacités principales

Le projet :

1. analyse les item tags au chargement,
2. classe les sources selon des règles prudentes,
3. publie des tags unifiés privés et publics,
4. fusionne les membres des tags source,
5. réécrit certains ingrédients de recettes codés en dur,
6. met en cache les résultats de classification.

### 3. Structure du dépôt

- `common/` : logique partagée, Mixins, ressources
- `fabric/` : bootstrap Fabric
- `forge/` : bootstrap Forge
- `analysis/` : scripts et résultats d'analyse

### 4. Flux d'exécution

Le mod s'appuie sur `OneEnoughMod.init()` et sur deux Mixins : `TagGroupLoaderMixin` pour la fusion des tags et `RecipeManagerMixin` pour la réécriture des recettes.

### 5. Classification automatique

La classification est volontairement conservatrice. Elle filtre d'abord les tags structurels ou auxiliaires, puis évalue la crédibilité de la source, vérifie les noms des membres et applique des seuils d'acceptation différents pour les sources fortes et faibles.

### 6. Configuration

Le fichier `config/one-enough-mod.json` permet de contrôler les racines de scan, les balises blanches et noires, les groupes autorisés, les alias, les remplacements explicites d'objets, ainsi que l'interception des recettes.

### 7. Construction

Commandes principales :

```powershell
.\gradlew.bat build
.\gradlew.bat buildFabric
.\gradlew.bat buildForge
```

### 8. Limites actuelles

- rechargement de configuration non entièrement dynamique,
- réécriture limitée aux objets ingrédients simples,
- éléments ambigus volontairement ignorés,
- dépendance à des tags source sémantiquement corrects.

### 9. Documents liés

- [classification-rules.md](classification-rules.md)
- [review.md](review.md)
- [analysis/github-delight-scan/README.md](analysis/github-delight-scan/README.md)

---

## Español

### 1. Resumen

One Enough es un mod de compatibilidad para packs de comida y agricultura que combinan varios addons de estilo Farmer's Delight. Su meta es unificar ingredientes equivalentes registrados bajo distintos item ids.

### 2. Capacidades principales

El proyecto:

1. escanea item tags cargados en tiempo de ejecución,
2. clasifica las fuentes con reglas conservadoras,
3. publica tags privados y públicos unificados,
4. fusiona miembros procedentes de múltiples tags fuente,
5. reescribe ingredientes simples codificados con item id,
6. cachea resultados de clasificación.

### 3. Estructura

- `common/`: lógica compartida, Mixins y recursos
- `fabric/`: arranque de Fabric
- `forge/`: arranque de Forge
- `analysis/`: scripts y evidencia de análisis

### 4. Flujo de ejecución

El mod entra por `OneEnoughMod.init()` y se integra con `TagGroupLoaderMixin` y `RecipeManagerMixin` para la fusión de tags y la reescritura de recetas.

### 5. Clasificación automática

La clasificación es deliberadamente conservadora: filtra tags auxiliares, evalúa la fuerza de la fuente, valida nombres de miembros y aplica distintos umbrales de aceptación.

### 6. Configuración

`config/one-enough-mod.json` controla raíces de escaneo, allowlists, blocklists, aliases, overrides por item, caché y reescritura de recetas.

### 7. Compilación

```powershell
.\gradlew.bat build
.\gradlew.bat buildFabric
.\gradlew.bat buildForge
```

### 8. Límites actuales

- la configuración suele requerir reinicio,
- solo se reescriben ingredientes `{"item":"..."}` simples,
- los objetos ambiguos se omiten,
- el sistema depende de tags fuente razonables.

### 9. Documentos relacionados

- [classification-rules.md](classification-rules.md)
- [review.md](review.md)
- [analysis/github-delight-scan/README.md](analysis/github-delight-scan/README.md)

---

## Русский

### 1. Обзор

One Enough — это мод совместимости для продовольственных и фермерских сборок, особенно для наборов с несколькими дополнениями в стиле Farmer's Delight. Его задача — объединять эквивалентные ингредиенты, зарегистрированные под разными item id.

### 2. Основные возможности

Проект:

1. сканирует item tags во время выполнения,
2. классифицирует источники по консервативным правилам,
3. публикует приватные и публичные объединенные теги,
4. объединяет участников из нескольких source tags,
5. переписывает простые ингредиенты рецептов, заданные через item id,
6. кэширует результаты классификации.

### 3. Структура репозитория

- `common/`: общая логика, Mixins, ресурсы
- `fabric/`: загрузочный слой Fabric
- `forge/`: загрузочный слой Forge
- `analysis/`: скрипты и результаты анализа

### 4. Поток выполнения

Мод входит через `OneEnoughMod.init()` и использует `TagGroupLoaderMixin` и `RecipeManagerMixin` для объединения тегов и переписывания рецептов.

### 5. Автоматическая классификация

Классификатор намеренно осторожен: сначала отбрасывает вспомогательные теги, затем оценивает силу источника, проверяет имена участников и применяет разные пороги принятия.

### 6. Конфигурация

`config/one-enough-mod.json` управляет корнями сканирования, белыми и черными списками, алиасами, явными переопределениями предметов, кэшем и переписыванием рецептов.

### 7. Сборка

```powershell
.\gradlew.bat build
.\gradlew.bat buildFabric
.\gradlew.bat buildForge
```

### 8. Текущие ограничения

- после изменения конфигурации обычно нужен перезапуск,
- переписываются только простые объекты `{"item":"..."}`,
- неоднозначные предметы пропускаются,
- система зависит от качественно объявленных source tags.

### 9. Связанные документы

- [classification-rules.md](classification-rules.md)
- [review.md](review.md)
- [analysis/github-delight-scan/README.md](analysis/github-delight-scan/README.md)

---

## العربية

### 1. نظرة عامة

One Enough هو مود توافق لحزم الطعام والزراعة، وخاصة الحزم التي تجمع عدة إضافات من نمط Farmer's Delight. هدفه هو توحيد المكونات المتكافئة حتى لو كانت مسجلة بمعرفات عناصر مختلفة.

### 2. القدرات الأساسية

يقوم المشروع بما يلي:

1. فحص item tags أثناء التشغيل،
2. تصنيف المصادر وفق قواعد محافظة،
3. نشر وسوم موحدة خاصة وعامة،
4. دمج أعضاء الوسوم القادمة من مصادر متعددة،
5. إعادة كتابة بعض مكونات الوصفات البسيطة المكتوبة بمعرف عنصر مباشر،
6. تخزين نتائج التصنيف في cache.

### 3. بنية المستودع

- `common/` للمنطق المشترك و Mixins والموارد
- `fabric/` لطبقة الإقلاع الخاصة بـ Fabric
- `forge/` لطبقة الإقلاع الخاصة بـ Forge
- `analysis/` لسكربتات التحليل ونتائجها

### 4. تدفق التشغيل

يبدأ المود من `OneEnoughMod.init()` ويستخدم `TagGroupLoaderMixin` و `RecipeManagerMixin` من أجل دمج الوسوم وإعادة كتابة الوصفات.

### 5. التصنيف التلقائي

التصنيف متحفظ عمدا: يستبعد الوسوم المساعدة أولا، ثم يقيم قوة المصدر، ويتحقق من أسماء الأعضاء، ثم يطبق حدود قبول مختلفة.

### 6. الإعدادات

الملف `config/one-enough-mod.json` يتحكم في جذور الفحص، والقوائم البيضاء والسوداء، والأسماء البديلة، والتعيينات الصريحة للعناصر، والتخزين المؤقت، واعتراض الوصفات.

### 7. البناء

```powershell
.\gradlew.bat build
.\gradlew.bat buildFabric
.\gradlew.bat buildForge
```

### 8. الحدود الحالية

- تعديل الإعدادات يتطلب غالبا إعادة تشغيل،
- إعادة الكتابة تغطي فقط الكائنات البسيطة `{"item":"..."}`،
- العناصر المبهمة يتم تجاوزها،
- النظام يعتمد على وجود وسوم مصدر ذات معنى صحيح.

### 9. المستندات المرتبطة

- [classification-rules.md](classification-rules.md)
- [review.md](review.md)
- [analysis/github-delight-scan/README.md](analysis/github-delight-scan/README.md)

---

## Português

### 1. Visão geral

One Enough é um mod de compatibilidade para modpacks de comida e agricultura, especialmente packs com vários addons no estilo Farmer's Delight. Seu objetivo é unificar ingredientes equivalentes registrados com item ids diferentes.

### 2. Capacidades principais

O projeto:

1. escaneia item tags em tempo de execução,
2. classifica fontes com regras conservadoras,
3. publica tags unificadas privadas e públicas,
4. funde membros vindos de várias source tags,
5. reescreve ingredientes simples de receitas codificados com item id,
6. armazena resultados em cache.

### 3. Estrutura do repositório

- `common/`: lógica compartilhada, Mixins e recursos
- `fabric/`: bootstrap do Fabric
- `forge/`: bootstrap do Forge
- `analysis/`: scripts e resultados de análise

### 4. Fluxo de execução

O mod entra por `OneEnoughMod.init()` e usa `TagGroupLoaderMixin` e `RecipeManagerMixin` para fusão de tags e reescrita de receitas.

### 5. Classificação automática

O classificador é propositalmente conservador: ele filtra tags auxiliares, avalia a força da fonte, valida nomes de membros e aplica limiares diferentes de aceitação.

### 6. Configuração

`config/one-enough-mod.json` controla raízes de varredura, allowlists, blocklists, aliases, overrides por item, cache e reescrita de receitas.

### 7. Build

```powershell
.\gradlew.bat build
.\gradlew.bat buildFabric
.\gradlew.bat buildForge
```

### 8. Limitações atuais

- alterações de configuração normalmente exigem reinício,
- apenas objetos simples `{"item":"..."}` são reescritos,
- itens ambíguos são ignorados,
- o sistema depende de source tags semanticamente razoáveis.

### 9. Documentos relacionados

- [classification-rules.md](classification-rules.md)
- [review.md](review.md)
- [analysis/github-delight-scan/README.md](analysis/github-delight-scan/README.md)

---

## हिन्दी

### 1. परिचय

One Enough एक compatibility mod है, खासकर उन food और farming modpacks के लिए जो कई Farmer's Delight शैली के addons को साथ चलाते हैं। इसका लक्ष्य समान काम करने वाली सामग्री को एक जैसा व्यवहार देना है, भले ही उनके item ids अलग हों।

### 2. मुख्य क्षमताएँ

यह प्रोजेक्ट:

1. runtime पर item tags स्कैन करता है,
2. स्रोतों को conservative नियमों से classify करता है,
3. private और public unified tags प्रकाशित करता है,
4. कई source tags के सदस्यों को merge करता है,
5. simple hardcoded recipe ingredients को rewrite करता है,
6. classification results को cache करता है।

### 3. repository संरचना

- `common/`: shared logic, Mixins, resources
- `fabric/`: Fabric bootstrap
- `forge/`: Forge bootstrap
- `analysis/`: analysis scripts और outputs

### 4. runtime flow

मोड `OneEnoughMod.init()` से शुरू होता है और `TagGroupLoaderMixin` तथा `RecipeManagerMixin` के जरिए tags merge और recipes rewrite करता है।

### 5. automatic classification

classifier जानबूझकर conservative है: पहले helper tags हटाता है, फिर source strength जाँचता है, member names verify करता है और अलग acceptance thresholds लागू करता है।

### 6. configuration

`config/one-enough-mod.json` scan roots, allowlists, blocklists, aliases, item overrides, cache और recipe rewrite behavior को नियंत्रित करता है।

### 7. build

```powershell
.\gradlew.bat build
.\gradlew.bat buildFabric
.\gradlew.bat buildForge
```

### 8. वर्तमान सीमाएँ

- configuration बदलने के बाद आम तौर पर restart चाहिए,
- केवल simple `{"item":"..."}` ingredient objects rewrite होते हैं,
- ambiguous items जानबूझकर skip किए जाते हैं,
- सिस्टम meaningful source tags पर निर्भर करता है।

### 9. संबंधित दस्तावेज़

- [classification-rules.md](classification-rules.md)
- [review.md](review.md)
- [analysis/github-delight-scan/README.md](analysis/github-delight-scan/README.md)

## License

This project is available under the CC0-1.0 license.
