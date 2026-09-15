#!/usr/bin/env python3
"""Apply the GWS Wave-only Live TV browser and EPG fallback changes.

This script is intentionally idempotent. It exists so the isolated Wave CI can keep the
large inherited Kotlin files patched without touching main / GWS Online.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SHELL = ROOT / "app/src/main/java/tv/own/owntv/features/shell/OwnTVShell.kt"
EPG_VM = ROOT / "app/src/main/java/tv/own/owntv/features/epg/EpgViewModel.kt"
WAVE_BROWSER = ROOT / "app/src/main/java/tv/own/owntv/features/shell/components/WaveLiveBrowseOverlay.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        if new in text:
            print(f"{label}: already applied")
            return text
        raise RuntimeError(f"{label}: expected source block not found")
    return text.replace(old, new, 1)


def patch_wave_browser() -> None:
    text = WAVE_BROWSER.read_text(encoding="utf-8")
    if "import androidx.compose.foundation.layout.weight" not in text:
        text = text.replace(
            "import androidx.compose.foundation.layout.width\n",
            "import androidx.compose.foundation.layout.width\nimport androidx.compose.foundation.layout.weight\n",
            1,
        )
    text = text.replace(".fillMaxWidth(0.88f)", ".fillMaxWidth()", 1)
    WAVE_BROWSER.write_text(text, encoding="utf-8")
    print("WaveLiveBrowseOverlay: full-screen layout ready")


def patch_shell() -> None:
    text = SHELL.read_text(encoding="utf-8")

    text = replace_once(
        text,
        "onOpenChannelList = if (isTunedLive && liveCanZap) { { showChannelList = true } } else null,",
        "onOpenChannelList = if (isTunedLive) { { showChannelList = true } } else null,",
        "Live browser opener",
    )

    start_marker = "                // Left — the playing channel's own provider category.\n"
    end_marker = "                // GUIDE — real EPG grid in the same sliding family as categories/channels, while video keeps playing.\n"
    sentinel = "GWS_WAVE_FULL_HEIGHT_LIVE_BROWSER"

    if sentinel not in text:
        start = text.find(start_marker)
        end = text.find(end_marker, start)
        if start < 0 or end < 0:
            raise RuntimeError("Fullscreen Live drawer block not found")
        new_block = '''                // GWS_WAVE_FULL_HEIGHT_LIVE_BROWSER
                // One slide-in Live TV browser: categories stay visible on the left while the selected
                // category's channels stay beside them. No second-Left mini category menu.
                if (showChannelList && isLiveChannel) {
                    tv.own.owntv.features.shell.components.WaveLiveBrowseOverlay(
                        categories = browserCategories,
                        currentCategoryId = previewChannel?.categoryId,
                        channels = zapChannels,
                        currentId = previewChannel?.id,
                        nowPlaying = overlayNowPlaying,
                        title = zapOverlayTitle,
                        showNumbers = directTuneEnabled,
                        onSelectCategory = { catId -> liveVm.loadChannelsForCategory(catId) },
                        onSelectChannel = { channel -> liveVm.ensurePlaying(channel) },
                        onDismiss = {
                            showChannelList = false
                            liveVm.hideCategoryBrowser()
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
'''
        text = text[:start] + new_block + text[end:]
        print("OwnTVShell: replaced two-stage Live drawer with Wave full-screen browser")
    else:
        print("OwnTVShell: full-screen browser already applied")

    SHELL.write_text(text, encoding="utf-8")


def patch_epg() -> None:
    text = EPG_VM.read_text(encoding="utf-8")

    old_query = "            val rawChannels = channelDao.channelsWithGuide(ids, q, MAX_CHANNELS)"
    new_query = '''            // GWS_WAVE_SHOW_PROVIDER_CHANNELS_WITHOUT_EPG
            // The provider lineup is authoritative. Guide data enriches rows when ids match, but a
            // missing/mismatched EPG id must never make a real provider channel disappear.
            val rawChannels = channelDao.allForSources(playlistIds, MAX_CHANNELS).let { all ->
                if (q.isBlank()) all else all.filter { it.name.contains(q, ignoreCase = true) }
            }'''
    text = replace_once(text, old_query, new_query, "Guide provider-channel fallback")

    old_message = '''            val message = when {
                stored == 0 -> null // handled by the "No EPG added" prompt (hasEpgSources=false)
                channels.isEmpty() && q.isNotBlank() -> EpgMessage.NoChannelsForQuery(q)
                channels.isEmpty() -> EpgMessage.MismatchedIds
                else -> null
            }'''
    new_message = '''            val message = when {
                channels.isEmpty() && q.isNotBlank() -> EpgMessage.NoChannelsForQuery(q)
                else -> null
            }'''
    text = replace_once(text, old_message, new_message, "Guide mismatch warning removal")

    text = text.replace(
        "/** All channels with guide data in the window; each row loads its own programmes lazily. */",
        "/** Provider channels in the guide; rows without matching EPG remain tunable and simply have no programmes. */",
        1,
    )

    EPG_VM.write_text(text, encoding="utf-8")
    print("EpgViewModel: provider channels remain visible without matching guide ids")


def main() -> None:
    patch_wave_browser()
    patch_shell()
    patch_epg()


if __name__ == "__main__":
    main()
