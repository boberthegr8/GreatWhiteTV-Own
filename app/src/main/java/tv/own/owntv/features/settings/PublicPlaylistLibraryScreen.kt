package tv.own.owntv.features.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.features.settings.data.PlaylistAutoRefresh
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * GWS v1.0.18 advanced feature: a curated library of public/free M3U endpoints.
 *
 * These definitions are intentionally compiled into the APK. They are never mixed with, substituted
 * for, or used to mutate the user's primary provider/Hush source. Enabling one creates an ordinary
 * M3U SourceEntity through the same importer as Settings -> Playlists -> Add Source; disabling one
 * deletes only the SourceEntity whose exact URL matches the built-in definition.
 */
internal data class PublicPlaylistDefinition(
    val id: String,
    @StringRes val nameRes: Int,
    @StringRes val descriptionRes: Int,
    val url: String,
)

internal object PublicPlaylistCatalog {
    val all = listOf(
        PublicPlaylistDefinition(
            id = "iptv_org_worldwide",
            nameRes = R.string.gws_public_iptv_org_worldwide_name,
            descriptionRes = R.string.gws_public_iptv_org_worldwide_desc,
            url = "https://iptv-org.github.io/iptv/index.m3u",
        ),
        PublicPlaylistDefinition(
            id = "iptv_org_canada",
            nameRes = R.string.gws_public_iptv_org_canada_name,
            descriptionRes = R.string.gws_public_iptv_org_canada_desc,
            url = "https://iptv-org.github.io/iptv/countries/ca.m3u",
        ),
        PublicPlaylistDefinition(
            id = "iptv_org_usa",
            nameRes = R.string.gws_public_iptv_org_usa_name,
            descriptionRes = R.string.gws_public_iptv_org_usa_desc,
            url = "https://iptv-org.github.io/iptv/countries/us.m3u",
        ),
        PublicPlaylistDefinition(
            id = "iptv_org_sports",
            nameRes = R.string.gws_public_iptv_org_sports_name,
            descriptionRes = R.string.gws_public_iptv_org_sports_desc,
            url = "https://iptv-org.github.io/iptv/categories/sports.m3u",
        ),
        PublicPlaylistDefinition(
            id = "iptv_org_movies",
            nameRes = R.string.gws_public_iptv_org_movies_name,
            descriptionRes = R.string.gws_public_iptv_org_movies_desc,
            url = "https://iptv-org.github.io/iptv/categories/movies.m3u",
        ),
        PublicPlaylistDefinition(
            id = "iptv_org_news",
            nameRes = R.string.gws_public_iptv_org_news_name,
            descriptionRes = R.string.gws_public_iptv_org_news_desc,
            url = "https://iptv-org.github.io/iptv/categories/news.m3u",
        ),
        PublicPlaylistDefinition(
            id = "pluto_global",
            nameRes = R.string.gws_public_pluto_global_name,
            descriptionRes = R.string.gws_public_pluto_global_desc,
            url = "https://raw.githubusercontent.com/BuddyChewChew/app-m3u-generator/main/playlists/plutotv_all.m3u",
        ),
        PublicPlaylistDefinition(
            id = "samsung_global",
            nameRes = R.string.gws_public_samsung_global_name,
            descriptionRes = R.string.gws_public_samsung_global_desc,
            url = "https://raw.githubusercontent.com/BuddyChewChew/app-m3u-generator/main/playlists/samsungtvplus_all.m3u",
        ),
        PublicPlaylistDefinition(
            id = "plex_canada",
            nameRes = R.string.gws_public_plex_canada_name,
            descriptionRes = R.string.gws_public_plex_canada_desc,
            url = "https://raw.githubusercontent.com/BuddyChewChew/app-m3u-generator/main/playlists/plex_ca.m3u",
        ),
        PublicPlaylistDefinition(
            id = "free_tv",
            nameRes = R.string.gws_public_free_tv_name,
            descriptionRes = R.string.gws_public_free_tv_desc,
            url = "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8",
        ),
    )
}

private fun String.publicPlaylistKey(): String = trim().trimEnd('/')

