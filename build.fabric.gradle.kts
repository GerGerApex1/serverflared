import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.kotlin.dsl.named

plugins {
	id("mod-platform")
	alias(libs.plugins.gradleup.shadow)
	alias(libs.plugins.jvmdowngrader)
	alias(libs.plugins.architectury.loom)
}
extra["essential.defaults.loom.fabric-loader"] =
	"net.fabricmc:fabric-loader:${property("fabric.loader")}"

stonecutter {
	val (version, loader) = current.project.split('-', limit = 2)
	properties.tags(version, loader)
}
platform {
	loader = "fabric"
	dependencies {
		required("minecraft") {
			versionRange = ">=${prop("deps.minecraft.min")} <${prop("deps.minecraft.max")}"
			environment = "server"
		}
		required("fabric-api") {
			slug("fabric-api")
			versionRange = ">=${prop("deps.fabric-api")}"
		}
		required("fabricloader") {
			versionRange = ">=${property("fabric.loader")}"
		}
	}
}
dependencies {
	implementation(libs.jackson.core)
	implementation(libs.jackson.dataformat.yaml)
	implementation(libs.jackson.databind)
	implementation(libs.jackson.annotations)
	implementation(libs.snakeyaml)
	minecraft("com.mojang:minecraft:${prop("loader.minecraft")}")
	mappings(loom.officialMojangMappings())
	modImplementation("net.fabricmc.fabric-api:fabric-api:${prop("deps.fabric-api")}")
}


tasks.assemble {
	//dependsOn("remapJar")
}
