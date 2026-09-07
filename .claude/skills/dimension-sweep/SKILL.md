---
name: dimension-sweep
description: Sweep the decompiled Minecraft sources for behaviour hardcoded to minecraft:overworld/the_nether/the_end, cross-reference it against this mod's fixes/ mixins, and report or fix what breaks in custom dimensions. Invoke when the user says "find dimension pinned behaviour", "what breaks in custom dimensions", "dimension fixes sweep", after a Minecraft version bump, or when a vanilla mechanic misbehaves in a created world. For the version bump itself, the mixin/javap/verification mechanics, and reporting discipline, use fabric-mod-migration.
---

# dimension-sweep

mc-worlds runs player-created worlds as real dimensions with their own keys (`minecraft:<id>`). Vanilla repeatedly branches on the *dimension key* rather than on data, so any such branch is a mechanic that silently misbehaves in a created world. This skill finds those branches and decides which ones this mod must patch.

Run it after every MC version bump — Mojang adds, moves and inverts these checks between versions.

Assumes `fabric-mod-migration` for the shared mechanics: extracting sources, confirming descriptors and ordinals with `javap`, the registration-completeness check, the verification gates, and observed-vs-derived reporting. This file adds only what is mc-worlds specific.

## The classifier

`util/DimensionDetectionUtil` is the single source of truth. Never re-derive its logic.

```java
isOverworldLikeDimension(Level) / isNetherLikeDimension(Level) / isEndLikeDimension(Level)
getVanillaDimensionMapping(Level)                  // custom key -> vanilla key; NEVER null (falls back to level.dimension())
getVanillaDimensionMapping(Holder<DimensionType>)  // may return null
shouldTreatAsVanillaDimension(Level, ResourceKey<Level>)
```

It matches on `dimensionTypeRegistration()` — the builtin type **or** the mod's tags in `tags/DimensionTypeTags` (`mc-worlds:overworld_like` / `nether_like` / `end_like`). A world created `from-dimension minecraft:the_end` carries the vanilla `minecraft:the_end` dimension *type*, so it classifies as End-like without needing the tag.

## 1. Sweep (four axes)

Point `$MC` at the extracted 26.x sources.

```bash
# 1. vanilla dimension keys
grep -rn "Level\.\(END\|NETHER\|OVERWORLD\)\b" --include="*.java" "$MC" | grep -v "/world/level/Level.java"

# 2. dimension-type and level-stem pins
grep -rn "BuiltinDimensionTypes\.\|LevelStem\.\(OVERWORLD\|NETHER\|END\)" --include="*.java" "$MC"

# 3. key comparisons
grep -rn "dimension()\s*\(==\|!=\)\s*[A-Za-z]\|dimension()\.equals(" --include="*.java" "$MC"

# 4. implicit overworld stand-ins
grep -rn "\.overworld()" --include="*.java" "$MC"
```

Triage each hit into **already covered**, **needs a fix**, or **no action**. Ignore hits in `data/`, `util/datafix/`, `util/filefix/`, `gametest/`, and world-creation screens — datagen, migration and UI, not runtime mechanics.

## 2. Never register a dormant mixin without re-deriving it

`fabric-mod-migration` §4 has the registration check. The mc-worlds-specific lesson is what to do when it fires: **`fixes.ServerPlayerTriggerMixin` had existed unregistered since it was written, and its logic was also wrong** — it fired on *entering* a Nether-like dimension when vanilla fires `NETHER_TRAVEL` on *leaving*, and its injection point sat inside vanilla's own `if`, so it could never have run. A mixin nobody loaded is a mixin nobody tested. Re-derive it against current vanilla before wiring it up.

## 3. Fix patterns

**Map the key, let vanilla's comparison stand.** This is the house pattern — 14 of the 15 `fixes/` mixins use it. Feed vanilla's own `==`/`!=` a mapped key instead of overriding the outcome.

