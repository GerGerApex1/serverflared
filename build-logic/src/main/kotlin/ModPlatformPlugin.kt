@file:Suppress("unused", "DuplicatedCode")

import ProjectConfiguration.configureDependancies
import ProjectConfiguration.configureIdea
import ProjectConfiguration.configureJarTask
import ProjectConfiguration.configureJava
import ProjectConfiguration.configureProcessResources
import ProjectConfiguration.registerBuildAndCollectTask
import Publishing.configurePublishing
import ShadowTask.createShadowImplConfiguration
import Utils.resolveJavaVersion
import dev.kikugie.stonecutter.build.StonecutterBuildExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.extensions.stdlib.toDefaultLowerCase
import org.gradle.kotlin.dsl.*
import javax.inject.Inject

fun Project.prop(name: String): String = (findProperty(name) ?: "") as String

fun Project.env(variable: String): String? = providers.environmentVariable(variable).orNull

fun Project.envTrue(variable: String): Boolean = env(variable)?.toDefaultLowerCase() == "true"

abstract class ModPlatformPlugin @Inject constructor() : Plugin<Project> {
	override fun apply(project: Project) = with(project) {
		val stonecutter = extensions.getByType<StonecutterBuildExtension>()

		val inferredLoader = project.buildFile.name.substringAfter('.').replace(".gradle.kts", "")
		val extension = extensions.create("platform", ModPlatformExtension::class.java).apply {
			loader.convention(inferredLoader)
			jarTask.convention("shadowJar")
			sourcesJarTask.convention("sourcesJar")
		}


		listOf(
			"org.jetbrains.kotlin.jvm",
			"com.google.devtools.ksp",
			"xyz.wagyourtail.jvmdowngrader",
			"com.gradleup.shadow",
			"gg.essential.loom"
			//	"xyz.wagyourtail.unimined"
		).forEach { apply(plugin = it) }
		configureDependancies(stonecutter, extension)

		afterEvaluate {
			configureProject(extension)
		}
	}

	private fun Project.configureProject(extension: ModPlatformExtension) {
		val loader = extension.loader.get()
		val isFabric = loader == "fabric"
		val isNeoForge = loader == "neoforge"
		val isForge = loader == "forge"
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

		version = "$modVersion$channelTag+$mcVersion-$loader"

		extension.requiredJava.set(
			resolveJavaVersion()
		)

		if (isFabric) {
			extension.dependencies {
				required("java") {
					versionRange = ">=${extension.requiredJava.get().majorVersion}"
				}
			}
		}

		// configureFletchingTable()
		configureJarTask(modId)
		createShadowImplConfiguration()
		configureIdea()
		configureProcessResources(
			isFabric,
			isNeoForge,
			isForge,
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
			loader,
			stonecutter,
			"$modVersion$channelTag",
			channelTag,
			version.toString()
		)
	}
}
