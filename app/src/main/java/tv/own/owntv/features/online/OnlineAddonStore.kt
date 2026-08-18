package tv.own.owntv.features.online

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class OnlineAddon(
    val id: String,
    val name: String,
    val baseUrl: String,
    val resources: Set<String>,
    val types: Set<String>,
    val builtIn: Boolean = false,
) {
    val supportsCatalog: Boolean get() = "catalog" in resources
    val supportsMeta: Boolean get() = "meta" in resources
    val supportsStream: Boolean get() = "stream" in resources
}

/**
 * App-private Online add-on list. The IPTV source database is intentionally untouched: experimental
 * Online providers cannot corrupt, migrate, or otherwise interfere with a user's HUSH/CCTV playlists.
 */
class OnlineAddonStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(): List<OnlineAddon> = BUILT_INS + custom()

    fun streamProviders(): List<OnlineAddon> = all().filter { it.supportsStream }

    fun custom(): List<OnlineAddon> {
        val raw = prefs.getString(KEY_CUSTOM, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val json = array.optJSONObject(i) ?: continue
                    val id = json.optString("id").takeIf { it.isNotBlank() } ?: continue
                    val name = json.optString("name").takeIf { it.isNotBlank() } ?: id
                    val base = json.optString("baseUrl").takeIf { it.startsWith("http://") || it.startsWith("https://") }
                        ?: continue
                    add(
                        OnlineAddon(
                            id = id,
                            name = name,
                            baseUrl = base.trimEnd('/').removeSuffix("/manifest.json"),
                            resources = jsonStringSet(json.optJSONArray("resources")),
                            types = jsonStringSet(json.optJSONArray("types")),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun add(manifest: OnlineAddonManifest, baseUrl: String) {
        val next = custom()
            .filterNot { it.id == manifest.id }
            .plus(
                OnlineAddon(
                    id = manifest.id,
                    name = manifest.name,
                    baseUrl = baseUrl.trim().trimEnd('/').removeSuffix("/manifest.json"),
                    resources = manifest.resources,
                    types = manifest.types,
                ),
            )
        save(next)
    }

    fun remove(id: String) {
        save(custom().filterNot { it.id == id })
    }

    fun clearCustom() {
        prefs.edit().remove(KEY_CUSTOM).apply()
    }

    private fun save(addons: List<OnlineAddon>) {
        val array = JSONArray()
        addons.forEach { addon ->
            array.put(
                JSONObject()
                    .put("id", addon.id)
                    .put("name", addon.name)
                    .put("baseUrl", addon.baseUrl)
                    .put("resources", JSONArray(addon.resources.toList()))
                    .put("types", JSONArray(addon.types.toList())),
            )
        }
        prefs.edit().putString(KEY_CUSTOM, array.toString()).apply()
    }

    private fun jsonStringSet(array: JSONArray?): Set<String> = buildSet {
        if (array == null) return@buildSet
        for (i in 0 until array.length()) array.optString(i).takeIf { it.isNotBlank() }?.let(::add)
    }

    companion object {
        private const val PREFS = "greatwhite_online_addons"
        private const val KEY_CUSTOM = "custom"

        val BUILT_INS = listOf(
            OnlineAddon(
                id = "com.linvo.cinemeta",
                name = "Cinemeta",
                baseUrl = StremioAddonClient.CINEMETA,
                resources = setOf("catalog", "meta"),
                types = setOf("movie", "series"),
                builtIn = true,
            ),
            OnlineAddon(
                id = "org.stremio.watchhub",
                name = "WatchHub",
                baseUrl = StremioAddonClient.WATCHHUB,
                resources = setOf("stream"),
                types = setOf("movie", "series"),
                builtIn = true,
            ),
        )
    }
}
