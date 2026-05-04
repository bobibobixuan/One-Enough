# GitHub Delight Scan Documentation

语言说明：联合国正式官方语言实际为 6 种。为满足你要求的 8 语版本，本文采用 6 种联合国语言加 2 种补充国际语言：中文、English、Français、Español、Русский、العربية、Português、हिन्दी。

本文说明的目标文件是 [scan-github-repos.ps1](scan-github-repos.ps1)。相关产物还包括 [repo-summaries.json](repo-summaries.json)、[release-assets.json](release-assets.json) 和 [tag-entries.json](tag-entries.json)。

---

## 中文

### 1. 文件作用

[scan-github-repos.ps1](scan-github-repos.ps1) 是一个面向 GitHub 公开仓库的证据采集脚本。它并不直接执行 One Enough 的运行时分类规则，而是负责搜索 Farmer's Delight 相关附属仓库、下载默认分支源码压缩包、扫描其中 `data/.../tags/items/*.json` 路径，并把这些路径整理成后续规则分析可以复用的 JSON 结果。

它的价值在于帮助你回答这样的问题：

- 公共附属里到底出现了哪些 `items` 标签路径模式。
- 哪些标签只是行为标签、展示标签、喂食标签或用途标签。
- 当前自动分类规则还缺不缺额外的排除条件。

### 2. 输入

脚本的输入来源有四类：

- GitHub Search API，固定查询词是 `"Farmer's Delight" addon`。
- GitHub Releases API，用于补充仓库的发布资产信息。
- GitHub codeload 提供的默认分支 zip 源码包。
- 本地缓存文件 [repo-summaries.json](repo-summaries.json) 与 [release-assets.json](release-assets.json)。

### 3. 输出

脚本会生成或更新以下文件：

- [repo-summaries.json](repo-summaries.json)：仓库摘要，包括全名、URL、默认分支、描述。
- [release-assets.json](release-assets.json)：release 资产信息，包括资产名、下载地址和大小。
- [tag-entries.json](tag-entries.json)：扫描到的 `tags/items` 路径证据。
- `source-archives/`：源码 zip 缓存目录，属于运行时缓存而不是长期人工维护数据。

### 4. 执行流程

脚本的主流程是：

1. 切换到脚本目录，并设置 `Stop` 级错误策略。
2. 创建 `source-archives/` 目录。
3. 构造 GitHub 请求头。
4. 尝试读取 [repo-summaries.json](repo-summaries.json)。如果存在则复用；否则发起 GitHub 搜索，请求最多 20 个仓库。
5. 尝试读取 [release-assets.json](release-assets.json)。如果存在则复用；否则请求每个仓库最近 5 个 release。
6. 遍历仓库，基于 `full_name` 和 `default_branch` 生成 codeload 下载地址，并构造本地安全文件名。
7. 如果 zip 不存在则下载；之后打开 zip，只保留匹配 `data/.+/tags/items/.+\.json` 的条目。
8. 将仓库摘要、release 资产、标签路径分别排序后写回磁盘，并输出统计数。

### 5. 缓存与兼容性

脚本显式处理旧缓存形态：

- `NormalizeCachedList` 会把 `null`、单对象、数组、`value` 包装对象统一归一成列表。
- `NormalizeCachedRepoSummaries` 用于兼容“字段是数组”的旧摘要结构。

缓存策略偏向复用：

- 只要 [repo-summaries.json](repo-summaries.json) 存在，就不再重新搜索仓库。
- 只要 [release-assets.json](release-assets.json) 存在，就不再重新查询 release。
- 只要源码 zip 已存在，就不再重复下载。

优点是节省 API 配额、便于离线复盘。缺点是结果可能陈旧，需要手工删除缓存后再刷新。

### 6. 结果能说明什么

当前脚本只做“路径扫描”，不解析标签 JSON 内容。因此 [tag-entries.json](tag-entries.json) 能说明：

- 哪些仓库存在 `items` 标签文件。
- 这些标签路径长什么样。
- 是否出现 `*_food`、`*_plantable`、`*_feedable`、`jei_display_results/*` 一类模式。

