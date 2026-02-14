import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
	id("mod-platform")
}

platform {
	loader = "neoforge"
	dependencies {
		required("minecraft") {
			forgeVersionRange = "[${prop("deps.minecraft")}, ${prop("deps.minecraft.maxVersion")})"
			environment = "server"
		}
		required("neoforge") {
			forgeVersionRange = "[1,)"
		}

	}
}

unimined.minecraft {
	version = prop("deps.minecraft")

	mappings {
		mojmap()
	}

	neoForge {
		loader(prop("deps.neoforge"))
	}

	minecraftRemapper.config {
		ignoreConflicts(true)
	}
	runs {
		config("client") {

		}
		config("server") {
			workingDir("run/")
			//name = "NeoForge Server (${prop("deps.minecraft")})"
		}
	}
}
dependencies {
	implementation(libs.jackson.dataformat.yaml)
	implementation(libs.jackson.core)
	implementation(libs.jackson.dataformat.yaml)
	implementation(libs.jackson.databind)
	implementation(libs.jackson.annotations)
	implementation(libs.snakeyaml)
	/*
	include(libs.jackson.dataformat.yaml)
	include(libs.jackson.databind)
	include(libs.jackson.annotations)
	include(libs.snakeyaml)
	include(libs.jackson.core)
	forgeRuntimeLibrary(libs.jackson.dataformat.yaml)
	forgeRuntimeLibrary(libs.jackson.databind)
	forgeRuntimeLibrary(libs.jackson.annotations)
	forgeRuntimeLibrary(libs.snakeyaml)
	forgeRuntimeLibrary(libs.jackson.core)
	 */
}
repositories {
	maven {
		name = "NeoForged"
		url = uri("https://maven.neoforged.net/releases")
	}
}
