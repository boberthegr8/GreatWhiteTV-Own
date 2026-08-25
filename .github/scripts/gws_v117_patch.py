from pathlib import Path
import re


def p(path: str) -> Path:
    return Path(path)


def replace_once(path: str, old: str, new: str, label: str) -> None:
    f = p(path)
    text = f.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    f.write_text(text.replace(old, new, 1), encoding="utf-8")


# Player HUD: physical GUIDE remote key opens the in-player Guide drawer.
hud = "app/src/main/java/tv/own/owntv/player/PlayerHud.kt"
replace_once(
    hud,
    "import androidx.compose.ui.input.key.onPreviewKeyEvent\n",
    "import androidx.compose.ui.input.key.onPreviewKeyEvent\nimport androidx.compose.ui.input.key.nativeKeyEvent\n",
    "PlayerHud nativeKeyEvent import",
)
replace_once(
    hud,
    "    onOpenHistoryList: (() -> Unit)? = null,\n    // Live rewind / timeshift",
    "    onOpenHistoryList: (() -> Unit)? = null,\n"
    "    // Live: physical GUIDE key opens the sliding EPG over the playing channel.\n"
    "    onOpenGuide: (() -> Unit)? = null,\n"
    "    // Live rewind / timeshift",
    "PlayerHud guide callback",
)
replace_once(
    hud,
    "                // The category list lives at logical Start; history lives at logical End.\n"
    "                onOpenChannelList != null && !controlsVisible &&",
    "                // GUIDE is a dedicated appliance-style key and works whether the HUD is visible or hidden.\n"
    "                onOpenGuide != null && e.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_GUIDE -> {\n"
    "                    onOpenGuide(); true\n"
    "                }\n"
    "                // The category list lives at logical Start; history lives at logical End.\n"
    "                onOpenChannelList != null && !controlsVisible &&",
    "PlayerHud guide key handling",
)

# Shell: add a true sliding Guide layer over live video.
shell = "app/src/main/java/tv/own/owntv/features/shell/OwnTVShell.kt"
replace_once(
    shell,
    "    var showHistoryList by remember { mutableStateOf(false) }\n",
    "    var showHistoryList by remember { mutableStateOf(false) }\n"
    "    var showGuideOverlay by remember { mutableStateOf(false) }\n",
    "Shell guide state",
)
replace_once(
    shell,
    "                    inert = showChannelList || showHistoryList || showCategoryBrowser || showSubtitleSearch || showLocalSubPicker,",
    "                    inert = showChannelList || showHistoryList || showGuideOverlay || showCategoryBrowser || showSubtitleSearch || showLocalSubPicker,",
    "Shell HUD inert state",
)
replace_once(
    shell,
    "                    onOpenHistoryList = if (isTunedLive) { { showHistoryList = true } } else null,\n"
    "                    onRewindLive =",
    "                    onOpenHistoryList = if (isTunedLive) { { showHistoryList = true } } else null,\n"
    "                    onOpenGuide = if (isTunedLive) {\n"
    "                        {\n"
    "                            showChannelList = false\n"
    "                            showHistoryList = false\n"
    "                            liveVm.hideCategoryBrowser()\n"
    "                            showGuideOverlay = true\n"
    "                        }\n"
    "                    } else null,\n"
    "                    onRewindLive =",
    "Shell guide callback",
)
guide_overlay = """                // GUIDE — real EPG grid in the same sliding family as categories/channels, while video keeps playing.
                if (showGuideOverlay && isLiveChannel) {
                    tv.own.owntv.features.shell.components.GuideDrawerOverlay(
                        onDismiss = { showGuideOverlay = false },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        EpgScreen(
                            onBack = { showGuideOverlay = false },
                            onFullscreen = {},
                            onPlayChannel = { ch, _ ->
                                showGuideOverlay = false
                                restoreFocus = false
                                liveVm.watchFromGuide(ch)
                                zapSource = MainSection.LIVE_TV
                                homeVm.stopPreview()
                            },
                            onPlayCatchup = { ch, prog ->
                                showGuideOverlay = false
                                restoreFocus = false
                                liveVm.playCatchupProgramme(ch, prog)
                                zapSource = MainSection.LIVE_TV
                                homeVm.stopPreview()
                            },
                            onAddEpg = {
                                showGuideOverlay = false
                                playerMode = PlayerMode.MINI
                                openEpgAdd = true
                                onSelectSection(MainSection.SETTINGS)
                            },
                            restoreFocus = false,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
"""
replace_once(
    shell,
    "                // Right — recently watched, to hop straight back to the previous channel.\n",
    guide_overlay + "                // Right — recently watched, to hop straight back to the previous channel.\n",
    "Shell Guide overlay insertion",
)

