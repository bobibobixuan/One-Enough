# One Enough — Português

## 1. Visão geral

One Enough é um mod de compatibilidade para modpacks de comida e agricultura, especialmente packs com vários addons no estilo Farmer's Delight. Seu objetivo é unificar ingredientes equivalentes registrados com item ids diferentes.

## 2. Capacidades principais

O projeto:

1. escaneia item tags em tempo de execução,
2. classifica fontes com regras conservadoras,
3. publica tags unificadas privadas e públicas,
4. funde membros vindos de várias source tags,
5. reescreve ingredientes simples de receitas codificados com item id,
6. armazena resultados em cache.

## 3. Estrutura do repositório

- `common/`: lógica compartilhada, Mixins e recursos
- `fabric/`: bootstrap do Fabric
- `forge/`: bootstrap do Forge
- `analysis/`: scripts e resultados de análise

## 4. Fluxo de execução

O mod entra por `OneEnoughMod.init()` e usa `TagGroupLoaderMixin` e `RecipeManagerMixin` para fusão de tags e reescrita de receitas.

## 5. Classificação automática

O classificador é propositalmente conservador: ele filtra tags auxiliares, avalia a força da fonte, valida nomes de membros e aplica limiares diferentes de aceitação.

## 6. Configuração

`config/one-enough-mod.json` controla raízes de varredura, allowlists, blocklists, aliases, overrides por item, cache e reescrita de receitas.

## 7. Build

```powershell
.\gradlew.bat build
.\gradlew.bat buildFabric
.\gradlew.bat buildForge
```

## 8. Limitações atuais

- alterações de configuração normalmente exigem reinício,
- apenas objetos simples `{"item":"..."}` são reescritos,
- itens ambíguos são ignorados,
- o sistema depende de source tags semanticamente razoáveis.

## 9. Documentos relacionados

- [classification-rules.md](../classification-rules.md)
- [review.md](../review.md)
- [analysis/github-delight-scan/README.md](../analysis/github-delight-scan/README.md)
