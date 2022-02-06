package smol_access.model

import smol_access.Access
import smol_access.Constants
import smol_access.business.ModLoader
import java.nio.file.Path
import java.util.*
import kotlin.io.path.exists
import kotlin.math.absoluteValue

data class Mod(
    val id: String,
    val isEnabledInGame: Boolean,
    val variants: List<ModVariant>,
) {

    /**
     * A mod is enabled if:
     * 1. It's in enabled_mods.json.
     * 2. Its mod folder is in the /mods folder.
     */
    fun isEnabled(modVariant: ModVariant) =
        isEnabledInGame && modVariant.modsFolderInfo.folder.resolve(Constants.MOD_INFO_FILE).exists()

    data class ModsFolderInfo(
        val folder: Path
    )

    val enabledVariants: List<ModVariant>
        get() = variants.filter { isEnabled(it) }

    val findFirstEnabled: ModVariant?
        get() = variants.firstOrNull { isEnabled(it) }

    val findFirstDisabled: ModVariant?
        get() = variants.firstOrNull { !isEnabled(it) }

    val findHighestVersion: ModVariant?
        get() = variants.maxByOrNull { it.modInfo.version }

    val findFirstEnabledOrHighestVersion: ModVariant?
        get() = findFirstEnabled ?: findHighestVersion

    val hasEnabledVariant: Boolean
        get() = findFirstEnabled != null

    companion object {
        val MOCK = Mod(
            id = "mock",
            isEnabledInGame = true,
            variants = listOf(
                ModVariant(
                    modInfo = ModInfo.v095(
                        _id = "mock",
                        _name = "Mock Mod",
                        _author = "Wisp",
                        _utilityString = "false",
                        _description = "An isolationist authoritarian Theocracy thrust into Persean politics by necessity, this high-tech faction utilises flexible pulse-based energy weapons and unique solar shielding.",
                        _gameVersion = "0.95.1-RC15",
                        _jars = listOf("jar.jar"),
                        _modPlugin = "mod/plugin",
                        _dependencies = listOf(Dependency(_id = "lw_lazylib", name = null, versionString = null)),
                        versionString = Version(raw = "1.0.0")
                    ),
                    versionCheckerInfo = null,
                    modsFolderInfo = ModsFolderInfo(Path.of(""))
                )
            )
        )
    }
}

data class ModVariant(
    val modInfo: ModInfo,
    val versionCheckerInfo: VersionCheckerInfo?,
    val modsFolderInfo: Mod.ModsFolderInfo
) {
    /**
     * Composite key: mod id + mod version.
     */
    val smolId: SmolId
        get() = createSmolId(modInfo)

    companion object {
        private val smolIdAllowedChars = Regex("""[^0-9a-zA-Z\\.\-_]""")
        fun createSmolId(id: String, version: Version) =
            buildString {
                append(id.replace(smolIdAllowedChars, "").take(6))
                append("-")
                append(version.toString().replace(smolIdAllowedChars, "").take(9))
                append("-")
                append(
                    Objects.hash(
                        id,
                        version.toString()
                    )
                        .absoluteValue // Increases chance of a collision but ids look less confusing.
                )
            }

        fun createSmolId(modInfo: ModInfo) = createSmolId(modInfo.id, modInfo.version)

        val MOCK: ModVariant
            get() = Mod.MOCK.variants.first()
    }

    internal fun mod(modLoader: ModLoader) = modLoader.mods.value?.mods!!.first { it.id == modInfo.id }
    fun mod(access: Access) = access.mods.value?.mods!!.first { it.id == modInfo.id }

    val isModInfoEnabled: Boolean
        get() = modsFolderInfo.folder.resolve(Constants.MOD_INFO_FILE).exists()

    private val systemFolderNameAllowedChars = Regex("""[^0-9a-zA-Z\\.\-_ ]""")
    fun generateVariantFolderName() = "${modInfo.name?.replace(systemFolderNameAllowedChars, "")}_${smolId}"
}

typealias SmolId = String
typealias ModId = String