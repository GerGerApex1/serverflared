import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.kotlin.dsl.named
import xyz.wagyourtail.jvmdg.gradle.task.DowngradeJar
import xyz.wagyourtail.jvmdg.gradle.task.ShadeJar
import xyz.wagyourtail.unimined.internal.minecraft.task.RemapJarTaskImpl

plugins {
	id("mod-platform")
	alias(libs.plugins.gradleup.shadow)
	alias(libs.plugins.jvmdowngrader)
	alias(libs.plugins.unimined)

}
val javaCompileVersion: JavaVersion = when {
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
	loader = "fabric"
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
unimined.minecraft {
	version = prop("loader.minecraft")

	mappings {
		mojmap()
	}

	fabric {
		loader("${property("fabric.loader")}")
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

val fabricLifecycleModule = fabricApi.fabricModule("fabric-lifecycle-events-v1", prop("deps.fabric-api"))
val fabricBaseModule = fabricApi.fabricModule("fabric-api-base", prop("deps.fabric-api"))
tasks.assemble {
	dependsOn("remapJar")
}
dependencies {
	implementation(libs.jackson.core)
	implementation(libs.jackson.dataformat.yaml)
	implementation(libs.jackson.databind)
	implementation(libs.jackson.annotations)
	implementation(libs.snakeyaml)
	"modImplementation"(fabricLifecycleModule)
	"modImplementation"(fabricBaseModule)
}
