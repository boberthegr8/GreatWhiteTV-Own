package tv.own.owntv.features.settings

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.core.database.dao.EpgDao
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.network.DohPresets
import tv.own.owntv.core.repository.SourceRepository
import tv.own.owntv.core.sync.ImportFinalizer
import tv.own.owntv.core.sync.SyncContentTypes
import tv.own.owntv.core.sync.work.CatalogSyncScheduler
import tv.own.owntv.core.sync.work.EpgSyncScheduler
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVTextField
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.theme.OwnTVTheme

private enum class ProviderDnsStatus { IDLE, INVALID, APPLYING, SUCCESS, FAILED }

@Composable
fun DnsSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = OwnTVTheme.colors
    val vm: SettingsViewModel = koinViewModel()
    val scope = rememberCoroutineScope()

    // Provider-DNS dependencies are intentionally kept out of the normal IPTV browse/playback path.
    // This screen changes one SourceEntity only after the user explicitly presses Change DNS & Refresh.
    val sourceRepository: SourceRepository = koinInject()
    val importFinalizer: ImportFinalizer = koinInject()
    val catalogSyncScheduler: CatalogSyncScheduler = koinInject()
    val epgSyncScheduler: EpgSyncScheduler = koinInject()
    val epgDao: EpgDao = koinInject()

    val sources by vm.sources.collectAsStateWithLifecycle()
    val defaultSourceId by vm.defaultSourceId.collectAsStateWithLifecycle()
    val xtreamSources = sources.filter { it.type == SourceType.XTREAM }

    var providerSourceId by remember { mutableStateOf<Long?>(null) }
    var providerServer by remember { mutableStateOf("") }
    var providerStatus by remember { mutableStateOf(ProviderDnsStatus.IDLE) }
    val providerSource = xtreamSources.firstOrNull { it.id == providerSourceId }

    // Default to the active Xtream playlist, otherwise the first Xtream account. Re-sync the field only
    // when the source itself changes in Room; ordinary typing does not touch Room and is left alone.
    LaunchedEffect(xtreamSources.map { it.id to it.url }, defaultSourceId) {
        val chosen = xtreamSources.firstOrNull { it.id == providerSourceId }
            ?: xtreamSources.firstOrNull { it.id == defaultSourceId }
            ?: xtreamSources.firstOrNull()
        providerSourceId = chosen?.id
        providerServer = chosen?.url.orEmpty()
        if (providerStatus != ProviderDnsStatus.APPLYING) providerStatus = ProviderDnsStatus.IDLE
    }

    fun cycleProvider() {
        if (xtreamSources.size <= 1) return
        val current = xtreamSources.indexOfFirst { it.id == providerSourceId }.coerceAtLeast(0)
        val next = xtreamSources[(current + 1) % xtreamSources.size]
        providerSourceId = next.id
        providerServer = next.url
        providerStatus = ProviderDnsStatus.IDLE
    }

    fun applyProviderDns() {
        val source = providerSource ?: return
        val normalized = normalizeProviderServer(providerServer)
        if (normalized == null) {
            providerStatus = ProviderDnsStatus.INVALID
            return
        }
        providerStatus = ProviderDnsStatus.APPLYING
        scope.launch {
            runCatching {
                val oldBase = source.url.trim().trimEnd('/')
                val newBase = normalized.trimEnd('/')
                // If the user had an explicit XMLTV URL on the same old host/base, move that URL with
                // the provider DNS too. A completely separate/manual EPG URL is deliberately untouched.
                val movedEpgUrl = source.epgUrl?.let { epg ->
                    if (oldBase.isNotBlank() && epg.startsWith(oldBase, ignoreCase = true)) {
                        newBase + epg.substring(oldBase.length)
                    } else {
                        epg
                    }
                }
                // copy() is deliberate: username/password and every other provider setting remain byte-for-byte
                // unchanged. lastSyncAt is cleared so the new endpoint is treated as requiring fresh data.
                val updated = source.copy(url = newBase, epgUrl = movedEpgUrl, lastSyncAt = null)
                sourceRepository.updateSource(updated)

                // Replace any old-endpoint work, then force both catalog and guide against the new endpoint.
                catalogSyncScheduler.cancelSync(source.id)
                epgSyncScheduler.cancelSync(source.id)
                val counts = importFinalizer.contentCounts(source.id)
                catalogSyncScheduler.enqueueSync(
                    sourceId = source.id,
                    reason = "provider_dns_change",
                    contentTypes = SyncContentTypes.enabledOf(updated),
                    baseItemCount = counts.channels + counts.movies + counts.series,
                )
                val baseProgrammes = epgDao.countForSources(listOf(source.id))
                epgSyncScheduler.enqueueSync(
                    sourceId = source.id,
                    reason = "provider_dns_change",
                    baseProgrammes = baseProgrammes,
                )
                updated
            }.onSuccess { updated ->
                providerServer = updated.url
                providerStatus = ProviderDnsStatus.SUCCESS
            }.onFailure {
                providerStatus = ProviderDnsStatus.FAILED
            }
        }
    }

    val dnsConfig by vm.dnsConfig.collectAsStateWithLifecycle()
    val dnsTestState by vm.dnsTest.collectAsStateWithLifecycle()

    fun dnsToServerText(cfg: tv.own.owntv.core.network.DnsConfig): String {
        if (cfg.dohUrl.isNotBlank()) return cfg.dohUrl
        if (cfg.host.isNotBlank()) {
            val p = if (cfg.port > 0 && cfg.port != 53) ":${cfg.port}" else ""
            return "${cfg.host}$p"
        }
        return ""
    }

    // Existing advanced network-resolver DNS controls. These are intentionally separate from provider DNS.
    val hasServer = dnsConfig.host.isNotBlank() || dnsConfig.dohUrl.isNotBlank()
    var toggleOn by remember { mutableStateOf(dnsConfig.enabled || hasServer) }
    var server by remember { mutableStateOf(dnsToServerText(dnsConfig)) }

    LaunchedEffect(dnsConfig.enabled, dnsConfig.host, dnsConfig.port, dnsConfig.dohUrl) {
        toggleOn = dnsConfig.enabled || dnsConfig.host.isNotBlank() || dnsConfig.dohUrl.isNotBlank()
        server = dnsToServerText(dnsConfig)
    }

    val serverConfigured = server.trim().isNotBlank()
    val effectiveEnabled = toggleOn && serverConfigured

    fun applyToggle(on: Boolean) {
        toggleOn = on
        if (!on) {
            vm.saveDns(enabled = false, host = "", port = 53, dohUrl = "")
            vm.resetDnsTest()
        }
    }

    fun applySave() {
        val s = server.trim()
        val (host, port, doh) = if (s.startsWith("https://", ignoreCase = true)) {
            Triple("", 53, s)
        } else {
            val colon = s.lastIndexOf(':')
            if (colon > 0 && s.indexOf(':') == colon) {
                val h = s.substring(0, colon).trim()
                val p = s.substring(colon + 1).trim().toIntOrNull() ?: 53
                Triple(h, p, "")
            } else {
                Triple(s, 53, "")
            }
        }
        vm.saveDns(enabled = s.isNotBlank(), host, port, doh)
        vm.resetDnsTest()
    }

    val providerPlaylistFocus = remember { FocusRequester() }
    val providerServerFocus = remember { FocusRequester() }
    val providerApplyFocus = remember { FocusRequester() }
    val toggleFocus = remember { FocusRequester() }
    val firstPresetFocus = remember { FocusRequester() }
    val serverFieldFocus = remember { FocusRequester() }
    val saveFocus = remember { FocusRequester() }

    LaunchedEffect(xtreamSources.isNotEmpty()) {
        kotlinx.coroutines.delay(60)
        if (xtreamSources.isNotEmpty()) runCatching { providerPlaylistFocus.requestFocus() }
        else runCatching { toggleFocus.requestFocus() }
    }

    LaunchedEffect(toggleOn) {
        if (!toggleFocus.captureFocus()) return@LaunchedEffect
        toggleFocus.freeFocus()
        kotlinx.coroutines.delay(60)
        if (toggleOn) runCatching { firstPresetFocus.requestFocus() }
        else runCatching { toggleFocus.requestFocus() }
    }

    BackHandler { onBack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .roundedPanel()
            .focusProperties {
                onEnter = {
                    if (xtreamSources.isNotEmpty()) runCatching { providerPlaylistFocus.requestFocus() }
                    else runCatching { toggleFocus.requestFocus() }
                }
            }
            .focusGroup()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 40.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Header(stringResource(R.string.settings_dns), onBack)
        Spacer(Modifier.height(8.dp))

        GroupLabel(stringResource(R.string.settings_provider_dns_title))
        Text(
            stringResource(R.string.settings_provider_dns_warning),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))

        if (providerSource == null) {
            Text(
                stringResource(R.string.settings_provider_dns_no_xtream),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        } else {
            OwnTVButton(
                label = stringResource(R.string.settings_provider_dns_playlist, providerSource.name),
                onClick = ::cycleProvider,
                style = if (xtreamSources.size > 1) OwnTVButtonStyle.PRIMARY else OwnTVButtonStyle.SECONDARY,
                modifier = Modifier
                    .focusRequester(providerPlaylistFocus)
                    .focusProperties { down = providerServerFocus },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.settings_provider_dns_current, providerSource.url),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OwnTVTextField(
                value = providerServer,
                onValueChange = {
                    providerServer = it.take(500)
                    providerStatus = ProviderDnsStatus.IDLE
                },
                label = stringResource(R.string.settings_provider_dns_server),
                placeholder = stringResource(R.string.settings_provider_dns_hint),
                focusRequester = providerServerFocus,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusProperties {
                        up = providerPlaylistFocus
                        down = providerApplyFocus
                    },
            )
            Spacer(Modifier.height(12.dp))
            OwnTVButton(
                label = stringResource(R.string.settings_provider_dns_apply),
                onClick = ::applyProviderDns,
                modifier = Modifier
                    .focusRequester(providerApplyFocus)
                    .focusProperties {
                        up = providerServerFocus
                        down = toggleFocus
                    },
            )
            when (providerStatus) {
                ProviderDnsStatus.IDLE -> Unit
                ProviderDnsStatus.INVALID -> ProviderDnsStatusText(stringResource(R.string.settings_provider_dns_invalid), isError = true)
                ProviderDnsStatus.APPLYING -> ProviderDnsStatusText(stringResource(R.string.settings_provider_dns_applying))
                ProviderDnsStatus.SUCCESS -> ProviderDnsStatusText(stringResource(R.string.settings_provider_dns_success))
                ProviderDnsStatus.FAILED -> ProviderDnsStatusText(stringResource(R.string.settings_provider_dns_failed), isError = true)
            }
        }

        Spacer(Modifier.height(24.dp))
        GroupLabel(stringResource(R.string.settings_provider_dns_advanced))
        Text(
            stringResource(R.string.settings_provider_dns_advanced_note),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))

        Row2(
            icon = OwnTVIcon.SEARCH,
            title = stringResource(R.string.settings_dns_use_custom),
            desc = stringResource(R.string.settings_dns_toggle_description),
            chip = stringResource(if (effectiveEnabled) R.string.common_on else R.string.common_off),
            primaryChip = effectiveEnabled,
            modifier = Modifier
                .focusRequester(toggleFocus)
                .focusProperties {
                    if (providerSource != null) up = providerApplyFocus
                    if (toggleOn) down = firstPresetFocus
                },
            onClick = { applyToggle(!toggleOn) },
        )

        if (toggleOn && !serverConfigured) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_dns_server_missing),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFEF4444),
            )
        }

        if (toggleOn) {
            Column {
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.focusProperties {
                        up = toggleFocus
                        down = serverFieldFocus
                    },
                ) {
                    var first = true
                    for ((label, url) in DohPresets.all) {
                        val isActive = server.trim() == url
                        OwnTVButton(
                            label = label,
                            onClick = {
                                server = url
                                toggleOn = true
                                applySave()
                            },
                            style = if (isActive) OwnTVButtonStyle.PRIMARY else OwnTVButtonStyle.SECONDARY,
                            modifier = if (first) {
                                first = false
                                Modifier.focusRequester(firstPresetFocus)
                            } else {
                                Modifier
                            },
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                OwnTVTextField(
                    value = server,
                    onValueChange = { server = it },
                    label = stringResource(R.string.settings_dns_server),
                    placeholder = stringResource(R.string.settings_dns_server_hint),
                    focusRequester = serverFieldFocus,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusProperties {
                            up = firstPresetFocus
                            down = saveFocus
                        },
                )

                Spacer(Modifier.height(20.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.focusProperties { up = serverFieldFocus },
                ) {
                    OwnTVButton(stringResource(R.string.common_save), onClick = { applySave() }, modifier = Modifier.focusRequester(saveFocus))
                    OwnTVButton(
                        label = stringResource(
                            if (dnsTestState is SettingsViewModel.DnsTestState.Testing) R.string.settings_testing
                            else R.string.settings_dns_test,
                        ),
                        onClick = {
                            val s = server.trim()
                            val doh = if (s.startsWith("https://", ignoreCase = true)) s else ""
                            vm.testDns(toggleOn, s, 53, doh)
                        },
                        style = OwnTVButtonStyle.SECONDARY,
                    )
                    DnsTestLabel(dnsTestState)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.settings_dns_explanation),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.settings_dns_limitations),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}

private fun normalizeProviderServer(raw: String): String? {
    val trimmed = raw.trim().trimEnd('/')
    if (trimmed.isBlank()) return null
    val candidate = if (trimmed.contains("://")) trimmed else "http://$trimmed"
    val uri = runCatching { Uri.parse(candidate) }.getOrNull() ?: return null
    val validScheme = uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)
    if (!validScheme || uri.host.isNullOrBlank()) return null
    return candidate.trimEnd('/')
}

@Composable
private fun ProviderDnsStatusText(text: String, isError: Boolean = false) {
    Spacer(Modifier.height(8.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) Color(0xFFEF4444) else OwnTVTheme.colors.primary,
    )
}

@Composable
private fun DnsTestLabel(state: SettingsViewModel.DnsTestState) {
    val colors = OwnTVTheme.colors
    val (text, color) = when (state) {
        is SettingsViewModel.DnsTestState.Ok -> stringResource(
            R.string.settings_dns_resolved,
            state.millis,
        ) to colors.primary
        is SettingsViewModel.DnsTestState.Fail -> state.failure.displayText() to Color(0xFFEF4444)
        else -> null to colors.onSurfaceVariant
    }
    if (text != null) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
private fun SettingsViewModel.DnsTestFailure.displayText(): String = when (this) {
    SettingsViewModel.DnsTestFailure.ServerRequired -> stringResource(R.string.settings_dns_enter_server)
    SettingsViewModel.DnsTestFailure.ServerNotReachable -> stringResource(R.string.settings_dns_not_reachable)
    SettingsViewModel.DnsTestFailure.TimedOut -> stringResource(R.string.settings_dns_timed_out)
    SettingsViewModel.DnsTestFailure.NetworkUnreachable -> stringResource(R.string.settings_dns_network_unreachable)
    SettingsViewModel.DnsTestFailure.ConnectionRefused -> stringResource(R.string.settings_dns_connection_refused)
    is SettingsViewModel.DnsTestFailure.NoAddresses -> stringResource(R.string.settings_dns_no_addresses, host)
    is SettingsViewModel.DnsTestFailure.Unknown -> rawMessage
    SettingsViewModel.DnsTestFailure.Generic -> stringResource(R.string.settings_dns_test_failed)
}
