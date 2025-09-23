pluginManagement {
	repositories {
		mavenCentral()
		gradlePluginPortal()
		maven ("https://maven.fabricmc.net/")
	}
}

plugins {
	id("dev.kikugie.stonecutter") version "0.7.10"
}

stonecutter {
	create(rootProject) {
		versions("1.21.9-pre4")
		vcsVersion = "1.21.9-pre4"
	}
}