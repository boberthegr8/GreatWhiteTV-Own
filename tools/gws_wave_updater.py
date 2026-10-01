#!/usr/bin/env python3
"""Enable the isolated GWS Wave updater without changing GWS Online's release path.

The first CI run applies the patch to UpdateManager.kt and commits that generated source back to
`gws-wave`. Subsequent runs are idempotent. Wave reads a tiny manifest from the dedicated
`gws-wave-downloads` branch; GWS Online continues to use its existing GitHub Releases updater.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "app/src/main/java/tv/own/owntv/core/update/UpdateManager.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        if new in text:
            print(f"{label}: already applied")
            return text
        raise RuntimeError(f"{label}: expected source block not found")
    print(f"{label}: applied")
    return text.replace(old, new, 1)


def main() -> None:
    text = TARGET.read_text(encoding="utf-8")

    text = replace_once(
        text,
        "    data class UpdateInfo(val version: String, val notes: String, val apkUrl: String)",
        "    data class UpdateInfo(val version: String, val notes: String, val apkUrl: String, val versionCode: Int? = null)",
        "Wave update metadata",
    )

    dormant = '''        // GWS Wave is a separate applicationId and must never install GWS Online release APKs.\n        // Keep this updater dormant until Wave has a dedicated release feed.\n        if (context.packageName == "tv.gws.wave") {\n            _state.value = State.UpToDate\n            return\n        }'''
    routed = '''        // GWS Wave has its own package, signing identity and update feed. Never point it at\n        // GWS Online releases; the two apps remain independently installable and independently updated.\n        if (context.packageName == WAVE_PACKAGE) {\n            checkWaveManual()\n            return\n        }'''
    text = replace_once(text, dormant, routed, "Wave updater routing")

    marker = "    /** Queries GWS Online's latest release. */\n"
    sentinel = "GWS_WAVE_UPDATE_MANIFEST_URL"
    if sentinel not in text:
        wave_check = '''    /** GWS Wave-only update check against the stable manifest published by Wave CI. */\n    private fun checkWaveManual() {\n        if (_state.value is State.Checking || _state.value is State.Downloading) return\n        _state.value = State.Checking\n        scope.launch {\n            runCatching {\n                // Cache-bust because raw.githubusercontent.com may otherwise briefly serve the previous\n                // manifest immediately after CI publishes a fresh APK.\n                val request = Request.Builder()\n                    .url("$WAVE_MANIFEST_URL?t=${System.currentTimeMillis()}")\n                    .header("User-Agent", "GWSWave")\n                    .build()\n                client.newCall(request).execute().use { resp ->\n                    if (!resp.isSuccessful) throw CheckHttpException(resp.code)\n                    val body = resp.body.string()\n                    if (body.isBlank()) throw InvalidReleaseResponseException()\n                    val o = runCatching { JSONObject(body) }.getOrElse { throw InvalidReleaseResponseException() }\n                    val versionCode = o.optInt("versionCode", -1)\n                    val version = o.optString("versionName").takeIf { it.isNotBlank() }\n                        ?: throw InvalidReleaseResponseException()\n                    val apkUrl = o.optString("apkUrl").takeIf { it.startsWith("https://") }\n                        ?: throw InvalidReleaseResponseException()\n                    val notes = o.optString("notes").take(16_000)\n                    if (versionCode <= 0) throw InvalidReleaseResponseException()\n                    val info = UpdateInfo(version, notes, apkUrl, versionCode)\n                    if (versionCode > BuildConfig.VERSION_CODE) _state.value = State.Available(info)\n                    else _state.value = State.UpToDate\n                }\n            }.onFailure { error ->\n                Log.w(TAG, "GWS Wave update check failed: ${error.message}", error)\n                _state.value = State.Failed(failureFor(error, checking = true))\n            }\n        }\n    }\n\n'''
        if marker not in text:
            raise RuntimeError("GWS Online updater marker not found")
        text = text.replace(marker, wave_check + marker, 1)
        print("Wave manifest checker: applied")
    else:
        print("Wave manifest checker: already applied")

    text = replace_once(
        text,
        '''                val dir = File(context.filesDir, "updates").apply { mkdirs() }\n                val out = File(dir, "gws-online-update.apk")\n                val request = Request.Builder().url(info.apkUrl).header("User-Agent", "GWSOnline").build()''',
        '''                val dir = File(context.filesDir, "updates").apply { mkdirs() }\n                val isWave = context.packageName == WAVE_PACKAGE\n                val out = File(dir, if (isWave) "gws-wave-update.apk" else "gws-online-update.apk")\n                val request = Request.Builder()\n                    .url(info.apkUrl)\n                    .header("User-Agent", if (isWave) "GWSWave" else "GWSOnline")\n                    .build()''',
        "Wave APK download target",
    )

    text = replace_once(
        text,
        '''        private const val TAG = "UpdateManager"\n        const val REPO = "boberthegr8/GreatWhiteTV-Own"''',
        '''        private const val TAG = "UpdateManager"\n        private const val WAVE_PACKAGE = "tv.gws.wave"\n        // GWS_WAVE_UPDATE_MANIFEST_URL — deliberately separate from GWS Online GitHub Releases.\n        private const val WAVE_MANIFEST_URL =\n            "https://raw.githubusercontent.com/boberthegr8/GreatWhiteTV-Own/gws-wave-downloads/wave-update.json"\n        const val REPO = "boberthegr8/GreatWhiteTV-Own"''',
        "Wave update feed constant",
    )

    TARGET.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    main()
