from pathlib import Path
import subprocess


def replace_once(path: str, old: str, new: str, label: str) -> None:
    f = Path(path)
    text = f.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    f.write_text(text.replace(old, new, 1), encoding="utf-8")


# GUIDE is a physical remote key: consume only KeyDown so one press cannot toggle twice.
replace_once(
    "app/src/main/java/tv/own/owntv/player/PlayerHud.kt",
    "onOpenGuide != null && e.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_GUIDE -> {",
    "onOpenGuide != null && e.type == KeyEventType.KeyDown && "
    "e.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_GUIDE -> {",
    "GUIDE key-down guard",
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

series_repo = "app/src/main/java/tv/own/owntv/core/repository/SeriesRepository.kt"
classify(series_repo, "http://", "url")
classify(series_repo, "https://", "url")

live = "app/src/main/java/tv/own/owntv/features/live/LiveViewModel.kt"
classify(
    live,
    "(^|[^A-Z0-9])(4K|UHD|2160P?|HEVC|H[.]?265)([^A-Z0-9]|$)",
    "regex",
)
classify(live, "mpv (UHD fast path)", "log")
