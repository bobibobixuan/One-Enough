import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	id("dev.architectury.loom")
	`maven-publish`
	id("org.jetbrains.kotlin.jvm")
}

base {
	archivesName.set("${providers.gradleProperty("archives_base_name").get()}-forge")
}

val cleanTask = tasks.named("clean")

tasks.configureEach {
	if (name != "clean") {
		mustRunAfter(cleanTask)
	}
}

repositories {
	maven {
		name = "Forge"
		url = uri("https://maven.minecraftforge.net/")
	}
	maven {
		name = "Kotlin for Forge"
		url = uri("https://thedarkcolour.github.io/KotlinForForge/")
	}
	mavenCentral()
}

loom {
	silentMojangMappingsLicense()

	forge {
		mixinConfig("one-enough-mod.mixins.json")
	}
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
	add("forge", "net.minecraftforge:forge:${providers.gradleProperty("minecraft_version").get()}-${providers.gradleProperty("forge_version").get()}")
	implementation("thedarkcolour:kotlinforforge:${providers.gradleProperty("kff_version").get()}")
}

tasks.processResources {
	val replaceProperties = mapOf(
		"mod_id" to providers.gradleProperty("forge_mod_id").get(),
		"mod_name" to providers.gradleProperty("mod_name").get(),
		"mod_license" to providers.gradleProperty("mod_license").get(),
		"mod_version" to providers.gradleProperty("mod_version").get(),
		"mod_authors" to providers.gradleProperty("mod_authors").get(),
		"mod_description" to providers.gradleProperty("mod_description").get(),
		"mod_url" to providers.gradleProperty("mod_url").get(),
		"issue_tracker_url" to providers.gradleProperty("issue_tracker_url").get(),
		"minecraft_version" to providers.gradleProperty("minecraft_version").get(),
		"forge_version" to providers.gradleProperty("forge_version").get(),
	)

	inputs.properties(replaceProperties)

	filesMatching("META-INF/mods.toml") {
		expand(replaceProperties)
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