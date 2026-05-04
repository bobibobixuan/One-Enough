# Review

## 本次整理目标

- 让运行时链路更清晰，只保留 分类、缓存、标签合并 三段核心职责。
- 补强分类机制，把原先揉在一起的筛选、归类、组装流程拆开。
- 去除内部 API 接入，避免桥接层再维护一份额外的运行时 schema。

## 结构调整

### 1. 运行时链路收敛

- 位置：common/src/main/kotlin/top/bobixuan/OneEnoughRuntimeTagBridge.kt
- 调整：mergeBuiltItemTags 现在只负责三件事：尝试读取缓存、触发分类或手动扫描、把结果合并回运行时标签。
- 效果：桥接层不再承担 API schema 同步职责，控制流比之前更短，缓存命中和非命中路径也保持一致。

### 2. 移除 API 接入

- 位置：common/src/main/kotlin/top/bobixuan/OneEnoughRuntimeTagBridge.kt
- 调整：删除 OneEnoughAPI 的 RuntimeSchema 同步逻辑，并移除独立的 OneEnoughAPI 文件。
- 效果：运行时只保留 snapshot 这一份内部状态，减少了一套重复映射和由此带来的状态漂移风险。

## 分类机制

### 1. 分类流程显式分层

- 位置：common/src/main/kotlin/top/bobixuan/OneEnoughTagClassifier.kt
- 调整：分类器现在按 筛选相关标签 -> 生成分类分配 -> 组装最终分组 三段执行。
- 效果：分类逻辑不再依赖多张并行 map 在同一个函数里来回写入，后续加规则会更容易落点。

### 2. 引入显式分类对象

- 位置：common/src/main/kotlin/top/bobixuan/OneEnoughTagClassifier.kt
- 调整：新增内部分类概念 TagCategory、ClassifiedAssignment、GroupAccumulator。
- 效果：每一步的数据边界明确了：TagCategory 表示类别提示，ClassifiedAssignment 表示单个标签项被分到哪个组，GroupAccumulator 负责把分类结果汇总成最终组。

### 3. 分类结果继续保留已有安全约束

- 位置：common/src/main/kotlin/top/bobixuan/OneEnoughTagClassifier.kt、common/src/main/kotlin/top/bobixuan/OneEnoughCache.kt
- 保留：宽泛标签不再给每个成员硬拆独立组、工具关键字不再被当成食物信号、category hints 会随分类结果进入缓存。
- 保留：缓存仍然按 state hash 失效，避免旧分类结果在配置或标签成员变化后被错误复用。

### 4. 自动分类现在会排除种子和储物类来源

- 位置：common/src/main/kotlin/top/bobixuan/OneEnoughTagClassifier.kt、common/src/main/kotlin/top/bobixuan/OneEnoughCache.kt
- 背景：外部模组原始数据里，甜椒作物标签、甜椒种子标签、甜椒箱子配方本来是彼此分开的；问题出在自动分类把 forge:seeds/*、forge:storage_blocks/* 这类标签也当成了 bell pepper 组的来源。
- 后果：种子或箱子物品会被并入 c:bell_peppers 这一类公共标签，进一步让 9 个甜椒做箱子的配方、1 个甜椒拆种子的配方被错误地接受种子输入，形成反向合成漏洞。
- 修复：自动分类阶段现在会显式排除 seeds、storage_blocks、crate、bag 这类来源标签，同时也会过滤掉 seed、crate、bag 这类非作物成员物品；缓存版本同步升级，避免旧坏缓存继续生效。

### 5. 自动分类规则现在按“来源可信度 + 成员证据 + 接受阈值”执行

- 位置：common/src/main/kotlin/top/bobixuan/OneEnoughTagClassifier.kt
- 来源可信度：
	- crops/*、vegetables/* 或显式 whitelist 的标签属于强来源。
	- foods/*、裸标签、以及其他仅靠内容命中的标签属于弱来源。
- 成员证据：
	- 成员若有 itemGroupOverrides，按显式覆盖接受。
	- 否则成员必须是原始作物形态，且规范化后的名字必须等于组名，或是组名加一个允许的变体后缀，比如 bell_pepper_red 归到 bell_pepper。
	- 种子、箱子、袋子、切片、汤、果酱、粉末、烘烤/烟熏/腌制等加工产物不会被当成原始作物成员。
- 接受阈值：
	- 强来源标签只要有足够成员证据支持，就允许进入组。
	- 弱来源标签必须做到“所有参与判断的成员都支持同一组”才会被自动接纳。
- 效果：自动分类从“看起来像食物就归类”改成了“只有来源和成员同时给出足够证据才归类”。

### 6. 基于线上样本补排辅助/谓词标签

- 位置：common/src/main/kotlin/top/bobixuan/OneEnoughTagClassifier.kt、analysis/modrinth-delight-scan/tag-entries.json
- 样本：这次额外拉取了 39 个 Modrinth 公开 Farmer's Delight 相关模组文件，并结合本地整合包里 38 个 Delight 系 jar，一共扫描了 77 个 jar、1624 个物品标签文件。
- 发现：当前“最终接受”阶段已经足够保守，没有在这批样本里放进新的容器/加工组；但前置筛选里仍会遇到一些明显不该参与分组的辅助标签，例如 `can_be_salted`、`can_use_applesauce_as_egg`、`flat_on_cutting_board`、`jei_display_results/*`。
- 调整：预筛选阶段现在会额外排除 `can_*`、`flat_on_*`、`used_in_*`、`works_with_*`、`supports_*`，以及 `jei_display_results/*`、`display_results/*` 这类辅助路径。
- 效果：规则没有放宽，但候选池更干净，后续继续接其他附属时更不容易被展示标签或用途标签干扰。

### 7. GitHub 公共源码包继续补出 `_food` / `_plantable` 一类用途标签

- 位置：analysis/github-delight-scan/scan-github-repos.ps1、analysis/github-delight-scan/tag-entries.partial.json、common/src/main/kotlin/top/bobixuan/OneEnoughTagClassifier.kt
- 方式：从 GitHub 公开仓库搜索里挑出 20 个 Farmer's Delight 附属仓库，下载源码包后扫描到了 310 条 `tags/items` 路径。
- 发现：新增样本里高频出现 `chicken_food`、`pig_food`、`cochineal_food`、`special_food`、`flower_box_plantable`、`mob_feedable`、`snail_snacks` 这类标签。它们不是原料来源，而是喂食、种植或行为用途标签。
- 调整：辅助标签过滤又补了一层后缀规则，额外排除 `*_food`、`*_foods`、`*_snack`、`*_snacks`、`*_plantable`、`*_feedable`。
- 效果：这一步仍然没有放宽任何自动归组条件，只是把 GitHub 生态里常见的用途标签更早挡在候选池外。

## 仍需关注

### 1. 配置文件仍然是进程级缓存

- 位置：common/src/main/kotlin/top/bobixuan/OneEnoughConfig.kt
- 风险：玩家修改 one-enough-mod.json 后如果只做 reload，不重启游戏，blacklist、override、rewriteRecipes 等改动仍不会立即生效。

## 验证

- .\gradlew.bat :fabric:clean :forge:clean :fabric:compileKotlin :forge:compileKotlin
- .\gradlew.bat build
- .\analysis\modrinth-delight-scan\analyze-acceptance.ps1
