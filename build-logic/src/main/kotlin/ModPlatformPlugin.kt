@file:Suppress("unused", "DuplicatedCode")

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import dev.kikugie.stonecutter.build.StonecutterBuildExtension
import me.modmuss50.mpp.ModPublishExtension
import me.modmuss50.mpp.ReleaseType
import net.fabricmc.loom.task.RemapJarTask
import org.gradle.api.JavaVersion
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Copy
import org.gradle.internal.extensions.stdlib.toDefaultLowerCase
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.*
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.plugins.ide.idea.model.IdeaModel
import java.util.*
import javax.inject.Inject
import org.gradle.jvm.toolchain.JavaLanguageVersion
import xyz.wagyourtail.jvmdg.gradle.task.DowngradeJar
import xyz.wagyourtail.jvmdg.gradle.task.ShadeJar

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
		val includeDependancy = configurations.maybeCreate("includeDep").apply {
			isCanBeConsumed = false
			isCanBeDeclared = true
		}
		val commonDependancy = configurations.maybeCreate("common").apply {
			isCanBeConsumed = false
			isCanBeDeclared = true
		}
		listOf(
			"org.jetbrains.kotlin.jvm",
			"com.google.devtools.ksp",
			"xyz.wagyourtail.jvmdowngrader",
			"com.gradleup.shadow",
			"gg.essential.loom"
			//	"xyz.wagyourtail.unimined"
		).forEach { apply(plugin = it) }
		configureDependancies(stonecutter, extension, includeDependancy, commonDependancy)

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
		configureJava(stonecutter, extension.requiredJava.get())
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

	private fun Project.configureJarTask(modId: String) {

		tasks.withType<Jar>().configureEach {
			archiveBaseName.set(modId)
		}
	}

	private fun Project.configureProcessResources(
		isFabric: Boolean,
		isNeoForge: Boolean,
		isForge: Boolean,
		modId: String,
		modVersion: String,
		mcVersion: String,
		extension: ModPlatformExtension,
		requiredJava: JavaVersion
	) {
		tasks.named<ProcessResources>("processResources") {
			dependsOn(tasks.named("stonecutterGenerate"))
			dependsOn("kspKotlin")

			filesMatching("*.mixins.json") { expand("java" to "JAVA_${requiredJava.majorVersion}") }

			var contributors = prop("mod.contributors")
			var authors = prop("mod.authors")
			var issuesUrl = prop("mod.issues_url")
			if (issuesUrl == "") issuesUrl = prop("mod.sources_url") + "/issues"

			if (isFabric) {
				contributors = contributors.replace(", ", "\", \"")
				authors = authors.replace(", ", "\", \"")
			}

			val dependencies = buildDependenciesBlock(isFabric, modId, extension.dependencies)

			val props = mapOf(
				"version" to modVersion,
				"minecraft" to mcVersion,
				"id" to modId,
				"name" to prop("mod.name"),
				"group" to prop("mod.group"),
				"authors" to authors,
				"contributors" to contributors,
				"license" to prop("mod.license"),
				"description" to prop("mod.description"),
				"issues_url" to issuesUrl,
				"homepage_url" to prop("mod.homepage_url"),
				"sources_url" to prop("mod.sources_url"),
				"discord_url" to prop("mod.discord_url"),
				"dependencies" to dependencies
			)

			when {
				isFabric -> {
					filesMatching("fabric.mod.json") {
						expand(props)
						filter { line ->
							line.replace(
								"\"dependencies\": {}",
								dependencies
							)
						}
					}
					exclude(
						"META-INF/mods.toml",
						"META-INF/neoforge.mods.toml",
						"aw/*.cfg",
						".cache",
						"pack.mcmeta"
					)
				}

				isNeoForge -> {
					filesMatching("META-INF/neoforge.mods.toml") { expand(props) }
					exclude(
						"META-INF/mods.toml",
						"fabric.mod.json",
						"aw/*.accesswidener",
						".cache",
						"pack.mcmeta"
					)
				}

				isForge -> {
					filesMatching("META-INF/mods.toml") { expand(props) }
					exclude(
						"META-INF/neoforge.mods.toml",
						"fabric.mod.json",
						"aw/*.accesswidener",
						".cache"
					)
				}
			}
		}
	}

	private fun buildDependenciesBlock(
		isFabric: Boolean, modId: String, deps: DependenciesConfig
	): String = if (isFabric) {
		buildString {
			fun joinGroup(
				name: String, container: NamedDomainObjectContainer<Dependency>
			): String? {
				if (container.isEmpty()) return null
				val entries = container.joinToString(",\n    ") {
					"\"${it.modid.get()}\": \"${it.versionRange.get()}\""
				}
				return "\n  \"$name\": {\n    $entries\n  }"
			}

			val groups = listOfNotNull(
				joinGroup("depends", deps.required),
				joinGroup("recommends", deps.optional),
				joinGroup("breaks", deps.incompatible)
			)

			append(groups.joinToString(","))
		}
	} else {
		buildString {
			fun appendBlock(container: NamedDomainObjectContainer<Dependency>, type: String) {
				container.forEach {
					appendLine(
						"""

						[[dependencies.$modId]]
						modId = "${it.modid.get()}"
						side = "${it.environment.get().uppercase(Locale.getDefault())}"
                        versionRange = "${it.forgeVersionRange.get()}"
						mandatory = ${if (type == "required") "true" else "false"}
                        type = "$type"
						""".replace("                  ", "").trimIndent()
					)
				}
			}

			appendBlock(deps.required, "required")
			appendBlock(deps.optional, "optional")
			appendBlock(deps.incompatible, "incompatible")
		}
	}

	private fun Project.configureJava(
		stonecutter: StonecutterBuildExtension,
		requiredJava: JavaVersion
	) {
		extensions.configure<JavaPluginExtension>("java") {
			withSourcesJar()
			withJavadocJar()
			sourceCompatibility = requiredJava
			targetCompatibility = requiredJava
		}
	}

	private fun Project.configureIdea() {
		extensions.configure<IdeaModel>("idea") {
			module {
				isDownloadJavadoc = true
				isDownloadSources = true
			}
		}
	}

	/*
	private fun Project.configureFletchingTable() {
		extensions.configure<FletchingTableExtension> {
			mixins.create("main").apply {
				mixin("default", "${prop("mod.id")}.mixins.json")
			}
		}
	}
	*/

	private fun Project.registerBuildAndCollectTask(
		extension: ModPlatformExtension,
		modVersion: String
	) {
		tasks.register<Copy>("buildAndCollect") {
			group = "build"
			from(
				tasks.named(extension.jarTask.get()),
				tasks.named(extension.sourcesJarTask.get()),
				tasks.named("javadocJar").get()
			)
			into(rootProject.layout.buildDirectory.file("libs/$modVersion"))
			dependsOn("build")
		}
	}

	private fun Project.configurePublishing(
		ext: ModPlatformExtension,
		loader: String,
		stonecutter: StonecutterBuildExtension,
		modVersion: String,
		channelTag: String,
		fullVersion: String,
	) {
		val additionalVersions =
			(findProperty("publish.additionalVersions") as String?)?.split(',')?.map(String::trim)
				?.filter(String::isNotEmpty).orEmpty()

		val releaseType = ReleaseType.of(
			channelTag.substringAfter('-').substringBefore('.').ifEmpty { "stable" })

		extensions.configure<ModPublishExtension>("publishMods") {
			if (prop("dont.publish") == "true") {
				println("Publishing is disabled for this project (dont.publish=true)")
				return@configure
			}
			val mrStaging = envTrue("TEST_PUBLISHING_WITH_MR_STAGING")

			val modrinthAccessToken = env("MODRINTH_API_TOKEN")
			val curseforgeAccessToken = env("CURSEFORGE_API_TOKEN")
			if (!envTrue("ENABLE_PUBLISHING")) {
				dryRun = true
			}

			// val jarTask = tasks.named(ext.jarTask.get()).map { it as Jar }
			// val srcJarTask = tasks.named(ext.sourcesJarTask.get()).map { it as Jar }
			val currentVersion = stonecutter.current.version
			val deps = ext.dependencies

			if (loader == "forge") {
				//TODO: Implement shadowJar to conversion
				val shadowJarTask = tasks.named("shadowJar").map { it as Jar }
				file.set(shadowJarTask.flatMap { it.archiveFile })
			} else {
				val jarTask = tasks.named(ext.jarTask.get()).map { it as Jar }
				file.set(jarTask.flatMap(Jar::getArchiveFile))
			}
			val srcJarTask = tasks.named(ext.sourcesJarTask.get()).map { it as Jar }

			additionalFiles.from(srcJarTask.flatMap(Jar::getArchiveFile))
			type = releaseType
			version = fullVersion
			changelog.set(rootProject.file("CHANGELOG.md").readText())
			modLoaders.add(loader)

			displayName =
				"${prop("mod.name")} $modVersion ${loader.replaceFirstChar(Char::titlecase)} $currentVersion"

			modrinth(deps, currentVersion, additionalVersions, mrStaging, modrinthAccessToken)
			if (!mrStaging) curseforge(
				deps,
				currentVersion,
				additionalVersions,
				false,
				curseforgeAccessToken
			)
		}
	}

	fun whenNotNull(stringProp: Property<String>, action: (String) -> Unit) {
		if (!stringProp.orNull.isNullOrBlank()) action(stringProp.get())
	}

	private fun ModPublishExtension.modrinth(
		deps: DependenciesConfig,
		currentVersion: String,
		additionalVersions: List<String>,
		staging: Boolean,
		acesssToken: String?
	) = modrinth {
		if (staging) apiEndpoint = "https://staging-api.modrinth.com/v2"
		projectId = project.prop("publish.modrinth")
		accessToken = acesssToken
		minecraftVersions.addAll(listOf(currentVersion) + additionalVersions)

		if (!staging) {
			deps.required.forEach { dep -> whenNotNull(dep.modrinth) { requires(it) } }
			deps.optional.forEach { dep -> whenNotNull(dep.modrinth) { optional(it) } }
			deps.incompatible.forEach { dep -> whenNotNull(dep.modrinth) { incompatible(it) } }
			deps.embeds.forEach { dep -> whenNotNull(dep.modrinth) { embeds(it) } }
		}
	}

	private fun ModPublishExtension.curseforge(
		deps: DependenciesConfig,
		currentVersion: String,
		additionalVersions: List<String>,
		staging: Boolean,
		acesssToken: String?
	) = curseforge {
		projectId = project.prop("publish.curseforge")
		accessToken = acesssToken
		minecraftVersions.addAll(listOf(currentVersion) + additionalVersions)

		deps.required.forEach { dep -> whenNotNull(dep.curseforge) { requires(it) } }
		deps.optional.forEach { dep -> whenNotNull(dep.curseforge) { optional(it) } }
		deps.incompatible.forEach { dep -> whenNotNull(dep.curseforge) { incompatible(it) } }
		deps.embeds.forEach { dep -> whenNotNull(dep.curseforge) { embeds(it) } }
	}

	private fun Project.configureDependancies(
		stonecutter: StonecutterBuildExtension,
		modPlatformExtension: ModPlatformExtension,
		includeDepConfiguration: org.gradle.api.artifacts.Configuration,
		commonConfiguration: org.gradle.api.artifacts.Configuration
	) {
		configurations.matching { it.name == "implementation" }.all {
			extendsFrom(includeDepConfiguration)
		}
		configurations.matching { it.name == "implementation" }.all {
			extendsFrom(commonConfiguration)
		}
		if(supportsJarInJar(stonecutter, modPlatformExtension.loader.get())) {
			pluginManager.withPlugin("gg.essential.loom") {
				configurations.matching { it.name == "include" }.all {
					extendsFrom(includeDepConfiguration)
				}
			}
		} else {
			tasks.named<ShadowJar>("shadowJar") {
				configurations = listOf(includeDepConfiguration)
			}
		}
		if(!stonecutter.eval(stonecutter.current.version, ">=26.1")) {
			val needDowngrade = JavaVersion.current() > resolveJavaVersion()
			afterEvaluate {
				if (needDowngrade) {
					configureManagedDowngrade()
				} else {
					configureDirectRemap()
				}
			}
		}
	}
	private fun Project.configureManagedDowngrade() {
		project.tasks.named<RemapJarTask>("remapJar") {
			dependsOn(tasks.named("shadowJar"))
			inputFile.set(tasks.named<ShadowJar>("shadowJar").flatMap { it.archiveFile })
			archiveClassifier.set("")
		}

		project.tasks.named<DowngradeJar>("downgradeJar") {
			dependsOn(tasks.named("remapJar"))
			inputFile.set(tasks.named<RemapJarTask>("remapJar").flatMap { it.archiveFile })
			archiveClassifier = "downgradedJar"
			downgradeTo = resolveJavaVersion()
		}

		project.tasks.named<ShadeJar>("shadeDowngradedApi") {
			dependsOn(tasks.named("downgradeJar"))
			inputFile.set(tasks.named<DowngradeJar>("downgradeJar").flatMap { it.archiveFile })
			archiveClassifier = "shadeDowngradedJar"
		}
	}

	private fun Project.configureDirectRemap() {
		pluginManager.withPlugin("gg.essential.loom") {
			tasks.named<RemapJarTask>("remapJar") {
				dependsOn("shadowJar")
				inputFile.set(tasks.named<ShadowJar>("shadowJar").flatMap { it.archiveFile })
				archiveClassifier.set("")
			}
		}
	}

	fun Project.configureShadowJar() {
		pluginManager.withPlugin("com.gradleup.shadow") {
			tasks.named<ShadowJar>("shadowJar") {
				relocate("com.fasterxml.jackson", "me.gergerapex1.shaded.fasterxml.jackson")
				relocate("org.yaml.snakeyaml", "me.gergerapex1.shaded.org.yaml.snakeyaml")
				archiveClassifier.set("shadow")
			}
		}
	}

	fun Project.createShadowImplConfiguration() {
		val shadowImpl = configurations.maybeCreate("shadowImpl").apply {
			isCanBeResolved = true
			isCanBeConsumed = false
		}
		tasks.named<ShadowJar>("shadowJar") {
			configurations = listOf(shadowImpl)
		}
	}

	fun Project.resolveJavaVersion(): JavaVersion {
		val stonecutter = extensions.getByType<StonecutterBuildExtension>()
		return when {
			stonecutter.eval(stonecutter.current.version, ">=26.1") -> JavaVersion.VERSION_25
			stonecutter.eval(stonecutter.current.version, ">=1.20.6") -> JavaVersion.VERSION_21
			stonecutter.eval(stonecutter.current.version, ">=1.20.5") -> JavaVersion.VERSION_21
			stonecutter.eval(stonecutter.current.version, ">=1.18") -> JavaVersion.VERSION_17
			stonecutter.eval(stonecutter.current.version, ">=1.17") -> JavaVersion.VERSION_16
			else -> JavaVersion.VERSION_1_8
		}
	}

	fun Project.resolveJavaLanguageVersion(): JavaLanguageVersion {
		val stonecutter = extensions.getByType<StonecutterBuildExtension>()

		return when {
			stonecutter.eval(stonecutter.current.version, ">=26.1") -> JavaLanguageVersion.of(25)
			stonecutter.eval(stonecutter.current.version, ">=1.20.6") -> JavaLanguageVersion.of(21)
			stonecutter.eval(stonecutter.current.version, ">=1.20.5") -> JavaLanguageVersion.of(21)
			stonecutter.eval(stonecutter.current.version, ">=1.18") -> JavaLanguageVersion.of(17)
			stonecutter.eval(stonecutter.current.version, ">=1.17") -> JavaLanguageVersion.of(16)
			else -> JavaLanguageVersion.of(8)
		}
	}
	private fun Project.supportsJarInJar(stonecutter: StonecutterBuildExtension, loader: String): Boolean {
		return when (loader) {
			"fabric" -> true
			"neoforge" -> true
			"forge" -> stonecutter.eval(stonecutter.current.version, ">=1.18")
			"deobfuscated" -> true
			else -> false
		}
	}
}
