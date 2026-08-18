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
import org.json.JSONObject
import tv.own.owntv.BuildConfig
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Great White Online's independent, SHA-256 verified update channel. */
class UpdateManager(
    private val context: Context,
    private val client: OkHttpClient,
) {
    data class UpdateInfo(
        val version: String,
        val notes: String,
        val apkUrl: String,
        val sha256: String = "",
    )

    sealed interface Failure {
        data class CheckHttp(val code: Int) : Failure
        data object NoCompatibleApk : Failure
        data object InvalidReleaseResponse : Failure
        data object CheckNetwork : Failure
        data class DownloadHttp(val code: Int) : Failure
        data object EmptyDownload : Failure
        data object DigestMismatch : Failure
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
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private class CheckHttpException(val code: Int) : IOException()
    private class DownloadHttpException(val code: Int) : IOException()
    private class InvalidReleaseResponseException : IOException()
    private class EmptyDownloadException : IOException()
    private class DigestMismatchException : IOException()
    private class InstallException(cause: Throwable) : IOException(cause)

    private fun failureFor(error: Throwable, checking: Boolean): Failure = when (error) {
        is CheckHttpException -> Failure.CheckHttp(error.code)
        is DownloadHttpException -> Failure.DownloadHttp(error.code)
        is InvalidReleaseResponseException -> Failure.InvalidReleaseResponse
        is EmptyDownloadException -> Failure.EmptyDownload
        is DigestMismatchException -> Failure.DigestMismatch
        else -> if (checking) Failure.CheckNetwork else Failure.DownloadNetwork
    }

    val currentVersion: String = BuildConfig.VERSION_NAME

    /** Reads only the Online feed, never the Great White Streams TV release channel. */
    fun check() {
        if (_state.value is State.Checking || _state.value is State.Downloading) return
        _state.value = State.Checking
        scope.launch {
            runCatching {
                val request = Request.Builder()
                    .url(FEED_URL)
                    .header("Accept", "application/json")
                    .header("User-Agent", USER_AGENT)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw CheckHttpException(response.code)
                    val raw = response.body.string()
                    if (raw.isBlank()) throw InvalidReleaseResponseException()
                    val json = runCatching { JSONObject(raw) }.getOrElse { throw InvalidReleaseResponseException() }
                    val version = json.optString("version").takeIf { it.isNotBlank() }
                        ?: throw InvalidReleaseResponseException()
                    val x86 = android.os.Build.SUPPORTED_ABIS.firstOrNull() == "x86_64"
                    val apkUrl = json.optString(if (x86) "x86ApkUrl" else "apkUrl").takeIf { it.isNotBlank() }
                        ?: throw InvalidReleaseResponseException()
                    val sha256 = json.optString(if (x86) "x86Sha256" else "sha256").takeIf { it.isNotBlank() }
                        ?: throw InvalidReleaseResponseException()
                    val info = UpdateInfo(
                        version = version,
                        notes = json.optString("notes").take(16_000),
                        apkUrl = apkUrl,
                        sha256 = sha256.removePrefix("sha256:").lowercase(),
                    )
                    _state.value = if (isNewer(version, currentVersion)) State.Available(info) else State.UpToDate
                }
            }.onFailure { error ->
                Log.w(TAG, "online update check failed: ${error.message}", error)
                _state.value = State.Failed(failureFor(error, checking = true))
            }
        }
    }

    fun downloadAndInstall() {
        val info = (_state.value as? State.Available)?.info ?: return
        _state.value = State.Downloading(0)
        scope.launch {
            runCatching {
                val dir = File(context.filesDir, "updates").apply { mkdirs() }
                val out = File(dir, "greatwhite-online-update.apk")
                val request = Request.Builder().url(info.apkUrl).header("User-Agent", USER_AGENT).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw DownloadHttpException(response.code)
                    val body = response.body
                    val total = body.contentLength()
                    var copied = 0L
                    body.byteStream().use { input ->
                        out.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                copied += count
                                if (total > 0) _state.value = State.Downloading((copied * 100 / total).toInt())
                            }
                        }
                    }
                    if (copied == 0L) throw EmptyDownloadException()
                }
                if (!digestMatches(out, info.sha256)) {
                    out.delete()
                    throw DigestMismatchException()
                }
                runCatching { install(out) }.getOrElse { throw InstallException(it) }
                _state.value = State.Available(info)
            }.onFailure { error ->
                Log.w(TAG, "online update download failed: ${error.message}", error)
                val failure = if (error is InstallException) Failure.Install else failureFor(error, checking = false)
                _state.value = State.Failed(failure, retryInfo = info)
            }
        }
    }

    private fun digestMatches(file: File, expected: String): Boolean {
        if (expected.length != 64) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return actual.equals(expected, ignoreCase = true)
    }

    private fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun retry() {
        val failed = _state.value as? State.Failed ?: return
        val info = failed.retryInfo
        if (info != null && failed.failure !is Failure.CheckHttp &&
            failed.failure !is Failure.InvalidReleaseResponse && failed.failure !is Failure.CheckNetwork
        ) {
            _state.value = State.Available(info)
            downloadAndInstall()
        } else {
            check()
        }
    }

    fun reset() {
        if (_state.value !is State.Downloading) _state.value = State.Idle
    }

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

    companion object {
        private const val TAG = "GreatWhiteOnlineUpdate"
        private const val USER_AGENT = "GreatWhiteOnline"
        private const val FEED_URL =
            "https://raw.githubusercontent.com/boberthegr8/GreatWhiteTV-Own/greatwhite-online/auto/online-update.json"
    }
}
