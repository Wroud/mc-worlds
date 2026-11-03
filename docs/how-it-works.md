# How Worlds works

An overview of what Worlds does and what you can rely on. The commands are in the [README](../README.md), and mods should read [API.md](../API.md).

## Worlds

A created world is a real dimension, saved under `dimensions/<namespace>/<path>` in the server world.

- **Loading.** A world loads when a player enters it or something asks for it, and unloads a minute after the last player leaves. The world holding the server spawn stays loaded. `/worlds settings loadOnStartup` loads a world at startup.
- **Time, weather and rules.** Each world has its own clock and weather, changed with vanilla `/time` and `/weather` run inside it.
- **Vanilla mechanics.** The End dragon, portals, maps and ender pearls work in created worlds as they do in the original three.
- **Deleting.** `/worlds delete` moves everyone out, then deletes the world's files.

## Spawns

There are three, and each has its own command:

| Spawn | Set with | Used for |
|---|---|---|
| Server spawn | `/setworldspawn` | First join, and players outside any group |
| World spawn | `/worlds settings spawn` | `/worlds tp` |
| Group spawn | `/worlds group spawn` | Respawn, End exit, compass and spawn protection inside a group |

## Game rules

`/gamerule` inside a created world changes that world only, and rules you don't change follow the server. `/gamerule <rule> inherit` hands a rule back. In a group, a rule belongs to the whole group and the world's own overrides pause until it leaves. A few rules, tagged `#mc-worlds:global`, always apply to the whole server.

## Importing

`/worlds import` copies a save, a zip or a download into a staging folder, upgrades it with vanilla's own file fixer, and only then installs it, so a failed import leaves nothing behind. The world keeps its generator, seed, spawn, weather, time, filled maps and the game rules that differ. One import runs at a time.

## World groups

A group is a named set of worlds that act as one vanilla world. Worlds stay outside any group until you add them, and everything outside a group, the vanilla dimensions included, behaves as vanilla does, in `minecraft:default`.

- **Main worlds.** A group has up to three: an Overworld, a Nether and an End. The first world of each kind to join takes the slot, and `join <group> main` replaces it. Other worlds are ordinary members.
- **Portals.** A portal leads to the matching main world of the same group. If that world is missing, the player sees a notice and stays.
- **Respawn.** A player respawns inside the group: at a bed or anchor set there, otherwise at the group spawn. A bed in another group doesn't count.
- **Game mode.** `mc-worlds:default_game_mode` and `mc-worlds:force_game_mode` give a group its own game mode.
- **Deleting a group.** Its worlds return to `minecraft:default` and the player data kept for it is deleted. The id can be used again once its last player has left.

## Moving between groups

Going to a world in another group, by any teleport or respawn, is a group switch. It runs at the end of the tick and works like a vanilla respawn: the player is saved, then rebuilt from their copy for the target group.

- The player closes menus and leaves their vehicle first, and held items return to the inventory.
- The first visit to a group starts empty, as on a first join.
- The player lands where the teleport points, not where they last stood in that group.
- Only players cross. A mob or item sent to a world in another group stays where it is.

At login, the dimension the player logged out in decides the group. If that dimension is gone, the player joins at the server spawn, with the copy for the group that spawn is in.

## Player data

Each group keeps its own inventory, ender chest, experience, health, effects, game mode, respawn point, statistics, advancements and the data other mods store on the player. See [compatibility.md](compatibility.md) for how other mods behave.

| Folder under `players/` | Holds |
|---|---|
| `data`, `stats`, `advancements` | The copy for `minecraft:default`, as vanilla stores it |
| `groups/<namespace>/<path>/` | The copy for that group, in the same vanilla format |
| `mc-worlds/` | Where each player last was. It doesn't exist until you create a group |

## Temporary worlds and groups

Temporary worlds and groups are for minigames and events. They get a generated id, so several can run at once, and they are deleted when closed and at startup. A crash leaves nothing behind.

- **Joining.** Only temporary worlds can join a temporary group. A temporary world can also join an ordinary group.
- **Entering.** Any teleport into the group enters it, and the player starts empty. Their position before entering is remembered as their home. A world in a temporary group is never a home.
- **Leaving.** `/worlds group temp leave`, deleting the group, or a restart returns players home with everything they had. Nothing from inside comes with them. If the home is gone, they go to the server spawn.
- **Dying inside.** A player respawns in the group. If it has been deleted, they respawn at home.
- **Rejoining.** A player who disconnects comes back to the match if it still exists, otherwise home.

### From data packs

World commands run at operator level 2, the level functions run at by default. Generated ids are in the `mc-worlds:temporary` command storage, under `worlds.<name>.id` and `groups.<name>.id`, for use with function macros:

```mcfunction
$execute in $(id) run tp @a 0 100 0
```

A world's entry appears once it is ready, so a function that creates one should wait for it before using the world.

## Other mods

Mods need no integration: see [compatibility.md](compatibility.md). Mods that want control can use [API.md](../API.md).
