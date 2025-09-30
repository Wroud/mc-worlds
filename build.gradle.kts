import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.ChangelogPluginExtension

plugins {
    id("fabric-loom") version "1.11-SNAPSHOT"
    id("maven-publish")
    id("me.modmuss50.mod-publish-plugin") version "1.0.0"
    id("org.jetbrains.changelog") version "2.4.0"
}

version = findProperty("mod_version") as String + "+" + findProperty("minecraft_version")
group = findProperty("maven_group") as String

base {
    archivesName = findProperty("maven_artifact_id") as String
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

sourceSets {
    main {
        java {
            srcDir("versions/${stonecutter.current.version}/src/main/java")
        }
    }
}

fabricApi {
    configureDataGeneration() {
        modId = "mc-worlds-datagen"
        client = false
        createSourceSet = true
    }
}

fun DependencyHandlerScope.includeMod(dep: String) {
    include(modImplementation(dep)!!)
}

fun DependencyHandlerScope.includeDep(dep: String) {
    include(implementation(dep)!!)
}

dependencies {
    minecraft("com.mojang:minecraft:${findProperty("minecraft_version")}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${findProperty("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${findProperty("fabric_version")}")

    includeMod("me.lucko:fabric-permissions-api:${findProperty("permission_api_version")}")
}

publishMods {
    displayName = "${findProperty("mod_name")} ${version.get()}"
    file = tasks.remapJar.get().archiveFile
    changelog = fetchChangelog()

    type = STABLE
    modLoaders.add("fabric")
    modLoaders.add("quilt")


    curseforge {
        javaVersions.add(JavaVersion.VERSION_21)
        projectSlug = "worlds"

        projectId = findProperty("curseforge_project_id")!!.toString()
        minecraftVersions.add(findProperty("curseforge_minecraft_version")!!.toString())
        accessToken = providers.environmentVariable("CURSEFORGE_TOKEN")
    }
    modrinth {
        projectId = findProperty("modrinth_project_id")!!.toString()
        minecraftVersions.add(findProperty("minecraft_version")!!.toString())
        accessToken = providers.environmentVariable("MODRINTH_TOKEN")
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

afterEvaluate {
    tasks.findByName("runClient")?.dependsOn("runDatagen")
    tasks.findByName("runServer")?.dependsOn("runDatagen")
    tasks.findByName("build")?.dependsOn("runDatagen")
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