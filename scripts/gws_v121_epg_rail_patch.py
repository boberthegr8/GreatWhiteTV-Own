from pathlib import Path

vm = Path("app/src/main/java/tv/own/owntv/features/epg/EpgViewModel.kt")
s = vm.read_text()
s = s.replace(
    "all.groupBy { it.epgChannelId }.mapValues { (_, v) -> v.sortedBy { it.startMs } }",
    "all.groupBy { it.epgChannelId.trim().lowercase() }.mapValues { (_, v) -> v.sortedBy { it.startMs } }",
)
marker = "    @Volatile private var lastStored = -1 // stored programme count the cache was built from (data-change guard)\n"
if "autoRefreshAttemptedFor" not in s:
    if marker not in s:
        raise SystemExit("auto refresh field anchor not found")
    s = s.replace(marker, marker + "    @Volatile private var autoRefreshAttemptedFor: Set<Long> = emptySet()\n", 1)
old = "            val hasEpg = epgIds.isNotEmpty()\n            val message = when {\n"
new = """            val hasEpg = stored > 0 || epgIds.isNotEmpty()
            val noCurrentProgrammes = rowCache.values.none { it.isNotEmpty() }
            val refreshKey = playlistIds.toSet()
            val shouldAutoRefresh = (stored == 0 || noCurrentProgrammes) &&
                refreshKey.isNotEmpty() && autoRefreshAttemptedFor != refreshKey
            val message = when {
"""
if old in s:
    s = s.replace(old, new, 1)
old2 = """            _state.value = EpgUiState(
                channels = channels, windowStart = windowStart, windowEnd = windowEnd, now = now,
                loading = false, message = message, hasEpgSources = hasEpg, stats = stats, catchupCount = catchupCount,
                favoriteCount = favoriteIds.size,
            )

            // Pass 2 (background): merge the catch-up lookback into rowCache gradually, so a multi-day × many-
"""
new2 = """            _state.value = EpgUiState(
                channels = channels, windowStart = windowStart, windowEnd = windowEnd, now = now,
                loading = false, refreshing = shouldAutoRefresh, message = message, hasEpgSources = hasEpg, stats = stats, catchupCount = catchupCount,
                favoriteCount = favoriteIds.size,
            )

            // Self-heal a blank Live guide after a source switch or stale provider cache.
            if (shouldAutoRefresh) {
                autoRefreshAttemptedFor = refreshKey
                runCatching {
                    sourceRepository.observeSources(pid).first()
                        .filter { it.id in playlistIds }
                        .forEach { epgRepository.refresh(it) }
                    epgSourceStore.getAll().forEach { epgRepository.refreshUrl(it.id, it.url, it.userAgent) }
                }
                cachedWindow = null
                lastStored = -1
                rowCache.clear()
                shiftedRowCache.clear()
                _state.value = _state.value.copy(refreshing = false)
                load()
                return@launch
            }

            // Pass 2 (background): merge the catch-up lookback into rowCache gradually, so a multi-day × many-
"""
if old2 in s:
    s = s.replace(old2, new2, 1)
vm.write_text(s)

screen = Path("app/src/main/java/tv/own/owntv/features/epg/EpgScreen.kt")
e = screen.read_text()
if "import androidx.compose.animation.AnimatedVisibility" not in e:
    e = e.replace(
        "import androidx.activity.compose.BackHandler\n",
        "import androidx.activity.compose.BackHandler\n"
        "import androidx.compose.animation.AnimatedVisibility\n"
        "import androidx.compose.animation.core.MutableTransitionState\n"
        "import androidx.compose.animation.fadeIn\n"
        "import androidx.compose.animation.fadeOut\n"
        "import androidx.compose.animation.slideInHorizontally\n"
        "import androidx.compose.animation.slideOutHorizontally\n",
        1,
    )
if "import androidx.compose.foundation.layout.fillMaxHeight" not in e:
    e = e.replace("import androidx.compose.foundation.layout.fillMaxSize\n", "import androidx.compose.foundation.layout.fillMaxSize\nimport androidx.compose.foundation.layout.fillMaxHeight\n", 1)
if "import androidx.compose.foundation.lazy.items\n" not in e:
    e = e.replace("import androidx.compose.foundation.lazy.itemsIndexed\n", "import androidx.compose.foundation.lazy.items\nimport androidx.compose.foundation.lazy.itemsIndexed\n", 1)

top = """            // Category filter (#8): narrow the guide to one group instead of all channels at once.
            if (guideCategories.isNotEmpty()) {
                val catLabel = categoryFilter?.let { key -> guideCategories.firstOrNull { it.key == key }?.name } ?: stringResource(R.string.content_epg_all)
                OwnTVButton(stringResource(R.string.content_epg_category_button, catLabel), onClick = { showCategoryPicker = true }, icon = OwnTVIcon.MENU, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.width(12.dp))
            }
"""
e = e.replace(top, "", 1)

