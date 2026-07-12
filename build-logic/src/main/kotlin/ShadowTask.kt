import Utils.resolveJavaVersion
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.fabricmc.loom.task.RemapJarTask
import org.gradle.api.Project
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.named
import xyz.wagyourtail.jvmdg.gradle.task.DowngradeJar
import xyz.wagyourtail.jvmdg.gradle.task.ShadeJar

object ShadowTask {
	fun Project.configureShadow() {
		configureShadowJar()
		createShadowImplConfiguration()
	}
	 fun Project.configureManagedDowngrade() {
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

	fun Project.configureDirectRemap() {
		pluginManager.withPlugin("gg.essential.loom") {
			tasks.named<RemapJarTask>("remapJar") {
				dependsOn("shadowJar")
				inputFile.set(tasks.named<ShadowJar>("shadowJar").flatMap { it.archiveFile })
				archiveClassifier.set("")
			}
		}
	}
	fun Project.configureSpigotShadowTask() {
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
				archiveClassifier.set("shadow")
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
			archiveClassifier=""
		}
	}
}