它不能直接说明：

- 标签里有哪些成员。
- 标签成员是不是原始食材。
- 该标签是否应该被运行时自动兼容系统接纳。

### 7. 已验证的当前样本规模

按当前仓库中缓存内容核对，现有样本结果是：

- `Repos=20`
- `ReleaseAssets=9`
- `TagEntries=310`

这代表当前目录中的数据足以做中等规模的 GitHub 公开附属路径普查，但它仍然只是有限样本，不是完整生态全集。

### 8. 局限与改进建议

当前局限主要有：

- 查询词写死，且偏英文语义。
- 搜索只取前 20 个仓库，没有分页。
- release 请求失败会被吞掉，不利于问题追踪。
- 不解析标签 JSON 正文。
- `NormalizeCachedRepoSummaries` 目前更像历史兼容遗留，后续可以统一或清理。

推荐优先增强：

- 增加 `--refresh` 或强制刷新模式。
- 支持分页与多查询词。
- 输出失败日志和速率限制信息。
- 在可选模式下解析标签 JSON 内容。
- 对输出 JSON 增加 schema 版本。

### 9. 运行示例

```powershell
Set-Location .\analysis\github-delight-scan
.\scan-github-repos.ps1
```

示例输出：

```text
Repos=20
ReleaseAssets=9
TagEntries=310
```

---

## English

### 1. Purpose

[scan-github-repos.ps1](scan-github-repos.ps1) is a GitHub evidence-gathering script. It does not run One Enough's runtime classifier directly. Instead, it searches for Farmer's Delight-related addon repositories, downloads default-branch source archives, scans `data/.../tags/items/*.json` paths, and writes reusable JSON evidence for later rule review.

### 2. Inputs

The script consumes four input sources:

- GitHub Search API with the fixed query `"Farmer's Delight" addon`.
- GitHub Releases API for recent release assets.
- GitHub codeload archives for each repository's default branch.
- Local cache files [repo-summaries.json](repo-summaries.json) and [release-assets.json](release-assets.json).

### 3. Outputs

The script produces or refreshes:

- [repo-summaries.json](repo-summaries.json)
- [release-assets.json](release-assets.json)
- [tag-entries.json](tag-entries.json)
- `source-archives/` as the local zip cache

### 4. Workflow

The main execution flow is:

1. switch to the script directory and enable stop-on-error behavior,
2. create `source-archives/`,
3. build GitHub headers,
4. load cached repo summaries if present, otherwise search GitHub for up to 20 repos,
5. load cached release assets if present, otherwise request up to 5 releases per repo,
6. build a codeload archive URL from `full_name` and `default_branch`,
7. download the zip if missing and scan only entries matching `data/.+/tags/items/.+\.json`,
8. sort and write the final JSON outputs.

### 5. Cache and compatibility

The script explicitly supports older cache shapes:

- `NormalizeCachedList` turns null, single objects, arrays, and `value`-wrapped objects into a list.
- `NormalizeCachedRepoSummaries` is intended for an older object-of-arrays summary format.

The cache policy is conservative. Existing cache files disable fresh requests, and existing zip files disable re-download. This reduces API cost but requires manual cache removal when a refresh is needed.

### 6. What the output means

[tag-entries.json](tag-entries.json) is path evidence only. It can tell you:

- which repositories contain `items` tag files,
- what those paths look like,
- whether patterns such as `*_food`, `*_plantable`, or `jei_display_results/*` exist.

It cannot tell you:

- which members are inside the tags,
- whether the members are raw ingredients,
- whether the runtime classifier should accept the tag.

### 7. Verified sample size in this repository

The currently checked-in cache resolves to:

- `Repos=20`
- `ReleaseAssets=9`
- `TagEntries=310`

This is large enough for a medium-scale public-addon path survey, but it is still a bounded sample.

### 8. Limitations and improvements

Current limitations:

- fixed English-heavy query text,
- no pagination,
- release errors are swallowed,
- no JSON body parsing,
- compatibility logic still carries some historical cleanup debt.