# Live 4K/UHD: take the engine path already optimized for hardware-direct 4K/HDR.
live = "app/src/main/java/tv/own/owntv/features/live/LiveViewModel.kt"
replace_once(
    live,
    "    private suspend fun playChannel(channel: ChannelEntity) {\n",
    """    private val uhdNameRx = Regex(
        "(^|[^A-Z0-9])(4K|UHD|2160P?|HEVC|H[.]?265)([^A-Z0-9]|$)",
        RegexOption.IGNORE_CASE,
    )

    private fun likelyUhd(channel: ChannelEntity): Boolean = uhdNameRx.containsMatchIn(channel.name)

    private suspend fun playChannel(channel: ChannelEntity) {
""",
    "Live UHD helper",
)
replace_once(
    live,
    "        val drmProtected = channel.drmConfig != null\n"
    "        val onMpv = if (drmProtected) false else pin ?: (refusing || setting.startsOnMpv)\n",
    "        val drmProtected = channel.drmConfig != null\n"
    "        // Tagged UHD/4K/HEVC channels go straight to mpv's MediaCodec direct path instead of\n"
    "        // spending the first open on ExoPlayer. Explicit pins/EXO_ONLY and DRM still win.\n"
    "        val uhdFastPath = pin == null && !drmProtected && setting.allowsHandover && likelyUhd(channel)\n"
    "        val onMpv = if (drmProtected) false else pin ?: (uhdFastPath || refusing || setting.startsOnMpv)\n",
    "Live UHD routing",
)
replace_once(
    live,
    "            pin != null -> \"${if (onMpv) \"mpv\" else \"exoplayer\"} (pinned)\"\n"
    "            refusing -> \"mpv (panel refuses segments)\"",
    "            pin != null -> \"${if (onMpv) \"mpv\" else \"exoplayer\"} (pinned)\"\n"
    "            uhdFastPath -> \"mpv (UHD fast path)\"\n"
    "            refusing -> \"mpv (panel refuses segments)\"",
    "Live UHD reason",
)

# Xtream Series: tolerate panel-specific episode shapes and extension formats.
xt = "app/src/main/java/tv/own/owntv/core/parser/XtreamClient.kt"
replace_once(
    xt,
    "data class XtEpisode(\n"
    "    val id: String, val seasonNumber: Int, val episodeNumber: Int, val title: String, val containerExt: String?,\n"
    ")",
    "data class XtEpisode(\n"
    "    val id: String, val seasonNumber: Int, val episodeNumber: Int, val title: String, val containerExt: String?,\n"
    "    val directSource: String? = null,\n"
    ")",
    "XtEpisode direct source",
)
old_episode = """    private fun readEpisode(reader: JsonReader, out: MutableList<XtEpisode>, seasonFallback: Int) {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) { reader.skipValue(); return }
        var id: String? = null
        var epNum = 0
        var title = ""
        var ext: String? = null
        var season = seasonFallback
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.nextStringOrNull()
                "episode_num" -> epNum = reader.nextIntOrNull() ?: epNum
                "title" -> title = reader.nextStringOrNull() ?: title
                "container_extension" -> ext = reader.nextStringOrNull()
                "season" -> reader.nextIntOrNull()?.let { if (it > 0) season = it }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        // Keep a missing provider title empty. The Compose episode renderer supplies a localized
        // episode-number fallback; storing English here would freeze the device language in the DB.
        id?.let { out.add(XtEpisode(it, season, epNum, title.trim(), ext)) }
    }
"""
new_episode = """    private fun readEpisode(reader: JsonReader, out: MutableList<XtEpisode>, seasonFallback: Int) {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) { reader.skipValue(); return }
        var id: String? = null
        var epNum = 0
        var title = ""
        var ext: String? = null
        var directSource: String? = null
        var season = seasonFallback

        fun readInfoObject() {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) { reader.skipValue(); return }
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "container_extension", "container_ext", "extension" ->
                        reader.nextStringOrNull()?.trim()?.trimStart('.')?.takeIf { it.isNotBlank() }?.let { ext = it }
                    "direct_source", "stream_url" ->
                        reader.nextStringOrNull()?.trim()?.takeIf { it.isNotBlank() }?.let { directSource = it }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        }

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.nextStringOrNull()
                "episode_num" -> epNum = reader.nextIntOrNull() ?: epNum
                "title" -> title = reader.nextStringOrNull() ?: title
                "container_extension", "container_ext", "extension" ->
                    ext = reader.nextStringOrNull()?.trim()?.trimStart('.')?.takeIf { it.isNotBlank() }
                "direct_source", "stream_url" ->
                    directSource = reader.nextStringOrNull()?.trim()?.takeIf { it.isNotBlank() }
                "season" -> reader.nextIntOrNull()?.let { if (it > 0) season = it }
                "info" -> readInfoObject()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        // Keep a missing provider title empty. The Compose episode renderer supplies a localized
        // episode-number fallback; storing English here would freeze the device language in the DB.
        id?.let { out.add(XtEpisode(it, season, epNum, title.trim(), ext, directSource)) }
    }
"""
replace_once(xt, old_episode, new_episode, "Xtream episode parser")
replace_once(
    xt,
    """    fun movieUrl(s: SourceEntity, streamId: String, ext: String?) =
        "${base(s)}/movie/${s.username}/${s.password}/$streamId.${ext ?: "mp4"}"
    fun seriesEpisodeUrl(s: SourceEntity, episodeId: String, ext: String?) =
        "${base(s)}/series/${s.username}/${s.password}/$episodeId.${ext ?: "mp4"}"
""",
    """    private fun normalizedStreamExt(ext: String?): String =
        ext?.trim()?.trimStart('.')?.takeIf { it.isNotBlank() } ?: "mp4"

    fun movieUrl(s: SourceEntity, streamId: String, ext: String?) =
        "${base(s)}/movie/${s.username}/${s.password}/$streamId.${normalizedStreamExt(ext)}"
    fun seriesEpisodeUrl(s: SourceEntity, episodeId: String, ext: String?) =
        "${base(s)}/series/${s.username}/${s.password}/$episodeId.${normalizedStreamExt(ext)}"
""",
    "Xtream URL extension normalization",
)

