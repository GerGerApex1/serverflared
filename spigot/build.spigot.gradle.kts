import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.named
import org.jetbrains.kotlin.gradle.utils.extendsFrom

plugins {
	id("mod-platform")
	alias(libs.plugins.gradleup.shadow)
	alias(libs.plugins.jvmdowngrader)
}
stonecutter {

}
platform {
	loader.set(Loader.SPIGOT)
}
repositories {
	mavenCentral()

	maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")

	maven("https://repo.codemc.org/repository/maven-public/")
}
java {
	toolchain {
		languageVersion.set(JavaLanguageVersion.of(8))
	}
}
val shade by configurations.creating

dependencies {
	// Compile against the oldest API you support.
	compileOnly("org.spigotmc:spigot-api:${prop("deps.spigot.api")}")
	common(project(":common"))
	includeDep(libs.jackson.core)
	includeDep(libs.jackson.dataformat.yaml)
	includeDep(libs.jackson.databind)
	includeDep(libs.jackson.annotations)
	includeDep(libs.snakeyaml)
}

tasks.build {
	dependsOn(tasks.shadeDowngradedApi)
}
