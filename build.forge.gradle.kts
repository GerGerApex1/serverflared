import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import xyz.wagyourtail.jvmdg.gradle.task.DowngradeJar
import xyz.wagyourtail.jvmdg.gradle.task.ShadeJar

plugins {
	id("mod-platform")
	alias(libs.plugins.gradleup.shadow)
	alias(libs.plugins.jvmdowngrader)
}

val minorVersion = prop("deps.minecraft").split(".")[1].toInt()
val javaCompileVersion: JavaVersion = when {
	stonecutter.eval(stonecutter.current.version, ">=1.20.6") -> JavaVersion.VERSION_21
	stonecutter.eval(stonecutter.current.version, ">=1.18") -> JavaVersion.VERSION_17
	stonecutter.eval(stonecutter.current.version, ">=1.17") -> JavaVersion.VERSION_16
	else -> JavaVersion.VERSION_1_8
}
platform {
	loader = "forge"
	dependencies {
		required("minecraft") {
			forgeVersionRange = "[${prop("deps.minecraft")}]"
			environment = "server"
		}
		required("forge") {
			forgeVersionRange = "(1,)"
		}
	}
}
unimined.minecraft {
	version = prop("deps.minecraft")

	mappings {
		if (14 <= minorVersion) {
			mojmap()
		} else {
			searge()
			if (!(providers.gradleProperty("deps.mcp.channel").isPresent) || property("deps.mcp.channel") == "snapshot") {
				mcp("snapshot", prop("deps.mcp"))
			} else if (property("deps.mcp.channel") == "stable") {
				mcp("stable", prop("deps.mcp"))
			} else {
				error("Unknown MCP channel ${property("deps.mcp.channel")}")
			}
		}
	}

	//side("server")
	forge {
		loader("${property("deps.forge")}")
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
}
dependencies {
	// include(libs.jackson.dataformat.yaml)
	// include(libs.jackson.databind)
	// include(libs.jackson.annotations)
	// include(libs.snakeyaml)
	// include(libs.jackson.core)
	implementation(libs.jackson.core)
	implementation(libs.jackson.dataformat.yaml)
	implementation(libs.jackson.databind)
	implementation(libs.jackson.annotations)
	implementation(libs.snakeyaml)

	// Source: https://mvnrepository.com/artifact/xyz.wagyourtail.jvmdowngrader/jvmdowngrader-java-api
	//include("xyz.wagyourtail.jvmdowngrader:jvmdowngrader-java-api:1.3.6:downgraded-8")
	// forgeRuntimeLibrary(libs.jackson.dataformat.yaml)
	// forgeRuntimeLibrary(libs.jackson.databind)
	// forgeRuntimeLibrary(libs.jackson.annotations)
	// forgeRuntimeLibrary(libs.snakeyaml)
	// forgeRuntimeLibrary(libs.jackson.core)
}
val shadowImpl by configurations.creating {
	isCanBeResolved = true
	isCanBeConsumed = false
	extendsFrom(configurations.implementation.get())
}
/*
tasks.named<Jar>("jar") {
	archiveClassifier.set("unshaded")
}
tasks.named("remapJar") {
	dependsOn("shadowJar")
}
tasks.named<DowngradeJar>("downgradeJar") {
	//dependsOn(tasks.named<RemapJarTask>("remapJar").get().archiveFile)
	inputFile.set(tasks.named<Jar>("jar").get().archiveFile)
	archiveClassifier = "downgradedJar"
	downgradeTo = JavaVersion.VERSION_1_8
}
tasks.named<ShadeJar>(	"shadeDowngradedApi") {
	inputFile.set(tasks.named<DowngradeJar>("downgradeJar").get().archiveFile)
	archiveClassifier = "shadeDowngradedJar"
	downgradeTo = JavaVersion.VERSION_1_8
}
tasks.shadowJar {
	dependsOn("shadeDowngradedApi")
	configurations = listOf(shadowImpl)
	System.out.println("")
	archiveClassifier.set("shadow")
	relocate("com.fasterxml.jackson", "me.gergerapex1.shaded.fasterxml.jackson")
}
 */
/*
tasks.register<DowngradeJar>("customDowngrade") {
	inputFile.set(tasks.named<Jar>("jar").get().archiveFile)
	downgradeTo.set(javaCompileVersion)
	archiveClassifier.set("")
}

tasks.register<ShadeJar>("customShadeDowngrade") {
	inputFile.set(tasks.named<DowngradeJar>("customDowngrade").get().archiveFile)
	downgradeTo.set(javaCompileVersion)
	archiveClassifier.set("test")
}

 */
/*

 */
/*
java {
	java {
		toolchain {
			languageVersion.set(
				when {
					stonecutter.eval(stonecutter.current.version, ">=1.20.6") -> JavaLanguageVersion.of(21)
					stonecutter.eval(stonecutter.current.version, ">=1.18") -> JavaLanguageVersion.of(17)
					stonecutter.eval(stonecutter.current.version, ">=1.17") -> JavaLanguageVersion.of(16)
					else -> JavaLanguageVersion.of(8)
				}
			)
		}
	}
}
 */
