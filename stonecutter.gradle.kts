import org.jetbrains.kotlin.gradle.utils.property

plugins {
	alias(libs.plugins.stonecutter)
	alias(libs.plugins.dotenv)
	alias(libs.plugins.jsonlang.postprocess).apply(false)
	alias(libs.plugins.mod.publish.plugin).apply(false)
	alias(libs.plugins.kotlin.jvm).apply(false)
	alias(libs.plugins.devtools.ksp).apply(false)
	//alias(libs.plugins.fletching.table).apply(false)
	alias(libs.plugins.gradleup.shadow).apply(false)
	alias(libs.plugins.unimined).apply(false)
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

	val isLegacyForge = false
	constants["legacy_forge"] = isLegacyForge
	constants["release"] = property("mod.id") != "modtemplate"
	replacements.string(current.parsed > "1.18", "forge_imports_modern") {
		replace("FMLServerStartedEvent", "ServerStartedEvent")
		replace("FMLServerStartingEvent", "ServerStartingEvent")
		replace("FMLServerStoppingEvent", "ServerStoppingEvent")
	}
	swaps["fml_serverevents"] = when {
		eval(current.version, ">=1.18") -> "import net.minecraftforge.event.server.*;"
		eval(current.version, "~1.17") -> "import net.minecraftforge.fmlserverevents.*;"
		else -> "import net.minecraftforge.fml.event.server.*;"
	}
	swaps["fml_serverlifecyclehooks_1_18"] = when {
		eval(current.version, ">=1.18") -> "import net.minecraftforge.server.ServerLifecycleHooks;"
		eval(current.version, "~1.17") -> "import net.minecraftforge.fmllegacy.server.ServerLifecycleHooks;"
		else -> "import net.minecraftforge.fml.server.ServerLifecycleHooks;"
	}
}
