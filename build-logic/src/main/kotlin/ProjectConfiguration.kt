import ShadowTask.configureDirectRemap
import ShadowTask.configureManagedDowngrade
import ShadowTask.configureSpigotShadowTask
import Utils.resolveJavaVersion
import Utils.supportsJarInJar
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import dev.kikugie.stonecutter.build.StonecutterBuildExtension
import org.gradle.api.JavaVersion
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.Copy
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.expand
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.plugins.ide.idea.model.IdeaModel
import java.util.Locale
import kotlin.text.uppercase

object ProjectConfiguration {
	fun buildDependenciesBlock(
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
	fun Project.configureJarTask(modId: String) {
		tasks.withType<Jar>().configureEach {
			archiveBaseName.set(modId)
		}
	}

	fun Project.configureJava(
		requiredJava: JavaVersion
	) {
		extensions.configure<JavaPluginExtension>("java") {
			withSourcesJar()
			withJavadocJar()
			sourceCompatibility = requiredJava
			targetCompatibility = requiredJava
		}
	}

	fun Project.configureIdea() {
		extensions.configure<IdeaModel>("idea") {
			module {
				isDownloadJavadoc = true
				isDownloadSources = true
			}
		}
	}

	fun Project.configureProcessResources(
		loader: Loader,
		modId: String,
		modVersion: String,
		mcVersion: String,
		extension: ModPlatformExtension,
		requiredJava: JavaVersion
	) {
		tasks.named<ProcessResources>("processResources") {
			dependsOn(tasks.named("stonecutterGenerate"))
			dependsOn("kspKotlin")
			val isFabric = loader == Loader.FABRIC
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
			val spigotProps = mapOf(
				"apiVersion" to prop("spigot.apiVersion"),
			)
			when (loader) {
				Loader.FABRIC -> {
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

				Loader.NEOFORGE -> {
					filesMatching("META-INF/neoforge.mods.toml") { expand(props) }
					exclude(
						"META-INF/mods.toml",
						"fabric.mod.json",
						"aw/*.accesswidener",
						".cache",
						"pack.mcmeta"
					)
				}

				Loader.FORGE -> {
					filesMatching("META-INF/mods.toml") { expand(props) }
					exclude(
						"META-INF/neoforge.mods.toml",
						"fabric.mod.json",
						"aw/*.accesswidener",
						".cache"
					)
				}
				Loader.SPIGOT -> {
					filesMatching("plugin.yml") {
						expand(props + spigotProps)
					}
					exclude(
						"META-INF/mods.toml",
						"META-INF/neoforge.mods.toml",
						"fabric.mod.json",
						"aw/*.cfg",
						".cache",
						"pack.mcmeta"
					)
				}
				else -> {

				}
			}
		}
	}
	fun Project.configureDependancies(
		stonecutter: StonecutterBuildExtension,
		modPlatformExtension: ModPlatformExtension,
	) {
		val includeDependancy = configurations.maybeCreate("includeDep").apply {
			isCanBeConsumed = false
			isCanBeDeclared = true
		}
		val commonDependancy = configurations.maybeCreate("common").apply {
			isCanBeConsumed = false
			isCanBeDeclared = true
		}
		configurations.matching { it.name == "implementation" }.all {
			extendsFrom(includeDependancy)
		}
		configurations.matching { it.name == "implementation" }.all {
			extendsFrom(commonDependancy)
		}
		if(supportsJarInJar(modPlatformExtension.loader.get().loader)) {
			pluginManager.withPlugin("gg.essential.loom") {
				configurations.matching { it.name == "include" }.all {
					extendsFrom(includeDependancy)
				}
			}
		}
		if(!stonecutter.eval(stonecutter.current.version, ">=26.1")) {
			val needDowngrade = JavaVersion.current() > resolveJavaVersion()
			afterEvaluate {
				if(modPlatformExtension.loader.get() == Loader.SPIGOT) {
					afterEvaluate {
						configureSpigotShadowTask()
					}
				} else {
					if (needDowngrade) {
						configureManagedDowngrade()
					} else {
						configureDirectRemap()
					}
				}
			}
		}

	}
	fun Project.registerBuildAndCollectTask(
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
}
