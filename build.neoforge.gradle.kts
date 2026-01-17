import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
	id("mod-platform")
	id("gg.essential.loom")
}

platform {
	loader = "neoforge"
	dependencies {
		required("minecraft") {
			forgeVersionRange = "[${prop("deps.minecraft")}]"
		}
		required("neoforge") {
			forgeVersionRange = "[1,)"
		}
	}
}

dependencies {
	minecraft("com.mojang:minecraft:${prop("deps.minecraft")}")
	neoForge("net.neoforged:neoforge:${property("deps.neoforge")}")

	mappings(loom.officialMojangMappings())
	implementation(libs.jackson.dataformat.yaml)
	implementation(libs.jackson.databind)
}

loom {
	runs {
		runs.named("server") {
			server()
			ideConfigGenerated(true)
			runDir = "run/"
			environment = "server"
			configName = "NeoForge Server (${prop("deps.minecraft")})"
		}
		runs.named("client") {
			ideConfigGenerated(false)
		}
	}

	mods {
		register(property("mod.id") as String) {
			sourceSet(sourceSets["main"])
		}
	}

}
val shadowBundle: Configuration by configurations.creating {
	isCanBeConsumed = false
	isCanBeResolved = true
}
tasks.withType<ShadowJar> {
	configurations = listOf(shadowBundle)
	archiveClassifier.set("shadowed")
}
repositories {
	maven {
		name = "NeoForged"
		url = uri("https://maven.neoforged.net/releases")
	}
}
