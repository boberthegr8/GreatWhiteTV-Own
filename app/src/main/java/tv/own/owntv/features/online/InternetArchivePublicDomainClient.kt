package tv.own.owntv.features.online

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * No-login public-domain movie source for Great White Online.
 *
 * This client searches Internet Archive, then only exposes files from items whose metadata explicitly
 * marks them as public domain. It never touches the IPTV database and never requires an account.
 */
class InternetArchivePublicDomainClient(
    private val client: OkHttpClient = OkHttpClient(),
) {
    fun streamsFor(item: OnlineItem): List<OnlineStream> {
        if (item.type != "movie" || item.name.isBlank()) return emptyList()

        val title = item.name.trim()
        val year = item.year?.take(4)?.toIntOrNull()
        val queryParts = mutableListOf(
            "mediatype:movies",
            "title:(\"${escapeQuery(title)}\")",
        )
        if (year != null) queryParts += "year:$year"
        val query = queryParts.joinToString(" AND ")
        val searchUrl = "https://archive.org/advancedsearch.php" +
            "?q=${encode(query)}&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=year&rows=8&page=1&output=json"

        val docs = JSONObject(get(searchUrl))
            .optJSONObject("response")
            ?.optJSONArray("docs")
            ?: return emptyList()

        val out = mutableListOf<OnlineStream>()
        for (i in 0 until docs.length()) {
            val doc = docs.optJSONObject(i) ?: continue
            val identifier = doc.optString("identifier").takeIf { it.isNotBlank() } ?: continue
            val metadataRoot = runCatching { JSONObject(get("https://archive.org/metadata/${encodePath(identifier)}")) }
                .getOrNull() ?: continue
            val metadata = metadataRoot.optJSONObject("metadata") ?: continue
            if (!isExplicitPublicDomain(metadata)) continue

            val files = metadataRoot.optJSONArray("files") ?: continue
            for (j in 0 until files.length()) {
                val file = files.optJSONObject(j) ?: continue
                val name = file.optString("name").takeIf { it.isNotBlank() } ?: continue
                if (!isPlayableVideo(name, file.optString("format"))) continue
                val url = "https://archive.org/download/${encodePath(identifier)}/${encodePathSegments(name)}"
                out += OnlineStream(
                    name = "Internet Archive · Public Domain",
                    title = buildString {
                        append(metadata.optString("title").ifBlank { title })
                        file.optString("format").takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                    },
                    url = url,
                    externalUrl = null,
                )
                if (out.size >= 4) return out
            }
        }
        return out
    }

    private fun isExplicitPublicDomain(metadata: JSONObject): Boolean {
        val rights = metadata.optString("rights").lowercase()
        val license = metadata.optString("licenseurl").lowercase()
        return "public domain" in rights ||
            "creativecommons.org/publicdomain" in license ||
            "creativecommons.org/public-domain" in license
    }

    private fun isPlayableVideo(name: String, format: String): Boolean {
        val lowerName = name.lowercase()
        val lowerFormat = format.lowercase()
        return lowerName.endsWith(".mp4") || lowerName.endsWith(".m4v") ||
            "h.264" in lowerFormat || "mpeg4" in lowerFormat || "mpeg-4" in lowerFormat
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

    private fun escapeQuery(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun encode(value: String): String = URLEncoder
        .encode(value, StandardCharsets.UTF_8.toString())
        .replace("+", "%20")

    private fun encodePath(value: String): String = encode(value).replace("%2F", "/", ignoreCase = true)

    private fun encodePathSegments(value: String): String = value
        .split('/')
        .joinToString("/") { encode(it) }
}
