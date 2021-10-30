package config

import com.squareup.moshi.Moshi
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import org.tinylog.Logger
import util.IOLock
import util.mkdirsIfNotExist
import java.io.File


class GamePath internal constructor(
    private val appConfig: AppConfig
) {
    fun isValidGamePath(path: String): Boolean {
        IOLock.read {
            val file = File(path)

            if (!file.exists()) return false

            var hasGameExe = false
            var hasGameCoreExe = false

            file.walkTopDown().maxDepth(1)
                .forEach {
                    if (it.nameWithoutExtension == "starsector") hasGameExe = true
                    if (it.nameWithoutExtension == "starsector-core") hasGameCoreExe = true
                }

            return hasGameExe && hasGameCoreExe
        }
    }

    fun getDefaultStarsectorPath(platform: Platform): File? =
        kotlin.runCatching {
            when (platform) {
                Platform.Windows ->
                    Advapi32Util.registryGetStringValue(
                        WinReg.HKEY_CURRENT_USER,
                        "SOFTWARE\\Fractal Softworks\\Starsector",
                        ""
                    )
                Platform.MacOS -> "" // TODO
                Platform.Linux -> "" // TODO
                else -> "" // TODO
            }
        }
            .mapCatching { File(it) }
            .onFailure {
                Logger.debug { it.message ?: "" }
                it.printStackTrace()
            }
            .getOrNull()

    fun getModsPath(
        starsectorPath: File = appConfig.gamePath?.let { File(it) }
            ?: throw NullPointerException("Game path not found")
    ): File {
        val mods = File(starsectorPath, "mods")

        IOLock.write {
            mods.mkdirsIfNotExist()
        }

        return mods
    }
}

enum class Platform {
    Windows,
    MacOS,
    Linux
}