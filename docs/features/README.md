# Feature demand and roadmap

Internal notes on which world-management features players and server owners ask for, how each one fits mc-worlds, and where it stands. Researched 2026-10-07 by comparing mc-worlds with Multiverse-Core (Paper) and the Fabric alternatives; demand and Fabric coverage of the open features re-checked 2026-10-29. Each feature has its own design doc in this folder.

## The bar every feature must meet

mc-worlds stays as close as possible to vanilla so MC updates stay easy and behaviour is predictable:

- reuse vanilla mechanisms: vanilla commands acting on the world they run in, `SavedData` in the world's own storage, dimension types and world presets from data packs;
- keep the Overworld, Nether and End behaving exactly as vanilla;
- add the fewest and smallest mixins, and list every compile-clean breakage point in the `AGENTS.md` version-bump checks;
- don't add policies vanilla doesn't have (permissions, economy, inventories).

## Where the demand comes from

| Source | What it shows |
|---|---|
| Multiverse-Core issues sorted by 👍 ([link](https://github.com/Multiverse/Multiverse-Core/issues?q=is%3Aissue+sort%3Areactions-%2B1-desc)) | Top real requests: per-world time (#79, 23 👍, plus #1824, 9 👍), structures only generating if the main world has them (#2310, 33 👍), per-world world border (#1776, 14 👍), data pack world generation (#2751, #2832), alphabetical `mv list` (#1960, 4 👍), "don't spawn villages" (#789) |
| senseiwells Multiverse for Fabric ([issues](https://github.com/senseiwells/Multiverse/issues)) | The only feature requests: a list command (#4) and console support (#3) |
| IsaiahMC Multiworld issues sorted by reactions ([link](https://github.com/IsaiahMC/multiworld/issues?q=is%3Aissue+sort%3Areactions-%2B1-desc)) | Top requests: per-world inventories (#4, 25; #26, 17), then importing an existing world (#2 and #37, 5 👍 each, plus #41, #72, #151), all open |
| mc-worlds tracker ([issues](https://github.com/wroud/mc-worlds/issues)) | One feature request: #9 (2026-10-09), per-world inventories, a creative-only world and modded world generation |
| Modrinth downloads, 2026-10-29 | Fabric managers: Multiworld 86.5K, mc-worlds 20.4K, melius World Manager 15.2K, Multiverse Dimensions 7.1K. Paper: Multiverse-Core 916K, Multiverse-Inventories 295K, Multiverse-NetherPortals 102K |
| Feature sets of Fabric alternatives | Multiworld (~80K downloads): gamerule, difficulty, spawn, list, custom portals. WorldEngine: per-world gamemode, PvP, difficulty, gamerules, inventories, mob spawning, custom portals, auto-unload. WorldGameRules (4.3K downloads): per-dimension game rules only. melius-worldmanager: import. Fantasy (library): layered game rules |
| Server-owner guides ([GameServerKings](https://www.gameserverkings.com/knowledge-base/minecraft/multiverse-multiple-worlds/)) | Main use cases: resetting resource worlds, creative build worlds, minigame arenas, hub/lobby worlds; most-used add-ons are separate inventories and Nether/End linking |
| Spigot JIRA SPIGOT-169, SPIGOT-4971; GlobalGamerule plugins | Players expect most game rules to stay shared across dimensions ("keepInventory doesn't work in the Nether"), so per-world rules must inherit by default |

Demand evidence is GitHub reactions, issue trackers and competitor feature sets; Reddit could not be searched.

## Status

| Feature | Demand | Fit with vanilla | Status | Doc |
|---|---|---|---|---|
| Per-world time | Highest-voted Multiverse request | Real vanilla clock per world | ✅ Shipped before 1.9 | — |
| Per-world weather | Common | Vanilla `/weather` acts on the world | ✅ Shipped before 1.9 | — |
| Vanilla mechanics in every world (End dragon, portals, maps, pearls) | Main differentiator; not offered by Multiverse without add-ons | `mixin/fixes/` | ✅ Shipped | — |
| Lazy loading / auto-unload | WorldEngine lists it; needed for many worlds | Vanilla level lifecycle | ✅ Shipped | — |
| `/worlds list` | Only feature request on senseiwells' tracker; Multiworld has it | Read-only, vanilla `/datapack list` style | ✅ Shipped 1.9.0 | [worlds-list.md](worlds-list.md) |
| Per-world game rules | Every competitor offers it; also covers PvP, mob spawning, weather and daylight toggles | Vanilla `GameRuleMap` per world, vanilla `/gamerule` extended | ✅ Shipped 1.10.0 (as a `/gamerule` extension, not `/worlds settings gamerule`) | [per-world-gamerules.md](per-world-gamerules.md) |
| Per-world world border | 14 👍 on Multiverse | Already per-dimension in vanilla: `/execute in <world> run worldborder …` | ✅ Works through vanilla | — |
| Data pack presets and dimension types | Multiverse #2751, #2832 | `from-preset`, `from-dimension` | ✅ Shipped | — |
| Import a world folder | Core Multiverse command; melius-worldmanager has it | Vanilla file fixer, saved-data readers and storage layout; one chunk datafix context mixin | 🟢 Shipped in 1.11.0 (folders, zips and zip download links) | [world-import.md](world-import.md) |
| Clone a world | Multiverse, senseiwells | Shares import's copy-and-install step | 🟢 Approved, not started | [world-clone.md](world-clone.md) |
| Regenerate / reset a world | Top use case (resource worlds); Chunky `trim` does not cover it | Composes delete and create | 🟡 Proposed | [world-regen.md](world-regen.md) |
| Per-world difficulty | Multiverse, Multiworld, WorldEngine, Paper | Vanilla already reads and syncs difficulty per level | 🟡 Proposed, low priority | [per-world-difficulty.md](per-world-difficulty.md) |
| Per-world "generate structures" | 33 👍 on Multiverse #2310 | Data pack flat presets with `structure_overrides: []` cover the stated reasons | 🔴 Not building; document the data pack route | [per-world-generate-structures.md](per-world-generate-structures.md) |
| Own Nether and End per world (linked portals) | Multiverse-NetherPortals, Dimension Link, senseiwells' "vanilla set" | Would change portal destinations | 🔴 Out of scope; add-on territory | — |
| Separate inventories per world | Most-reacted request on Multiworld (#4, #26) and in mc-worlds #9; Multiverse-Inventories has 295K downloads | Vanilla has one inventory per player | 🔴 Filtered; separate mod if ever | — |
| Forced gamemode per world | Multiverse, WorldEngine | No vanilla link between world and gamemode | 🔴 Filtered; data pack tick function can do it | — |
| Permissions, entry fees, player limits, hidden worlds | Multiverse | Needs third-party permission and economy APIs | 🔴 Filtered | — |
| Custom portals | Multiverse-Portals, Multiworld, WorldEngine | New blocks and mechanics | 🔴 Filtered; Stargate covers it through the API | — |
| Aliases, anchors, purge, `who` | Multiverse | Cosmetic, or already possible with `/execute in` | 🔴 Filtered | — |

## Open features: demand and coverage

Checked 2026-10-29. Downloads are Modrinth. Details and sources are in each feature's doc.

| Feature | Demand | Already covered on Fabric by | Order |
|---|---|---|---|
| Import | High: the most-requested missing feature on Multiworld (5 open issues); blocks Paper owners from moving to Fabric (Multiworld #151) | melius World Manager (15.2K), Overworld only. Multiworld and Multiverse Dimensions have none | 1 |
| Regen | Medium–high: a top server use case with a category of Paper plugins (WorldReset 8.1K, ResourceWorldResetter 1.2K, …); no GitHub votes | No world manager. Single-purpose mods: World Reset (6.9K, main world only), Aethro Resource World (817), Timed Harvest (216, 1.20.1) | 2 |
| Clone | Low–medium: used on Multiverse (31 issues, mostly bugs), never requested on Fabric | Multiverse Dimensions (7.1K) | 3, built on import |
| Difficulty | Low: no Fabric requests; Multiverse issues are "doesn't stick" bugs | Multiworld (86.5K), CustomDimensions (1.3K, 1.21.1) | 4 |

Design changes from this check:
- Regen carries per-world game rule overrides over. They live in the world's `data/` folder since 1.10.0, so deleting the folder would drop them. The border is now also recommended to carry over.
- Import supports migrating a Paper 26.1+ server, whose Multiverse worlds are dimensions inside one save. A datapack dimension imports when the server has the same datapack, and is refused before copying otherwise.
- Import was redesigned the same day: no chunk pre-upgrade pass (a chunk datafix context mixin instead, which also helps existing overworld-like worlds), no busy set or `WorldTransfer` class, the save's weather, clock and differing game rules carried over, the Overworld by default with `all` as an opt-in, and sources only from the server root with one nested level. Later the same day: `LEVEL_ADMINS`; another mc-worlds server's worlds supported through its `worlds_data.dat`; Paper 26.1+ supported after testing a Paper 26.3 + Multiverse save in Docker (each world is a dimension with its own generator settings); filled maps carried under fresh ids with one vanilla `RegionStorageUpgrader` pass.
- Clone has an open question on running from command blocks and functions, for per-player templates.
- Difficulty asks whether its inherit syntax should match the shipped `/gamerule … inherit`.

## Stability work found along the way

- **Structure template cache crash (vanilla MC-271899).** Several overworld-like worlds placing villages at the same time could crash chunk generation. Vanilla never runs into it; mc-worlds exposed it. Fixed in 1.10.0 by `mixin/fixes/StructureTemplatePaletteMixin`, the same approach as Paper and Moonrise. Reproduce with `mc-server-probe`: `--fresh --cmd "worlds create minecraft:a; … minecraft:f; wait:200"`.
