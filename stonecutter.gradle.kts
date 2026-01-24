import org.jetbrains.kotlin.gradle.utils.property

plugins {
	alias(libs.plugins.stonecutter)
	alias(libs.plugins.dotenv)
	alias(libs.plugins.fabric.loom).apply(false)
	alias(libs.plugins.neoforged.moddev).apply(false)
	alias(libs.plugins.jsonlang.postprocess).apply(false)
	alias(libs.plugins.mod.publish.plugin).apply(false)
	alias(libs.plugins.kotlin.jvm).apply(false)
	alias(libs.plugins.devtools.ksp).apply(false)
	//alias(libs.plugins.fletching.table).apply(false)
	alias(libs.plugins.legacyforge.moddev).apply(false)
	alias(libs.plugins.gradleup.shadow).apply(false)
}

stonecutter active file(".sc_active_version")

for (version in stonecutter.versions.map { it.version }.distinct()) tasks.register("publish$version") {
	group = "publishing"
	dependsOn(stonecutter.tasks.named("publishMods") { metadata.version == version })
}

stonecutter tasks {
	val ordering = versionComparator.thenComparingInt { task ->
		if (task.metadata.project.endsWith("fabric")) 1 else 0
	}

	listOf("publishModrinth", "publishCurseforge").forEach { taskName ->
		gradle.allprojects {
			if (project.tasks.findByName(taskName) != null) {
				order(taskName, ordering)
			}
		}
	}
}

stonecutter parameters {
	constants.match(node.metadata.project.substringAfterLast('-'), "fabric", "neoforge", "forge")
	filters.include("**/*.fsh", "**/*.vsh")
	swaps["mod_version"] = "\"" + property("mod.version") + "\";"
	swaps["mod_id"] = "\"" + property("mod.id") + "\";"
	swaps["mod_name"] = "\"" + property("mod.name") + "\";"
	swaps["mod_group"] = "\"" + property("mod.group") + "\";"
	swaps["minecraft"] = "\"" + node.metadata.version + "\";"
	val minorVersion = node.metadata.version
		.split(".")
		.getOrNull(1)
		?.toIntOrNull()

	val isLegacyForge = minorVersion?.let { it <= 12 } ?: false
	constants["legacy_forge"] = isLegacyForge
	constants["release"] = property("mod.id") != "modtemplate"

	// 1.10.2 swap
	swaps["mc_1_10_2_port"] = when {
		current.parsed < "1.10" -> "return server.getPort();"
		else -> "return server.getServerPort();"
	}
	swaps["mc_1_10_2_hostname"] = when {
		current.parsed < "1.10" -> "return server.getHostname();"
		else -> "return server.getServerHostname();"
	}
	//println(current.parsed >= "1.18")
	replacements.string("forge_imports_modern", current.parsed >= "1.18") {
		replace("net.minecraftforge.fml.event.server", "net.minecraftforge.event.server")
		replace("FMLServerStartedEvent", "ServerStartedEvent")
		replace("FMLServerStartingEvent", "ServerStartingEvent")
		replace("FMLServerStoppingEvent", "ServerStoppingEvent")
		// remove FML from event names
	}
}
