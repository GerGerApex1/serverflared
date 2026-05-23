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
		maven("https://maven.wagyourtail.xyz/snapshots") { name = "WagYourTail Snapshots" }

	}
	includeBuild("build-logic")
}

plugins {
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
	id("dev.kikugie.stonecutter") version "0.9.4"
}
buildscript {
	dependencies {
		classpath("org.apache.commons:commons-compress:1.27.1")
	}
}
val loaders = listOf("forge", "fabric", "neoforge")
val minecraftVersions = listOf(
	"26.1",
)

stonecutter {
	create(rootProject) {
		fun createVersionDirectory(mcVersionList: List<String>, loaderList: List<String>) {
			for (mcVersion in mcVersionList) {
				for (loader in loaderList) {
					val minorVersion = mcVersion.split(".").getOrNull(1)?.toIntOrNull()?: continue
					//if (!isSupported(minorVersion, loader)) continue
					println("Adding version $mcVersion with loader $loader")
					version("$mcVersion-$loader", mcVersion).buildscript = "build.$loader.gradle.kts"
				}
			}
		}

		createVersionDirectory(minecraftVersions, loaders)
		vcsVersion = "26.1-forge"
	}
}
fun isSupported(minorVersion: Int, loader: String): Boolean {

	return when (loader) {
		"fabric"   -> minorVersion >= 16
		"neoforge" -> minorVersion >= 20
		else       -> true
	}
}
