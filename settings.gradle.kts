pluginManagement {
	repositories {
		mavenLocal()
		mavenCentral()
		gradlePluginPortal()
		maven("https://maven.fabricmc.net/") { name = "Fabric" }
		maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
		maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
		maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
		maven("https://maven.parchmentmc.org") { name = "ParchmentMC" }
		maven("https://maven.minecraftforge.net/") { name = "Forge" }
		maven("https://jitpack.io") { name = "Jitpack" }
		maven("https://maven.architectury.dev/") { name = "Architectury" }
		exclusiveContent {
			forRepository { maven("https://api.modrinth.com/maven") { name = "Modrinth" } }
			filter { includeGroup("maven.modrinth") }
		}
		maven("https://repo.essential.gg/repository/maven-public")
		maven("https://repo.spongepowered.org/maven/")
		maven("https://maven.wagyourtail.xyz/releases") { name = "WagYourTail Releases" }
		maven("https://maven.wagyourtail.xyz/snapshots") { name = "WagYourTail Releases" }

	}
	includeBuild("build-logic")
}

plugins {
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
	id("dev.kikugie.stonecutter") version "0.9.6"
}
buildscript {
	dependencies {
		classpath("org.apache.commons:commons-compress:1.27.1")
	}
}
val loaders = listOf("forge", "fabric", "neoforge")
val loadersNoForge = listOf("fabric", "neoforge")
val minecraftVersions = listOf(
	"1.8.9",
	"1.9",
	"1.10",
	"1.11",
	"1.12",
	// temporary phase out 1.13.2 support
	//"1.13.2",
	"1.17.1",
	"1.14.4",
	"1.15.2",
	"1.16.5",
	"1.18.2",
	"1.19.2",
	"1.20.2",
	"1.21.1",
)
// TODO: add 26.2 to lists once forge updates
/*
val deobfuscatedMinecraftVersions = listOf(
	"26.1",
	"26.2"
)
 */
val ciVersions = providers.gradleProperty("ci.versions").orNull
	?.split(",", ";", " ")
	?.map { it.trim().removePrefix(":modloaders:").removePrefix(":spigot:") }
	?.filter { it.isNotEmpty() }
	?.toSet()

fun shouldInclude(projectName: String): Boolean =
	ciVersions == null || projectName in ciVersions

stonecutter {
	val modloaderEntries = mutableListOf<Triple<String, String, String>>()
	for (mcVersion in listOf("26.1", "26.2", "26.3")) {
		for (loader in loadersNoForge) {
			val projectName = "$mcVersion-$loader"
			if (shouldInclude(projectName)) {
				modloaderEntries.add(Triple(projectName, mcVersion, "build.deobfuscated.gradle.kts"))
			}
		}
	}
	for (mcVersion in minecraftVersions) {
		for (loader in loaders) {
			val minorVersion = mcVersion.split(".").getOrNull(1)?.toIntOrNull() ?: continue
			if (!isSupported(minorVersion, loader)) continue
			val projectName = "$mcVersion-$loader"
			if (shouldInclude(projectName)) {
				modloaderEntries.add(Triple(projectName, mcVersion, "build.$loader.gradle.kts"))
			}
		}
	}

	if (modloaderEntries.isNotEmpty()) {
		create("modloaders") {
			for ((projectName, mcVersion, buildScript) in modloaderEntries) {
				version(projectName, mcVersion).buildscript = buildScript
			}
			vcsVersion = if (modloaderEntries.any { it.first == "1.21.1-fabric" }) {
				"1.21.1-fabric"
			} else {
				modloaderEntries.first().first
			}
		}
	}

	val spigotEntries = listOf("1.8.8-spigot", "1.16.1-spigot").filter(::shouldInclude)
	if (spigotEntries.isNotEmpty()) {
		create("spigot") {
			for (projectName in spigotEntries) {
				version(projectName).buildscript = "build.spigot.gradle.kts"
			}
		}
	}
}

fun isSupported(minorVersion: Int, loader: String): Boolean =
	when (loader) {
		"fabric" -> minorVersion >= 16
		"neoforge" -> minorVersion >= 20
		else -> true
	}
include("common")