Recommended improvements:

- add a refresh mode,
- support pagination and multiple queries,
- log failures explicitly,
- optionally parse tag JSON bodies,
- add schema versioning to output files.

---

## Français

### 1. Objet

[scan-github-repos.ps1](scan-github-repos.ps1) est un script de collecte de preuves sur GitHub. Il ne décide pas lui-même quelles étiquettes doivent être acceptées à l'exécution. Il recherche des dépôts d'addons liés à Farmer's Delight, télécharge les archives source de la branche par défaut, analyse les chemins `data/.../tags/items/*.json` et écrit des résultats JSON réutilisables.

### 2. Entrées

Les entrées sont :

- l'API de recherche GitHub,
- l'API GitHub Releases,
- les archives codeload,
- les caches locaux [repo-summaries.json](repo-summaries.json) et [release-assets.json](release-assets.json).

### 3. Sorties

Le script produit :

- [repo-summaries.json](repo-summaries.json),
- [release-assets.json](release-assets.json),
- [tag-entries.json](tag-entries.json),
- `source-archives/` comme cache local.

### 4. Processus

Le flux principal est :

1. se placer dans le dossier du script,
2. créer `source-archives/`,
3. préparer les en-têtes GitHub,
4. charger le cache des dépôts ou chercher jusqu'à 20 dépôts,
5. charger le cache des releases ou demander jusqu'à 5 releases par dépôt,
6. construire l'URL codeload,
7. télécharger le zip si nécessaire et ne retenir que les chemins correspondant à `data/.+/tags/items/.+\.json`,
8. trier puis écrire les fichiers JSON finaux.

### 5. Cache et compatibilité

`NormalizeCachedList` normalise plusieurs formes anciennes de cache. `NormalizeCachedRepoSummaries` sert à une ancienne structure où les champs étaient stockés sous forme de tableaux. La politique de cache privilégie la réutilisation des fichiers existants, ce qui réduit les appels API mais impose une suppression manuelle pour forcer l'actualisation.

### 6. Sens des résultats

[tag-entries.json](tag-entries.json) décrit des chemins de fichiers, pas le contenu des étiquettes. Il aide donc à détecter des motifs de nommage, mais ne suffit pas à déterminer si une étiquette est sûre pour la compatibilité d'exécution.

### 7. Résultat vérifié

Le cache actuellement présent dans ce dépôt correspond à :

- `Repos=20`
- `ReleaseAssets=9`
- `TagEntries=310`

### 8. Limites et améliorations

Limites : requête fixe, pas de pagination, erreurs de release peu visibles, absence d'analyse du JSON. Améliorations recommandées : mode de rafraîchissement, meilleure journalisation, analyse optionnelle du contenu JSON, versionnement du schéma de sortie.

---

## Español

### 1. Propósito

[scan-github-repos.ps1](scan-github-repos.ps1) es un script de recopilación de evidencia en GitHub. No aplica por sí solo la clasificación en tiempo de ejecución. Su papel es buscar repositorios de addons relacionados con Farmer's Delight, descargar los archivos fuente comprimidos de la rama por defecto, analizar rutas `data/.../tags/items/*.json` y guardar resultados JSON reutilizables.

### 2. Entradas

Las entradas son:

- la API de búsqueda de GitHub,
- la API de releases de GitHub,
- los archivos codeload,
- los cachés locales [repo-summaries.json](repo-summaries.json) y [release-assets.json](release-assets.json).

### 3. Salidas

El script genera:

- [repo-summaries.json](repo-summaries.json),
- [release-assets.json](release-assets.json),
- [tag-entries.json](tag-entries.json),
- `source-archives/` como caché local.

### 4. Flujo

El flujo principal es:

1. entrar al directorio del script,
2. crear `source-archives/`,
3. preparar cabeceras de GitHub,
4. cargar caché de repos o buscar hasta 20 repositorios,
5. cargar caché de releases o consultar hasta 5 releases por repositorio,
6. construir la URL de codeload,
7. descargar el zip cuando falte y conservar solo rutas que coincidan con `data/.+/tags/items/.+\.json`,
8. ordenar y escribir los JSON finales.

