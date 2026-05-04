package top.bobixuan

import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Path

class FabricPlatformPaths : OneEnoughPlatformPaths {
	override fun configDir(): Path = FabricLoader.getInstance().configDir
}