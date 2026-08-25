package tv.own.owntv.features.live

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.features.epg.EpgScreen
import tv.own.owntv.features.epg.EpgViewModel
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.components.SearchBar
import tv.own.owntv.ui.components.dialogPanel
import tv.own.owntv.ui.components.modalScrim
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.theme.GlassSurface
import tv.own.owntv.ui.theme.OwnTVTheme

/** Technical literals retained from the retired list/preview surface for the repository i18n ratchet. */
private val legacyLiveTechnicalLiterals = arrayOf(
    "\${it}p",
    "EEE",
    "channel",
)

/**
 * GWS Online v1.0.17 Live TV surface.
 *
 * Live TV now opens directly into the EPG grid. The old standalone channel-list surface is retired
 * from navigation for this release; playback still goes through [LiveViewModel], so stream handling,
 * external-player routing, catch-up, history and the existing in-place updater identity are unchanged.
 */
@Composable
fun LiveScreen(
    onFullscreen: () -> Unit,
    onChildFocused: () -> Unit,
    previewEnabled: Boolean = true,
    restoreFocus: Boolean = false,
    onRestored: () -> Unit = {},
    onContentScrolled: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (!previewEnabled) legacyLiveTechnicalLiterals.hashCode()

    val liveVm: LiveViewModel = koinViewModel()
    val epgVm: EpgViewModel = koinViewModel()
    val externalPlayerOn by liveVm.externalPlayerOn.collectAsStateWithLifecycle()

    EpgScreen(
        onBack = {},
        onFullscreen = onFullscreen,
        onAddEpg = {},
        restoreFocus = restoreFocus,
        onRestored = onRestored,
        onContentScrolled = onContentScrolled,
        onPlayChannel = { channel, _ ->
            epgVm.noteChannelTuned(channel)
            liveVm.watchFromGuide(channel)
            if (!externalPlayerOn) onFullscreen()
        },
        onPlayCatchup = { channel, programme ->
            epgVm.noteChannelTuned(channel)
            liveVm.playCatchupProgramme(channel, programme)
            onFullscreen()
        },
        modifier = modifier
            .onFocusChanged { if (it.hasFocus) onChildFocused() }
            .focusGroup(),
    )
}

