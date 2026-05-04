# 自动分类规则说明

## 目标

这套规则的目标不是“尽量多归类”，而是“只在证据足够时自动归类”。

One Enough 现在的自动分类只会在同时满足以下条件时把一个标签吸收进运行时兼容组：

1. 来源标签本身看起来像可信的原始作物来源。
2. 标签里的成员物品名字，能够支持这个组名。
3. 整个标签通过最终接受阈值，不会因为少量巧合命中就被收进去。

## 总流程

代码入口在 [common/src/main/kotlin/top/bobixuan/OneEnoughTagClassifier.kt](common/src/main/kotlin/top/bobixuan/OneEnoughTagClassifier.kt#L109)。

自动分类分为 5 步：

1. 预筛选相关标签。
2. 判断来源标签可信度。
3. 逐个成员检查命名证据。
4. 按阈值决定是否接受整个来源标签。
5. 汇总成最终兼容组并写入缓存。

## 第一步：预筛选相关标签

入口逻辑在 [common/src/main/kotlin/top/bobixuan/OneEnoughTagClassifier.kt](common/src/main/kotlin/top/bobixuan/OneEnoughTagClassifier.kt#L138)。

会先排除两类标签：

1. 明显结构性标签。
2. 明显支持性或加工型标签。
3. 明显辅助性或谓词型标签。

当前会排除的来源标签关键词包括：

- `seed` / `seeds`
- `storage_blocks`
- `crate` / `bag`
- `slice` / `soup` / `jam` / `powder`
- `roasted` / `fried` / `pickled` / `smoked`
- `ingredient` / `ingredients`
- `bottle` / `jar` / `bowl` / `plate` / `cup`
- `can_*` / `flat_on_*` / `used_in_*`
- `jei_display_results/*` / `display_results/*`
- `*_food` / `*_snack(s)` / `*_plantable` / `*_feedable`

这样做的目的，是把自动分类收敛在“原始作物”和“原始蔬菜”附近，而不是让加工食品、储物块、支持材料也参与同一套兼容。

这里的“辅助性或谓词型标签”是这次线上样本扫描后补进来的。像 `can_be_salted`、`can_use_applesauce_as_egg`、`flat_on_cutting_board`、`jei_display_results/*`，以及 GitHub 附属源码里反复出现的 `chicken_food`、`pig_food`、`flower_box_plantable`、`mob_feedable`、`snail_snacks` 这类标签，虽然经常含有食物成员，但它们表达的是“用途”“喂食关系”或“展示位”，不是“原始作物来源”。

## 第二步：来源标签可信度

逻辑在 [common/src/main/kotlin/top/bobixuan/OneEnoughTagClassifier.kt](common/src/main/kotlin/top/bobixuan/OneEnoughTagClassifier.kt#L232)。

来源标签分成两档：

### 强来源

满足任一条件即可：

- 在配置的 `whitelistTags` 中。
- 具有 `crops/*` 类别提示。
- 具有 `vegetables/*` 类别提示。

这类标签通常已经表达了“这是原始农作物/蔬菜”的明确语义。

### 弱来源

例如：

- `foods/*`
- 裸标签
- 仅靠内容扫描命中的其他模组标签

这类标签的语义不够直接，所以会套更严格的接受阈值。

## 第三步：成员证据

逻辑在 [common/src/main/kotlin/top/bobixuan/OneEnoughTagClassifier.kt](common/src/main/kotlin/top/bobixuan/OneEnoughTagClassifier.kt#L259)。

每个成员物品都要单独判定是否真的支持当前组名。

### 直接接受

若物品在配置 `itemGroupOverrides` 中有显式覆盖，则直接按配置结果归组。

### 自动接受

若没有显式覆盖，成员必须满足：

1. 不是种子、箱子、袋子、切片、汤、果酱、粉末等支持/加工物。
2. 规范化后的物品名，与组名完全一致，或者只是合法变体。

合法变体目前只允许“原始作物 + 变体后缀”的形式，例如：

- `bell_pepper_red` -> `bell_pepper`
- `bell_pepper_green` -> `bell_pepper`
- `bell_pepper_yellow` -> `bell_pepper`

相关逻辑在 [common/src/main/kotlin/top/bobixuan/OneEnoughTagClassifier.kt](common/src/main/kotlin/top/bobixuan/OneEnoughTagClassifier.kt#L378)。

## 第四步：接受阈值

逻辑在 [common/src/main/kotlin/top/bobixuan/OneEnoughTagClassifier.kt](common/src/main/kotlin/top/bobixuan/OneEnoughTagClassifier.kt#L313)。

### 强来源标签

只要有足够比例的成员能给出支持证据，就允许接入。

当前规则：

- 接受成员占比 `>= 0.5`

### 弱来源标签

必须非常干净，不能混着别的东西。

当前规则：

- 参与判断的成员必须全部支持同一组
- 即 `acceptedMembers == consideredMembers`

这样可以避免 `foods/*` 之类宽泛标签，把整锅汤、切片、半成品都一起拖进来。

## 第五步：汇总与缓存

当一个来源标签被接受后，成员会被汇总成最终兼容组，再写入缓存。

相关逻辑在：

- 汇总分组：[common/src/main/kotlin/top/bobixuan/OneEnoughTagClassifier.kt](common/src/main/kotlin/top/bobixuan/OneEnoughTagClassifier.kt#L329)
- 缓存版本：[common/src/main/kotlin/top/bobixuan/OneEnoughCache.kt](common/src/main/kotlin/top/bobixuan/OneEnoughCache.kt#L13)

缓存会和当前规则、当前标签成员、当前配置一起参与失效判断，所以规则变化后不会继续复用旧的坏分类结果。

## 为什么这样设计

核心原则只有一句：

自动分类要偏保守，宁可少收，也不能把语义不同的物品打通。

如果把以下东西错误并进原始作物组：

- 种子
- 储物箱子 / 袋子
- 切片
- 汤 / 炒菜 / 果酱 / 粉末

就会直接污染配方输入，进而出现“1 粒种子顶替 1 个完整作物”这种反向合成漏洞。

## 当前可配置介入点

如果自动规则还不够精确，可以通过配置人工兜底：

- `whitelistTags`
- `blacklistTags`
- `whitelistGroups`
- `blacklistGroups`
- `itemBlacklist`
- `itemGroupOverrides`

这些配置适合处理个别模组的命名特例，而不是把整个自动分类退回成硬编码名单。

## 还能怎么继续优化

当前我认为还有两类值得继续做，但不该盲目一起加：

### 1. 变体后缀做成配置项

现在允许的颜色/品质后缀还是代码内置集合。把它做成配置项后，整合包可以自己扩展例如 `striped`、`heirloom`、`mini` 这类安全变体。

### 2. 输出拒绝原因日志

现在规则已经比较保守，但如果某个标签没被接纳，用户还得靠读代码理解原因。可以补一个 debug 级别日志，明确打印：

- 因为来源太弱被拒绝
- 因为成员命名不支持组名被拒绝
- 因为成员里混入了种子/切片/汤等支持物被拒绝

这样调规则会轻松很多。