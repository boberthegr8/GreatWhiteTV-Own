package tv.own.owntv.features.online

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

data class OnlineItem(
    val id: String,
    val type: String,
    val name: String,
    val poster: String?,
    val description: String?,
    val year: String?,
)

data class OnlineStream(
    val name: String,
    val title: String?,
    val url: String?,
    val externalUrl: String?,
)

/**
 * Small Stremio-protocol adapter used by Great White Online.
 *
 * Add-ons remain ordinary HTTPS endpoints; no third-party executable code is loaded into the app.
 * The same adapter can therefore serve the official catalog today and user-configurable compatible
 * providers later without coupling the IPTV database/player stack to any one online source.
 */
class StremioAddonClient(
    private val client: OkHttpClient = OkHttpClient(),
) {
    fun catalog(addonBaseUrl: String, type: String, catalogId: String): List<OnlineItem> {
        val root = normalizeBase(addonBaseUrl)
        val body = get("$root/catalog/$type/$catalogId.json")
        val metas = JSONObject(body).optJSONArray("metas") ?: return emptyList()
        return buildList {
            for (i in 0 until metas.length()) {
                val meta = metas.optJSONObject(i) ?: continue
                val id = meta.optString("id").takeIf { it.isNotBlank() } ?: continue
                val name = meta.optString("name").takeIf { it.isNotBlank() } ?: continue
                add(
                    OnlineItem(
                        id = id,
                        type = meta.optString("type").ifBlank { type },
                        name = name,
                        poster = meta.optString("poster").takeIf { it.isNotBlank() },
                        description = meta.optString("description").takeIf { it.isNotBlank() },
                        year = when {
                            meta.has("releaseInfo") -> meta.optString("releaseInfo").takeIf { it.isNotBlank() }
                            meta.has("year") -> meta.optString("year").takeIf { it.isNotBlank() }
                            else -> null
                        },
                    ),
                )
            }
        }
    }

    fun streams(addonBaseUrl: String, type: String, id: String): List<OnlineStream> {
        val root = normalizeBase(addonBaseUrl)
        val body = get("$root/stream/$type/$id.json")
        val streams = JSONObject(body).optJSONArray("streams") ?: return emptyList()
        return buildList {
            for (i in 0 until streams.length()) {
                val stream = streams.optJSONObject(i) ?: continue
                add(
                    OnlineStream(
                        name = stream.optString("name").ifBlank { "Stream ${i + 1}" },
                        title = stream.optString("title").takeIf { it.isNotBlank() },
                        url = stream.optString("url").takeIf { it.startsWith("http://") || it.startsWith("https://") },
                        externalUrl = stream.optString("externalUrl").takeIf { it.startsWith("http://") || it.startsWith("https://") },
                    ),
                )
            }
        }
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

    companion object {
        /** Official Stremio movie/series catalog endpoint. */
        const val CINEMETA = "https://v3-cinemeta.strem.io"
    }
}