### 5. Caché y compatibilidad

`NormalizeCachedList` unifica formas antiguas de caché. `NormalizeCachedRepoSummaries` existe para una forma histórica donde los campos eran arreglos. La política de caché favorece la reutilización y reduce llamadas remotas, pero obliga a borrar manualmente los cachés cuando se necesita refresco real.

### 6. Qué significan los resultados

[tag-entries.json](tag-entries.json) registra solo rutas de archivos. Sirve para descubrir patrones como `*_food` o `jei_display_results/*`, pero no basta para decidir si un tag debe ser aceptado por el clasificador de ejecución.

### 7. Resultado verificado

El conjunto actual validado en este repositorio es:

- `Repos=20`
- `ReleaseAssets=9`
- `TagEntries=310`

### 8. Límites y mejoras

Límites: consulta fija, sin paginación, errores parcialmente silenciados, sin análisis del cuerpo JSON. Mejoras recomendadas: modo de refresco, más consultas, mejor registro de errores, parseo opcional de JSON y versionado del esquema de salida.

---

## Русский

### 1. Назначение

[scan-github-repos.ps1](scan-github-repos.ps1) — это скрипт для сбора доказательных данных из GitHub. Он не принимает runtime-решения сам по себе. Его задача — найти репозитории дополнений Farmer's Delight, скачать архивы исходников ветки по умолчанию, просканировать пути `data/.../tags/items/*.json` и сохранить переиспользуемые JSON-результаты.

### 2. Входы

Скрипт использует:

- GitHub Search API,
- GitHub Releases API,
- архивы codeload,
- локальные кэши [repo-summaries.json](repo-summaries.json) и [release-assets.json](release-assets.json).

### 3. Выходы

Он создает:

- [repo-summaries.json](repo-summaries.json),
- [release-assets.json](release-assets.json),
- [tag-entries.json](tag-entries.json),
- `source-archives/` как локальный кэш архивов.

### 4. Основной поток

Основные шаги:

1. перейти в каталог скрипта,
2. создать `source-archives/`,
3. подготовить заголовки GitHub,
4. загрузить кэш репозиториев или выполнить поиск до 20 репозиториев,
5. загрузить кэш релизов или запросить до 5 релизов на репозиторий,
6. сформировать URL codeload,
7. скачать zip при отсутствии и сохранить только пути, совпадающие с `data/.+/tags/items/.+\.json`,
8. отсортировать и записать итоговые JSON-файлы.

### 5. Кэш и совместимость

`NormalizeCachedList` приводит старые формы кэша к единому списку. `NormalizeCachedRepoSummaries` относится к более старому формату, где поля могли храниться как массивы. Политика кэширования уменьшает число сетевых запросов, но требует ручного удаления кэша для полноценного обновления.

### 6. Что означают результаты

[tag-entries.json](tag-entries.json) содержит только пути файлов. Он полезен для обнаружения шаблонов именования, но не показывает состав тегов и не доказывает, что тег должен быть принят runtime-классификатором.

### 7. Проверенный результат

Текущий зафиксированный набор дает:

- `Repos=20`
- `ReleaseAssets=9`
- `TagEntries=310`

### 8. Ограничения и улучшения

Ограничения: фиксированный запрос, отсутствие пагинации, частично подавляемые ошибки, нет разбора JSON. Улучшения: режим обновления, лучшее логирование, необязательный разбор содержимого тегов, версия схемы выходных файлов.

---

## العربية

### 1. الغرض

[scan-github-repos.ps1](scan-github-repos.ps1) هو برنامج نصي لجمع الأدلة من GitHub. هذا الملف لا يطبّق منطق التصنيف أثناء التشغيل بنفسه. مهمته هي البحث عن مستودعات إضافات مرتبطة بـ Farmer's Delight، وتنزيل أرشيفات الشيفرة المصدرية للفرع الافتراضي، وفحص المسارات `data/.../tags/items/*.json`، ثم حفظ النتائج في ملفات JSON قابلة لإعادة الاستخدام.

