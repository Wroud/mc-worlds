plugins {
    id("dev.kikugie.stonecutter")
    id("org.jetbrains.changelog") version "2.4.0"
}
stonecutter active "1.21.9-rc1"

changelog {
    path = rootProject.file("CHANGELOG.md").path
    version = findProperty("mod_version") as String
}