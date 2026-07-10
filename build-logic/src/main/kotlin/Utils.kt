import dev.kikugie.stonecutter.build.StonecutterBuildExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.getByType

object Utils {
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
	fun supportsJarInJar(stonecutter: StonecutterBuildExtension, loader: String): Boolean {
		return when (loader) {
			"fabric" -> true
			"neoforge" -> true
			"forge" -> stonecutter.eval(stonecutter.current.version, ">=1.18")
			"deobfuscated" -> true
			else -> false
		}
	}
}
