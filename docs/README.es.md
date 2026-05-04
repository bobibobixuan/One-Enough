# One Enough — Español

## 1. Resumen

One Enough es un mod de compatibilidad para packs de comida y agricultura que combinan varios addons de estilo Farmer's Delight. Su meta es unificar ingredientes equivalentes registrados bajo distintos item ids.

## 2. Capacidades principales

El proyecto:

1. escanea item tags cargados en tiempo de ejecución,
2. clasifica las fuentes con reglas conservadoras,
3. publica tags privados y públicos unificados,
4. fusiona miembros procedentes de múltiples tags fuente,
5. reescribe ingredientes simples codificados con item id,
6. cachea resultados de clasificación.

## 3. Estructura

- `common/`: lógica compartida, Mixins y recursos
- `fabric/`: arranque de Fabric
- `forge/`: arranque de Forge
- `analysis/`: scripts y evidencia de análisis

## 4. Flujo de ejecución

El mod entra por `OneEnoughMod.init()` y se integra con `TagGroupLoaderMixin` y `RecipeManagerMixin` para la fusión de tags y la reescritura de recetas.

## 5. Clasificación automática

La clasificación es deliberadamente conservadora: filtra tags auxiliares, evalúa la fuerza de la fuente, valida nombres de miembros y aplica distintos umbrales de aceptación.

## 6. Configuración

`config/one-enough-mod.json` controla raíces de escaneo, allowlists, blocklists, aliases, overrides por item, caché y reescritura de recetas.

## 7. Compilación

```powershell
.\gradlew.bat build
.\gradlew.bat buildFabric
.\gradlew.bat buildForge
```

## 8. Límites actuales

- la configuración suele requerir reinicio,
- solo se reescriben ingredientes `{"item":"..."}` simples,
- los objetos ambiguos se omiten,
- el sistema depende de tags fuente razonables.

## 9. Documentos relacionados

- [classification-rules.md](../classification-rules.md)
- [review.md](../review.md)
- [analysis/github-delight-scan/README.md](../analysis/github-delight-scan/README.md)
