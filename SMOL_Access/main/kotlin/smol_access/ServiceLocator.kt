package smol_access

import com.github.salomonbrys.kotson.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.squareup.moshi.FromJson
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.hjson.JsonValue
import smol_access.business.*
import smol_access.config.AppConfig
import smol_access.config.GamePath
import smol_access.config.VersionCheckerCache
import smol_access.config.VramCheckerCache
import smol_access.model.ModInfo
import smol_access.themes.ThemeConfig
import smol_access.util.ManualReloadTrigger

var SL = ServiceLocator()
private val basicMoshi = Moshi.Builder()
    .addLast(KotlinJsonAdapterFactory()).build()
private val basicGson = GsonBuilder().create()

@OptIn(ExperimentalStdlibApi::class)
class ServiceLocator internal constructor(
    val manualReloadTrigger: ManualReloadTrigger = ManualReloadTrigger(),
    val moshi: Moshi = Moshi.Builder()
        .add(ModInfoAdapter())
        .addLast(KotlinJsonAdapterFactory())
        .build(),
    val gson: Gson = buildGson(),
    val themeConfig: ThemeConfig = ThemeConfig(gson = gson),
    val downloadManager: DownloadManager = DownloadManager(),
    internal val versionCheckerCache: VersionCheckerCache = VersionCheckerCache(gson = gson),
    val versionChecker: VersionChecker = VersionChecker(gson = gson, versionCheckerCache = versionCheckerCache),
    val appConfig: AppConfig = AppConfig(gson = gson),
    internal val modInfoLoader: ModInfoLoader = ModInfoLoader(moshi = moshi, gson = gson),
    val gamePath: GamePath = GamePath(appConfig = appConfig),
    internal val gameEnabledMods: GameEnabledMods = GameEnabledMods(gson, gamePath),
    val archives: Archives = Archives(
        config = appConfig,
        gamePath = gamePath,
        gson = gson,
        moshi = moshi,
        modInfoLoader = modInfoLoader
    ),
    internal val modLoader: ModLoader = ModLoader(
        gamePath = gamePath,
        archives = archives,
        modInfoLoader = modInfoLoader,
        config = appConfig,
        gameEnabledMods = gameEnabledMods,
        versionChecker = versionChecker
    ),
    val vramChecker: VramCheckerManager = VramCheckerManager(
        modLoader = modLoader,
        gamePath = gamePath,
        vramCheckerCache = VramCheckerCache(gson = gson)
    ),
    internal val staging: Staging = Staging(
        config = appConfig,
        gamePath = gamePath,
        modLoader = modLoader,
        gameEnabledMods = gameEnabledMods,
        archives = archives,
        manualReloadTrigger = manualReloadTrigger
    ),
    val access: Access = Access(staging = staging, config = appConfig, modLoader = modLoader, archives = archives),
    val userManager: UserManager = UserManager(
        appConfig = appConfig, access = access, modLoader = modLoader
    ),
    val modRepo: ModRepo = ModRepo()
)

private fun buildGson() = GsonBuilder()
    .setPrettyPrinting()
    .setLenient()
    .serializeNulls()
    .registerTypeAdapter<ModInfo> {
        serialize { (src, _, _) ->
            when (src) {
                is ModInfo.v091 -> basicGson.toJson(src, ModInfo.v091::class.java).toJson()
                is ModInfo.v095 -> basicGson.toJson(src, ModInfo.v095::class.java).toJson()
            }
        }
        deserialize { arg: DeserializerArg ->
            val json = if (arg.json.isJsonObject)
                arg.json
            else JsonParser().parse(arg.json.asString)

            // Check for 0.95 format
            if (json["version"].isJsonObject) {
                basicGson.fromJson<ModInfo.v095>(json)
            } else {
                basicGson.fromJson<ModInfo.v091>(json)
            }
        }
    }
    .create()

@ExperimentalStdlibApi
class ModInfoAdapter {
    @ToJson
    fun toJson(obj: ModInfo): String {
        return when (obj) {
            is ModInfo.v091 -> basicMoshi.adapter<ModInfo.v091>().toJson(obj)
            is ModInfo.v095 -> basicMoshi.adapter<ModInfo.v095>().toJson(obj)
        }
    }

    @FromJson
    fun fromJson(jsonAsMap: Map<String, String>): ModInfo {
        val json = JsonValue.readHjson(basicMoshi.adapter<Map<String, String>>().toJson(jsonAsMap))
        return basicMoshi.modInfoJsonAdapter(json).fromJson(json.toString())!!
    }
}

//class ModInfoJsonAdapter2 : JsonAdapter<ModInfo>() {
//    override fun fromJson(reader: JsonReader): ModInfo? {
//        return JsonValue.readHjson(reader.nextSource())
//    }
//
//    @OptIn(ExperimentalStdlibApi::class)
//    override fun toJson(writer: JsonWriter, value: ModInfo?) {
//        return when (value) {
//            is ModInfo.v091 -> SMOL_Access.basicMoshi.adapter<ModInfo.v091>().toJson(writer, value)
//            is ModInfo.v095 -> SMOL_Access.basicMoshi.adapter<ModInfo.v095>().toJson(writer, value)
//            null -> throw NullPointerException()
//        }
//    }
//}

@OptIn(ExperimentalStdlibApi::class)
fun Moshi.modInfoJsonAdapter(json: JsonValue) =
    // Check for 0.95 format
    if (json.asObject().get("version").isObject) {
        this.adapter<ModInfo.v095>()
    } else {
        this.adapter<ModInfo.v091>()
    }