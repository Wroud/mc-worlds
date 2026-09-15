import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.ChangelogPluginExtension
import java.net.HttpURLConnection
import java.net.URI

plugins {
    id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT"
    id("maven-publish")
    id("me.modmuss50.mod-publish-plugin") version "1.0.0"
    id("org.jetbrains.changelog") version "2.4.0"
}

version = findProperty("mod_version") as String + "+" + findProperty("minecraft_version")
group = findProperty("maven_group") as String

base {
    archivesName = findProperty("maven_artifact_id") as String
}

repositories {
    mavenCentral()
    maven("https://api.modrinth.com/maven") {
        content { includeGroup("maven.modrinth") }
    }
}

loom {
    splitEnvironmentSourceSets()

    runConfigs.all {
        ideConfigGenerated(true)
    }

    mods {
        create("mc-worlds") {
            sourceSet(sourceSets["main"])
            sourceSet(sourceSets["client"])
        }
    }

    runs {
        named("client") {
            programArgs("--username", "Wroud")
        }
    }
}

// sourceSets {
//     main {
//         java {
//             srcDir("versions/${stonecutter.current.version}/src/main/java")
//         }
//     }
// }

fabricApi {
    configureDataGeneration() {
        modId = "mc-worlds-datagen"
        client = false
        createSourceSet = true
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${findProperty("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${findProperty("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${findProperty("fabric_version")}")
    // modRuntimeOnly("maven.modrinth:<slug>:<version>") // Example mod from Modrinth
}

tasks {
    processResources {
        val props = mapOf(
            "version" to project.version,
            "javaVersion" to findProperty("java_version")
        )

        inputs.properties(props)

        filesMatching("fabric.mod.json") {
            expand(props)
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
	options.release = 25
}

java {
	  withSourcesJar()
    toolchain.languageVersion = JavaLanguageVersion.of(findProperty("java_version") as String)
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.named("sourcesJar") {
    dependsOn(tasks.named("runDatagen"))
}

val minecraftVersion = findProperty("minecraft_version")!!.toString()
val githubRepository = providers.environmentVariable("GITHUB_REPOSITORY").getOrElse("wroud/mc-worlds")

publishMods {
    dryRun = providers.environmentVariable("DRY_RUN").orNull == "true" ||
        listOf("CURSEFORGE_TOKEN", "MODRINTH_TOKEN").any { providers.environmentVariable(it).orNull.isNullOrEmpty() }
    displayName = "${findProperty("mod_name")} ${version.get()}"
    file = tasks.jar.get().archiveFile
    changelog = fetchChangelog()

    type = when {
        "-snapshot" in minecraftVersion -> ALPHA
        "-pre" in minecraftVersion || "-rc" in minecraftVersion -> BETA
        else -> STABLE
    }
    modLoaders.add("fabric")
    modLoaders.add("quilt")


    curseforge {
        javaVersions.add(JavaVersion.VERSION_25)
        clientRequired = true
        serverRequired = true
        projectSlug = "worlds"
        requires("fabric-api")

        projectId = findProperty("curseforge_project_id")!!.toString()
        minecraftVersions.add(findProperty("curseforge_minecraft_version")!!.toString())
        accessToken = providers.environmentVariable("CURSEFORGE_TOKEN")
    }
    modrinth {
        requires("fabric-api")

        projectId = findProperty("modrinth_project_id")!!.toString()
        minecraftVersions.add(minecraftVersion)
        accessToken = providers.environmentVariable("MODRINTH_TOKEN")
    }
    github {
        accessToken = providers.environmentVariable("GITHUB_TOKEN")
        repository = githubRepository
        commitish = providers.environmentVariable("GITHUB_REF_NAME").getOrElse("main")
    }
}

fun registerPublishedCheck(name: String, platform: String, url: String, headers: Map<String, String>) =
    tasks.register(name) {
        val modVersion = version.toString()
        doLast {
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", githubRepository)
            headers.forEach(connection::setRequestProperty)
            when (val code = connection.responseCode) {
                404 -> logger.lifecycle("$modVersion is not yet on $platform")
                200 -> throw GradleException("$modVersion is already published on $platform")
                else -> throw GradleException("Could not check $platform for $modVersion: HTTP $code from $url")
            }
        }
    }

val checkModrinthVersion = registerPublishedCheck(
    "checkModrinthVersion",
    "Modrinth",
    "https://api.modrinth.com/v2/project/${findProperty("modrinth_project_id")}/version/$version",
    emptyMap(),
)

val githubToken = providers.environmentVariable("GITHUB_TOKEN").orNull

val checkGithubRelease = registerPublishedCheck(
    "checkGithubRelease",
    "GitHub",
    "https://api.github.com/repos/$githubRepository/releases/tags/$version",
    githubToken?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap(),
)
checkGithubRelease.configure { onlyIf("GITHUB_TOKEN is set") { githubToken != null } }

tasks.named("publishModrinth") { dependsOn(checkModrinthVersion) }
tasks.named("publishGithub") { dependsOn(checkGithubRelease) }

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            
            groupId = project.group.toString()
            artifactId = base.archivesName.get()
            version = project.version.toString()
            
            pom {
                name.set(findProperty("mod_name") as String)
                description.set(findProperty("description") as String)
                url.set(findProperty("url") as String)

                licenses {
                    license {
                        name.set(findProperty("license") as String)
                        url.set(findProperty("license_url") as String)
                    }
                }
                
                developers {
                    developer {
                        id.set("wroud")
                        name.set("Wroud")
                        email.set("support@wroud.dev")
                    }
                }
                
                scm {
                    connection.set("scm:git:git://github.com/Wroud/mc-worlds.git")
                    developerConnection.set("scm:git:ssh://github.com:Wroud/mc-worlds.git")
                    url.set(findProperty("url") as String)
                }
            }
        }
    }
    
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/wroud/mc-worlds")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: project.findProperty("gpr.user") as String?
                password = System.getenv("GITHUB_TOKEN") ?: project.findProperty("gpr.key") as String?
            }
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