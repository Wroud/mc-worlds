# Forceloaded chunks in lazily loaded worlds

**Status.** Open, not fixed. Found on 2026-10-29 while testing `/worlds import`; it is not caused by import and also happens with `/worlds create`.

## Symptom

After a server restart, an mc-worlds world that had forceloaded chunks:

- answers `execute in <world> run forceload query` with "No force loaded chunks", although the chunks were forceloaded before the restart;
- never auto-unloads, even with no players in it for well over the 1200-tick limit (`CustomServerLevel.STOP_AFTER`). One test world stayed loaded for more than 2600 ticks.

The same save opened as the server's own world lists its forced chunks (66 in the test save), so vanilla behaves differently.

Imports make this visible more often: a world's `data/minecraft/chunk_tickets.dat` is carried with its dimension, so a survival save with chunk loaders brings its forceloads along.

## Reproduce

With the `mc-server-probe` skill (`--cmd` mode):

1. `run.sh --universe fl --fresh --cmd "worlds create imp:fl; @await created|failed 600; execute in imp:fl run forceload add 0 0; execute in imp:fl run forceload query; save-all flush"`. The query lists chunk 0, 0.
2. `run.sh --universe fl --cmd "execute in imp:fl run forceload query; wait:2600; @levels"`. Observed: "No force loaded chunks", and `imp:fl` is still in `@levels`. Expected: chunk 0, 0 listed, and the world either kept loaded because of it (vanilla treats a level with active tickets as active) or reported consistently.
3. `run.sh --universe fl --cmd "execute in imp:fl run forceload remove all; wait:1300; @levels"`. Observed: "Unmarked all force loaded chunks", but `imp:fl` is still loaded 1300 ticks later. The removal does not reach whatever keeps the level active. (During import testing, some imported worlds did unload after `forceload remove all`, so this step is not consistent.)

Steps 1-3 were run on 2026-10-29 on the import branch build; the output matched the observations above.

Imported variant: import any fixture built with `/forceload` (for example a vanilla save from the import test recipes) and run steps 2 and 3 against the imported id.

## What is known

- Vanilla keeps forced chunks in `TicketStorage` (`world/level/TicketStorage.java`), saved per dimension as `chunk_tickets`. On load, saved tickets are "deactivated"; `MinecraftServer.prepareLevels` reactivates them for the levels that exist at startup (`MinecraftServer.java:549-553`).
- mc-worlds levels load later, so `state/ActivationLevelState.java:24-29` calls `activateAllDeactivatedTickets()` on the level's `TicketStorage` when the level becomes active.
- `forceload query` reads `level.getForceLoadedChunks()`, which is `TicketStorage.chunksWithForcedTickets` (`TicketStorage.java:388-390`). That set is rebuilt in `updateForcedChunks()` (`:270`) and added to in `addTicket` (`:174`).
- `ServerLevel.tick` resets `emptyTime` while `chunkSource.hasActiveTickets()` is true (`ServerLevel.java:417-420`), and `ActiveLevelState` only stops a level when `getEmptyTime() > STOP_AFTER`. So a level whose tickets are active never unloads, which matches the second symptom.

Unverified explanations to check first, in this order:

1. The tickets are active (the level keeps ticking) but `chunksWithForcedTickets` is not updated on the reactivation path, so the query shows nothing and `forceload remove` cannot find them. Step 3 points this way.
2. The `TicketStorage` instance reactivated in `ActivationLevelState` is not the one the level's chunk source and `forceload` command use.
3. Tickets are activated twice, or under a different ticket type, after the lazy load.

## Expected behaviour

Match vanilla: after a restart, `forceload query` lists the saved forced chunks, and a world with forced chunks counts as active. Whether such a world should still auto-unload is a separate decision. Vanilla never unloads levels, so mc-worlds has to choose: keep worlds with forced chunks loaded (consistent with vanilla's "active" rule), or unload them and reactivate their tickets on the next load.
