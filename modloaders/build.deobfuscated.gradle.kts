plugins {
	id("mod-platform")
	alias(libs.plugins.gradleup.shadow)
}
val (version, mcLoader) = stonecutter.current.project.split('-', limit = 2)

stonecutter {
	properties.tags(version, mcLoader)
}

unimined {
	minecraft {
		this.version = prop("loader.minecraft")
		when (mcLoader) {
			"fabric" -> loader("fabric", prop("fabric.loader"))
			"neoforge" -> loader("neoforge", prop("loader.neoforge"))
			"forge" -> loader("forge", prop("loader.forge"))
		}
		main.accessWideners.from(rootProject.file("modloaders/src/main/resources/serverflared.official.accesswidener"))
	}
}

java.toolchain.languageVersion = JavaLanguageVersion.of(25)
platform {
	loader.set(Loader.valueOf(mcLoader.uppercase()))
	dependencies {
		required("minecraft") {
			versionRange = ">=${prop("deps.minecraft.min")} <${prop("deps.minecraft.max")}"
			environment = "server"
		}
		if (mcLoader == "fabric") {
			required("fabric-api") {
				slug("fabric-api")
				versionRange = ">=${prop("deps.fabric-api")}"
			}
			required("fabricloader") {
				versionRange = ">=${property("fabric.loader")}"
			}
		}
		if (mcLoader == "forge") {
			required("forge") {
				forgeVersionRange = "(1,)"
			}
		}
		if (mcLoader == "neoforge") {
			required("neoforge") {
				forgeVersionRange = "[1.0,)"
			}
		}
	}
}

tasks.assemble {
	dependsOn("shadowJar")
}

dependencies {
	includeDep(libs.jackson.core)
	includeDep(libs.jackson.dataformat.yaml)
	includeDep(libs.jackson.databind)
	includeDep(libs.jackson.annotations)
	includeDep(libs.snakeyaml)

	if (mcLoader == "fabric") {
		"modImplementation"(
			unimined.loaderApiModule(
				"fabric",
				"fabric-lifecycle-events-v1",
				prop("deps.fabric-api")
			)
		)
		"modImplementation"(
			unimined.loaderApiModule(
				"fabric",
				"fabric-api-base",
				prop("deps.fabric-api")
			)
		)
	}
	common(project(":common"))
}