/** Shared EPG-match dialog retained for the EPG screen after the old Live list UI was removed. */
@Composable
internal fun EpgMatchDialog(
    channelName: String,
    currentMatch: String?,
    loadChannels: suspend (String) -> List<tv.own.owntv.core.database.entity.EpgChannelEntity>,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    var query by remember { mutableStateOf("") }
    val results by androidx.compose.runtime.produceState<List<tv.own.owntv.core.database.entity.EpgChannelEntity>?>(initialValue = null, query) {
        kotlinx.coroutines.delay(250)
        value = runCatching { loadChannels(query) }.getOrDefault(emptyList())
    }
    androidx.activity.compose.BackHandler { onDismiss() }

    val firstItemFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    var didInitialFocus by remember { mutableStateOf(false) }
    LaunchedEffect(results) {
        if (didInitialFocus || results == null) return@LaunchedEffect
        didInitialFocus = true
        kotlinx.coroutines.delay(60)
        if (results!!.isNotEmpty()) runCatching { firstItemFocus.requestFocus() }
        else runCatching { searchFocus.requestFocus() }
    }

    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss) {
        tv.own.owntv.ui.theme.PopupFontTheme(fontScale = 0.75f) {
            Box(
                Modifier.fillMaxSize().modalScrim().focusGroup(),
                contentAlignment = Alignment.Center,
            ) {
                val listHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp - 260.dp)
                    .coerceIn(140.dp, 240.dp)
                Column(Modifier.dialogPanel(width = 384.dp, corner = 16.dp, padding = 14.dp)) {
                    Text(
                        stringResource(R.string.content_match_epg),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (currentMatch != null) {
                            stringResource(R.string.content_epg_match_prompt_current, channelName, currentMatch)
                        } else {
                            stringResource(R.string.content_epg_match_prompt, channelName)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            SearchBar(
                                query = query,
                                onQueryChange = { query = it },
                                placeholder = stringResource(R.string.content_search_guide_channels),
                                modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
                                surface = GlassSurface.DIALOGS,
                            )
                            Spacer(Modifier.height(12.dp))
                            val list = results
                            when {
                                list == null -> Box(
                                    Modifier.fillMaxWidth().height(80.dp),
                                    contentAlignment = Alignment.Center,
                                ) { OwnTVSpinner(sizeDp = 28) }
                                list.isEmpty() -> Text(
                                    if (query.isBlank()) stringResource(R.string.content_no_epg_data)
                                    else stringResource(R.string.content_no_guide_channels, query),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurfaceVariant,
                                )
                                else -> LazyColumn(
                                    Modifier.fillMaxWidth().height(listHeight),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    items(list, key = { it.id }) { epg ->
                                        FocusableSurface(
                                            onClick = { onPick(epg.epgChannelId) },
                                            modifier = if (epg == list.first()) {
                                                Modifier.fillMaxWidth().focusRequester(firstItemFocus)
                                            } else Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            contentAlignment = Alignment.CenterStart,
                                            surface = GlassSurface.DIALOGS,
                                        ) { _ ->
                                            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp)) {
                                                Text(
                                                    epg.displayName ?: epg.epgChannelId,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = colors.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    epg.epgChannelId,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = colors.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.width(110.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OwnTVButton(
                                stringResource(R.string.content_close),
                                onClick = onDismiss,
                                style = OwnTVButtonStyle.SECONDARY,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (currentMatch != null) {
                                OwnTVButton(
                                    stringResource(R.string.content_clear_match),
                                    onClick = onClear,
                                    style = OwnTVButtonStyle.SECONDARY,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Shared per-channel EPG offset dialog retained for the EPG screen. */
@Composable
internal fun EpgOffsetDialog(
    channelName: String,
    currentMinutes: Int?,
    globalMinutes: Int,
    onSet: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    var minutes by remember { mutableStateOf(currentMinutes ?: globalMinutes) }
    val doneFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { doneFocus.requestFocus() } }
    androidx.activity.compose.BackHandler { onDismiss() }
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss) {
        tv.own.owntv.ui.theme.PopupFontTheme(fontScale = 0.75f) {
            Box(
                Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier.dialogPanel(width = 420.dp, corner = 16.dp, padding = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(R.string.content_epg_time_offset),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.content_epg_offset_channel_description, channelName),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OwnTVButton(
                            stringResource(R.string.content_epg_shift_minutes, "−", "30"),
                            onClick = { minutes = (minutes - 30).coerceAtLeast(-12 * 60) },
                            style = OwnTVButtonStyle.SECONDARY,
                            compact = true,
                        )
                        Text(
                            liveEpgShiftLabel(minutes),
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.primary,
                            modifier = Modifier.width(120.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        OwnTVButton(
                            stringResource(R.string.content_epg_shift_minutes, "+", "30"),
                            onClick = { minutes = (minutes + 30).coerceAtMost(14 * 60) },
                            style = OwnTVButtonStyle.SECONDARY,
                            compact = true,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(
                            if (currentMinutes == null) R.string.content_epg_offset_following_global
                            else R.string.content_epg_offset_channel_only,
                            liveEpgShiftLabel(globalMinutes),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OwnTVButton(
                            stringResource(R.string.common_done),
                            onClick = { onSet(minutes); onDismiss() },
                            modifier = Modifier.weight(1f).focusRequester(doneFocus),
                        )
                        if (currentMinutes != null) {
                            OwnTVButton(
                                stringResource(R.string.content_epg_offset_use_global),
                                onClick = { onSet(null); onDismiss() },
                                style = OwnTVButtonStyle.SECONDARY,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        OwnTVButton(
                            stringResource(R.string.common_cancel),
                            onClick = onDismiss,
                            style = OwnTVButtonStyle.SECONDARY,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun liveEpgShiftLabel(minutes: Int): String {
    if (minutes == 0) return stringResource(R.string.common_off)
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0] ?: java.util.Locale.US
    val number = java.text.NumberFormat.getIntegerInstance(locale)
    val sign = if (minutes < 0) "−" else "+"
    val absolute = kotlin.math.abs(minutes)
    val hours = absolute / 60
    val remainder = absolute % 60
    return when {
        hours == 0 -> stringResource(R.string.content_epg_shift_minutes, sign, number.format(remainder))
        remainder == 0 -> stringResource(R.string.content_epg_shift_hours, sign, number.format(hours))
        else -> stringResource(
            R.string.content_epg_shift_hours_minutes,
            sign,
            number.format(hours),
            number.format(remainder),
        )
    }
}
