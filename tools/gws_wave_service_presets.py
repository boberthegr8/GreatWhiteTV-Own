from pathlib import Path

PATH = Path("app/src/main/java/tv/own/owntv/features/setup/AddSourceScreen.kt")
text = PATH.read_text(encoding="utf-8")

SENTINEL = "private enum class WaveService"
if SENTINEL in text:
    print("GWS Wave service selector already applied")
    raise SystemExit(0)

old = "private enum class SourceKind { XTREAM, M3U, STALKER }"
new = """private enum class SourceKind { XTREAM, M3U, STALKER }

private enum class WaveService(@param:StringRes val labelRes: Int, @param:StringRes val dnsRes: Int?) {
    HUSH(R.string.wave_service_hush, R.string.wave_service_hush_dns),
    CCTV(R.string.wave_service_cctv, R.string.wave_service_cctv_dns),
    PUREVISION(R.string.wave_service_purevision, R.string.wave_service_purevision_dns),
    CUSTOM(R.string.wave_service_custom, null),
}

private fun normalizedWaveDns(value: String): String = value.trim().trimEnd('/')

private fun detectWaveService(server: String, hush: String, cctv: String, purevision: String): WaveService = when (normalizedWaveDns(server)) {
    normalizedWaveDns(hush) -> WaveService.HUSH
    normalizedWaveDns(cctv) -> WaveService.CCTV
    normalizedWaveDns(purevision) -> WaveService.PUREVISION
    else -> WaveService.CUSTOM
}"""
assert old in text, "SourceKind marker not found"
text = text.replace(old, new, 1)

old = """    val colors = OwnTVTheme.colors
    val editing = initial != null
    var kind by remember {"""
new = """    val colors = OwnTVTheme.colors
    val editing = initial != null
    val hushDns = stringResource(R.string.wave_service_hush_dns)
    val cctvDns = stringResource(R.string.wave_service_cctv_dns)
    val purevisionDns = stringResource(R.string.wave_service_purevision_dns)
    val waveDnsByService = mapOf(
        WaveService.HUSH to hushDns,
        WaveService.CCTV to cctvDns,
        WaveService.PUREVISION to purevisionDns,
    )
    var kind by remember {"""
assert old in text, "Composable state marker not found"
text = text.replace(old, new, 1)

old = '    var server by remember(initial) { mutableStateOf(if (initial != null && initial.type == SourceType.XTREAM) initial.url else "") }'
new = '    var server by remember(initial, hushDns) { mutableStateOf(if (initial != null && initial.type == SourceType.XTREAM) initial.url else hushDns) }\n    var waveService by remember(initial, hushDns, cctvDns, purevisionDns) { mutableStateOf(detectWaveService(server, hushDns, cctvDns, purevisionDns)) }'
assert old in text, "Server state marker not found"
text = text.replace(old, new, 1)

old = """    var showFileBrowser by remember { mutableStateOf(false) }
    var showAutoRefreshPicker by remember { mutableStateOf(false) }"""
new = """    var showFileBrowser by remember { mutableStateOf(false) }
    var showAutoRefreshPicker by remember { mutableStateOf(false) }
    var showWaveServicePicker by remember { mutableStateOf(false) }"""
assert old in text, "Picker state marker not found"
text = text.replace(old, new, 1)

old = """                SourceKind.XTREAM -> {
                    OwnTVTextField(server, { server = it }, label = stringResource(R.string.setup_server_url), placeholder = stringResource(R.string.setup_server_example), keyboardType = KeyboardType.Uri, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(14.dp))
                    OwnTVTextField(username, { username = it }, label = stringResource(R.string.setup_username), modifier = Modifier.fillMaxWidth())"""
new = """                SourceKind.XTREAM -> {
                    Text(stringResource(R.string.wave_service_title), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.wave_service_description), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    OwnTVButton(
                        label = stringResource(waveService.labelRes),
                        onClick = { showWaveServicePicker = true },
                        style = OwnTVButtonStyle.SECONDARY,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(14.dp))
                    OwnTVTextField(
                        server,
                        {
                            server = it
                            waveService = detectWaveService(it, hushDns, cctvDns, purevisionDns)
                        },
                        label = stringResource(R.string.wave_dns_server),
                        placeholder = stringResource(R.string.setup_server_example),
                        keyboardType = KeyboardType.Uri,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.wave_dns_change_hint), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    Spacer(Modifier.height(14.dp))
                    OwnTVTextField(username, { username = it }, label = stringResource(R.string.setup_username), modifier = Modifier.fillMaxWidth())"""
assert old in text, "Xtream form marker not found"
text = text.replace(old, new, 1)

old = """      if (showAutoRefreshPicker) {
          PickerDialog("""
new = """      if (showWaveServicePicker) {
          PickerDialog(
              title = stringResource(R.string.wave_service_picker_title),
              options = WaveService.entries.map { it.name to stringResource(it.labelRes) },
              selected = waveService.name,
              onSelect = { value ->
                  val selected = runCatching { WaveService.valueOf(value) }.getOrDefault(WaveService.CUSTOM)
                  waveService = selected
                  waveDnsByService[selected]?.let { server = it }
                  showWaveServicePicker = false
              },
              onDismiss = { showWaveServicePicker = false },
          )
      }
      if (showAutoRefreshPicker) {
          PickerDialog("""
assert old in text, "Auto-refresh picker marker not found"
text = text.replace(old, new, 1)

PATH.write_text(text, encoding="utf-8")
print("Applied GWS Wave Hush/CCTV/Purevision/Custom service selector")
