plugins {
	id("mod-platform")
	alias(libs.plugins.gradleup.shadow)
}

stonecutter {
	val (version, loader) = current.project.split('-', limit = 2)
	properties.tags(version, loader)
}

platform {
	loader.set(Loader.NEOFORGE)
	dependencies {
		required("minecraft") {
			forgeVersionRange = "[${prop("deps.minecraft.min")}, ${prop("deps.minecraft.max")}]"
			environment = "server"
		}
		required("neoforge") {
			forgeVersionRange = "[1.0,)"
		}
	}
}

unimined {
	minecraft {
		version = prop("loader.minecraft")
		loader("neoforge", prop("loader.neoforge"))
		mappings {
			builtin("mojmap") {
				named("named")
				mapNamespace("mojmap" to "named")
			}
		}
		main.accessWideners.from(rootProject.file("modloaders/src/main/resources/serverflared.accesswidener"))
	}
}

dependencies {
	includeDep(libs.jackson.core)
	includeDep(libs.jackson.dataformat.yaml)
	includeDep(libs.jackson.databind)
	includeDep(libs.jackson.annotations)
	includeDep(libs.snakeyaml)
	common(project(":common"))
}

tasks.assemble {
	dependsOn(tasks.matching { it.name == "remapJar" })
}
