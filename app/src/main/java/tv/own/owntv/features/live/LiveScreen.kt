package tv.own.owntv.features.live

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.features.epg.EpgScreen
import tv.own.owntv.features.epg.EpgViewModel

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
    // Kept in the signature because the shell already supplies it. The EPG-first Live TV surface does
    // not run the old in-pane video preview, which also avoids starting a stream simply by browsing.
    @Suppress("UNUSED_VARIABLE")
    val previewStillAvailableToShell = previewEnabled

    val liveVm: LiveViewModel = koinViewModel()
    val epgVm: EpgViewModel = koinViewModel()
    val externalPlayerOn by liveVm.externalPlayerOn.collectAsStateWithLifecycle()

    EpgScreen(
        onBack = {},
        onFullscreen = onFullscreen,
        // EPG sources remain configured in Settings. Existing users keep their sources and cached data;
        // this screen deliberately does not create a second navigation path just to add one.
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