series_repo = "app/src/main/java/tv/own/owntv/core/repository/SeriesRepository.kt"
replace_once(
    series_repo,
    "                streamUrl = xtream.seriesEpisodeUrl(source, e.id, e.containerExt),\n",
    "                streamUrl = e.directSource?.takeIf { it.startsWith(\"http://\") || it.startsWith(\"https://\") }\n"
    "                    ?: xtream.seriesEpisodeUrl(source, e.id, e.containerExt),\n",
    "Series direct source",
)

# TV polish / speed: lighter video dimming, larger logos, stronger labels, no crossfade tax.
for overlay in [
    "app/src/main/java/tv/own/owntv/features/shell/components/ChannelListOverlay.kt",
    "app/src/main/java/tv/own/owntv/features/shell/components/CategoryBrowserOverlay.kt",
]:
    replace_once(
        overlay,
        ".modalScrim(strength = 0.58f)",
        ".modalScrim(strength = 0.34f)",
        f"{overlay} scrim",
    )

channel_overlay = "app/src/main/java/tv/own/owntv/features/shell/components/ChannelListOverlay.kt"
replace_once(
    channel_overlay,
    ".size(40.dp)\n                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))",
    ".size(48.dp)\n                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))",
    "Channel logo size",
)
replace_once(
    channel_overlay,
    "modifier = Modifier.size(20.dp)",
    "modifier = Modifier.size(22.dp)",
    "Channel fallback icon size",
)
replace_once(
    channel_overlay,
    "style = MaterialTheme.typography.bodyMedium,\n                    color = when {",
    "style = MaterialTheme.typography.bodyLarge,\n                    color = when {",
    "Channel title typography",
)

app = "app/src/main/java/tv/own/owntv/OwnTVApp.kt"
replace_once(app, "            .crossfade(true)\n", "            .crossfade(false)\n", "Disable grid image crossfade")

# Startup surface should match the GWS navy shell, not the old green undertone.
colors = p("app/src/main/res/values/colors.xml")
if colors.exists():
    text = colors.read_text(encoding="utf-8")
    text2, count = re.subn(
        r'<color name="owntv_window_bg">#[0-9A-Fa-f]{6,8}</color>',
        '<color name="owntv_window_bg">#050B12</color>',
        text,
        count=1,
    )
    if count != 1:
        raise SystemExit(f"window background color: expected one match, found {count}")
    colors.write_text(text2, encoding="utf-8")
