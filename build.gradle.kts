plugins {
	base
	id("dev.architectury.loom") version "1.14.473" apply false
	id("fabric-loom") version "1.16.1" apply false
	id("org.jetbrains.kotlin.jvm") version "2.0.0" apply false
}

allprojects {
	version = providers.gradleProperty("mod_version").get()
	group = providers.gradleProperty("maven_group").get()
}

tasks.register("buildFabric") {
	group = LifecycleBasePlugin.BUILD_GROUP
	description = "Build the Fabric variant."
	dependsOn(":fabric:build")
}

tasks.register("buildForge") {
	group = LifecycleBasePlugin.BUILD_GROUP
	description = "Build the Forge variant."
	dependsOn(":forge:build")
}

tasks.named("build") {
	dependsOn("buildFabric", "buildForge")
}

tasks.named("clean") {
	dependsOn(":fabric:clean", ":forge:clean")
}
