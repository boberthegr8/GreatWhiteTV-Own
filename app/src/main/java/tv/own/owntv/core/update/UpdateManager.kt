package tv.own.owntv.core.update

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.koin.core.context.GlobalContext
import tv.own.owntv.BuildConfig
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.core.database.entity.ProfileSourceCrossRef
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.HlsSupport
import tv.own.owntv.core.model.SourceType
import java.io.File
import java.io.IOException

/**
 * In-app updates straight from GWS Online's GitHub Releases. Startup and manual checks share the same
 * release path, and every install is preceded by an app-private playlist/account safety snapshot.
 * Android normally preserves Room data during an in-place update; the snapshot is a fallback that is
 * restored automatically on the first launch of the new version only if the source table is empty.
 */
class UpdateManager(
    private val context: Context,
    private val client: OkHttpClient,
) {
    data class UpdateInfo(val version: String, val notes: String, val apkUrl: String)

    sealed interface Failure {
        data class CheckHttp(val code: Int) : Failure
        data object NoCompatibleApk : Failure
        data object InvalidReleaseResponse : Failure
        data object CheckNetwork : Failure
        data class DownloadHttp(val code: Int) : Failure
        data object EmptyDownload : Failure
        data object DownloadNetwork : Failure
        data object Install : Failure
    }

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val info: UpdateInfo) : State
        data class Downloading(val percent: Int) : State
        data class Failed(val failure: Failure, val retryInfo: UpdateInfo? = null) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sourceDao: SourceDao by lazy { GlobalContext.get().get() }
    private val profileDao: ProfileDao by lazy { GlobalContext.get().get() }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private class CheckHttpException(val code: Int) : IOException()
    private class DownloadHttpException(val code: Int) : IOException()
    private class NoCompatibleApkException : IOException()
    private class InvalidReleaseResponseException : IOException()
    private class EmptyDownloadException : IOException()

    private fun failureFor(error: Throwable, checking: Boolean): Failure = when (error) {
        is CheckHttpException -> Failure.CheckHttp(error.code)
        is DownloadHttpException -> Failure.DownloadHttp(error.code)
        is NoCompatibleApkException -> Failure.NoCompatibleApk
        is InvalidReleaseResponseException -> Failure.InvalidReleaseResponse
        is EmptyDownloadException -> Failure.EmptyDownload
        else -> if (checking) Failure.CheckNetwork else Failure.DownloadNetwork
    }

    val currentVersion: String = BuildConfig.VERSION_NAME

    init {
        // The new package has already replaced the old one when this runs. If Android/Room retained the
        // sources (normal case), the safety snapshot is simply discarded. If they disappeared, restore
        // the playlists, credentials and profile links without sending the user through setup again.
        scope.launch {
            runCatching { restorePlaylistSnapshotIfNeeded() }
                .onFailure { Log.e(TAG, "playlist safety restore failed", it) }
        }
    }

    /** Startup check used by the shell. Future releases now surface automatically after launch. */
    fun check() = checkManual()

    /** Queries GWS Online's latest release. */
    fun checkManual() {
        if (_state.value is State.Checking || _state.value is State.Downloading) return
        _state.value = State.Checking
        scope.launch {
            runCatching {
                val request = Request.Builder()
                    .url("https://api.github.com/repos/$REPO/releases/latest")
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "GWSOnline")
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) throw CheckHttpException(resp.code)
                    val body = resp.body.string()
                    if (body.isBlank()) throw InvalidReleaseResponseException()
                    val o = runCatching { JSONObject(body) }.getOrElse { throw InvalidReleaseResponseException() }
                    val version = o.optString("tag_name").removePrefix("v").takeIf { it.isNotBlank() }
                        ?: throw InvalidReleaseResponseException()
                    val notes = o.optString("body").take(16_000)
                    val assets = o.optJSONArray("assets") ?: throw InvalidReleaseResponseException()
                    val wantX86 = android.os.Build.SUPPORTED_ABIS.firstOrNull() == "x86_64"
                    val candidates = (0 until assets.length())
                        .asSequence()
                        .mapNotNull { assets.optJSONObject(it) }
                        .mapNotNull { asset ->
                            val name = asset.optString("name")
                            val url = asset.optString("browser_download_url")
                            if (!name.endsWith(".apk") || url.isBlank()) null else name to url
                        }
                        .toList()
                    val apkUrl = if (wantX86) {
                        candidates.firstOrNull { (name, _) -> name.contains("x86_64", ignoreCase = true) }?.second
                    } else {
                        candidates.firstOrNull { (name, _) -> name.equals("GWSOnline.apk", ignoreCase = true) }?.second
                            ?: candidates.firstOrNull { (name, _) -> name.equals("GreatWhiteTV.apk", ignoreCase = true) }?.second
                            ?: candidates.firstOrNull { (name, _) -> name.equals("OwnTV.apk", ignoreCase = true) }?.second
                            ?: candidates.firstOrNull { (name, _) -> !name.contains("x86_64", ignoreCase = true) }?.second
                    } ?: throw NoCompatibleApkException()
                    val info = UpdateInfo(version, notes, apkUrl)
                    if (isNewer(version, currentVersion)) _state.value = State.Available(info)
                    else _state.value = State.UpToDate
                }
            }.onFailure { error ->
                Log.w(TAG, "update check failed: ${error.message}", error)
                _state.value = State.Failed(failureFor(error, checking = true))
            }
        }
    }

    /** Downloads the release APK with progress, snapshots playlists, then opens the system installer. */
    fun downloadAndInstall() {
        val info = (_state.value as? State.Available)?.info ?: return
        _state.value = State.Downloading(0)
        scope.launch {
            runCatching {
                val dir = File(context.filesDir, "updates").apply { mkdirs() }
                val out = File(dir, "gws-online-update.apk")
                val request = Request.Builder().url(info.apkUrl).header("User-Agent", "GWSOnline").build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) throw DownloadHttpException(resp.code)
                    val body = resp.body
                    val total = body.contentLength()
                    var copied = 0L
                    body.byteStream().use { input ->
                        out.outputStream().use { output ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                output.write(buf, 0, n)
                                copied += n
                                if (total > 0) _state.value = State.Downloading((copied * 100 / total).toInt())
                            }
                        }
                    }
                    if (copied == 0L) throw EmptyDownloadException()
                }

                // Never hand the APK to Android until the active playlist/account definitions are safely
                // serialized. This includes Xtream/Stalker credentials and profile-to-source links.
                runCatching { snapshotPlaylistState() }.getOrElse { throw InstallException(it) }
                runCatching { install(out) }.getOrElse { throw InstallException(it) }
                _state.value = State.Available(info)
            }.onFailure { error ->
                Log.w(TAG, "update download/install failed: ${error.message}", error)
                val failure = if (error is InstallException) Failure.Install else failureFor(error, checking = false)
                _state.value = State.Failed(failure, retryInfo = info)
            }
        }
    }

    private fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun safetyFile(): File {
        val dir = File(context.filesDir, "update-safety").apply { mkdirs() }
        return File(dir, "playlist-snapshot.json")
    }

    private suspend fun snapshotPlaylistState() {
        val sources = sourceDao.getAllOnce()
        val profiles = profileDao.getAllOnce()
        val links = sourceDao.allLinks()

        val root = JSONObject()
            .put("schema", 1)
            .put("fromVersion", currentVersion)
            .put("createdAt", System.currentTimeMillis())
            .put("profiles", JSONArray().apply {
                profiles.forEach { p ->
                    put(JSONObject()
                        .put("id", p.id)
                        .put("name", p.name)
                        .put("avatarColor", p.avatarColor)
                        .put("avatarId", p.avatarId)
                        .put("isKids", p.isKids)
                        .putNullable("pinHash", p.pinHash)
                        .put("createdAt", p.createdAt))
                }
            })
            .put("sources", JSONArray().apply {
                sources.forEach { s ->
                    put(JSONObject()
                        .put("id", s.id)
                        .put("name", s.name)
                        .put("type", s.type.name)
                        .put("url", s.url)
                        .putNullable("username", s.username)
                        .putNullable("password", s.password)
                        .putNullable("mac", s.mac)
                        .putNullable("stalkerSerialNumber", s.stalkerSerialNumber)
                        .putNullable("stalkerDeviceId", s.stalkerDeviceId)
                        .putNullable("stalkerDeviceId2", s.stalkerDeviceId2)
                        .putNullable("stalkerSignature", s.stalkerSignature)
                        .putNullable("userAgent", s.userAgent)
                        .putNullable("epgUrl", s.epgUrl)
                        .put("syncLive", s.syncLive)
                        .put("syncMovies", s.syncMovies)
                        .put("syncSeries", s.syncSeries)
                        .put("hlsSupported", s.hlsSupported.code)
                        .put("preferHls", s.preferHls)
                        .put("livePrerollSecs", s.livePrerollSecs)
                        .put("maxConnections", s.maxConnections)
                        .put("createdAt", s.createdAt)
                        .putNullableLong("lastSyncAt", s.lastSyncAt))
                }
            })
            .put("links", JSONArray().apply {
                links.forEach { link ->
                    put(JSONObject().put("profileId", link.profileId).put("sourceId", link.sourceId))
                }
            })

        val target = safetyFile()
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeText(root.toString())
        if (!temp.renameTo(target)) {
            target.writeText(temp.readText())
            temp.delete()
        }
    }

    private suspend fun restorePlaylistSnapshotIfNeeded() {
        val file = safetyFile()
        if (!file.isFile) return
        val root = runCatching { JSONObject(file.readText()) }.getOrElse {
            file.delete()
            return
        }

        // Same-version relaunch means the installer never completed; retain the snapshot for a retry.
        val fromVersion = root.optString("fromVersion")
        if (fromVersion.isBlank() || fromVersion == currentVersion) return

        // Normal Android update: data survived. Snapshot has served its purpose and can be removed.
        if (sourceDao.getAllOnce().isNotEmpty()) {
            file.delete()
            return
        }

        val profiles = root.optJSONArray("profiles") ?: JSONArray()
        for (i in 0 until profiles.length()) {
            val p = profiles.optJSONObject(i) ?: continue
            val id = p.optLong("id", 0L)
            if (id <= 0L || profileDao.getById(id) != null) continue
            profileDao.insert(
                ProfileEntity(
                    id = id,
                    name = p.optString("name", "GWS Online"),
                    avatarColor = p.optInt("avatarColor", 0),
                    avatarId = p.optInt("avatarId", 0),
                    isKids = p.optBoolean("isKids", false),
                    pinHash = p.nullableString("pinHash"),
                    createdAt = p.optLong("createdAt", System.currentTimeMillis()),
                ),
            )
        }

        val restoredSourceIds = mutableSetOf<Long>()
        val sources = root.optJSONArray("sources") ?: JSONArray()
        for (i in 0 until sources.length()) {
            val s = sources.optJSONObject(i) ?: continue
            val id = s.optLong("id", 0L)
            if (id <= 0L) continue
            val type = runCatching { SourceType.valueOf(s.optString("type")) }.getOrNull() ?: continue
            sourceDao.insert(
                SourceEntity(
                    id = id,
                    name = s.optString("name", "Playlist"),
                    type = type,
                    url = s.optString("url", ""),
                    username = s.nullableString("username"),
                    password = s.nullableString("password"),
                    mac = s.nullableString("mac"),
                    stalkerSerialNumber = s.nullableString("stalkerSerialNumber"),
                    stalkerDeviceId = s.nullableString("stalkerDeviceId"),
                    stalkerDeviceId2 = s.nullableString("stalkerDeviceId2"),
                    stalkerSignature = s.nullableString("stalkerSignature"),
                    userAgent = s.nullableString("userAgent"),
                    epgUrl = s.nullableString("epgUrl"),
                    syncLive = s.optBoolean("syncLive", true),
                    syncMovies = s.optBoolean("syncMovies", true),
                    syncSeries = s.optBoolean("syncSeries", true),
                    hlsSupported = HlsSupport.fromCode(s.optInt("hlsSupported", HlsSupport.UNKNOWN.code)),
                    preferHls = s.optBoolean("preferHls", false),
                    livePrerollSecs = s.optInt("livePrerollSecs", -1),
                    maxConnections = s.optInt("maxConnections", 0),
                    createdAt = s.optLong("createdAt", System.currentTimeMillis()),
                    lastSyncAt = s.nullableLong("lastSyncAt"),
                ),
            )
            restoredSourceIds += id
        }

        val links = root.optJSONArray("links") ?: JSONArray()
        for (i in 0 until links.length()) {
            val link = links.optJSONObject(i) ?: continue
            val profileId = link.optLong("profileId", 0L)
            val sourceId = link.optLong("sourceId", 0L)
            if (sourceId in restoredSourceIds && profileId > 0L && profileDao.getById(profileId) != null) {
                sourceDao.link(ProfileSourceCrossRef(profileId = profileId, sourceId = sourceId))
            }
        }

        file.delete()
        Log.i(TAG, "Restored ${restoredSourceIds.size} playlist source(s) from update safety snapshot")
    }

    private fun JSONObject.putNullable(key: String, value: String?): JSONObject =
        put(key, value ?: JSONObject.NULL)

    private fun JSONObject.putNullableLong(key: String, value: Long?): JSONObject =
        put(key, value ?: JSONObject.NULL)

    private fun JSONObject.nullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

    private fun JSONObject.nullableLong(key: String): Long? =
        if (!has(key) || isNull(key)) null else optLong(key)

    /** Retries the failed phase without losing a successfully resolved release asset. */
    fun retry() {
        val failed = _state.value as? State.Failed ?: return
        val info = failed.retryInfo
        if (info != null && failed.failure !is Failure.CheckHttp && failed.failure !is Failure.NoCompatibleApk &&
            failed.failure !is Failure.InvalidReleaseResponse && failed.failure !is Failure.CheckNetwork
        ) {
            _state.value = State.Available(info)
            downloadAndInstall()
        } else {
            checkManual()
        }
    }

    fun reset() {
        if (_state.value !is State.Downloading) _state.value = State.Idle
    }

    /** Numeric segment-wise compare: "1.10.0" > "1.9.3"; non-numeric junk compares as 0. */
    private fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val l = local.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private class InstallException(cause: Throwable) : IOException(cause)

    companion object {
        private const val TAG = "UpdateManager"
        const val REPO = "boberthegr8/GreatWhiteTV-Own"
    }
}
