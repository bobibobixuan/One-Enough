package top.bobixuan

import net.minecraftforge.fml.loading.FMLPaths
import java.nio.file.Path

class ForgePlatformPaths : OneEnoughPlatformPaths {
	override fun configDir(): Path = FMLPaths.CONFIGDIR.get()
}