@Composable
fun PublicPlaylistLibraryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: SettingsViewModel = koinViewModel()
    val sources by vm.sources.collectAsStateWithLifecycle()
    val importState by vm.importState.collectAsStateWithLifecycle()
    val deletingIds by vm.deletingSourceIds.collectAsStateWithLifecycle()
    val colors = OwnTVTheme.colors
    val context = LocalContext.current

    var pending by remember { mutableStateOf<List<PublicPlaylistDefinition>>(emptyList()) }
    var active by remember { mutableStateOf<PublicPlaylistDefinition?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    val installedById: Map<String, SourceEntity> = remember(sources) {
        val m3uByUrl = sources
            .asSequence()
            .filter { it.type == SourceType.M3U }
            .associateBy { it.url.publicPlaylistKey() }
        PublicPlaylistCatalog.all.mapNotNull { definition ->
            m3uByUrl[definition.url.publicPlaylistKey()]?.let { definition.id to it }
        }.toMap()
    }

    val busy = active != null || pending.isNotEmpty() || importState is SettingsViewModel.ImportState.Running

    fun queueInstall(definitions: List<PublicPlaylistDefinition>) {
        if (busy) return
        val missing = definitions.filter { it.id !in installedById }
        if (missing.isEmpty()) {
            notice = context.getString(R.string.gws_public_library_already_installed)
        } else {
            notice = null
            pending = missing
        }
    }

    // The normal importer owns one foreground import job at a time. Drive Install All as a queue so
    // every playlist receives a complete import/finalize cycle instead of one add cancelling another.
    LaunchedEffect(importState, pending, active) {
        when (importState) {
            SettingsViewModel.ImportState.Idle -> {
                if (active == null && pending.isNotEmpty()) {
                    val next = pending.first()
                    pending = pending.drop(1)
                    active = next
                    vm.addM3u(
                        name = context.getString(next.nameRes),
                        url = next.url,
                        autoRefresh = PlaylistAutoRefresh.HOURS_24,
                        isDefault = false,
                    )
                }
            }
            SettingsViewModel.ImportState.Running -> Unit
            is SettingsViewModel.ImportState.Success -> {
                notice = active?.let {
                    context.getString(R.string.gws_public_library_installed_notice, context.getString(it.nameRes))
                }
                active = null
                vm.resetImport()
            }
            is SettingsViewModel.ImportState.Failed -> {
                notice = active?.let {
                    context.getString(R.string.gws_public_library_install_failed, context.getString(it.nameRes))
                } ?: context.getString(R.string.gws_public_library_install_failed_generic)
                active = null
                vm.resetImport()
            }
        }
    }

    BackHandler {
        vm.cancelImport()
        pending = emptyList()
        active = null
        onBack()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .roundedPanel()
            .focusGroup()
            .padding(horizontal = 40.dp, vertical = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.gws_public_library_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.gws_public_library_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(16.dp))
            OwnTVButton(stringResource(R.string.common_back), onClick = {
                vm.cancelImport()
                pending = emptyList()
                active = null
                onBack()
            }, style = OwnTVButtonStyle.SECONDARY)
        }

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OwnTVButton(
                label = stringResource(
                    if (busy) R.string.gws_public_library_installing_ellipsis
                    else R.string.gws_public_library_install_all,
                ),
                onClick = { queueInstall(PublicPlaylistCatalog.all) },
            )
            OwnTVButton(
                label = stringResource(R.string.gws_public_library_remove_all),
                onClick = {
                    if (!busy) {
                        installedById.values
                            .filter { it.id !in deletingIds }
                            .forEach(vm::delete)
                        notice = context.getString(
                            if (installedById.isEmpty()) R.string.gws_public_library_none_installed
                            else R.string.gws_public_library_removing_all,
                        )
                    }
                },
                style = OwnTVButtonStyle.SECONDARY,
            )
            Text(
                stringResource(R.string.gws_public_library_count, installedById.size, PublicPlaylistCatalog.all.size),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
            )
            if (importState is SettingsViewModel.ImportState.Running) {
                OwnTVSpinner(sizeDp = 22)
            }
        }

        notice?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.primary)
        }

        Spacer(Modifier.height(14.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(PublicPlaylistCatalog.all, key = { it.id }) { definition ->
                val installed = installedById[definition.id]
                val deleting = installed != null && installed.id in deletingIds
                val isActive = active?.id == definition.id
                val queued = pending.any { it.id == definition.id }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.surfaceContainerHigh)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(definition.nameRes),
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.onSurface,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                stringResource(
                                    when {
                                        deleting -> R.string.gws_public_library_removing
                                        isActive -> R.string.gws_public_library_installing
                                        queued -> R.string.gws_public_library_queued
                                        installed != null -> R.string.gws_public_library_installed
                                        else -> R.string.gws_public_library_not_installed
                                    },
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (installed != null && !deleting) colors.primary else colors.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            stringResource(definition.descriptionRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                        Text(
                            definition.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    when {
                        deleting -> OwnTVSpinner(sizeDp = 22)
                        isActive || queued -> OwnTVButton(
                            stringResource(R.string.gws_public_library_working),
                            onClick = {},
                            style = OwnTVButtonStyle.SECONDARY,
                        )
                        installed != null -> OwnTVButton(
                            stringResource(R.string.gws_public_library_remove),
                            onClick = { if (!busy) vm.delete(installed) },
                            style = OwnTVButtonStyle.SECONDARY,
                        )
                        else -> OwnTVButton(
                            stringResource(R.string.gws_public_library_install),
                            onClick = { queueInstall(listOf(definition)) },
                        )
                    }
                }
            }
        }
    }
}
