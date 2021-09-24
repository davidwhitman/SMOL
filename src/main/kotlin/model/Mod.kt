package model

import java.io.File
import java.util.*

data class Mod(
    val id: String,
    val isEnabledInGame: Boolean,
    val modsFolderInfo: ModsFolderInfo?,
    val variants: Map<Int, ModVariant>,
) {

    /**
     * A mod is enabled if:
     * 1. It's in enabled_mods.json.
     * 2. Its mod folder is in the /mods folder.
     * 3. It's marked as enabled in SMOL (by the user).
     */
    fun isEnabled(modVariant: ModVariant) = isEnabledInGame && modVariant.isEnabledInSmol && modsFolderInfo != null

    data class ModsFolderInfo(
        val folder: File
    )

    val state: ModState = when {
        variants.values.any { isEnabled(it) } -> ModState.Enabled
        variants.values.any { it.stagingInfo != null } -> ModState.Disabled
        else -> ModState.Uninstalled
    }

    val findFirstEnabled: ModVariant?
        get() = variants.values.firstOrNull { isEnabled(it) }

    val findFirstDisabled: ModVariant?
        get() = variants.values.firstOrNull { !isEnabled(it) }
}

/**
 * @param stagingInfo null if not installed, not null otherwise
 */
data class ModVariant(
    val modInfo: ModInfo,
    val isEnabledInSmol: Boolean,
    val stagingInfo: StagingInfo?,
    val archiveInfo: ArchiveInfo?,
) {
    /**
     * Composite key: mod id + mod version.
     */
    val smolId: Int = Objects.hash(modInfo.id, modInfo.version.toString())

    // incredibly inelegant way of doing a parent-child relationship
    @Transient
    lateinit var mod: Mod

    val exists = stagingInfo != null || archiveInfo != null

    data class ArchiveInfo(
        val folder: File
    )

    data class StagingInfo(
        val folder: File
    )
}