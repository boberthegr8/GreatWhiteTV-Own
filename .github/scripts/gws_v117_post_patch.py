from pathlib import Path
import subprocess


def replace_once(path: str, old: str, new: str, label: str) -> None:
    f = Path(path)
    text = f.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    f.write_text(text.replace(old, new, 1), encoding="utf-8")


# PlayerHud's KeyEvent exposes nativeKeyEvent as a member in this Compose version; the extension import
# used by newer Compose builds does not exist here. Remove only that bad import and keep the member access.
replace_once(
    "app/src/main/java/tv/own/owntv/player/PlayerHud.kt",
    "import androidx.compose.ui.input.key.nativeKeyEvent\n",
    "",
    "PlayerHud unsupported nativeKeyEvent import",
)

# GUIDE is a physical remote key: consume only KeyDown so one press cannot toggle twice.
replace_once(
    "app/src/main/java/tv/own/owntv/player/PlayerHud.kt",
    "onOpenGuide != null && e.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_GUIDE -> {",
    "onOpenGuide != null && e.type == KeyEventType.KeyDown && "
    "e.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_GUIDE -> {",
    "GUIDE key-down guard",
)

# v1.0.17 changes how Xtream episode URLs are parsed. Existing installs can have episode rows created by
# the older parser, and the normal six-hour cache would otherwise keep those stale rows after upgrading.
# Force exactly one post-upgrade refresh for Xtream shows whose episode cache predates this build epoch.
series_repo_path = "app/src/main/java/tv/own/owntv/core/repository/SeriesRepository.kt"
replace_once(
    series_repo_path,
    "        if (!shouldRefreshEpisodes(cachedCount, syncedAt, System.currentTimeMillis())) return@withContext true\n",
    "        val needsV117XtreamRepair = source.type == SourceType.XTREAM && hasCache &&\n"
    "            syncedAt in 1 until V117_SERIES_REPAIR_EPOCH_MS\n"
    "        if (!needsV117XtreamRepair &&\n"
    "            !shouldRefreshEpisodes(cachedCount, syncedAt, System.currentTimeMillis())\n"
    "        ) return@withContext true\n",
    "Series v1.0.17 one-time repair gate",
)
replace_once(
    series_repo_path,
    "        /** Safety cap on season paging (a show never has hundreds of season pages). */\n",
    "        // 2026-08-25 05:30 UTC. Older Xtream episode caches get rebuilt once after v1.0.17.\n"
    "        const val V117_SERIES_REPAIR_EPOCH_MS = 1_787_635_800_000L\n\n"
    "        /** Safety cap on season paging (a show never has hundreds of season pages). */\n",
    "Series v1.0.17 repair epoch",
)

checker = ["python", "tools/i18n/check_hardcoded_strings.py"]
subprocess.run(checker + ["prune-safe"], check=True)


def classify(path: str, text: str, category: str) -> None:
    subprocess.run(
        checker
        + [
            "classify-safe",
            "--path",
            path,
            "--text",
            text,
            "--category",
            category,
        ],
        check=True,
    )


xt = "app/src/main/java/tv/own/owntv/core/parser/XtreamClient.kt"
for key in [
    "container_ext",
    "container_extension",
    "direct_source",
    "extension",
    "info",
    "stream_url",
]:
    classify(xt, key, "json")
classify(
    xt,
    '${base(s)}/movie/${s.username}/${s.password}/$streamId.${normalizedStreamExt(ext)}',
    "url",
)
classify(
    xt,
    '${base(s)}/series/${s.username}/${s.password}/$episodeId.${normalizedStreamExt(ext)}',
    "url",
)
classify(xt, "mp4", "protocol")

classify(series_repo_path, "http://", "url")
classify(series_repo_path, "https://", "url")

live = "app/src/main/java/tv/own/owntv/features/live/LiveViewModel.kt"
classify(
    live,
    "(^|[^A-Z0-9])(4K|UHD|2160P?|HEVC|H[.]?265)([^A-Z0-9]|$)",
    "regex",
)
classify(live, "mpv (UHD fast path)", "log")
