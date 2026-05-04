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

## Documentation by Language

| Language | File |
| --- | --- |
| 中文 | [docs/README.zh.md](docs/README.zh.md) |
| English | [docs/README.en.md](docs/README.en.md) |
| Français | [docs/README.fr.md](docs/README.fr.md) |
| Español | [docs/README.es.md](docs/README.es.md) |
| Русский | [docs/README.ru.md](docs/README.ru.md) |
| العربية | [docs/README.ar.md](docs/README.ar.md) |
| Português | [docs/README.pt.md](docs/README.pt.md) |
| हिन्दी | [docs/README.hi.md](docs/README.hi.md) |

## License

This project is available under the CC0-1.0 license.
