package tv.own.owntv.features.online

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.own.owntv.R
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.theme.GlassSurface
import tv.own.owntv.ui.theme.OwnTVTheme

private sealed interface OnlineCatalogState {
    data object Loading : OnlineCatalogState
    data class Ready(val items: List<OnlineItem>) : OnlineCatalogState
    data object Failed : OnlineCatalogState
}

/**
 * First-class Online destination. The first provider is Stremio's official Cinemeta catalog; the
 * provider adapter is deliberately generic so additional compatible add-ons can be configured later.
 */
@Composable
fun OnlineScreen(
    onChildFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    val client = remember { StremioAddonClient() }
    val state by produceState<OnlineCatalogState>(OnlineCatalogState.Loading) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val movies = client.catalog(StremioAddonClient.CINEMETA, "movie", "top").take(30)
                val series = client.catalog(StremioAddonClient.CINEMETA, "series", "top").take(30)
                OnlineCatalogState.Ready(movies + series)
            }.getOrElse { OnlineCatalogState.Failed }
        }
    }
    var selected by remember { mutableStateOf<OnlineItem?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        Text(
            text = stringResource(R.string.online_title),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.online_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))

        when (val current = state) {
            OnlineCatalogState.Loading -> CenterMessage(stringResource(R.string.online_loading))
            OnlineCatalogState.Failed -> CenterMessage(stringResource(R.string.online_error))
            is OnlineCatalogState.Ready -> {
                if (current.items.isEmpty()) {
                    CenterMessage(stringResource(R.string.online_empty))
                } else {
                    if (selected == null) selected = current.items.first()
                    Row(
                        modifier = Modifier.fillMaxSize().focusGroup(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .weight(0.95f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(current.items, key = { "${it.type}:${it.id}" }) { item ->
                                FocusableSurface(
                                    onClick = { selected = item },
                                    selected = selected?.id == item.id && selected?.type == item.type,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged {
                                            if (it.hasFocus) {
                                                selected = item
                                                onChildFocused()
                                            }
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    surface = GlassSurface.CARDS,
                                    contentAlignment = Alignment.CenterStart,
                                ) { focused ->
                                    Column(Modifier.padding(horizontal = 16.dp, vertical = 11.dp)) {
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (focused) colors.onSurface else colors.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                text = if (item.type == "series") stringResource(R.string.online_series) else stringResource(R.string.online_movies),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = colors.primary,
                                            )
                                            item.year?.let {
                                                Text(
                                                    text = it,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = colors.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .weight(1.25f)
                                .fillMaxHeight()
                                .padding(18.dp),
                        ) {
                            Text(
                                text = selected?.name.orEmpty(),
                                style = MaterialTheme.typography.headlineSmall,
                                color = colors.onSurface,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.online_source_cinemeta),
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.primary,
                            )
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = selected?.description ?: stringResource(R.string.online_item_no_description),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.onSurfaceVariant,
                                maxLines = 12,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(18.dp))
                            Text(
                                text = stringResource(R.string.online_provider_foundation),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterMessage(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = OwnTVTheme.colors.onSurfaceVariant,
        )
    }
}