```java
@ModifyExpressionValue(method = "canHaveWeather", at = @At(value = "INVOKE",
    target = "Lnet/minecraft/world/level/Level;dimension()Lnet/minecraft/resources/ResourceKey;"))
private ResourceKey<Level> mcworlds$mapDimension(ResourceKey<Level> original) {
    return DimensionDetectionUtil.getVanillaDimensionMapping((Level) (Object) this);
}
```

No null check — the `Level` overload cannot return null. Add `ordinal` only when the method has more than one matching invoke, and confirm it with `javap -c` first. Where the pin is a `GETSTATIC` of `Level.END`/`NETHER` rather than a `dimension()` call, map that instead with `@At(value = "FIELD", ...)` — see `fixes/FrostedIceBlockMixin`, `EndPortalBlockMixin`, `FallingBlockEntityMixin`. Where the key arrives as a parameter, use `@ModifyVariable(argsOnly = true, ordinal = N)` at HEAD — see `fixes/ChangeDimensionTriggerMixin`.

Mapping is preferred over narrowing the result (`@ModifyReturnValue`) for two concrete reasons: with `defaultRequire: 1`, an `@At` on the `dimension()` invoke **fails loudly at class load** the day Mojang drops the term — the notification this sweep exists to produce — whereas a return override survives silently and keeps enforcing a policy vanilla no longer has; and mapping stays correct if the gate's polarity inverts, while an AND-ed override can only ever subtract.

Reserve return-overriding for **mod-owned policy with no vanilla term to map** — `fixes/ServerLevelMixin`'s `CustomServerLevel.canTeleport()` gate is the one legitimate example in the tree.

## 4. Standing decisions

The covered-methods list is derivable, so don't maintain one — regenerate it:

```bash
grep -h "@Mixin(\|method = " src/main/java/dev/wroud/mc/worlds/mixin/fixes/*.java
```

What is *not* derivable, and is the real value here:

**Data-driven in 26.3 — do not chase.** `DimensionSpecialEffects` no longer exists; sky/fog are `EnvironmentAttributeMap` entries and `SkyRenderer` keys off `dimensionType().skybox()`. Dragon fight is gated on `dimensionType().hasEnderDragonFight()` with per-level `SavedData`. Bed and respawn-anchor behaviour are `BED_RULE` / `RESPAWN_ANCHOR_WORKS` attributes. Nether coordinate scaling is `coordinateScale`. The `overworld()` fallbacks in `MinecraftServer.findRespawnDimension`, `PrepareSpawnTask`, and `ServerPlayer.RespawnConfig` are null-guards already satisfied by `MinecraftServerMixin`'s lazy `getLevel`.

**Deliberately unpatched.** `ServerGamePacketListenerImpl`'s post-credits `CHANGED_DIMENSION.trigger(player, Level.END, Level.OVERWORLD)`. Hardcoded, but the vanilla keys are what the advancement criteria expect; mapping them risks breaking "The End?".

**The trap to remember.** Vanilla's End dimension type has `hasSkyLight=true, hasCeiling=false`, so `Level.canHaveWeather()`'s `dimension() != END` was the *only* thing suppressing storms there — every custom End-like world passed the other two terms and rained. Expect more gates of this shape: a data-driven predicate with one key comparison welded on the end.

## 5. Verify

Run `fabric-mod-migration` §6's gates. The mc-worlds-specific part is creating one world of each shape, which exercises the classifier and any constructor-time gate (`ServerLevel.<init>` calls `canHaveWeather()`):

```
worlds create end_test from-dimension minecraft:the_end
worlds create nether_test from-dimension minecraft:the_nether
worlds create over_test
```

`worlds create` takes an `IdentifierArgument`, so a bare `end_test` becomes `minecraft:end_test` — that is the key for `/execute in`. Two free signals confirm the classifier without extra tooling: End-like and Nether-like worlds skip the `Preparing spawn` log line that overworld-like worlds emit, and each world gets its dimension type's default clock (`the_end` has one, `the_nether` has none).

Level weather state is **not** readable from the server console, so a weather fix can only be confirmed in-game.
