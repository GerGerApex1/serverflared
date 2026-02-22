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
platform {
	loader = "neoforge"
	dependencies {
		required("minecraft") {
			forgeVersionRange = "[${prop("deps.minecraft")}, ${prop("deps.minecraft.maxVersion")})"
			environment = "server"
		}
		required("neoforge") {
			forgeVersionRange = "[1,)"
		}

	}
}

unimined.minecraft {
	version = prop("deps.minecraft")

	mappings {
		mojmap()
	}

	neoForge {
		loader(prop("deps.neoforge"))
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
val needDowngrade = JavaVersion.current() < javaCompileVersion
afterEvaluate {
	if (needDowngrade) {
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
		tasks.named<ShadeJar>("shadeDowngradedApi") {
			dependsOn("downgradeJar")
			inputFile.set(tasks.named<DowngradeJar>("downgradeJar").get().archiveFile)
			archiveClassifier = "shadeDowngradedJar"
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
