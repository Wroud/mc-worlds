# Feature demand and roadmap

Internal notes on which world-management features players and server owners ask for, how each one fits mc-worlds, and where it stands. Researched 2026-10-07 by comparing mc-worlds with Multiverse-Core (Paper) and the Fabric alternatives. Each feature has its own design doc in this folder.

## The bar every feature must meet

mc-worlds stays as close as possible to vanilla so MC updates stay easy and behaviour is predictable:

- reuse vanilla mechanisms: vanilla commands acting on the world they run in, `SavedData` in the world's own storage, dimension types and world presets from data packs;
- keep the Overworld, Nether and End behaving exactly as vanilla;
- add the fewest and smallest mixins, and list every compile-clean breakage point in the `CLAUDE.md` version-bump checks;
- don't add policies vanilla doesn't have (permissions, economy, inventories).

## Where the demand comes from

| Source | What it shows |
|---|---|
| Multiverse-Core issues sorted by 👍 ([link](https://github.com/Multiverse/Multiverse-Core/issues?q=is%3Aissue+sort%3Areactions-%2B1-desc)) | Top real requests: per-world time (#79, 23 👍, plus #1824, 9 👍), structures only generating if the main world has them (#2310, 33 👍), per-world world border (#1776, 14 👍), data pack world generation (#2751, #2832), alphabetical `mv list` (#1960, 4 👍), "don't spawn villages" (#789) |
| senseiwells Multiverse for Fabric ([issues](https://github.com/senseiwells/Multiverse/issues)) | The only feature requests: a list command (#4) and console support (#3) |
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
| Import a world folder | Core Multiverse command; melius-worldmanager has it | Vanilla upgrader and storage layout | 🟢 Approved, not started | [world-import.md](world-import.md) |
| Clone a world | Multiverse, senseiwells | Built on import's transfer routine | 🟢 Approved, not started | [world-clone.md](world-clone.md) |
| Regenerate / reset a world | Top use case (resource worlds); Chunky `trim` does not cover it | Composes delete and create | 🟡 Proposed | [world-regen.md](world-regen.md) |
| Per-world difficulty | Multiverse, Multiworld, WorldEngine, Paper | Vanilla already reads and syncs difficulty per level | 🟡 Proposed, low priority | [per-world-difficulty.md](per-world-difficulty.md) |
| Per-world "generate structures" | 33 👍 on Multiverse #2310 | Data pack flat presets with `structure_overrides: []` cover the stated reasons | 🔴 Not building; document the data pack route | [per-world-generate-structures.md](per-world-generate-structures.md) |
| Own Nether and End per world (linked portals) | Multiverse-NetherPortals, Dimension Link, senseiwells' "vanilla set" | Would change portal destinations | 🔴 Out of scope; add-on territory | — |
| Separate inventories per world | Multiverse-Inventories, Multiworld Inventories, WorldEngine | Vanilla has one inventory per player | 🔴 Filtered; separate mod if ever | — |
| Forced gamemode per world | Multiverse, WorldEngine | No vanilla link between world and gamemode | 🔴 Filtered; data pack tick function can do it | — |
| Permissions, entry fees, player limits, hidden worlds | Multiverse | Needs third-party permission and economy APIs | 🔴 Filtered | — |
| Custom portals | Multiverse-Portals, Multiworld, WorldEngine | New blocks and mechanics | 🔴 Filtered; Stargate covers it through the API | — |
| Aliases, anchors, purge, `who` | Multiverse | Cosmetic, or already possible with `/execute in` | 🔴 Filtered | — |

## Stability work found along the way

- **Structure template cache crash (vanilla MC-271899).** Several overworld-like worlds placing villages at the same time could crash chunk generation. Vanilla never runs into it; mc-worlds exposed it. Fixed in 1.10.0 by `mixin/fixes/StructureTemplatePaletteMixin`, the same approach as Paper and Moonrise. Reproduce with `mc-server-probe`: `--fresh --cmd "worlds create minecraft:a; … minecraft:f; wait:200"`.