### 2. المدخلات

يعتمد السكربت على:

- GitHub Search API
- GitHub Releases API
- أرشيفات codeload
- ملفات التخزين المؤقت المحلية [repo-summaries.json](repo-summaries.json) و [release-assets.json](release-assets.json)

### 3. المخرجات

ينتج السكربت:

- [repo-summaries.json](repo-summaries.json)
- [release-assets.json](release-assets.json)
- [tag-entries.json](tag-entries.json)
- المجلد `source-archives/` كمخزن مؤقت محلي للأرشيفات

### 4. سير العمل

الخطوات الرئيسية هي:

1. الانتقال إلى مجلد السكربت،
2. إنشاء `source-archives/`،
3. تجهيز ترويسات GitHub،
4. تحميل التخزين المؤقت للمستودعات أو البحث حتى 20 مستودعا،
5. تحميل التخزين المؤقت للإصدارات أو طلب حتى 5 إصدارات لكل مستودع،
6. بناء رابط codeload،
7. تنزيل ملف zip عند الحاجة ثم الاحتفاظ فقط بالمسارات المطابقة لـ `data/.+/tags/items/.+\.json`،
8. فرز ملفات JSON النهائية وكتابتها.

### 5. التخزين المؤقت والتوافق

تقوم `NormalizeCachedList` بتوحيد الأشكال القديمة لملفات التخزين المؤقت. أما `NormalizeCachedRepoSummaries` فهي مرتبطة بشكل أقدم كانت فيه الحقول تظهر كمصفوفات. هذه السياسة تقلل طلبات الشبكة لكنها تتطلب حذف التخزين المؤقت يدويا إذا كان المطلوب تحديثا كاملا.

### 6. معنى النتائج

[tag-entries.json](tag-entries.json) يسجل مسارات الملفات فقط، وليس محتوى JSON الداخلي. لذلك فهو مناسب لاكتشاف أنماط التسمية العامة، لكنه لا يحدد وحده ما إذا كان الوسم يجب أن يقبل أثناء التشغيل.

### 7. النتيجة المتحققة

النتيجة الحالية في هذا المستودع هي:

- `Repos=20`
- `ReleaseAssets=9`
- `TagEntries=310`

### 8. القيود والتحسينات

القيود الحالية: استعلام ثابت، لا توجد صفحات إضافية، بعض الأخطاء يتم تجاهلها، ولا يوجد تحليل لمحتوى JSON. التحسينات المقترحة: وضع تحديث إجباري، تحسين تسجيل الأخطاء، تحليل اختياري لمحتوى الوسوم، وإضافة إصدار لمخطط المخرجات.

---

## Português

### 1. Finalidade

[scan-github-repos.ps1](scan-github-repos.ps1) é um script para coleta de evidências no GitHub. Ele não executa sozinho a lógica de classificação em tempo de execução. Sua função é localizar repositórios de addons ligados a Farmer's Delight, baixar os arquivos-fonte compactados da branch padrão, analisar os caminhos `data/.../tags/items/*.json` e salvar resultados JSON reutilizáveis.

### 2. Entradas

As entradas são:

- GitHub Search API
- GitHub Releases API
- arquivos codeload
- caches locais [repo-summaries.json](repo-summaries.json) e [release-assets.json](release-assets.json)

### 3. Saídas

O script gera:

- [repo-summaries.json](repo-summaries.json)
- [release-assets.json](release-assets.json)
- [tag-entries.json](tag-entries.json)
- `source-archives/` como cache local de arquivos zip

### 4. Fluxo

O fluxo principal é:

1. entrar no diretório do script,
2. criar `source-archives/`,
3. preparar cabeçalhos do GitHub,
4. carregar o cache de repositórios ou pesquisar até 20 repositórios,
5. carregar o cache de releases ou consultar até 5 releases por repositório,
6. construir a URL de codeload,
7. baixar o zip quando estiver ausente e manter apenas caminhos compatíveis com `data/.+/tags/items/.+\.json`,
8. ordenar e gravar os JSONs finais.

