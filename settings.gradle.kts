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
stonecutter {
	create("modloaders") {
		fun createVersionDirectory(mcVersionList: List<String>, loaderList: List<String>) {
			for (mcVersion in mcVersionList) {
				for (loader in loaderList) {
					val minorVersion = mcVersion.split(".").getOrNull(1)?.toIntOrNull() ?: continue
					if (!isSupported(minorVersion, loader)) continue


					val buildScript = "build.$loader.gradle.kts"
					version("$mcVersion-$loader", mcVersion).buildscript = buildScript
					println("Adding version $mcVersion with loader $loader")
				}
			}
		}
		fun createDeobfuscatedVersionDirectory(mcVersionList: List<String>, loaderList: List<String>) {
			for (mcVersion in mcVersionList) {
				for (loader in loaderList) {
					val buildScript = "build.deobfuscated.gradle.kts"
					version("$mcVersion-$loader", mcVersion).buildscript = buildScript
					println("Adding deobfuscated version $mcVersion with loader $loader")
				}
			}
		}

		// createDeobfuscatedVersionDirectory(listOf("26.1"), listOf("fabric", "neoforge"))
		// createDeobfuscatedVersionDirectory(listOf("26.2"), listOf("fabric", "neoforge"))
		createDeobfuscatedVersionDirectory(listOf("26.1", "26.2"), loadersNoForge)
		createVersionDirectory(minecraftVersions, loaders)
		vcsVersion = "1.21.1-fabric"
	}
	create("spigot") {
		version("1.8.8-spigot").buildscript = "build.spigot.gradle.kts"
		version("1.16.1-spigot").buildscript = "build.spigot.gradle.kts"
	}
}

fun isSupported(minorVersion: Int, loader: String): Boolean =
	when (loader) {
		"fabric" -> minorVersion >= 16
		"neoforge" -> minorVersion >= 20
		else -> true
	}
include("common")
