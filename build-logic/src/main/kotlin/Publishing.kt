import dev.kikugie.stonecutter.build.StonecutterBuildExtension
import me.modmuss50.mpp.ModPublishExtension
import me.modmuss50.mpp.ReleaseType
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.assign

object Publishing {
	fun Project.configurePublishing(
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
	fun ModPublishExtension.modrinth(
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

	 fun ModPublishExtension.curseforge(deps: DependenciesConfig,
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
}
