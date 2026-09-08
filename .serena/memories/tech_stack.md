# mc-worlds — tech stack

- Java **25** (`options.release = 25`, toolchain from `java_version`). Mojmap names.
- Fabric Loom `1.17-SNAPSHOT` (plugin version in `build.gradle.kts`, not a property), `splitEnvironmentSourceSets()` → separate `main` / `client` / `datagen` source sets.
- Gradle wrapper **9.5.1**. **Coupling:** Loom 1.17.x declares `org.gradle.plugin.api-version 9.5.0`, so it requires Gradle 9.5+; bumping Loom without `gradle/wrapper/gradle-wrapper.properties` fails at *configuration* with `No matching variant ... org.gradle.plugin.api-version`.
- Stonecutter `0.7.10` (`settings.gradle.kts`), single version `latest`.
- SpongePowered Mixin (via Fabric Loader) + **MixinExtras** ≥ `0.5.0-rc.1`, declared in both mixins.json files.
- `me.modmuss50.mod-publish-plugin` (Modrinth/CurseForge/GitHub), `org.jetbrains.changelog` (release notes read from `CHANGELOG.md`), `maven-publish`.

## Where versions live

- `versions/latest/gradle.properties` — MC-coupled: `minecraft_version`, `fabric_version`, `java_version`, `curseforge_minecraft_version`.
- root `gradle.properties` — mod-owned: `mod_version`, `loader_version`, `maven_group`, project ids. Entries reading `[VERSIONED]` are stonecutter placeholders filled from the version file; never put a real version there.

Current pins live in those files — read them rather than trusting any memory. Bump procedure: the user-global `fabric-mod-migration` skill.

## Decompiled Minecraft sources

`./gradlew genSources`, then jars under `.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-{common,clientOnly}-<hash>/<version>/*-sources.jar`. Extract **both**; older versions persist under sibling hash dirs, so cross-version diffs are possible without re-downloading.
