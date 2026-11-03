# MC-Worlds Agent Instructions

Fabric Minecraft mod for managing multiple per-server worlds via in-game commands. Mod ID `mc-worlds`. Published to Modrinth, CurseForge, GitHub Releases and GitHub Packages from CI (`.github/workflows/release.yml`, `build.gradle.kts` `publishMods`; `platforms` and `dry_run` inputs). Versions in `gradle.properties` (mod) and `versions/latest/gradle.properties` (MC/Java).

**Read `../mc-worlds-internal/AGENTS.md` first when it exists.** It holds the architecture, patterns, version bump checks and test recipes, and `../mc-worlds-internal/docs/features/` holds the design docs and roadmap. Those notes are private: design plans, test plans and implementation notes never go in this public repository.

## Docs

- `docs/how-it-works.md`: the public overview. Keep it to behaviour, with no class names, mixin targets or test plans.
- `docs/compatibility.md`: what the README and store page claim about other mods. List a mod as tested with a real player only after that run.
- `API.md`: the public API, which is everything public on `McWorld`.
- `LISTING.md`: the store page, built with the `mod-listing` skill.

## Build & Run

```bash
./gradlew build         # build JAR → versions/latest/build/libs/worlds-<mod_version>+<mc_version>.jar
./gradlew runClient     # dev client
./gradlew runServer     # dev server (no test suite)
./gradlew runDatagen    # data generation
./gradlew genSources    # regenerate decompiled MC sources after MC upgrade
```

WorldThreader compatibility is a default check: `.agents/skills/mc-server-probe/scripts/compat-check.sh`.

Runtime checks go through the `mc-server-probe` skill's `--cmd` mode; releases through `mod-release`; the store page through `mod-listing`. The repository's `dimension-sweep` skill lives in `.agents/skills/`, with a symlink in `.claude/skills/`. The shared skills live in `../mc-mod-skills`; local symlinks in both skill directories are git-ignored. Recreate missing shared links from the repository root with:

```bash
for n in mc-server-probe mc-client mod-release fabric-mod-migration mod-listing; do
  for base in .agents .claude; do
    test -L "$base/skills/$n" || ln -s "../../../mc-mod-skills/$n" "$base/skills/$n"
  done
done
```

Per-mod values are in `.claude/mod-skills.env`.

## Conventions

- Add an operation to `McWorld`, then call it from a command in `command/`, registered in `WorldsCommands`. Text goes through `Component.translatable`, with keys in `src/main/resources/assets/mc-worlds/lang/en_us.json`.
- Mixins live under `src/main/java/dev/wroud/mc/worlds/mixin/` and are registered in `src/main/resources/worlds.mixins.json` or `src/client/resources/worlds.client.mixins.json`.
- Persisted data uses `Codec<T>` via `RecordCodecBuilder`.

## Minecraft Source (Mojmap)

Fabric Loom decompiles MC into source JARs under `.gradle/loom-cache/minecraftMaven/net/minecraft/`. Manual extract: `unzip .../minecraft-common-*-sources.jar -d /tmp/mc-src`. If the stable version has no `-sources.jar` yet, run `./gradlew :latest:genSources` first. Do not read a `-pre`/`-rc` decompile for the stable version.
