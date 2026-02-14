plugins {
	id("mod-platform")
}

platform {
	loader = "fabric"
	dependencies {
		required("minecraft") {
			versionRange = ">=${prop("deps.minecraft")} <${prop("deps.minecraft.maxVersion")}"
			environment = "server"
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

unimined.minecraft {
	version = prop("deps.minecraft")

	mappings {
		mojmap()
	}

	fabric {
		loader("0.12.0")
	}
	side("server")
	minecraftRemapper.config {
		ignoreConflicts(true)
	}
	runs {
		config("client") {

		}
		config("server") {
			//workingDir("run/")
			//name = "Fabric Server (${prop("deps.minecraft")})"
		}
	}
}
dependencies {
	//modImplementation("net.fabricmc.fabric-api:fabric-api:${prop("deps.fabric-api")}")
	//include(libs.jackson.dataformat.yaml)
	//include(libs.jackson.databind)
	//include(libs.jackson.annotations)
	//include(libs.snakeyaml)
	//include(libs.jackson.core)
	//implementation(libs.jackson.core)
	//implementation(libs.jackson.dataformat.yaml)
	//implementation(libs.jackson.databind)
	//implementation(libs.jackson.annotations)
	//implementation(libs.snakeyaml)
	//modLocalRuntime("com.terraformersmc:modmenu:${prop("deps.modmenu")}")
}
tasks.processResources  {
	println("Processing resources for Fabric...")
}
