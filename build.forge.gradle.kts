import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import dev.kikugie.stonecutter.StonecutterExperimentalAPI
import xyz.wagyourtail.jvmdg.gradle.task.DowngradeJar
import xyz.wagyourtail.jvmdg.gradle.task.ShadeJar
import xyz.wagyourtail.unimined.internal.minecraft.task.RemapJarTaskImpl

plugins {
	id("mod-platform")
	alias(libs.plugins.gradleup.shadow)
	alias(libs.plugins.jvmdowngrader)
}
stonecutter {
	// These would be "1.21.11", "neoforge" for example
	val (version, loader) = current.project.split('-', limit = 2)
	properties.tags(version, loader)
}
val minorVersion = prop("loader.minecraft").split(".")[1].toInt()
val javaCompileVersion: JavaVersion = when {
	stonecutter.eval(stonecutter.current.version, ">=1.20.6") -> JavaVersion.VERSION_21
	stonecutter.eval(stonecutter.current.version, ">=1.18") -> JavaVersion.VERSION_17
	stonecutter.eval(stonecutter.current.version, ">=1.17") -> JavaVersion.VERSION_16
	else -> JavaVersion.VERSION_1_8
}
jvmdg.shadePath = { "me.gergerapex1.shaded.jvmdg.api" }
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
unimined.minecraft {
	version = prop("loader.minecraft")

	mappings {
		if (14 <= minorVersion) {
			mojmap()
		} else {
			searge()
			if (!(providers.gradleProperty("mappings.mcp.channel").isPresent) || property("mappings.mcp.channel") == "snapshot") {
				mcp("snapshot", prop("mappings.mcp"))
			} else if (property("mappings.mcp.channel") == "stable") {
				mcp("stable", prop("mappings.mcp"))
			} else {
				error("Unknown MCP channel ${property("mappings.mcp.channel")}")
			}
		}
	}

	//side("server")
	minecraftForge {
		loader("${property("loader.forge")}")
	}

	minecraftRemapper.config {
		ignoreConflicts(true)
	}
	runs {
		config("server") {
			workingDir("run/")
			//name = "Forge Server (${prop("deps.minecraft")})"
		}
	}
	defaultRemapJar = true
}
dependencies {
	implementation(libs.jackson.core)
	implementation(libs.jackson.dataformat.yaml)
	implementation(libs.jackson.databind)
	implementation(libs.jackson.annotations)
	implementation(libs.snakeyaml)

	// Source: https://mvnrepository.com/artifact/xyz.wagyourtail.jvmdowngrader/jvmdowngrader-java-api
	//implementation("xyz.wagyourtail.jvmdowngrader:jvmdowngrader-java-api:1.3.6:downgraded-8")
}
val shadowImpl by configurations.creating {
	isCanBeResolved = true
	isCanBeConsumed = false
	extendsFrom(configurations.implementation.get())
}
val needDowngrade = JavaVersion.current() > javaCompileVersion
afterEvaluate {
	if (needDowngrade) {
		tasks.named<RemapJarTaskImpl>("remapJar") {
			dependsOn("shadeDowngradedApi")
			inputFile.set(tasks.named<ShadeJar>("shadeDowngradedApi").flatMap { it.archiveFile })
			archiveClassifier.set("")
		}
		tasks.named<DowngradeJar>("downgradeJar") {
			inputFile.set(tasks.named<ShadowJar>("shadowJar").get().archiveFile)
			archiveClassifier = "downgradedJar"
			downgradeTo = javaCompileVersion
		}
		tasks.named<ShadeJar>("shadeDowngradedApi") {
			dependsOn("downgradeJar")
			inputFile.set(tasks.named<DowngradeJar>("downgradeJar").get().archiveFile)
			archiveClassifier = "shadeDowngradedJar"
		}
	} else {
		tasks.named<RemapJarTaskImpl>("remapJar") {
			dependsOn("shadowJar")
			inputFile.set(tasks.named<ShadowJar>("shadowJar").flatMap { it.archiveFile })
			archiveClassifier.set("")
		}
	}
}
tasks.named<ShadowJar>("shadowJar") {
	configurations = listOf(shadowImpl)

	archiveClassifier.set("shadow")

	relocate("com.fasterxml.jackson", "me.gergerapex1.shaded.fasterxml.jackson")
	relocate("org.yaml.snakeyaml", "me.gergerapex1.shaded.org.yaml.snakeyaml")
}
tasks.assemble {
	dependsOn("remapJar")
}
