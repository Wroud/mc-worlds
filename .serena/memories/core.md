# mc-worlds — core

Fabric server-side mod. Creates/manages player-made Minecraft worlds **as real dimensions** at runtime, via `/worlds` commands. Mod id `mc-worlds`; published to Modrinth + CurseForge + GitHub Packages.

## Source map (`src/main/java/dev/wroud/mc/worlds/`)

- `McWorldMod` — `ModInitializer` entrypoint, event registration, `id(path)` helper.
- `McWorld` — per-`MinecraftServer` facade; reachable via `McWorldMod.getMcWorld(server)`.
- `manager/` — `WorldsManager` (load/create/delete/unload), `WorldsCreator` (preset/dimension/seed resolution), `WorldHandle`, `WorldsData` (`SavedData` for the world list), `ServerLevelProvider` + `DefaultServerLevelProvider`; `manager/level/data/WorldsLevelData` is the per-world config (Codec-persisted).
- `server/level/` — `CustomServerLevel` (extends `ServerLevel`), `state/` lifecycle (Init → Activation → Active → Stopping → Stopped), `state/SpawnPreparationHelper` (async fork of `MinecraftServer.setInitialSpawn`).
- `command/` — one class per command, registered in `WorldsCommands`.
- `mixin/` (35 files) — see `mem:conventions` for the three sub-packages and the injection idioms.
- `util/DimensionDetectionUtil` + `tags/DimensionTypeTags` — the dimension classifier; see `mem:conventions`.
- `core/registries/WorldsRegistries` — Fabric registry for `ServerLevelProvider`.
- `abstractions/` — thin indirections (`ServerPlayerAbstraction`, `TeleportTransitionAbstraction`) used by mixins to survive MC signature churn.
- `src/client/java/.../mixin/` — client-only mixins. `src/datagen/` — `DimensionTypeTagsProvider` emits the `*_like` dimension-type tags.

## Project-wide invariants

- **Stonecutter multi-version build.** The real project is the `:latest` subproject; several tasks do not exist on the root. See `mem:suggested_commands`.
- **Custom worlds are ordinary dimensions.** `worlds create <id>` takes an `IdentifierArgument`, so a bare `foo` becomes `minecraft:foo` — that is the dimension key for `/execute in`.
- **Vanilla hardcodes behaviour to the three vanilla dimension keys.** Every such branch is a mechanic that breaks in a created world; `mixin/fixes/` exists solely to patch them. Full sweep procedure + standing decisions: `.claude/skills/dimension-sweep/SKILL.md`.
- **No test suite.** Verification is manual and gated on actually running the game — `mem:task_completion`.
- Per-world clock: vanilla `ServerClockManager` instances taken from each level's **per-dimension** `SavedDataStorage` (`server/level/PerWorldClocks`), plus `CustomServerLevel.clockManager()` override and `mixin/ServerClockManagerMixin`. Do **not** reintroduce a forked manager.
- Per-world weather: `CustomServerLevel.getWeatherData()` override + `ServerLevelWeatherMixin` / `WeatherCommandMixin` / `MinecraftServerWeatherMixin`.
- `mixin/filefix/DataFixersMixin` registers a file fix whose version **must** stay derived (`fileFixes.getLast().getVersion() + 1`). A hardcoded version throws in `Bootstrap.bootStrap()` and the game cannot launch — client or server — while `./gradlew build` still reports success.

Toolchain and pinned versions: `mem:tech_stack`. Code style and mixin idioms: `mem:conventions`.
