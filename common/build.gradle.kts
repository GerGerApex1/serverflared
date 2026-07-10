plugins {
	id("java")
}

group = "me.gergerapex1.serverflared"
version = "1.0.0"

repositories {
	mavenCentral()
}

java {
	sourceCompatibility = JavaVersion.VERSION_1_8
	targetCompatibility = JavaVersion.VERSION_1_8
}
dependencies {
	implementation(libs.jackson.core)
	implementation(libs.jackson.dataformat.yaml)
	implementation(libs.jackson.databind)
	implementation(libs.jackson.annotations)
	implementation(libs.snakeyaml)
	compileOnly(libs.slf4j.api)
}

tasks.test {
	useJUnitPlatform()
}
