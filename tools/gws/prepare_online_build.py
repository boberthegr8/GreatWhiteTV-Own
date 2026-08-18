#!/usr/bin/env python3
"""Build-time transform for the standalone Great White Online app.

Starts with the proven Great White Streams TV transform so IPTV/provider behavior stays identical,
then gives Online its own Android identity and wires the Online destination into the upstream shell.
The Kotlin namespace remains upstream OwnTV to keep future merges manageable.
"""
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[2]


def replace_once(path: str, old: str, new: str) -> None:
    p = ROOT / path
    text = p.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one match in {path!r}, found {count}: {old!r}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")


# Retain every Great White IPTV customization first: HUSH/CCTV QR presets, editable DNS/server,
# Fire TV launcher support, Great White colors and the stable package-separation strategy.
subprocess.run([sys.executable, str(ROOT / "tools/gws/prepare_build.py")], cwd=ROOT, check=True)

# --- Standalone app identity ---------------------------------------------------------------
replace_once(
    "app/build.gradle.kts",
    'applicationId = "com.greatwhitestreams.tv"',
    'applicationId = "com.greatwhitestreams.online"',
)
replace_once(
    "app/src/main/AndroidManifest.xml",
    'android:label="Great White Streams TV"',
    'android:label="Great White Online"',
)
# Avoid Android asking which Great White app should receive OwnTV deep links when both are installed.
replace_once(
    "app/src/main/AndroidManifest.xml",
    'android:scheme="owntv" />\n                <data\n                    android:host="open"\n                    android:scheme="owntv" />',
    'android:scheme="greatwhiteonline" />\n                <data\n                    android:host="open"\n                    android:scheme="greatwhiteonline" />',
)

# --- Navigation model ----------------------------------------------------------------------
shell_vm = "app/src/main/java/tv/own/owntv/features/shell/ShellViewModel.kt"
replace_once(
    shell_vm,
    '    SERIES(tv.own.owntv.R.string.common_nav_series),\n    DOWNLOADS(tv.own.owntv.R.string.common_nav_downloads),',
    '    SERIES(tv.own.owntv.R.string.common_nav_series),\n    ONLINE(tv.own.owntv.R.string.common_nav_online),\n    DOWNLOADS(tv.own.owntv.R.string.common_nav_downloads),',
)
replace_once(
    shell_vm,
    'val browseOrder: List<MainSection> = listOf(HOME, LIVE_TV, MOVIES, SERIES, DOWNLOADS, EPG)',
    'val browseOrder: List<MainSection> = listOf(HOME, LIVE_TV, MOVIES, SERIES, ONLINE, DOWNLOADS, EPG)',
)
replace_once(
    shell_vm,
    '            add(HOME)\n            if (hasLive)',
    '            add(HOME)\n            add(ONLINE)\n            if (hasLive)',
)

# --- Main shell destination ----------------------------------------------------------------
shell = "app/src/main/java/tv/own/owntv/features/shell/OwnTVShell.kt"
replace_once(
    shell,
    'import tv.own.owntv.features.movies.MovieViewModel\n',
    'import tv.own.owntv.features.movies.MovieViewModel\nimport tv.own.owntv.features.online.OnlineScreen\n',
)
replace_once(
    shell,
    '''                        selectedSection == MainSection.DOWNLOADS -> DownloadsScreen(
                            onFullscreen = { openFullscreen() },''',
    '''                        selectedSection == MainSection.ONLINE -> OnlineScreen(
                            onPlayDirect = { item, episode, url ->
                                val displayTitle = episode?.let { "${item.name} · ${it.title}" } ?: item.name
                                player.play(
                                    url,
                                    title = displayTitle,
                                    year = item.year,
                                    isLive = false,
                                    contentKey = "ONLINE:${item.type}:${episode?.id ?: item.id}",
                                )
                                openFullscreen(MainSection.ONLINE)
                            },
                            onChildFocused = { focusedLayer = ShellLayer.CONTENT },
                            modifier = Modifier.fillMaxSize(),
                        )

                        selectedSection == MainSection.DOWNLOADS -> DownloadsScreen(
                            onFullscreen = { openFullscreen() },''',
)
replace_once(
    shell,
    '        MainSection.SERIES -> OwnTVIcon.SERIES\n        MainSection.DOWNLOADS -> OwnTVIcon.DOWNLOADS',
    '        MainSection.SERIES -> OwnTVIcon.SERIES\n        MainSection.ONLINE -> OwnTVIcon.MOVIES\n        MainSection.DOWNLOADS -> OwnTVIcon.DOWNLOADS',
)
replace_once(
    shell,
    '    MainSection.EPG -> emptyList()\n    MainSection.LIVE_TV -> listOf(',
    '    MainSection.EPG -> emptyList()\n    MainSection.ONLINE -> emptyList()\n    MainSection.LIVE_TV -> listOf(',
)
replace_once(
    shell,
    '    MainSection.SEARCH, MainSection.HOME, MainSection.EPG, MainSection.SETTINGS -> ""',
    '    MainSection.SEARCH, MainSection.HOME, MainSection.ONLINE, MainSection.EPG, MainSection.SETTINGS -> ""',
)

# Sidebar owns another exhaustive icon mapping. Reuse the movie-library glyph for the first Online
# build; branding can get a bespoke globe/stream icon without changing navigation semantics later.
replace_once(
    "app/src/main/java/tv/own/owntv/features/shell/components/Sidebar.kt",
    '        MainSection.SERIES -> OwnTVIcon.SERIES\n        MainSection.DOWNLOADS -> OwnTVIcon.DOWNLOADS',
    '        MainSection.SERIES -> OwnTVIcon.SERIES\n        MainSection.ONLINE -> OwnTVIcon.MOVIES\n        MainSection.DOWNLOADS -> OwnTVIcon.DOWNLOADS',
)
replace_once(
    "app/src/main/java/tv/own/owntv/ui/components/NavDuotoneIcon.kt",
    '            MainSection.MOVIES -> {',
    '            MainSection.MOVIES, MainSection.ONLINE -> {',
)

print("Great White Online build customizations applied:")
print("  applicationId: com.greatwhitestreams.online")
print("  app name: Great White Online")
print("  deep link scheme: greatwhiteonline://")
print("  IPTV features: preserved from Great White Streams TV")
print("  Online destination: enabled")
print("  Online search + episodes: enabled")
print("  Online source manager: enabled")
print("  Direct HTTP playback: OwnTV player")
