package tv.own.owntv.features.shell.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import kotlinx.coroutines.delay
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.CategoryEntity
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
 * Category browser drawn over the playing video. It is the first layer of the GWS Live TV drawer:
 * choosing a category replaces this drawer with that category's channel list while playback remains
 * full-screen behind it. Back or pushing outward returns to the channel list without changing channel.
 */
@Composable
fun CategoryBrowserOverlay(
    categories: List<Pair<CategoryEntity, String>>,
    currentCategoryId: Long?,
    onSelect: (categoryId: Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    val layoutDirection = LocalLayoutDirection.current
    val currentIndex = remember(categories, currentCategoryId) {
        categories.indexOfFirst { it.first.id == currentCategoryId }.coerceAtLeast(0)
    }
    val listState = rememberLazyListState()
    val focusCurrent = remember { FocusRequester() }

    // Give the drawer a real TV-style entrance instead of abruptly popping over the video.
    val panelWidth = 380.dp
    var revealed by remember { mutableStateOf(false) }
    val hiddenOffset = if (layoutDirection == LayoutDirection.Ltr) -panelWidth else panelWidth
    val slideOffset by animateDpAsState(
        targetValue = if (revealed) 0.dp else hiddenOffset,
        animationSpec = tween(durationMillis = 190),
        label = "gwsCategoryDrawerOffset",
    )

    LaunchedEffect(Unit) {
        revealed = true
        runCatching { listState.scrollToItem(currentIndex) }
        // Let the drawer start moving before handing D-pad focus to its selected row.
        delay(45)
        runCatching { focusCurrent.requestFocus() }
    }

    BackHandler { onDismiss() }

    Box(modifier = modifier.fillMaxSize().modalScrim(strength = 0.58f)) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = slideOffset)
                .fillMaxHeight()
                .width(panelWidth)
                .roundedPanel(radius = 22.dp, fillColor = ContentPanelFill, surface = GlassSurface.DIALOGS)
                .onPreviewKeyEvent { e ->
                    // Pushing outward from logical Start returns to the channel drawer.
                    if (e.type == KeyEventType.KeyDown &&
                        e.key.horizontalDirection(layoutDirection) == HorizontalDirection.START
                    ) {
                        onDismiss()
                        true
                    } else {
                        false
                    }
                }
                .padding(vertical = 18.dp),
        ) {
            Text(
                stringResource(R.string.content_category_browser_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp,
                    vertical = 6.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(categories, key = { it.first.id }) { (cat, displayName) ->
                    val isCurrent = cat.id == currentCategoryId
                    CategoryRow(
                        name = displayName,
                        isCurrent = isCurrent,
                        onClick = { onSelect(cat.id) },
                        modifier = if (cat.id == categories.getOrNull(currentIndex)?.first?.id) {
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
private fun CategoryRow(
    name: String,
    isCurrent: Boolean,
    onClick: () -> Unit,
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .background(colors.surfaceContainerLowest),
                contentAlignment = Alignment.Center,
            ) {
                OwnTVIcon(
                    OwnTVIcon.LIVE_TV,
                    tint = if (isCurrent) colors.primary else colors.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    isCurrent -> colors.primary
                    focused -> colors.onSurface
                    else -> colors.onSurfaceVariant
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
