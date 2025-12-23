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
	}
	includeBuild("build-logic")
}

plugins {
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
	id("dev.kikugie.stonecutter") version "0.8-beta.3"
}

val loaders = listOf("fabric", "neoforge", "forge",)
val minecraftVersions = listOf(
	"1.8.9",
	"1.9",
	"1.10.2",
	"1.11",
	"1.12.2",
	//
	//"1.14.4",
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
					val minorVersion = mcVersion.split(".")[1].toInt()
					// we dont support fabric versions <= 1.16.5
					if(minorVersion < 16 && loader == "fabric") continue
					// we dont support neoforge versions <= 1.19.x
					if(minorVersion < 20 && loader == "neoforge") continue
					// if minor version is 16 and below use legacy forge
					if(loader == "forge") {
						version("$mcVersion-$loader", mcVersion).buildscript = "build.legacyforge.gradle.kts"
						continue
					}

					version("$mcVersion-$loader", mcVersion).buildscript = "build.$loader.gradle.kts"
				}
			}
		}
		createVersionDirectory(minecraftVersions, loaders)
		vcsVersion = "1.21.1-fabric"
	}
}
