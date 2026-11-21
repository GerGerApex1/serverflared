plugins {
	id("mod-platform")
	alias(libs.plugins.gradleup.shadow)
}

stonecutter {
	val (version, loader) = current.project.split('-', limit = 2)
	properties.tags(version, loader)
}

unimined {
	minecraft {
		version = prop("loader.minecraft")
		loader("fabric", prop("fabric.loader"))
		mappings {
			intermediary()
			builtin("mojmap") {
				named("named")
				mapNamespace("mojmap" to "named")
			}
		}
		main.accessWideners.from(rootProject.file("modloaders/src/main/resources/serverflared.accesswidener"))
	}
}

platform {
	loader.set(Loader.FABRIC)
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
	includeDep(libs.jackson.core)
	includeDep(libs.jackson.dataformat.yaml)
	includeDep(libs.jackson.databind)
	includeDep(libs.jackson.annotations)
	includeDep(libs.snakeyaml)
	common(project(":common"))
	"modImplementation"("net.fabricmc.fabric-api:fabric-api:${prop("deps.fabric-api")}")
}

tasks.assemble {
	dependsOn(tasks.matching { it.name == "remapJar" })
}
