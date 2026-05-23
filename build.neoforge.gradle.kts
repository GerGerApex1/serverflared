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
	stonecutter.eval(stonecutter.current.version, ">=26.1") -> JavaVersion.VERSION_25
	stonecutter.eval(stonecutter.current.version, ">=1.20.5") -> JavaVersion.VERSION_21
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
tasks.named<ShadowJar>("shadowJar") {
	//from(tasks.named<Jar>("remapJarSearge").flatMap { it.archiveFile })

	configurations = listOf(shadowImpl)

	archiveClassifier.set("")

	//relocate("com.fasterxml.jackson", "me.gergerapex1.shaded.fasterxml.jackson")
	//relocate("org.yaml.snakeyaml", "me.gergerapex1.shaded.org.yaml.snakeyaml")
}
tasks.assemble {
	dependsOn("shadowJar")
}

repositories {
	maven {
		name = "NeoForged"
		url = uri("https://maven.neoforged.net/releases")
	}
}
