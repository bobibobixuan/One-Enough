import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	id("fabric-loom")
	`maven-publish`
	id("org.jetbrains.kotlin.jvm")
}

base {
	archivesName.set("${providers.gradleProperty("archives_base_name").get()}-fabric")
}

val cleanTask = tasks.named("clean")

tasks.configureEach {
	if (name != "clean") {
		mustRunAfter(cleanTask)
	}
}

repositories {
	maven {
		name = "Fabric"
		url = uri("https://maven.fabricmc.net/")
	}
	mavenCentral()
}

sourceSets {
	main {
		java.setSrcDirs(listOf("../common/src/main/java", "src/main/java"))
		kotlin.setSrcDirs(listOf("../common/src/main/kotlin", "src/main/kotlin"))
		resources.setSrcDirs(listOf("../common/src/main/resources", "src/main/resources"))
	}
}

dependencies {
	minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")
	mappings("net.fabricmc:yarn:${providers.gradleProperty("yarn_mappings").get()}:v2")
	modImplementation("net.fabricmc:fabric-loader:${providers.gradleProperty("fabric_loader_version").get()}")
	modImplementation("net.fabricmc:fabric-language-kotlin:${providers.gradleProperty("fabric_language_kotlin_version").get()}")
}

tasks.processResources {
	val replaceProperties = mapOf(
		"mod_id" to providers.gradleProperty("fabric_mod_id").get(),
		"mod_name" to providers.gradleProperty("mod_name").get(),
		"mod_license" to providers.gradleProperty("mod_license").get(),
		"mod_version" to providers.gradleProperty("mod_version").get(),
		"mod_authors" to providers.gradleProperty("mod_authors").get(),
		"mod_description" to providers.gradleProperty("mod_description").get(),
		"mod_url" to providers.gradleProperty("mod_url").get(),
		"issue_tracker_url" to providers.gradleProperty("issue_tracker_url").get(),
		"minecraft_version" to providers.gradleProperty("minecraft_version").get(),
		"fabric_loader_version" to providers.gradleProperty("fabric_loader_version").get(),
	)

	inputs.properties(replaceProperties)

	filesMatching("fabric.mod.json") {
		expand(replaceProperties)
	}
	filesMatching("pack.mcmeta") {
		expand("minecraft_version" to providers.gradleProperty("minecraft_version").get())
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.release = 17
}

tasks.named("build") {
	dependsOn("clean")
}

kotlin {
	compilerOptions {
		jvmTarget = JvmTarget.JVM_17
	}
}

java {
	withSourcesJar()

	sourceCompatibility = JavaVersion.VERSION_17
	targetCompatibility = JavaVersion.VERSION_17
}

tasks.jar {
	from(rootProject.file("LICENSE")) {
		rename { "${it}_${project.name}" }
	}
}

publishing {
	publications {
		register<MavenPublication>("mavenJava") {
			from(components["java"])
		}
	}

	repositories {
	}
}