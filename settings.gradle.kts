pluginManagement {
	repositories {
		maven {
			name = "Architectury"
			url = uri("https://maven.architectury.dev/")
		}
		maven {
			name = "Forge"
			url = uri("https://maven.minecraftforge.net/")
		}
		maven {
			name = "Fabric"
			url = uri("https://maven.fabricmc.net/")
		}
		maven {
			name = "Kotlin for Forge"
			url = uri("https://thedarkcolour.github.io/KotlinForForge/")
		}
		mavenCentral()
		gradlePluginPortal()
	}
}

rootProject.name = "one-enough-mod"

include("fabric", "forge")
