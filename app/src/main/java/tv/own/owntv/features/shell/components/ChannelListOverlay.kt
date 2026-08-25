package tv.own.owntv.features.shell.components

import tv.own.owntv.core.epg.displayLogoUrl
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.i18n.HorizontalDirection
import tv.own.owntv.core.i18n.horizontalDirection
import tv.own.owntv.ui.components.ContentPanelFill
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.modalScrim
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.theme.GlassSurface
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * Channel drawer shown over the playing video. The Live TV drawer is deliberately wider than the
 * category drawer so channel names and now-playing text are useful at TV distance. Pushing outward
 * from the left drawer opens categories; Back closes it to unobstructed video. The optional history
 * drawer remains on the opposite edge and uses a slightly narrower width.
 */
@Composable
fun ChannelListOverlay(
    channels: List<ChannelEntity>,
    currentId: Long?,
    onSelect: (ChannelEntity) -> Unit,
    onDismiss: () -> Unit,
    nowPlaying: Map<Long, String> = emptyMap(),
    title: String? = null,
    alignEnd: Boolean = false,
    showNumbers: Boolean = true,
    onOpenCategories: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    val layoutDirection = LocalLayoutDirection.current
    val dismissDirection = if (alignEnd) HorizontalDirection.END else HorizontalDirection.START
    val currentIndex = remember(channels, currentId) {
        channels.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
    }
    val listState = rememberLazyListState()
    val focusCurrent = remember { FocusRequester() }

    val panelWidth = if (alignEnd) 420.dp else 520.dp
    var revealed by remember { mutableStateOf(false) }
    val hiddenOffset = when {
        layoutDirection == LayoutDirection.Ltr && !alignEnd -> -panelWidth
        layoutDirection == LayoutDirection.Ltr && alignEnd -> panelWidth
        layoutDirection == LayoutDirection.Rtl && !alignEnd -> panelWidth
        else -> -panelWidth
    }
    val slideOffset by animateDpAsState(
        targetValue = if (revealed) 0.dp else hiddenOffset,
        animationSpec = tween(durationMillis = 190),
    )

    LaunchedEffect(Unit) {
        revealed = true
        runCatching { listState.scrollToItem(currentIndex) }
        delay(45)
        runCatching { focusCurrent.requestFocus() }
    }
    BackHandler { onDismiss() }

    Box(modifier = modifier.fillMaxSize().modalScrim(strength = 0.34f)) {
        Column(
            modifier = Modifier
                .align(if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart)
                .offset(x = slideOffset)
                .fillMaxHeight()
                .width(panelWidth)
                .roundedPanel(radius = 22.dp, fillColor = ContentPanelFill, surface = GlassSurface.DIALOGS)
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key.horizontalDirection(layoutDirection) == dismissDirection) {
                        // Pushing outward from the Live TV drawer swaps to the category drawer.
                        // Pushing outward from History simply closes that edge panel.
                        if (!alignEnd && onOpenCategories != null) onOpenCategories() else onDismiss()
                        true
                    } else {
                        false
                    }
                }
                .padding(vertical = 18.dp),
        ) {
            Text(
                title ?: stringResource(R.string.content_channel_overlay_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(channels, key = { it.id }) { ch ->
                    val isCurrent = ch.id == currentId
                    ChannelRow(
                        channel = ch,
                        isCurrent = isCurrent,
                        nowTitle = nowPlaying[ch.id],
                        showNumber = showNumbers,
                        onClick = { onSelect(ch) },
                        modifier = if (ch.id == channels.getOrNull(currentIndex)?.id) {
                            Modifier.focusRequester(focusCurrent)
                        } else {
                            Modifier
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(
    channel: ChannelEntity,
    isCurrent: Boolean,
    onClick: () -> Unit,
    nowTitle: String? = null,
    showNumber: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        selected = isCurrent,
        modifier = modifier.fillMaxWidth(),
        surface = GlassSurface.DIALOGS,
    ) { focused ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                    .background(colors.surfaceContainerLowest),
                contentAlignment = Alignment.Center,
            ) {
                if (!channel.displayLogoUrl.isNullOrBlank()) {
                    AsyncImage(model = channel.displayLogoUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
                } else {
                    OwnTVIcon(OwnTVIcon.LIVE_TV, tint = colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
                }
            }
            if (showNumber) {
                tv.own.owntv.ui.components.ChannelNumberColumn(
                    number = channel.number,
                    color = colors.onSurfaceVariant,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    channel.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = when {
                        isCurrent -> colors.primary
                        focused -> colors.onSurface
                        else -> colors.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().then(
                        if (focused) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier,
                    ),
                )
                if (nowTitle != null) {
                    Text(
                        nowTitle,
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
