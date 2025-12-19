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
		maven("https://files.minecraftforge.net/maven") { name = "Forge" }
		maven("https://jitpack.io") { name = "Jitpack" }
		exclusiveContent {
			forRepository { maven("https://api.modrinth.com/maven") { name = "Modrinth" } }
			filter { includeGroup("maven.modrinth") }
		}
	}
	includeBuild("build-logic")
}

plugins {
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
	id("dev.kikugie.stonecutter") version "0.8-beta.3"
}

val loaders = listOf("fabric", "neoforge", "forge")
val minecraftVersions = listOf(
	"1.8.9",
	"1.9",
	"1.10.2",
	"1.11",
	"1.12.2",
	"1.14.4",
	"1.15.2",
	"1.16.5",
	"1.18.2",
	"1.19.2",
	"1.20.4",
	"1.21.1",
)

stonecutter {
	create(rootProject) {
		fun createVersionDirectory(mcVersionList: List<String>, loaderList: List<String>) {
			for (mcVersion in mcVersionList) {
				for (loader in loaderList) {
					val minorVersion = mcVersion.split(".")[1].toInt();
					// we dont support fabric versions <= 1.16.5
					if(minorVersion < 16 && loader == "fabric") continue
					// we dont support neoforge versions <= 1.19.x
					if(minorVersion < 20 && loader == "neoforge") continue
					// temporary don't support forge because it's an asshole modloader
					if(loader == "forge") continue

					version("$mcVersion-$loader", mcVersion).buildscript = "build.$loader.gradle.kts"
				}
			}
		}
		createVersionDirectory(minecraftVersions, loaders)
		vcsVersion = "1.21.1-fabric"
	}
}
