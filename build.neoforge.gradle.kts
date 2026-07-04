import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.kotlin.dsl.named

plugins {
	id("mod-platform")
	alias(libs.plugins.gradleup.shadow)
	alias(libs.plugins.jvmdowngrader)
	alias(libs.plugins.architectury.loom)
}
extra["loom.platform"] =
	"forge"

stonecutter {
	val (version, loader) = current.project.split('-', limit = 2)
	properties.tags(version, loader)
}
platform {
	loader = "neoforge"
	dependencies {
		required("minecraft") {
			forgeVersionRange =  "[${prop("deps.minecraft.min")}, ${prop("deps.minecraft.max")}]"
			environment = "server"
		}
		required("neoforge") {
			forgeVersionRange = "[1.0,)"
		}

	}
}
val mcVersionArray = prop("loader.minecraft").split(".")

loom {
}
dependencies {
	implementation(libs.jackson.core)
	implementation(libs.jackson.dataformat.yaml)
	implementation(libs.jackson.databind)
	implementation(libs.jackson.annotations)
	implementation(libs.snakeyaml)
	minecraft("com.mojang:minecraft:${prop("loader.minecraft")}")
	mappings(loom.officialMojangMappings())

	neoForge("net.neoforged:neoforge:${prop("loader.neoforge")}")

	//"modImplementation"(include("net.fabricmc.fabric-api:fabric-api:${prop("deps.fabric-api")}"))
}
repositories {
	maven {
		name = "NeoForged"
		url = uri("https://maven.neoforged.net/releases")
	}
}


tasks.assemble {
	//dependsOn("remapJar")
}
