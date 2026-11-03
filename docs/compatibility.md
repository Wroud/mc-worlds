# Compatibility with other mods

Tested on 2026-11-03 with Worlds 2.0.0 on Minecraft 26.3. Other mods need no integration: Worlds never asks a mod to register its data.

## How other mods' data behaves in a world group

A group keeps a complete copy of the player's save data, not a list of known fields. So anything a mod stores on the player is separate per group, with no code in that mod.

| Where a mod keeps a player's data | What happens |
|---|---|
| In the player's save data: extra slots, backpacks, abilities, classes, Fabric attachments, Cardinal Components | Separate per group. The first visit to a group starts empty, and returning restores it |
| On an item, such as a shulker box or a bundle | Goes wherever the item goes |
| In the world's saved data, keyed by player | Shared across groups, as vanilla scoreboards are, unless the mod keys it by group ([API.md](../API.md)) |

Worlds outside a group all share one copy of the player, as in vanilla, so none of this matters until you create a group.

## Tested with a real player

A real client on a dedicated server, moving between groups, leaving and rejoining.

| Mod | Version | Result |
|---|---|---|
| Trinkets Updated (extra equipment slots) | 4.2.1+26.3 | A different item in the neck slot per group. Kept across switches and a relog. A temporary group starts empty, and its item doesn't reach home |
| Traveler's Backpack (backpack slot) | 26.3-11.4.3 | A backpack per group on the server, on the client and on disk, across switches and a relog |
| Origins Legacy (origins, with Cardinal Components) | 1.12.20+26.3 | An origin per group |
| Cardinal Components API, full bundle | 8.1.0 | Entering a created world works; earlier versions of Worlds crashed here |
| WorldThreader (parallel world ticking) | mc26.3-3.2.0 | Switches, portals, End exit and login. A default check for every change to players or worlds |

## Tested in a game window

Singleplayer with the real game open: creating worlds, a group, moving between groups, a death and a respawn. Sodium, Iris with Complementary Unbound, Jade, AppleSkin and Xaero's Minimap, all at once.

## Tested together on a server

Each set was loaded with Worlds on a dedicated server, with a fake player moving between groups, dying, entering a temporary group and being sent home, and worlds unloading. No crash, mixin error or exception. This shows they load and run together; it doesn't exercise each mod's own features.

| Kind | Mods |
|---|---|
| Performance | Lithium, FerriteCore, Krypton, Alternate Current, C2ME, ScalableLux, Very Many Players |
| Server tools | Fabric Carpet, spark, Chunky, WorldEdit, Ledger, BlueMap |
| Permissions and commands | LuckPerms, Fabric Permissions API, Styled Chat, Essential Commands |
| World generation and rendering | Terralith, Lithostitched, Tectonic, Distant Horizons |
| Content and libraries | Polymer, Waystones, Balm, Architectury API, GeckoLib, Fabric Language Kotlin |

BlueMap ran without rendering, because it waits for its resource download to be accepted.

The Trinkets, Traveler's Backpack and Origins set also ran this way on Fabric API 0.165.2, the newest at the time.

## Expected to work, no 26.3 build to test

Accessories and Traveler's Backpack use the same storage; Inventorio and LevelZ write their own keys into the player's save data; Sophisticated Backpacks keeps its contents with the item. All of them should be separate per group, but none has a 26.3 Fabric build, so this comes from how they store data on earlier versions.

Pufferfish's Skills keeps its data in the world's saved data, so skills stay shared across groups.

## Teleports and portals from other mods

A mod that moves a player with vanilla's teleport works unchanged: moving into another group becomes a group switch. Only players cross between groups. A mob or item sent to a world in another group stays where it is.

## Dimensions from other mods

`/worlds create <id> from-dimension <mod>:<dimension>` makes a new world from any registered dimension, including a mod's or a data pack's, and the original stays as it is.

- Generation and dimension type carry over. Tested with a data pack dimension; no mod that adds a dimension has a 26.3 Fabric build to test with.
- Code that checks for the exact original id doesn't run in the copy, such as a boss or a special spawn rule.
- A mod's own portal keeps leading to its original dimension.
- A dimension type outside the Overworld, Nether and End kinds joins a group as an ordinary member, not a main one, so portals don't link to it.
- A world using a removed mod's generator is skipped at load, with an error in the log.

Another mod's own level, created at runtime and not through Worlds, counts as being outside any group and can't join one.

## Found a problem?

Open an issue at <https://github.com/Wroud/mc-worlds/issues> with the mod's name and version.
