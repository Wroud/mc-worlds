# mc-worlds — definition of done

**There is no test suite, no linter and no formatter.** Do not look for `./gradlew test` — it is `NO-SOURCE`. Verification is manual and gated on running the game.

`BUILD SUCCESSFUL` is **not** sufficient whenever mixins are involved: injection points resolve at *class load*, so a renamed, split or moved vanilla method compiles clean and then crashes the game. Run the gates that can actually observe it.

## Gates

1. `./gradlew build` — compiles and runs datagen.
2. `./gradlew runServer --console=plain --args="--nogui --world world-<tag>"` — reaching `Done (Ns)!` validates every server-side mixin (`required: true` crashes on an unmatched injector) plus everything class-loaded during boot: commands, advancement predicates and triggers.
3. `./gradlew runClient` — reaching the main menu validates client mixins loaded at startup (`Minecraft`, item models). Classes that only load on world join are **not** covered; verify those against decompiled sources instead.
4. Functional smoke test over the FIFO recipe in `mem:suggested_commands`. Scope it to what changed; for dimension work, create one world of each shape (`from-dimension minecraft:the_end` / `the_nether` / default) — this also exercises constructor-time gates, since `ServerLevel.<init>` calls `canHaveWeather()`.

Grep the run log for `InvalidInjectionException|MixinApplyError|Encountered an unexpected` — count them, do not eyeball.

If persistence changed (`SavedData`, a codec, an id): set a distinctive value, `stop`, restart against the same world, read it back, and confirm the on-disk path is unchanged (`find <world> -name '*.dat'`).

## Reporting

State which gates ran, and separate **observed** from **derived**. Some state is not readable from the server console (level weather, sky) — for those, say the mixin was verified to apply and the behaviour derived, and name the in-game check that would confirm it. Never report a derivation as an observation.

## Release chores

Only when cutting a version: `CHANGELOG.md` entry under `## <mod_version> - <YYYY-MM-DD>` (the publish plugin reads it), and check `curseforge_minecraft_version` — CurseForge labels pre-releases `<major.minor>-Snapshot`, not the raw MC version string; a stable bump uses the plain version, which CurseForge may not list yet on release day.

Release type is derived from `minecraft_version` in `build.gradle.kts` (`-snapshot` → ALPHA, `-pre`/`-rc` → BETA, else STABLE) — do not hardcode it.

Publishing runs in CI via `release.yml` (see `mem:suggested_commands`), never from a local machine — no tokens exist locally, and `publishMods` dry-runs without them. mc-stargate consumes this mod from GitHub Packages: release mc-worlds first, confirm the run succeeded, then push and release mc-stargate.
