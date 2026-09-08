# mc-worlds — conventions

## Comments

**Zero comments by default** — enforced, not aspirational (see the user's global `CLAUDE.md`). The diff and surrounding code speak for themselves. The only admissible comment records a hidden runtime constraint a reader genuinely could not infer; prefer restructuring so it is unnecessary. Codec/schema descriptions are not comments. Do not touch pre-existing comments unless the code they describe changed — then delete rather than repair.

## Mixins

Three server-side packages under `mixin/`, all registered by hand in `src/main/resources/worlds.mixins.json`; client-only ones in `src/client/resources/worlds.client.mixins.json`.

- root `mixin/` — core wiring (server init, per-world clock/weather, wandering trader).
- `mixin/fixes/` — vanilla-parity fixes for custom dimensions.
- `mixin/filefix/` — DataFixer registration.

Invariants:

- `"required": true` + `"injectors": {"defaultRequire": 1}` — an unmatched injector **crashes at class load**, not at compile time. A green build proves nothing about mixins.
- The registration lists are hand-maintained; Mixin has no glob or package scan. A mixin file that is not listed is dead code that reads as coverage — this went unnoticed for many versions once. Verify the file set against the configs whenever mixins change.
- `worlds.mixins.json` is the **common** config: a `@Mixin(Level.class)` there applies client-side too. Target the widest class carrying the behaviour.
- **Prefer MixinExtras** (`@ModifyExpressionValue`, `@WrapOperation`, `@ModifyReturnValue`) over `@Redirect`, which claims an instruction exclusively and hard-fails when another mod wants it. A few legacy `@Redirect`s remain; convert on touch.
- Handler names are `mcworlds$`-prefixed; `@Unique` fields likewise.
- Prefer injecting on a **stable accessor** (`clockManager()`) over many volatile method descriptors — fewer edits per MC bump and better cross-mod behaviour.

## Dimension fixes — the house idiom

`util/DimensionDetectionUtil` is the only classifier; never re-derive it. It matches `dimensionTypeRegistration()` against the builtin type **or** the `mc-worlds:{overworld,nether,end}_like` tags in `tags/DimensionTypeTags`.

`getVanillaDimensionMapping(Level)` **never returns null** (falls back to `level.dimension()`); only the `Holder<DimensionType>` overload can. Do not write a null guard around the `Level` overload.

**Map the key, let vanilla's comparison stand** — feed vanilla's own `==`/`!=` a mapped key rather than overriding the outcome. This keeps the `@At` on the vanilla instruction, so the mixin fails loudly at class load if Mojang drops the term, and stays correct if the gate's polarity inverts. Reserve `@ModifyReturnValue`/`@Inject`-return for mod-owned policy that has no vanilla term to map. Confirm ordinals with `javap -c` before writing one.

Full sweep procedure, the four grep axes, and the standing "data-driven / deliberately unpatched" decisions live in `.claude/skills/dimension-sweep/SKILL.md`.

## Persistence & commands

- Persisted state uses `Codec<T>` built with `RecordCodecBuilder`, stored via `SavedData` + `SavedDataType`. `SavedDataType` equality is **id-based**; `ServerLevel.getDataStorage()` is per-dimension while `MinecraftServer.getDataStorage()` is the world root. Changing a `SavedDataType` id silently orphans existing saves.
- Commands are Brigadier, one class per command, registered in `command/WorldsCommands`, gated on `Commands.LEVEL_ADMINS`.
- User-facing text is `Component.translatable`; keys in `src/main/resources/assets/mc-worlds/lang/en_us.json`.