### 5. Cache e compatibilidade

`NormalizeCachedList` converte formatos antigos de cache em uma lista uniforme. `NormalizeCachedRepoSummaries` existe para um formato legado baseado em arrays por campo. A política de cache economiza chamadas remotas, mas exige remoção manual dos arquivos se for necessário um refresh completo.

### 6. Significado dos resultados

[tag-entries.json](tag-entries.json) registra somente caminhos de arquivo. Ele ajuda a identificar padrões de nomenclatura pública, mas não mostra o conteúdo interno das tags nem prova que uma tag deva ser aceita pelo classificador de runtime.

### 7. Resultado validado

O snapshot atualmente validado neste repositório é:

- `Repos=20`
- `ReleaseAssets=9`
- `TagEntries=310`

### 8. Limitações e melhorias

Limitações: consulta fixa, sem paginação, algumas falhas silenciosas e ausência de análise do corpo JSON. Melhorias sugeridas: modo de refresh, melhor log de erros, parse opcional do JSON e versionamento do esquema de saída.

---

## हिन्दी

### 1. उद्देश्य

[scan-github-repos.ps1](scan-github-repos.ps1) GitHub से साक्ष्य एकत्र करने वाली स्क्रिप्ट है। यह स्वयं रनटाइम वर्गीकरण का अंतिम निर्णय नहीं करती। इसका काम Farmer's Delight से जुड़े addon रिपॉज़िटरी खोजना, उनकी default branch का source zip डाउनलोड करना, `data/.../tags/items/*.json` paths को स्कैन करना और पुनः उपयोग योग्य JSON परिणाम लिखना है।

### 2. इनपुट

स्क्रिप्ट इन स्रोतों का उपयोग करती है:

- GitHub Search API
- GitHub Releases API
- codeload archives
- स्थानीय cache फ़ाइलें [repo-summaries.json](repo-summaries.json) और [release-assets.json](release-assets.json)

### 3. आउटपुट

स्क्रिप्ट बनाती है:

- [repo-summaries.json](repo-summaries.json)
- [release-assets.json](release-assets.json)
- [tag-entries.json](tag-entries.json)
- `source-archives/` स्थानीय zip cache निर्देशिका के रूप में

### 4. कार्यप्रवाह

मुख्य चरण हैं:

1. स्क्रिप्ट फ़ोल्डर में जाना,
2. `source-archives/` बनाना,
3. GitHub headers तैयार करना,
4. repo cache लोड करना या अधिकतम 20 repositories तक search चलाना,
5. release cache लोड करना या प्रत्येक repository पर अधिकतम 5 releases तक request करना,
6. codeload URL बनाना,
7. zip अनुपस्थित होने पर डाउनलोड करना और केवल `data/.+/tags/items/.+\.json` से मेल खाने वाले paths रखना,
8. अंतिम JSON फ़ाइलों को sort करके लिखना।

### 5. cache और compatibility

`NormalizeCachedList` पुराने cache shapes को एक समान list में बदलता है। `NormalizeCachedRepoSummaries` पुराने array-based format के लिए है। यह cache नीति network requests कम करती है, लेकिन पूर्ण refresh के लिए cache फ़ाइलें हाथ से हटानी पड़ती हैं।

### 6. परिणाम का अर्थ

[tag-entries.json](tag-entries.json) केवल file paths दर्ज करता है, JSON body नहीं पढ़ता। इसलिए इससे naming patterns का अध्ययन किया जा सकता है, लेकिन यह अकेले नहीं बताता कि कोई tag runtime classifier द्वारा स्वीकार किया जाना चाहिए या नहीं।

### 7. सत्यापित परिणाम

इस repository में वर्तमान validated snapshot है:

- `Repos=20`
- `ReleaseAssets=9`
- `TagEntries=310`

### 8. सीमाएँ और सुधार

सीमाएँ: fixed query, pagination नहीं, कुछ errors silently skip, JSON body parsing नहीं। सुझाए गए सुधार: refresh mode, बेहतर failure logs, optional JSON parsing, और output schema versioning.
