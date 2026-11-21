@file:Suppress("unused", "DuplicatedCode")

import ProjectConfiguration.configureDependancies
import ProjectConfiguration.configureIdea
import ProjectConfiguration.configureJarTask
import ProjectConfiguration.configureJava
import ProjectConfiguration.configureProcessResources
import ProjectConfiguration.registerBuildAndCollectTask
import Publishing.configurePublishing
import ShadowTask.configureShadow
import ShadowTask.createShadowImplConfiguration
import Utils.resolveJavaVersion
import dev.kikugie.stonecutter.build.StonecutterBuildExtension
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.extensions.stdlib.toDefaultLowerCase
import org.gradle.kotlin.dsl.*
import xyz.wagyourtail.unimined.UniminedExtension
import javax.inject.Inject

fun Project.prop(name: String): String = (findProperty(name) ?: "") as String

fun Project.env(variable: String): String? = providers.environmentVariable(variable).orNull

fun Project.envTrue(variable: String): Boolean = env(variable)?.toDefaultLowerCase() == "true"

val Project.unimined: UniminedExtension
	get() = extensions.getByType<UniminedExtension>()

fun Project.unimined(action: UniminedExtension.() -> Unit) {
	extensions.configure<UniminedExtension>(action)
}

abstract class ModPlatformPlugin @Inject constructor() : Plugin<Project> {
	override fun apply(project: Project) = with(project) {
		val stonecutter = extensions.getByType<StonecutterBuildExtension>()

		val inferredLoader = project.buildFile.name.substringAfter('.').replace(".gradle.kts", "")
		val jarTaskName = when (inferredLoader) {
			"spigot" -> "shadeDowngradedApi"
			"deobfuscated" -> "shadowJar"
			else -> "remapJar"
		}
		val isWindows = System.getProperty("os.name").lowercase().contains("win")
		val sourcesJarTaskName = when {
			inferredLoader == "spigot" || inferredLoader == "deobfuscated" -> "sourcesJar"
			inferredLoader == "forge" && isWindows -> "sourcesJar"
			else -> "remapSourcesJar"
		}
		if (inferredLoader == "forge" && isWindows) {
			tasks.matching { it.name == "remapSourcesJar" }.configureEach {
				enabled = false
			}
		}
		val extension = extensions.create("platform", ModPlatformExtension::class.java).apply {
			loader.convention(Loader.valueOf(inferredLoader.uppercase()))
			jarTask.convention(jarTaskName)
			sourcesJarTask.convention(sourcesJarTaskName)
		}


		listOf(
			"java",
			"com.gradleup.shadow",
		).forEach { apply(plugin = it) }
		extensions.configure<org.gradle.api.plugins.JavaPluginExtension>("java") {
			withSourcesJar()
			withJavadocJar()
		}
		if (extension.loader.get() == Loader.SPIGOT) {
			apply(plugin = "xyz.wagyourtail.jvmdowngrader")
		} else {
			apply(plugin = "xyz.wagyourtail.unimined")
			apply(plugin = "xyz.wagyourtail.unimined.adapter-stonecutter")
			if (extension.loader.get() != Loader.DEOBFUSCATED) {
				apply(plugin = "xyz.wagyourtail.unimined.jvmdg")
			}
		}
		configureDependancies(stonecutter, extension)

		afterEvaluate {
			configureProject(extension)
		}
	}

	private fun Project.configureProject(extension: ModPlatformExtension) {
		val loader = extension.loader.get()
		val modId = prop("mod.id")
		val modVersion = prop("mod.version")
		val channelTag = prop("mod.channel_tag")
		val mcVersion = prop("loader.minecraft")

		val stonecutter = extensions.getByType<StonecutterBuildExtension>()

		listOf(
			"java",
			"me.modmuss50.mod-publish-plugin",
			"idea",
		).forEach { apply(plugin = it) }

		version = "$modVersion$channelTag+$mcVersion-${loader.loader}"
		extension.requiredJava.set(
			resolveJavaVersion()
		)

		if (loader === Loader.FABRIC) {
			extension.dependencies {
				required("java") {
					versionRange = ">=${extension.requiredJava.get().majorVersion}"
				}
			}
		}
		configureJarTask(modId)
		configureShadow()
		configureIdea()
		configureProcessResources(
			loader,
			modId,
			"$modVersion$channelTag",
			mcVersion,
			extension,
			extension.requiredJava.get()
		)
		configureJava(extension.requiredJava.get())
		registerBuildAndCollectTask(extension, "$modVersion$channelTag")
		configurePublishing(
			extension,
			loader.loader,
			stonecutter,
			"$modVersion$channelTag",
			channelTag,
			version.toString()
		)
	}
}
