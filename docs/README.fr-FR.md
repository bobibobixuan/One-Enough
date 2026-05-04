# One Enough — Français

## 1. Vue d'ensemble

One Enough est un mod de compatibilité destiné aux packs alimentaires et agricoles, en particulier ceux qui combinent plusieurs addons de type Farmer's Delight. Son objectif est d'unifier des ingrédients équivalents déclarés sous des identifiants d'objet différents.

## 2. Capacités principales

Le projet :

1. analyse les item tags au chargement,
2. classe les sources selon des règles prudentes,
3. publie des tags unifiés privés et publics,
4. fusionne les membres des tags source,
5. réécrit certains ingrédients de recettes codés en dur,
6. met en cache les résultats de classification.

## 3. Structure du dépôt

- `common/` : logique partagée, Mixins, ressources
- `fabric/` : bootstrap Fabric
- `forge/` : bootstrap Forge
- `analysis/` : scripts et résultats d'analyse

## 4. Flux d'exécution

Le mod s'appuie sur `OneEnoughMod.init()` et sur deux Mixins : `TagGroupLoaderMixin` pour la fusion des tags et `RecipeManagerMixin` pour la réécriture des recettes.

## 5. Classification automatique

La classification est volontairement conservatrice. Elle filtre d'abord les tags structurels ou auxiliaires, puis évalue la crédibilité de la source, vérifie les noms des membres et applique des seuils d'acceptation différents pour les sources fortes et faibles.

## 6. Configuration

Le fichier `config/one-enough-mod.json` permet de contrôler les racines de scan, les balises blanches et noires, les groupes autorisés, les alias, les remplacements explicites d'objets, ainsi que l'interception des recettes.

## 7. Construction

Commandes principales :

```powershell
.\gradlew.bat build
.\gradlew.bat buildFabric
.\gradlew.bat buildForge
```

## 8. Limites actuelles

- rechargement de configuration non entièrement dynamique,
- réécriture limitée aux objets ingrédients simples,
- éléments ambigus volontairement ignorés,
- dépendance à des tags source sémantiquement corrects.

## 9. Documents liés

- [classification-rules.md](../classification-rules.md)
- [review.md](../review.md)
- [analysis/github-delight-scan/README.md](../analysis/github-delight-scan/README.md)
