import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.ChangelogPluginExtension

plugins {
    id("fabric-loom") version "1.11-SNAPSHOT"
    id("maven-publish")
    id("me.modmuss50.mod-publish-plugin") version "0.8.4"
    id("org.jetbrains.changelog")
}

version = findProperty("mod_version") as String + "+" + findProperty("minecraft_version")
group = findProperty("maven_group") as String

base {
    archivesName = findProperty("archives_base_name") as String
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(findProperty("java_version") as String)
}

repositories {
    mavenCentral()
}

loom {
    splitEnvironmentSourceSets()

    runConfigs.all {
        ideConfigGenerated(true)
    }
}

fun DependencyHandlerScope.includeMod(dep: String) {
    include(modImplementation(dep)!!)
}

fun DependencyHandlerScope.includeDep(dep: String) {
    include(implementation(dep)!!)
}

dependencies {
    // To change the versions see the gradle.properties file
    minecraft("com.mojang:minecraft:${findProperty("minecraft_version")}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${findProperty("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${findProperty("fabric_version")}")

    includeMod("me.lucko:fabric-permissions-api:${findProperty("permission_api_version")}")
}

publishMods {
    file.set(tasks.remapJar.get().archiveFile)
    type.set(STABLE)
    changelog.set(fetchChangelog())

    displayName = "Worlds ${version.get()}"
    modLoaders.add("fabric")


    // curseforge {
    //     accessToken = providers.environmentVariable("CURSEFORGE_TOKEN")
    //     projectId = ""
    //     minecraftVersions.addAll(findProperty("curseforge_minecraft_versions")!!.toString().split(", "))
    // }
    modrinth {
        accessToken = providers.environmentVariable("MODRINTH_TOKEN")
        projectId = "rDdf0tz6"
        minecraftVersions.addAll(findProperty("modrinth_minecraft_versions")!!.toString().split(", "))
    }
    github {
        accessToken = providers.environmentVariable("GITHUB_TOKEN")
        repository = providers.environmentVariable("GITHUB_REPOSITORY").getOrElse("wroud/mc-worlds")
        commitish = providers.environmentVariable("GITHUB_REF_NAME").getOrElse("main")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            
            groupId = project.group.toString()
            artifactId = base.archivesName.get()
            version = project.version.toString()
        }
    }
}

tasks {
    processResources {
        val props = mapOf(
            "version" to project.version,
            "javaVersion" to findProperty("java_version")
        )

        inputs.properties(props)

        filesMatching(listOf("fabric.mod.json", "*.mixins.json")) {
            expand(props)
        }
    }
}

fun fetchChangelog(): String {
    val log = rootProject.extensions.getByType<ChangelogPluginExtension>()
    val modVersion = findProperty("mod_version")!!.toString()
    return if (log.has(modVersion)) {
        log.renderItem(
                log.get(modVersion).withHeader(false),
                Changelog.OutputType.MARKDOWN
        )
    } else {
        ""
    }
}