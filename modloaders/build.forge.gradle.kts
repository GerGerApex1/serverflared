plugins {
	id("mod-platform")
	alias(libs.plugins.gradleup.shadow)
}

stonecutter {
	val (version, loader) = current.project.split('-', limit = 2)
	properties.tags(version, loader)
}

platform {
	loader.set(Loader.FORGE)
	dependencies {
		required("minecraft") {
			forgeVersionRange = "[${prop("deps.minecraft.min")}, ${prop("deps.minecraft.max")}]"
			environment = "server"
		}
		required("forge") {
			forgeVersionRange = "(1,)"
		}
	}
}

val minorVersion = prop("loader.minecraft").split(".")[1].toInt()
val forgeAccessTransformerResource = when {
	minorVersion < 14 -> null
	minorVersion < 17 -> rootProject.file("modloaders/src/main/resources/forge/legacy/META-INF/accesstransformer.cfg")
	else -> rootProject.file("modloaders/src/main/resources/forge/modern/META-INF/accesstransformer.cfg")
}
val forgeDevelopmentAccessTransformer = if (minorVersion in 14..16) {
	rootProject.file("modloaders/access-transformers/forge-legacy-development.cfg")
} else {
	forgeAccessTransformerResource
}

unimined {
	minecraft {
		version = prop("loader.minecraft")
		loader("forge", prop("loader.forge"))
		mappings {
			if (minorVersion >= 14) {
				builtin("mojmap") {
					named("named")
					mapNamespace("mojmap" to "named")
				}
			} else {
				val mcpChannel = providers.gradleProperty("mappings.mcp.channel").orElse("snapshot").get()
				if (mcpChannel != "snapshot" && mcpChannel != "stable") {
					error("Unknown MCP channel $mcpChannel")
				}
				maven("de.oceanlabs.mcp:mcp:${prop("loader.minecraft")}:srg@zip") {
					mapNamespace("source" to "official", "target" to "searge")
				}
				mcp(mcpChannel, prop("mappings.mcp"))
			}
		}
		forgeDevelopmentAccessTransformer?.let { main.accessTransformers.from(it) }
	}
}

forgeAccessTransformerResource?.let { transformer ->
	tasks.processResources {
		from(transformer.parentFile.parentFile)
	}
}

dependencies {
	includeDep(libs.jackson.core)
	includeDep(libs.jackson.dataformat.yaml)
	includeDep(libs.jackson.databind)
	includeDep(libs.jackson.annotations)
	includeDep(libs.snakeyaml)
	includeDep(project(":common"))
}

tasks.assemble {
	dependsOn(tasks.matching { it.name == "remapJar" })
}
