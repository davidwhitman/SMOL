import business.ModLoader
import business.Staging
import config.AppConfig
import config.Platform
import model.Mod
import model.ModVariant
import org.tinylog.Logger
import util.IOLock
import util.mkdirsIfNotExist
import util.toFileOrNull
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class Access internal constructor(
    private val staging: Staging,
    private val config: AppConfig,
    private val modLoader: ModLoader
) {

    /**
     * Checks the /mods, archives, and staging paths and sets them to null if they don't exist.
     */
    fun checkAndSetDefaultPaths(platform: Platform) {
        val uiConfig: AppConfig = SL.appConfig

        if (!SL.gamePath.isValidGamePath(uiConfig.gamePath ?: "")) {
            uiConfig.gamePath = SL.gamePath.getDefaultStarsectorPath(platform)?.absolutePath
        }

        if (uiConfig.archivesPath.toFileOrNull()?.exists() != true) {
            uiConfig.archivesPath = File(System.getProperty("user.home"), "SMOL/archives").absolutePath
        }

        SL.archives.getArchivesManifest()
            .also { Logger.debug { "Archives folder manifest: ${it?.manifestItems?.keys?.joinToString()}" } }

        if (uiConfig.stagingPath.toFileOrNull()?.exists() != true) {
            uiConfig.stagingPath = File(System.getProperty("user.home"), "SMOL/staging").absolutePath
        }

        Logger.debug { "Game: ${uiConfig.gamePath}" }
        Logger.debug { "Archives: ${uiConfig.archivesPath}" }
        Logger.debug { "Staging: ${uiConfig.stagingPath}" }
    }

    /**
     * Gets the current staging folder path.
     */
    fun getStagingPath() = config.stagingPath

    /**
     * @throws Exception
     */
    fun changeStagingPath(newPath: String) {
        IOLock.write {
            kotlin.runCatching {
                val newFolder = File(newPath)
                val oldFolder = File(config.stagingPath ?: return).also { if (!it.exists()) return }

                newFolder.mkdirsIfNotExist()

                Files.move(oldFolder.toPath(), newFolder.toPath(), StandardCopyOption.REPLACE_EXISTING)

                config.stagingPath = newPath
            }
                .onFailure { Logger.error(it) }
                .getOrThrow()
        }
    }

    /**
     * Reads all mods from /mods, staging, and archive folders.
     * @param noCache When true, will never return cached information.
     */
    fun getMods(noCache: Boolean): List<Mod> = modLoader.getMods(noCache = noCache)

    /**
     * Changes the active mod variant, or disables all if `null` is set.
     */
    suspend fun changeActiveVariant(mod: Mod, modVariant: ModVariant?): Result<Unit> {
        try {
            if (modVariant?.mod != null && mod != modVariant.mod) {
                val err = "Variant and mod were different! ${mod.id}, ${modVariant.smolId}"
                Logger.info { err }
                return Result.failure(RuntimeException(err))
            }

            if (modVariant != null && mod.isEnabled(modVariant)) {
                // Check if this is the only active variant.
                // If there are somehow more than one active, the rest of the method will clean that up.
                if (mod.variants.count { mod.isEnabled(it) } <= 1) {
                    Logger.info { "Variant is already active, nothing to do! $modVariant" }
                    return Result.success(Unit)
                }
            }

            val activeVariants = mod.variants.filter { mod.isEnabled(it) }

            if (modVariant == null && activeVariants.none()) {
                Logger.info { "No variants active, nothing to do! $mod" }
                return Result.success(Unit)
            }

            // Disable all active mod variants.
            // There should only ever be one active but might as well be careful.
            mod.variants
                .filter { mod.isEnabled(it) }
                .forEach { staging.disableInternal(it) }

            return if (modVariant != null) {
                // Enable the one we want.
                // Slower: Reload, since we just disabled it
//                val freshModVariant = modLoader.getMods().flatMap { it.variants }.first { it.smolId == modVariant.smolId }
                // Faster: Assume we disabled it and change the mod to be disabled.
                modVariant.mod = modVariant.mod.copy(isEnabledInGame = false)
                staging.enableInternal(modVariant)
            } else {
                Result.success(Unit)
            }
        } finally {
            staging.manualReloadTrigger.trigger.emit("For mod ${mod.id}, staged variant: $modVariant.")
        }
    }

    suspend fun stage(modVariant: ModVariant): Result<Unit> {
        try {
            return staging.stageInternal(modVariant)
        } finally {
            staging.manualReloadTrigger.trigger.emit("staged mod: $modVariant")
        }
    }

    suspend fun enable(modToEnable: ModVariant): Result<Unit> {
        try {
            return staging.enableInternal(modToEnable)
        } finally {
            staging.manualReloadTrigger.trigger.emit("Enabled mod: $modToEnable")
        }
    }

    suspend fun unstage(mod: Mod): Result<Unit> {
        try {
            return staging.unstageInternal(mod)
        } finally {
            staging.manualReloadTrigger.trigger.emit("Mod unstaged: $mod")
        }
    }

    suspend fun disable(modVariant: ModVariant): Result<Unit> {
        try {
            return staging.disableInternal(modVariant)
        } finally {
            staging.manualReloadTrigger.trigger.emit("Disabled mod: $modVariant")
        }
    }

}