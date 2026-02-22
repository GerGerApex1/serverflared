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
	stonecutter.eval(stonecutter.current.version, ">=1.20.5") -> JavaVersion.VERSION_21
	stonecutter.eval(stonecutter.current.version, ">=1.18") -> JavaVersion.VERSION_17
	stonecutter.eval(stonecutter.current.version, ">=1.17") -> JavaVersion.VERSION_16
	else -> JavaVersion.VERSION_1_8
}
platform {
	loader = "fabric"
	dependencies {
		required("minecraft") {
			versionRange = ">=${prop("deps.minecraft")} <${prop("deps.minecraft.maxVersion")}"
			environment = "server"
		}
		required("fabric-api") {
			slug("fabric-api")
			versionRange = ">=${prop("deps.fabric-api")} <${prop("deps.fabric-api.maxVersion")} "
		}
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
val shadowImpl by configurations.creating {
	isCanBeResolved = true
	isCanBeConsumed = false
	extendsFrom(configurations.implementation.get())
}
val needDowngrade = JavaVersion.current() < javaCompileVersion
afterEvaluate {
	if(needDowngrade) {
		tasks.assemble {
			dependsOn("remapJar")
		}
		tasks.named<RemapJarTaskImpl>("remapJar") {
			dependsOn("shadeDowngradedApi")
			inputFile.set(tasks.named<ShadeJar>("shadeDowngradedApi").flatMap { it.archiveFile })
			archiveClassifier.set("remapped")
		}
		tasks.named<DowngradeJar>("downgradeJar") {
			inputFile.set(tasks.named<ShadowJar>("shadowJar").get().archiveFile)
			archiveClassifier = "downgradedJar"
			downgradeTo = javaCompileVersion
		}
		tasks.named<ShadeJar>(	"shadeDowngradedApi") {
			dependsOn("downgradeJar")
			inputFile.set(tasks.named<DowngradeJar>("downgradeJar").get().archiveFile)
			archiveClassifier = "shadeDowngradedJar"
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

dependencies {
	implementation(libs.jackson.core)
	implementation(libs.jackson.dataformat.yaml)
	implementation(libs.jackson.databind)
	implementation(libs.jackson.annotations)
	implementation(libs.snakeyaml)
	"modImplementation"(fabricLifecycleModule)
	"modImplementation"(fabricBaseModule)
}
