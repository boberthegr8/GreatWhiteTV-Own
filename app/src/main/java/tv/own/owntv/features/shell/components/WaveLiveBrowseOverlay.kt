package tv.own.owntv.features.shell.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.CategoryEntity
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.epg.displayLogoUrl
import tv.own.owntv.core.i18n.HorizontalDirection
import tv.own.owntv.core.i18n.horizontalDirection
import tv.own.owntv.ui.components.ChannelNumberColumn
import tv.own.owntv.ui.components.ContentPanelFill
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.modalScrim
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.theme.GlassSurface
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * GWS Wave full-screen Live TV browser.
 *
 * Categories never disappear into a second-level mini menu: the whole browser slides in from the
 * left, with the category column permanently visible on the left and that category's channels on
 * the right. Left from the category column closes the browser; Back does the same. Selecting a
 * category updates the channel list without replacing the category column.
 */
@Composable
fun WaveLiveBrowseOverlay(
    categories: List<Pair<CategoryEntity, String>>,
    currentCategoryId: Long?,
    channels: List<ChannelEntity>,
    currentId: Long?,
    nowPlaying: Map<Long, String> = emptyMap(),
    title: String? = null,
    showNumbers: Boolean = true,
    onSelectCategory: (Long) -> Unit,
    onSelectChannel: (ChannelEntity) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    val layoutDirection = LocalLayoutDirection.current
    val scope = rememberCoroutineScope()
    val categoryListState = rememberLazyListState()
    val channelListState = rememberLazyListState()
    val categoryFocus = remember { FocusRequester() }
    val channelFocus = remember { FocusRequester() }
    var selectedCategoryId by remember(currentCategoryId) { mutableStateOf(currentCategoryId) }
    var revealed by remember { mutableStateOf(false) }
    var dismissing by remember { mutableStateOf(false) }

    val currentCategoryIndex = remember(categories, selectedCategoryId) {
        categories.indexOfFirst { it.first.id == selectedCategoryId }.coerceAtLeast(0)
    }
    val currentChannelIndex = remember(channels, currentId) {
        channels.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
    }

    fun dismissAnimated() {
        if (dismissing) return
        dismissing = true
        revealed = false
        scope.launch {
            delay(210)
            onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        runCatching { categoryListState.scrollToItem(currentCategoryIndex) }
        revealed = true
        delay(70)
        runCatching { categoryFocus.requestFocus() }
    }

    LaunchedEffect(channels, currentId) {
        if (channels.isNotEmpty()) runCatching { channelListState.scrollToItem(currentChannelIndex) }
    }

    BackHandler { dismissAnimated() }

    Box(modifier = modifier.fillMaxSize().modalScrim(strength = 0.42f)) {
        AnimatedVisibility(
            visible = revealed,
            enter = slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = tween(210),
            ) + fadeIn(tween(150)),
            exit = slideOutHorizontally(
                targetOffsetX = { -it },
                animationSpec = tween(190),
            ) + fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .roundedPanel(radius = 20.dp, fillColor = ContentPanelFill, surface = GlassSurface.DIALOGS)
                    .focusGroup()
                    .padding(14.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.35f)
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown &&
                                event.key.horizontalDirection(layoutDirection) == HorizontalDirection.START
                            ) {
                                dismissAnimated()
                                true
                            } else {
                                false
                            }
                        },
                ) {
                    Text(
                        text = stringResource(R.string.content_category_browser_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    LazyColumn(
                        state = categoryListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        items(categories, key = { it.first.id }) { (category, displayName) ->
                            val selected = category.id == selectedCategoryId
                            FocusableSurface(
                                onClick = {
                                    selectedCategoryId = category.id
                                    onSelectCategory(category.id)
                                },
                                selected = selected,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (category.id == categories.getOrNull(currentCategoryIndex)?.first?.id) {
                                            Modifier.focusRequester(categoryFocus)
                                        } else Modifier,
                                    ),
                                shape = RoundedCornerShape(10.dp),
                                surface = GlassSurface.DIALOGS,
                            ) { focused ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(9.dp)
                                            .clip(RoundedCornerShape(99.dp))
                                            .background(if (selected) colors.primary else colors.outlineVariant),
                                    )
                                    Text(
                                        text = displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = when {
                                            selected -> colors.primary
                                            focused -> colors.onSurface
                                            else -> colors.onSurfaceVariant
                                        },
                                        fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 12.dp)
                        .width(1.dp)
                        .background(colors.outlineVariant),
                )

                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.65f),
                ) {
                    Text(
                        text = title ?: stringResource(R.string.content_channel_overlay_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    LazyColumn(
                        state = channelListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        items(channels, key = { it.id }) { channel ->
                            val isCurrent = channel.id == currentId
                            FocusableSurface(
                                onClick = {
                                    onSelectChannel(channel)
                                    dismissAnimated()
                                },
                                selected = isCurrent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (channel.id == channels.getOrNull(currentChannelIndex)?.id) {
                                            Modifier.focusRequester(channelFocus)
                                        } else Modifier,
                                    ),
                                shape = RoundedCornerShape(10.dp),
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
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(colors.surfaceContainerLowest),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (!channel.displayLogoUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = channel.displayLogoUrl,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        } else {
                                            OwnTVIcon(
                                                OwnTVIcon.LIVE_TV,
                                                tint = colors.onSurfaceVariant,
                                                modifier = Modifier.size(22.dp),
                                            )
                                        }
                                    }
                                    if (showNumbers) {
                                        ChannelNumberColumn(
                                            number = channel.number,
                                            color = colors.onSurfaceVariant,
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = channel.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = when {
                                                isCurrent -> colors.primary
                                                focused -> colors.onSurface
                                                else -> colors.onSurfaceVariant
                                            },
                                            fontWeight = if (focused || isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.fillMaxWidth().then(
                                                if (focused) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier,
                                            ),
                                        )
                                        nowPlaying[channel.id]?.let { programme ->
                                            Text(
                                                text = programme,
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
                }
            }
        }
    }
}
