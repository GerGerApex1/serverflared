plugins {
	id("mod-platform")
	id("gg.essential.loom")
}

platform {
	loader = "fabric"
	dependencies {
		required("minecraft") {
			versionRange = ">=${prop("deps.minecraft")} <${prop("deps.minecraft.maxVersion")}"
		}
		/*
		required("fabric-api") {
			slug("fabric-api")
			versionRange = ">=${prop("deps.fabric-api")} <${prop("deps.fabric-api.maxVersion")} "
		}
		*/
		required("fabricloader") {
			versionRange = ">=0.12.0"
		}
	}
}

loom {
	// accessWidenerPath = rootProject.file("src/main/resources/aw/${stonecutter.current.version}.accesswidener")
	runs.named("client") {
		client()
		ideConfigGenerated(false)
		runDir = "run/"
		environment = "client"
		programArgs("--username=Dev")
		configName = "Fabric Client"
	}
	runs.named("server") {
		server()
		ideConfigGenerated(true)
		runDir = "run/"
		environment = "server"
		configName = "Fabric Server (${prop("deps.minecraft")})"
	}
}
dependencies {
	minecraft("com.mojang:minecraft:${prop("deps.minecraft")}")
	mappings(
		loom.layered {
			officialMojangMappings()
			if (hasProperty("deps.parchment")) parchment("org.parchmentmc.data:parchment-${prop("deps.parchment")}@zip")
		})
	modImplementation(libs.fabric.loader)
	modImplementation("net.fabricmc.fabric-api:fabric-api:${prop("deps.fabric-api")}")
	include(libs.jackson.dataformat.yaml)
	include(libs.jackson.databind)
	include(libs.jackson.annotations)
	include(libs.snakeyaml)
	implementation(libs.jackson.dataformat.yaml)
	implementation(libs.jackson.databind)
	implementation(libs.jackson.annotations)
	implementation(libs.snakeyaml)
	//modLocalRuntime("com.terraformersmc:modmenu:${prop("deps.modmenu")}")
}
