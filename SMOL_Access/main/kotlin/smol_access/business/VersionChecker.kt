package smol_access.business

import io.ktor.client.call.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import smol_access.Constants
import smol_access.HttpClientBuilder
import smol_access.config.VersionCheckerCache
import smol_access.model.Mod
import smol_access.model.ModId
import smol_access.model.VersionCheckerInfo
import timber.ktx.Timber
import timber.ktx.i
import utilities.Jsanity
import utilities.parallelMap
import utilities.trace
import java.time.Instant

interface IVersionChecker {
    /**
     * Gets the cached online version for the [mod].
     */
    fun getOnlineVersion(modId: ModId): VersionCheckerInfo?

    @Suppress("ConvertCallChainIntoSequence")
    /**
     * Look up the version checker file online.
     */
    suspend fun lookUpVersions(mods: List<Mod>, forceLookup: Boolean)
}

internal class VersionChecker(
    private val gson: Jsanity,
    private val versionCheckerCache: VersionCheckerCache,
    private val userManager: UserManager,
    private val modLoader: ModLoader,
    private val httpClientBuilder: HttpClientBuilder
) : IVersionChecker {
    companion object {
        const val DEFAULT_CHECK_INTERVAL_MILLIS: Long = 1000 * 60 * 5 // 5 mins
    }

    override fun getOnlineVersion(modId: ModId) =
        versionCheckerCache.onlineVersions[modId]

    @Suppress("ConvertCallChainIntoSequence")
    override suspend fun lookUpVersions(mods: List<Mod>, forceLookup: Boolean) {
        val msSinceLastCheck = Instant.now().toEpochMilli() - versionCheckerCache.lastCheckTimestamp
        val checkIntervalMillis =
            userManager.activeProfile.value.versionCheckerIntervalMillis ?: DEFAULT_CHECK_INTERVAL_MILLIS

        if (!forceLookup && msSinceLastCheck < checkIntervalMillis) {
            Timber.i { "Skipping version check, it has only been ${msSinceLastCheck / 1000}s of ${checkIntervalMillis / 1000}s." }
            return
        }

        withContext(Dispatchers.IO) {
            httpClientBuilder.invoke().use { client ->
                val results =
                    trace({ _, millis ->
                        Timber.tag(Constants.TAG_TRACE).i { "Version checked ${mods.count()} mods in ${millis}ms" }
                    }) {
                        mods
                            .distinctBy { it.id }
                            .mapNotNull { it.findHighestVersion }
                            .filter { !it.versionCheckerInfo?.masterVersionFile.isNullOrBlank() }
                            .parallelMap { modVariant ->
                                kotlin.runCatching {
                                    client.get<HttpResponse>(modVariant.versionCheckerInfo!!.masterVersionFile!!)
                                        .receive<String>()
                                        .let {
                                            modVariant.mod(modLoader) to gson.fromJson<VersionCheckerInfo>(
                                                json = it,
                                                shouldStripComments = true
                                            )
                                        }
                                }
                                    .onFailure {
                                        fun message(error: String?) =
                                            "Version check failed for ${modVariant.modInfo.name}: $error (url: ${modVariant.versionCheckerInfo?.masterVersionFile})"
                                        if (it is ClientRequestException) {
                                            Timber.w {
                                                // API errors tend to include the entire webpage html in the error message,
                                                // so only show the first line.
                                                message(it.message.lines().firstOrNull())
                                            }
                                        } else {
                                            Timber.w { message(it.message) }
                                        }
                                    }
                                    .getOrNull()
                            }
                            .filterNotNull()
                            .onEach {
                                Timber.d {
                                    "Version checked ${it.first.findHighestVersion?.modInfo?.name}: " +
                                            "existing: ${it.first.findHighestVersion?.versionCheckerInfo?.modVersion}" +
                                            ", online: ${it.second}" +
                                            " | (url: ${it.first.findHighestVersion?.versionCheckerInfo?.masterVersionFile})"
                                }
                            }
                    }

                versionCheckerCache.onlineVersions = results.associate { it.first.id to it.second }
                versionCheckerCache.lastCheckTimestamp = Instant.now().toEpochMilli()
            }
        }
    }
}