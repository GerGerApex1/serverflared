import org.gradle.kotlin.dsl.register

plugins {
	id("mod-platform")
	id("gg.essential.loom")
}

val minorVersion = "${prop("deps.minecraft")}".toString().split(".")[1].toInt()

platform {
	loader = "forge"
	dependencies {
		required("minecraft") {
			forgeVersionRange = "[${prop("deps.minecraft")}]"
		}
		required("forge") {
			forgeVersionRange = "[1,)"
		}
	}
}
dependencies {
	minecraft("com.mojang:minecraft:${prop("deps.minecraft")}")
	forge("net.minecraftforge:forge:${property("deps.minecraft")}-${property("deps.forge")}")
	println(minorVersion)
	if (14 <= minorVersion) {
		mappings(loom.officialMojangMappings())
	} else {
		mappings("de.oceanlabs.mcp:mcp_snapshot:${prop("deps.mcp")}@zip")
	}
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
			configName = "Forge Server (${prop("deps.minecraft")})"
		}
		runs.named("client") {
			ideConfigGenerated(false)
		}
	}
	runConfigs {
	forge {
		pack200Provider.set(dev.architectury.pack200.java.Pack200Adapter())
	}
}
/*
legacyForge {
	version = "${property("deps.minecraft")}-${property("deps.forge")}"

	runs {
		register("server") {
			server()
			gameDirectory = file("run/")
			ideName = "Legacy Forge Server (${stonecutter.active?.version})"
		}
	}


	mods {
		register(property("mod.id") as String) {
			sourceSet(sourceSets["main"])
		}
	}
}
*/

repositories {
	exclusiveContent {
		forRepository {
			maven {
				url = uri("https://repo.spongepowered.org/maven/")
			}
		}
		filter {
			//includeGroup("de.oceanlabs.mcp") // only dependencies from this group
		}
	}
}}

