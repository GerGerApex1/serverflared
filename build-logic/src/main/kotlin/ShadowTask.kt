import Utils.resolveJavaVersion
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.withType
import xyz.wagyourtail.jvmdg.gradle.task.DowngradeJar
import xyz.wagyourtail.jvmdg.gradle.task.ShadeJar

object ShadowTask {
	fun Project.configureShadow() {
		configureShadowJar()
		createShadowImplConfiguration()
	}

	fun Project.configureUniminedRemap() {
		pluginManager.withPlugin("xyz.wagyourtail.unimined") {
			val shadowJarTask = tasks.named<ShadowJar>("shadowJar")
			shadowJarTask.configure {
				archiveClassifier.set("shadow")
			}

			tasks.withType<Jar>().matching { it.name == "remapJar" }.configureEach {
				dependsOn(shadowJarTask)
				from(zipTree(shadowJarTask.flatMap { it.archiveFile }))
				archiveClassifier.set("")
			}
		}
	}

	fun Project.configureSpigotShadowTask() {
		project.tasks.named<Jar>("jar") {
			archiveClassifier.set("thin")
		}

		project.tasks.named<ShadowJar>("shadowJar") {
			archiveClassifier.set("shadow")
		}

		project.tasks.named<DowngradeJar>("downgradeJar") {
			dependsOn(tasks.named("shadowJar"))
			inputFile.set(tasks.named<ShadowJar>("shadowJar").flatMap { it.archiveFile })
			archiveClassifier = "downgradedJar"
			downgradeTo = resolveJavaVersion()
		}

		project.tasks.named<ShadeJar>("shadeDowngradedApi") {
			dependsOn(tasks.named("downgradeJar"))
			inputFile.set(tasks.named<DowngradeJar>("downgradeJar").flatMap { it.archiveFile })
			archiveClassifier = ""
		}
	}

	fun Project.configureShadowJar() {
		pluginManager.withPlugin("com.gradleup.shadow") {
			tasks.named<ShadowJar>("shadowJar") {
				relocate("com.fasterxml.jackson", "me.gergerapex1.shaded.fasterxml.jackson")
				relocate("org.yaml.snakeyaml", "me.gergerapex1.shaded.org.yaml.snakeyaml")
			}
		}
	}

	fun Project.createShadowImplConfiguration() {
		val shadowImpl = configurations.maybeCreate("shadowImpl").apply {
			isCanBeResolved = true
			isCanBeConsumed = false
		}
		val includeDep = configurations.named("includeDep")
		val commonDep = configurations.named("common")

		tasks.named<ShadowJar>("shadowJar") {
			configurations = listOf(includeDep.get(), commonDep.get(), shadowImpl)
		}
	}
}
