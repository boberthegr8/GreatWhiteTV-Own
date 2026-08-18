package tv.own.owntv.features.online

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class OnlineItem(
    val id: String,
    val type: String,
    val name: String,
    val poster: String?,
    val description: String?,
    val year: String?,
)

data class OnlineVideo(
    val id: String,
    val title: String,
    val season: Int?,
    val episode: Int?,
    val released: String?,
    val thumbnail: String?,
)

data class OnlineMeta(
    val item: OnlineItem,
    val videos: List<OnlineVideo>,
)

data class OnlineStream(
    val name: String,
    val title: String?,
    val url: String?,
    val externalUrl: String?,
)

data class OnlineAddonManifest(
    val id: String,
    val name: String,
    val description: String?,
    val resources: Set<String>,
    val types: Set<String>,
)

/**
 * Small Stremio-protocol adapter used by Great White Online.
 *
 * Add-ons remain ordinary HTTP(S) endpoints; no third-party executable code is loaded into the app.
 * The adapter understands the protocol's manifest, catalog, metadata and stream resources, including
 * catalog extra arguments such as search / genre / skip.
 */
class StremioAddonClient(
    private val client: OkHttpClient = OkHttpClient(),
) {
    fun manifest(addonBaseUrl: String): OnlineAddonManifest {
        val body = get("${normalizeBase(addonBaseUrl)}/manifest.json")
        val json = JSONObject(body)
        val id = json.optString("id").takeIf { it.isNotBlank() } ?: throw IOException("Manifest has no id")
        val name = json.optString("name").takeIf { it.isNotBlank() } ?: id
        val resources = buildSet {
            val array = json.optJSONArray("resources")
            if (array != null) {
                for (i in 0 until array.length()) {
                    val raw = array.opt(i)
                    when (raw) {
                        is String -> if (raw.isNotBlank()) add(raw)
                        is JSONObject -> raw.optString("name").takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
            }
        }
        val types = buildSet {
            val array = json.optJSONArray("types")
            if (array != null) for (i in 0 until array.length()) array.optString(i).takeIf { it.isNotBlank() }?.let(::add)
        }
        return OnlineAddonManifest(
            id = id,
            name = name,
            description = json.optString("description").takeIf { it.isNotBlank() },
            resources = resources,
            types = types,
        )
    }

    fun catalog(
        addonBaseUrl: String,
        type: String,
        catalogId: String,
        extras: Map<String, String> = emptyMap(),
    ): List<OnlineItem> {
        val root = normalizeBase(addonBaseUrl)
        val extraPath = extras
            .filterValues { it.isNotBlank() }
            .entries
            .joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
            .takeIf { it.isNotBlank() }
            ?.let { "/$it" }
            .orEmpty()
        val body = get("$root/catalog/${encodePath(type)}/${encodePath(catalogId)}$extraPath.json")
        val metas = JSONObject(body).optJSONArray("metas") ?: return emptyList()
        return buildList {
            for (i in 0 until metas.length()) {
                val meta = metas.optJSONObject(i) ?: continue
                parseItem(meta, type)?.let(::add)
            }
        }
    }

    fun meta(addonBaseUrl: String, type: String, id: String): OnlineMeta? {
        val root = normalizeBase(addonBaseUrl)
        val body = get("$root/meta/${encodePath(type)}/${encodePath(id)}.json")
        val meta = JSONObject(body).optJSONObject("meta") ?: return null
        val item = parseItem(meta, type) ?: return null
        val videos = buildList {
            val array = meta.optJSONArray("videos") ?: return@buildList
            for (i in 0 until array.length()) {
                val video = array.optJSONObject(i) ?: continue
                val videoId = video.optString("id").takeIf { it.isNotBlank() } ?: continue
                val season = video.optInt("season", -1).takeIf { it >= 0 }
                val episode = video.optInt("episode", -1).takeIf { it >= 0 }
                val title = video.optString("title").takeIf { it.isNotBlank() }
                    ?: buildString {
                        if (season != null) append("S$season")
                        if (episode != null) {
                            if (isNotEmpty()) append(" · ")
                            append("E$episode")
                        }
                        if (isEmpty()) append(videoId)
                    }
                add(
                    OnlineVideo(
                        id = videoId,
                        title = title,
                        season = season,
                        episode = episode,
                        released = video.optString("released").takeIf { it.isNotBlank() },
                        thumbnail = video.optString("thumbnail").takeIf { it.isNotBlank() },
                    ),
                )
            }
        }
        return OnlineMeta(item, videos)
    }

    fun streams(addonBaseUrl: String, type: String, id: String): List<OnlineStream> {
        val root = normalizeBase(addonBaseUrl)
        val body = get("$root/stream/${encodePath(type)}/${encodePath(id)}.json")
        val streams = JSONObject(body).optJSONArray("streams") ?: return emptyList()
        return buildList {
            for (i in 0 until streams.length()) {
                val stream = streams.optJSONObject(i) ?: continue
                val url = stream.optString("url").takeIf(::isHttpUrl)
                val externalUrl = stream.optString("externalUrl").takeIf(::isHttpUrl)
                // Great White Online deliberately ignores torrent/infoHash-only entries. The player
                // handles ordinary web media URLs; unsupported transport types simply do not appear.
                if (url == null && externalUrl == null) continue
                add(
                    OnlineStream(
                        name = stream.optString("name").takeIf { it.isNotBlank() }.orEmpty(),
                        title = stream.optString("title").takeIf { it.isNotBlank() },
                        url = url,
                        externalUrl = externalUrl,
                    ),
                )
            }
        }
    }

    private fun parseItem(meta: JSONObject, fallbackType: String): OnlineItem? {
        val id = meta.optString("id").takeIf { it.isNotBlank() } ?: return null
        val name = meta.optString("name").takeIf { it.isNotBlank() } ?: return null
        return OnlineItem(
            id = id,
            type = meta.optString("type").ifBlank { fallbackType },
            name = name,
            poster = meta.optString("poster").takeIf { it.isNotBlank() },
            description = meta.optString("description").takeIf { it.isNotBlank() },
            year = when {
                meta.has("releaseInfo") -> meta.optString("releaseInfo").takeIf { it.isNotBlank() }
                meta.has("year") -> meta.optString("year").takeIf { it.isNotBlank() }
                else -> null
            },
        )
    }

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "GreatWhiteOnline/1")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return response.body.string().takeIf { it.isNotBlank() } ?: throw IOException("Empty response")
        }
    }

    private fun normalizeBase(url: String): String = url
        .trim()
        .trimEnd('/')
        .removeSuffix("/manifest.json")

    private fun encode(value: String): String = URLEncoder
        .encode(value, StandardCharsets.UTF_8.toString())
        .replace("+", "%20")

    // Stremio ids commonly contain ':' (episode ids), so keep it readable in the route while escaping
    // everything that can change path structure.
    private fun encodePath(value: String): String = encode(value).replace("%3A", ":", ignoreCase = true)

    private fun isHttpUrl(value: String): Boolean = value.startsWith("http://") || value.startsWith("https://")

    companion object {
        /** Official Stremio movie/series catalog and metadata endpoint. */
        const val CINEMETA = "https://v3-cinemeta.strem.io"
        /** Official Stremio where-to-watch stream resolver. */
        const val WATCHHUB = "https://watchhub.strem.io"
    }
}
