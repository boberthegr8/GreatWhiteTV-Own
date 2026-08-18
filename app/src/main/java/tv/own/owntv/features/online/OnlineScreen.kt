package tv.own.owntv.features.online

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

private sealed interface OnlineStreamsState {
    data object Waiting : OnlineStreamsState
    data object Loading : OnlineStreamsState
    data class Ready(val streams: List<ResolvedOnlineStream>) : OnlineStreamsState
}

private data class ResolvedOnlineStream(
    val provider: OnlineAddon,
    val stream: OnlineStream,
)

/**
 * Great White Online's independent browse surface. Cinemeta supplies the default catalog and metadata;
 * stream-capable add-ons are queried separately. Only direct HTTP(S) or external provider links are
 * accepted by [StremioAddonClient], so unsupported transport types never reach OwnTV's player.
 */
@Composable
fun OnlineScreen(
    onPlayDirect: (item: OnlineItem, episode: OnlineVideo?, url: String) -> Unit,
    onChildFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = OwnTVTheme.colors
    val client = remember { StremioAddonClient() }
    val addonStore = remember { OnlineAddonStore(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var type by remember { mutableStateOf("movie") }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<OnlineItem?>(null) }
    var selectedEpisode by remember { mutableStateOf<OnlineVideo?>(null) }
    var sourcesOpen by remember { mutableStateOf(false) }
    var sourceRevision by remember { mutableIntStateOf(0) }

    val catalogState by produceState<OnlineCatalogState>(OnlineCatalogState.Loading, type, query) {
        delay(350)
        value = withContext(Dispatchers.IO) {
            runCatching {
                OnlineCatalogState.Ready(
                    client.catalog(
                        StremioAddonClient.CINEMETA,
                        type,
                        "top",
                        extras = if (query.isBlank()) emptyMap() else mapOf("search" to query.trim()),
                    ).take(80),
                )
            }.getOrElse { OnlineCatalogState.Failed }
        }
    }

    LaunchedEffect(catalogState) {
        val first = (catalogState as? OnlineCatalogState.Ready)?.items?.firstOrNull()
        if (selected == null || (catalogState as? OnlineCatalogState.Ready)?.items?.none { it.id == selected?.id } != false) {
            selected = first
            selectedEpisode = null
        }
    }

    val selectedMeta by produceState<OnlineMeta?>(null, selected?.id, selected?.type) {
        val item = selected
        value = if (item == null || item.type != "series") null
        else withContext(Dispatchers.IO) { runCatching { client.meta(StremioAddonClient.CINEMETA, item.type, item.id) }.getOrNull() }
    }

    LaunchedEffect(selectedMeta?.item?.id) {
        selectedEpisode = selectedMeta?.videos?.firstOrNull()
    }

    val targetId = if (selected?.type == "series") selectedEpisode?.id else selected?.id
    val streamState by produceState<OnlineStreamsState>(
        OnlineStreamsState.Waiting,
        selected?.id,
        selected?.type,
        targetId,
        sourceRevision,
    ) {
        val item = selected
        val id = targetId
        if (item == null || id == null) {
            value = OnlineStreamsState.Waiting
            return@produceState
        }
        value = OnlineStreamsState.Loading
        value = withContext(Dispatchers.IO) {
            val resolved = addonStore.streamProviders()
                .filter { item.type in it.types || it.types.isEmpty() }
                .flatMap { provider ->
                    runCatching { client.streams(provider.baseUrl, item.type, id) }
                        .getOrDefault(emptyList())
                        .map { ResolvedOnlineStream(provider, it) }
                }
            OnlineStreamsState.Ready(resolved)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
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
            }
            OnlinePill(
                label = stringResource(R.string.online_movies),
                selected = type == "movie" && !sourcesOpen,
                onClick = { sourcesOpen = false; type = "movie"; selected = null; selectedEpisode = null },
                onFocused = onChildFocused,
            )
            OnlinePill(
                label = stringResource(R.string.online_series),
                selected = type == "series" && !sourcesOpen,
                onClick = { sourcesOpen = false; type = "series"; selected = null; selectedEpisode = null },
                onFocused = onChildFocused,
            )
            OnlinePill(
                label = stringResource(R.string.online_sources),
                selected = sourcesOpen,
                onClick = { sourcesOpen = true },
                onFocused = onChildFocused,
            )
        }
        Spacer(Modifier.height(14.dp))

        if (sourcesOpen) {
            OnlineSourcesPanel(
                client = client,
                store = addonStore,
                revision = sourceRevision,
                onChanged = { sourceRevision++ },
                onFocused = onChildFocused,
                modifier = Modifier.fillMaxSize(),
            )
            return@Column
        }

        SearchBox(
            query = query,
            onQueryChange = { query = it.take(120) },
            onFocused = onChildFocused,
        )
        Spacer(Modifier.height(12.dp))

        when (val current = catalogState) {
            OnlineCatalogState.Loading -> CenterMessage(stringResource(R.string.online_loading))
            OnlineCatalogState.Failed -> CenterMessage(stringResource(R.string.online_error))
            is OnlineCatalogState.Ready -> {
                if (current.items.isEmpty()) {
                    CenterMessage(stringResource(R.string.online_empty))
                } else {
                    Row(
                        modifier = Modifier.fillMaxSize().focusGroup(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        LazyColumn(
                            modifier = Modifier.weight(0.9f).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(current.items, key = { "${it.type}:${it.id}" }) { item ->
                                FocusableSurface(
                                    onClick = { selected = item; selectedEpisode = null },
                                    selected = selected?.id == item.id && selected?.type == item.type,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged {
                                            if (it.hasFocus) {
                                                selected = item
                                                selectedEpisode = null
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
                                                Text(it, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        OnlineDetailsPane(
                            item = selected,
                            meta = selectedMeta,
                            selectedEpisode = selectedEpisode,
                            onEpisodeSelected = { selectedEpisode = it },
                            streams = streamState,
                            onStreamSelected = { choice ->
                                val item = selected ?: return@OnlineDetailsPane
                                when {
                                    choice.stream.url != null -> onPlayDirect(item, selectedEpisode, choice.stream.url)
                                    choice.stream.externalUrl != null -> {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(choice.stream.externalUrl))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        runCatching { context.startActivity(intent) }
                                            .onFailure {
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.online_external_open_failed),
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                            }
                                    }
                                }
                            },
                            onFocused = onChildFocused,
                            modifier = Modifier.weight(1.25f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineDetailsPane(
    item: OnlineItem?,
    meta: OnlineMeta?,
    selectedEpisode: OnlineVideo?,
    onEpisodeSelected: (OnlineVideo) -> Unit,
    streams: OnlineStreamsState,
    onStreamSelected: (ResolvedOnlineStream) -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    LazyColumn(
        modifier = modifier.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        item {
            Text(
                text = item?.name.orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.online_source_cinemeta),
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = item?.description ?: stringResource(R.string.online_item_no_description),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
                maxLines = 7,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(14.dp))
        }

        if (item?.type == "series") {
            item {
                Text(
                    text = stringResource(R.string.online_choose_episode),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            when {
                meta == null -> item { Text(stringResource(R.string.online_loading_episodes), color = colors.onSurfaceVariant) }
                meta.videos.isEmpty() -> item { Text(stringResource(R.string.online_no_episodes), color = colors.onSurfaceVariant) }
                else -> items(meta.videos, key = { it.id }) { episode ->
                    FocusableSurface(
                        onClick = { onEpisodeSelected(episode) },
                        selected = selectedEpisode?.id == episode.id,
                        modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.hasFocus) onFocused() },
                        shape = RoundedCornerShape(10.dp),
                        surface = GlassSurface.CARDS,
                        contentAlignment = Alignment.CenterStart,
                    ) { focused ->
                        Text(
                            text = episode.title,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (focused) colors.onSurface else colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(10.dp)) }
        }

        item {
            Text(
                text = stringResource(R.string.online_available_streams),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
        }
        when (streams) {
            OnlineStreamsState.Waiting -> item { Text(stringResource(R.string.online_no_streams), color = colors.onSurfaceVariant) }
            OnlineStreamsState.Loading -> item { Text(stringResource(R.string.online_loading_streams), color = colors.onSurfaceVariant) }
            is OnlineStreamsState.Ready -> {
                if (streams.streams.isEmpty()) {
                    item { Text(stringResource(R.string.online_no_streams), color = colors.onSurfaceVariant) }
                } else {
                    items(streams.streams, key = { "${it.provider.id}:${it.stream.url}:${it.stream.externalUrl}:${it.stream.title}" }) { choice ->
                        FocusableSurface(
                            onClick = { onStreamSelected(choice) },
                            modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.hasFocus) onFocused() },
                            shape = RoundedCornerShape(10.dp),
                            surface = GlassSurface.CARDS,
                            contentAlignment = Alignment.CenterStart,
                        ) { focused ->
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                                Text(
                                    text = choice.stream.name.ifBlank { choice.provider.name },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (focused) colors.onSurface else colors.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = choice.stream.title ?: choice.provider.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.primary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun OnlineSourcesPanel(
    client: StremioAddonClient,
    store: OnlineAddonStore,
    revision: Int,
    onChanged: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val checkingText = stringResource(R.string.online_source_checking)
    val addedText = stringResource(R.string.online_source_added)
    val invalidText = stringResource(R.string.online_source_invalid)
    val addons = remember(revision) { store.all() }

    Column(modifier.padding(horizontal = 8.dp)) {
        Text(
            stringResource(R.string.online_sources_title),
            style = MaterialTheme.typography.headlineSmall,
            color = colors.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(R.string.online_sources_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        SearchBox(
            query = url,
            hint = stringResource(R.string.online_source_url_hint),
            onQueryChange = { url = it.take(500) },
            onFocused = onFocused,
        )
        Spacer(Modifier.height(8.dp))
        OnlinePill(
            label = stringResource(R.string.online_source_add),
            selected = false,
            onClick = {
                if (url.isBlank()) return@OnlinePill
                message = checkingText
                scope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching { client.manifest(url) } }
                    result.onSuccess { manifest ->
                        if (manifest.resources.none { it in setOf("catalog", "meta", "stream") }) {
                            message = invalidText
                        } else {
                            store.add(manifest, url)
                            url = ""
                            message = addedText
                            onChanged()
                        }
                    }.onFailure { message = invalidText }
                }
            },
            onFocused = onFocused,
        )
        message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(addons, key = { it.id }) { addon ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceContainer)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(addon.name, color = colors.onSurface, style = MaterialTheme.typography.titleMedium)
                        Text(addon.baseUrl, color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (addon.builtIn) {
                        Text(stringResource(R.string.online_source_builtin), color = colors.primary, style = MaterialTheme.typography.labelMedium)
                    } else {
                        OnlinePill(
                            label = stringResource(R.string.online_source_remove),
                            selected = false,
                            onClick = { store.remove(addon.id); onChanged() },
                            onFocused = onFocused,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
    onFocused: () -> Unit,
    hint: String = stringResource(R.string.online_search_hint),
) {
    val colors = OwnTVTheme.colors
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceContainer)
            .border(1.dp, if (focused) colors.primary else colors.outlineVariant, shape)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        if (query.isBlank()) {
            Text(hint, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
        }
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.primary),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged {
                    focused = it.hasFocus
                    if (it.hasFocus) onFocused()
                },
        )
    }
}

@Composable
private fun OnlinePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        selected = selected,
        modifier = Modifier.onFocusChanged { if (it.hasFocus) onFocused() },
        shape = RoundedCornerShape(12.dp),
        surface = GlassSurface.CARDS,
        contentAlignment = Alignment.Center,
    ) { focused ->
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (focused || selected) colors.onSurface else colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun CenterMessage(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = OwnTVTheme.colors.onSurfaceVariant)
    }
}
