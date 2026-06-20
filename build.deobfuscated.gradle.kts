import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
	id("mod-platform")
	alias(libs.plugins.gradleup.shadow)
	alias(libs.plugins.architectury.loom)
}
val (version, mcLoader) = stonecutter.current.project.split('-', limit = 2)

stonecutter {
	properties.tags(version, mcLoader)
}
platform {
	loader = mcLoader
	dependencies {
		required("minecraft") {
			versionRange = ">=${prop("deps.minecraft.min")} <${prop("deps.minecraft.max")}"
			environment = "server"
		}
		if (mcLoader == "fabric") {
			required("fabric-api") {
				slug("fabric-api")
				versionRange = ">=${prop("deps.fabric-api")}"
			}
			required("fabricloader") {
				versionRange = ">=${property("fabric.loader")}"
			}
		}
		if (mcLoader == "forge") {
			required("forge") {
				forgeVersionRange = "(1,)"
			}
		}
		if (mcLoader == "neoforge") {
			required("neoforge") {
				forgeVersionRange = "[1.0,)"
			}
		}
	}
}
/*
unimined.minecraft {
	version = prop("loader.minecraft")
	side("server")
	runs {
		config("server") {
			//workingDir("run/")
			//name = "Fabric Server (${prop("deps.minecraft")})"
		}
	}
}
if (mcLoader == "fabric") {
 	unimined.minecraft { fabric { loader("${property("fabric.loader")}") } }
}
if (mcLoader == "forge") {
	unimined.minecraft { minecraftForge { loader("${property("loader.forge")}") } }
}
if (mcLoader == "neoForge") {
	unimined.minecraft { neoForge { loader("${property("loader.neoforge")}") } }

}
*/
val shadowImpl by configurations.creating {
	isCanBeResolved = true
	isCanBeConsumed = false
	extendsFrom(configurations.implementation.get())
}
tasks.named<ShadowJar>("shadowJar") {
	configurations = listOf(shadowImpl)

	archiveClassifier.set("shadow")

	relocate("com.fasterxml.jackson", "me.gergerapex1.shaded.fasterxml.jackson")
	relocate("org.yaml.snakeyaml", "me.gergerapex1.shaded.org.yaml.snakeyaml")
}

tasks.assemble {
	dependsOn("shadowJar")
}
repositories {
	maven("https://maven.fabricmc.net/") { name = "Fabric" }
	maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
	maven("https://maven.minecraftforge.net/") { name = "Forge" }
	maven("https://repo.spongepowered.org/maven/")

}
dependencies {
	implementation(libs.jackson.core)
	implementation(libs.jackson.dataformat.yaml)
	implementation(libs.jackson.databind)
	implementation(libs.jackson.annotations)
	implementation(libs.snakeyaml)

	minecraft("com.mojang:minecraft:${prop("loader.minecraft")}")
	if (mcLoader == "fabric") {
		//implementation()
		implementation(
			fabricApi.module(
				"fabric-lifecycle-events-v1",
				prop("deps.fabric-api")
			)
		)
		implementation(
			fabricApi.module(
				"fabric-api-base",
				prop("deps.fabric-api")
			)
		)
		implementation("net.fabricmc:fabric-loader:${property("fabric.loader")}")
	}
	if(mcLoader == "neoforge") {
		"neoForge"("net.neoforged:neoforge:${prop("loader.minecraft")}.${prop("loader.neoforge")}")
	}
}
