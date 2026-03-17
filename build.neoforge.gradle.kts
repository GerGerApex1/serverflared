import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.kotlin.dsl.named
import xyz.wagyourtail.jvmdg.gradle.task.DowngradeJar
import xyz.wagyourtail.jvmdg.gradle.task.ShadeJar
import xyz.wagyourtail.unimined.internal.minecraft.task.RemapJarTaskImpl

plugins {
	id("mod-platform")
	alias(libs.plugins.gradleup.shadow)
	alias(libs.plugins.jvmdowngrader)
}
val javaCompileVersion: JavaVersion = when {
	stonecutter.eval(stonecutter.current.version, ">=1.20.6") -> JavaVersion.VERSION_21
	stonecutter.eval(stonecutter.current.version, ">=1.18") -> JavaVersion.VERSION_17
	stonecutter.eval(stonecutter.current.version, ">=1.17") -> JavaVersion.VERSION_16
	else -> JavaVersion.VERSION_1_8
}
stonecutter {
	// These would be "1.21.11", "neoforge" for example
	val (version, loader) = current.project.split('-', limit = 2)
	properties.tags(version, loader)
}

platform {
	loader = "neoforge"
	dependencies {
		required("minecraft") {
			forgeVersionRange =  "[${prop("deps.minecraft.min")}, ${prop("deps.minecraft.max")}]"
			environment = "server"
		}
		required("neoforge") {
			forgeVersionRange = "[1.0,)"
		}

	}
}

unimined.minecraft {
	version = prop("loader.minecraft")

	mappings {
		mojmap()
	}

	neoForge {
		loader(prop("loader.neoforge"))
	}

	minecraftRemapper.config {
		ignoreConflicts(true)
	}
	runs {
		config("client") {

		}
		config("server") {
			workingDir("run/")
			//name = "NeoForge Server (${prop("deps.minecraft")})"
		}
	}
}
dependencies {
	implementation(libs.jackson.dataformat.yaml)
	implementation(libs.jackson.core)
	implementation(libs.jackson.databind)
	implementation(libs.jackson.annotations)
	implementation(libs.snakeyaml)
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
			dependsOn("shadowJar")
			inputFile.set(tasks.named<ShadowJar>("shadowJar").flatMap { it.archiveFile })
			archiveClassifier.set("")
		}

		tasks.named<DowngradeJar>("downgradeJar") {
			// keep as you had it
			inputFile.set(tasks.named<RemapJarTaskImpl>("remapJar").flatMap { it.archiveFile })
			archiveClassifier = "downgradedJar"
			downgradeTo = javaCompileVersion
		}

		tasks.named<ShadeJar>("shadeDowngradedApi") {
			//dependsOn("shadowJar")
			//jvmdg.multiReleaseOriginal = false
			//inputFile.set(tasks.named<ShadowJar>("shadowJar").flatMap { it.archiveFile })
			archiveClassifier = "shadeDowngradedJar"
		}

		tasks.named<ShadowJar>("shadowJar") {
			//dependsOn("downgradeJar")
			//from(zipTree(tasks.named<ShadeJar>("shadeDowngradedApi").flatMap { it.archiveFile }))
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
	//from(tasks.named<Jar>("remapJarSearge").flatMap { it.archiveFile })

	configurations = listOf(shadowImpl)

	archiveClassifier.set("shadow")

	relocate("com.fasterxml.jackson", "me.gergerapex1.shaded.fasterxml.jackson")
	relocate("org.yaml.snakeyaml", "me.gergerapex1.shaded.org.yaml.snakeyaml")
}
tasks.assemble {
	dependsOn("remapJar")
}

repositories {
	maven {
		name = "NeoForged"
		url = uri("https://maven.neoforged.net/releases")
	}
}