call = """                                onMatchEpg = { restoreChannelId = channel.id; matchChooser = channel },
                                inCellMode = inCellMode,
"""
if call in e:
    e = e.replace(call, """                                onMatchEpg = { restoreChannelId = channel.id; matchChooser = channel },
                                onOpenCategories = { showCategoryPicker = true },
                                inCellMode = inCellMode,
""", 1)

sig = """    onOpen: (EpgProgrammeEntity) -> Unit,
    onMatchEpg: () -> Unit,
    inCellMode: Boolean,
"""
if sig in e:
    e = e.replace(sig, """    onOpen: (EpgProgrammeEntity) -> Unit,
    onMatchEpg: () -> Unit,
    onOpenCategories: () -> Unit,
    inCellMode: Boolean,
""", 1)

label = """                // Physical by design: the guide is an LTR timeline, so the strip is always right.
                .focusProperties { right = stripFR }
                .onFocusChanged { if (it.isFocused) onExitToChannels() }, // back on a label ⇒ leave CELL stage
"""
if label in e:
    e = e.replace(label, """                // Physical by design: the guide is an LTR timeline, so the strip is always right.
                .focusProperties { right = stripFR }
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionLeft) {
                        onOpenCategories()
                        true
                    } else false
                }
                .onFocusChanged { if (it.isFocused) onExitToChannels() }, // back on a label ⇒ leave CELL stage
""", 1)

old_picker = """    if (showCategoryPicker) {
        tv.own.owntv.features.settings.PickerDialog(
            title = stringResource(R.string.content_epg_guide_category),
            options = listOf("ALL" to stringResource(R.string.content_epg_all_categories)) + guideCategories.map { it.key to it.name },
            selected = categoryFilter ?: "ALL",
            onSelect = { vm.setCategoryFilter(it.takeUnless { value -> value == "ALL" }); showCategoryPicker = false },
            onDismiss = { showCategoryPicker = false },
            searchable = guideCategories.size > 8,
        )
    }
"""
if old_picker in e:
    e = e.replace(old_picker, """    if (showCategoryPicker) {
        GuideCategoryRail(
            categories = guideCategories,
            selectedKey = categoryFilter,
            onSelect = { key -> vm.setCategoryFilter(key); showCategoryPicker = false },
            onDismiss = { showCategoryPicker = false },
        )
    }
""", 1)

insert_before = "/**\n * Review screen for the smart EPG matcher (#13): lists the lower-confidence suggestions the auto-match\n"
rail = r'''
@Composable
private fun GuideCategoryRail(
    categories: List<GuideCategory>,
    selectedKey: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val scope = rememberCoroutineScope()
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    val firstFocus = remember { FocusRequester() }

    fun close(after: (() -> Unit)? = null) {
        visibleState.targetState = false
        scope.launch {
            kotlinx.coroutines.delay(180)
            after?.invoke()
            onDismiss()
        }
    }

    BackHandler { close() }
    Popup(
        alignment = Alignment.CenterStart,
        onDismissRequest = { close() },
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    if (event.key == Key.DirectionRight) { close(); true } else false
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            AnimatedVisibility(
                visibleState = visibleState,
                enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
            ) {
                Column(
                    Modifier.width(320.dp).fillMaxHeight()
                        .background(colors.surfaceContainerHigh)
                        .padding(horizontal = 16.dp, vertical = 22.dp)
                        .focusGroup(),
                ) {
                    Text(stringResource(R.string.content_epg_guide_category), style = MaterialTheme.typography.headlineSmall, color = colors.onSurface)
                    Spacer(Modifier.height(14.dp))
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        item(key = Long.MIN_VALUE) {
                            FocusableSurface(
                                onClick = { close { onSelect(null) } },
                                modifier = Modifier.fillMaxWidth().focusRequester(firstFocus),
                                shape = RoundedCornerShape(10.dp),
                                unfocusedContainerColor = if (selectedKey == null) colors.primaryContainer.copy(alpha = 0.35f) else colors.surface,
                                contentAlignment = Alignment.CenterStart,
                                surface = GlassSurface.CARDS,
                            ) { focused ->
                                Text(stringResource(R.string.content_epg_all_categories), modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), color = if (focused || selectedKey == null) colors.primary else colors.onSurface, style = MaterialTheme.typography.titleSmall)
                            }
                        }
                        items(categories, key = { it.key }) { category ->
                            val selected = category.key == selectedKey
                            FocusableSurface(
                                onClick = { close { onSelect(category.key) } },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                unfocusedContainerColor = if (selected) colors.primaryContainer.copy(alpha = 0.35f) else colors.surface,
                                contentAlignment = Alignment.CenterStart,
                                surface = GlassSurface.CARDS,
                            ) { focused ->
                                Text(category.name, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), color = if (focused || selected) colors.primary else colors.onSurface, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(70)
        runCatching { firstFocus.requestFocus() }
    }
}

'''
if "private fun GuideCategoryRail(" not in e:
    if insert_before not in e:
        raise SystemExit("category rail insertion anchor not found")
    e = e.replace(insert_before, rail + insert_before, 1)

screen.write_text(e)
Path("GWS_VERSION").write_text("1.0.21\n")
