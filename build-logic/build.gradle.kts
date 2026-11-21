plugins {
	`kotlin-dsl`
}

gradlePlugin {
	plugins {
		register("modPlatform") {
			id = "mod-platform"
			implementationClass = "ModPlatformPlugin"
		}
	}
}

repositories {
	mavenLocal()
	mavenCentral()
	gradlePluginPortal()
	maven("https://maven.fabricmc.net/") { name = "Fabric" }
	maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
	maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
	maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
	maven("https://jitpack.io") { name = "Jitpack" }
	maven("https://maven.wagyourtail.xyz/releases") { name = "WagYourTail Releases" }
	maven("https://maven.wagyourtail.xyz/snapshots") { name = "WagYourTail Snapshots" }

}

dependencies {
	implementation(libs.kikugie.postprocess)
	implementation(libs.kikugie.stonecutter)
	implementation(libs.mod.publish.plugin)
	implementation(libs.foojay.resolver)
	implementation("com.gradleup.shadow:shadow-gradle-plugin:9.3.0")
	implementation(libs.jvmdowngrader)
	implementation(libs.unimined)
	runtimeOnly(libs.unimined.jvmdg)
	runtimeOnly(libs.unimined.stonecutter)
	runtimeOnly("org.tukaani:xz:1.10")
	//implementation(libs.fletching.table)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
	compilerOptions {
		freeCompilerArgs.add("-Xskip-metadata-version-check")
	}
}
