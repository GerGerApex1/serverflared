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
	loader = "forge"
	dependencies {
		required("minecraft") {
			forgeVersionRange =  "[${prop("deps.minecraft.min")}, ${prop("deps.minecraft.max")}]"
			environment = "server"
		}
		required("forge") {
			forgeVersionRange = "(1,)"
		}
	}
}
val minorVersion = prop("loader.minecraft").split(".")[1].toInt()

loom {
	forge {
		if(minorVersion <= 12) {
			pack200Provider.set(dev.architectury.pack200.java.Pack200Adapter())

		}
	}
}
val shadowImpl by configurations.creating {
	isCanBeResolved = true
	isCanBeConsumed = false
	extendsFrom(configurations.implementation.get())
}
dependencies {
	// implementation(libs.jackson.core)
	// implementation(libs.jackson.dataformat.yaml)
	// implementation(libs.jackson.databind)
	// implementation(libs.jackson.annotations)
	// implementation(libs.snakeyaml)
	minecraft("com.mojang:minecraft:${prop("loader.minecraft")}")
	if (14 <= minorVersion) {
		mappings(loom.officialMojangMappings())
	} else {
		if (!(providers.gradleProperty("mappings.mcp.channel").isPresent) || property("mappings.mcp.channel") == "snapshot") {
			mappings("de.oceanlabs.mcp:mcp_snapshot:${prop("mappings.mcp")}")
			println(prop("essential.defaults.loom"))
		} else if (property("mappings.mcp.channel") == "stable") {
			mappings("de.oceanlabs.mcp:mcp_stable:${prop("mappings.mcp")}")
		} else {
			error("Unknown MCP channel ${property("mappings.mcp.channel")}")
		}
	}
	forge("net.minecraftforge:forge:${prop("loader.minecraft")}-${prop("loader.forge")}")

	//"modImplementation"(include("net.fabricmc.fabric-api:fabric-api:${prop("deps.fabric-api")}"))
}


tasks.assemble {
	//dependsOn("remapJar")
}
