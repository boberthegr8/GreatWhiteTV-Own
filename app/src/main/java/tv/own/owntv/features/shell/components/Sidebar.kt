package tv.own.owntv.features.shell.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import tv.own.owntv.R
import tv.own.owntv.features.shell.MainSection
import tv.own.owntv.ui.components.BrandLockup
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.NavAccentBar
import tv.own.owntv.ui.components.OwnTVAvatar
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.RailPanelFill
import tv.own.owntv.ui.components.rememberNavLadderColors
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.theme.Dimens
import tv.own.owntv.ui.theme.GlassSurface
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.theme.glass

/**
 * GWS Wave TV-first navigation rail.
 *
 * Wave keeps the rail narrow while content has focus, then expands when the user moves left. The
 * information architecture deliberately mirrors how people browse IPTV on a television: Home,
 * Live TV, Guide, Movies, Series and Search are direct destinations instead of hiding useful
 * destinations behind secondary screens.
 */
@Composable
fun Sidebar(
    selected: MainSection,
    onSelect: (MainSection) -> Unit,
    visibleSections: Set<MainSection>,
    avatarId: Int,
    onPickAvatar: () -> Unit,
    profileName: String,
    sourceSummary: String?,
    onSwitchProfile: () -> Unit,
    selectedItemFocusRequester: FocusRequester,
    onFocused: () -> Unit,
    counts: (MainSection) -> Int = { 0 },
    topInset: Dp = Dimens.TopBarHeight,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    var hasFocus by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val expanded = hasFocus
    val sidebarWidth by animateDpAsState(
        targetValue = if (expanded) Dimens.SidebarWidthExpanded else Dimens.SidebarWidthCollapsed,
        label = "gwsSidebarWidth",
    )

    val hasLive = MainSection.LIVE_TV in visibleSections
    val waveSections = buildList {
        add(MainSection.HOME)
        if (hasLive) {
            add(MainSection.LIVE_TV)
            add(MainSection.EPG)
        }
        if (MainSection.MOVIES in visibleSections) add(MainSection.MOVIES)
        if (MainSection.SERIES in visibleSections) add(MainSection.SERIES)
        add(MainSection.SEARCH)
    }
    val focusSection = when {
        selected == MainSection.SETTINGS -> MainSection.SETTINGS
        selected in waveSections -> selected
        selected == MainSection.EPG && hasLive -> MainSection.EPG
        else -> MainSection.HOME
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .onFocusChanged {
                val entered = it.hasFocus && !hasFocus
                hasFocus = it.hasFocus
                if (it.hasFocus) onFocused()
                if (entered) {
                    scope.launch { runCatching { selectedItemFocusRequester.requestFocus() } }
                }
            }
            .focusGroup()
            .width(sidebarWidth)
            .padding(start = 6.dp, top = topInset, end = 6.dp, bottom = 6.dp)
            .roundedPanel(fillColor = RailPanelFill, surface = GlassSurface.SIDEBAR)
            .padding(top = 12.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        WaveLogo(expanded = expanded)
        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (expanded) {
                    Text(
                        text = stringResource(R.string.wave_browse).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, bottom = 7.dp),
                    )
                }

                waveSections.forEach { section ->
                    WaveNavItem(
                        section = section,
                        active = section == selected,
                        expanded = expanded,
                        count = counts(section),
                        onClick = { onSelect(section) },
                        modifier = if (section == focusSection) {
                            Modifier.focusRequester(selectedItemFocusRequester)
                        } else Modifier,
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp)
                .height(1.dp)
                .background(colors.outlineVariant),
        )

        WaveNavItem(
            section = MainSection.SETTINGS,
            active = selected == MainSection.SETTINGS,
            expanded = expanded,
            count = 0,
            onClick = { onSelect(MainSection.SETTINGS) },
            modifier = if (focusSection == MainSection.SETTINGS) {
                Modifier.focusRequester(selectedItemFocusRequester)
            } else Modifier,
        )
        Spacer(Modifier.height(8.dp))
        ProfileCard(
            expanded = expanded,
            avatarId = avatarId,
            profileName = profileName,
            sourceSummary = sourceSummary,
            onPickAvatar = onPickAvatar,
            onSwitchProfile = onSwitchProfile,
        )
    }
}

@Composable
private fun WaveLogo(expanded: Boolean) {
    if (expanded) {
        BrandLockup(
            modifier = Modifier.padding(horizontal = 10.dp),
            markSize = 32,
            textSize = 19,
        )
        return
    }

    val colors = OwnTVTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(shape)
            .background(colors.card)
            .border(2.dp, colors.primary, shape),
        contentAlignment = Alignment.Center,
    ) {
        OwnTVIcon(
            icon = OwnTVIcon.PLAY,
            tint = colors.primary,
            filled = true,
            modifier = Modifier.size(23.dp),
        )
    }
}

@Composable
private fun ProfileCard(
    expanded: Boolean,
    avatarId: Int,
    profileName: String,
    sourceSummary: String?,
    onPickAvatar: () -> Unit,
    onSwitchProfile: () -> Unit,
) {
    if (!expanded) {
        AvatarButton(
            avatarId = avatarId,
            sizeDp = 48,
            onClick = onSwitchProfile,
            onLongClick = onPickAvatar,
        )
        return
    }

    val colors = OwnTVTheme.colors
    val sourceLabel = sourceSummary ?: stringResource(R.string.shell_no_source)
    val displayName = profileName.ifBlank { stringResource(R.string.wave_profile_default) }
    val shape = RoundedCornerShape(14.dp)
    FocusableSurface(
        onClick = onSwitchProfile,
        onLongClick = onPickAvatar,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        shape = shape,
        focusedContainerColor = colors.surfaceContainerHighest,
        unfocusedContainerColor = colors.surfaceContainer.copy(alpha = 0.55f),
        selectedContainerColor = colors.surfaceContainer.copy(alpha = 0.55f),
        contentAlignment = Alignment.CenterStart,
        surface = GlassSurface.SIDEBAR,
    ) { focused ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OwnTVAvatar(avatarId = avatarId, modifier = Modifier.size(40.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (focused) colors.onSurface else colors.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (focused) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier,
                )
                Text(
                    text = sourceLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AvatarButton(
    avatarId: Int,
    sizeDp: Int,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    FocusableSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier.size(sizeDp.dp),
        shape = CircleShape,
        focusedScale = 1.02f,
        focusedContainerColor = OwnTVTheme.colors.surfaceContainerHighest,
        unfocusedContainerColor = Color.Transparent,
        selectedContainerColor = Color.Transparent,
        contentAlignment = Alignment.Center,
        surface = GlassSurface.SIDEBAR,
    ) { _ ->
        OwnTVAvatar(avatarId = avatarId, modifier = Modifier.size((sizeDp - 4).dp))
    }
}

@Composable
private fun WaveNavItem(
    section: MainSection,
    active: Boolean,
    expanded: Boolean,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    val shape = RoundedCornerShape(13.dp)
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        selected = active,
        shape = shape,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        selectedContainerColor = Color.Transparent,
        surface = GlassSurface.SIDEBAR,
        showFocusBorder = false,
        renderSelectionContainer = false,
        contentAlignment = Alignment.Center,
    ) { focused ->
        val ladder = rememberNavLadderColors(selected = active, focused = focused)
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            NavAccentBar(visible = ladder.showAccentBar, height = 24.dp)
            Box(
                modifier = Modifier
                    .then(
                        if (expanded) Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                        else Modifier.width(52.dp),
                    )
                    .height(42.dp)
                    .clip(shape)
                    .glass(surface = GlassSurface.SIDEBAR, baseFill = ladder.container, shape = shape)
                    .then(
                        if (active) {
                            Modifier.background(
                                Brush.linearGradient(
                                    listOf(
                                        colors.primary.copy(alpha = 0.62f),
                                        colors.primaryContainer.copy(alpha = 0.72f),
                                    ),
                                ),
                                shape,
                            )
                        } else Modifier,
                    )
                    .then(
                        when {
                            active -> Modifier.border(
                                1.dp,
                                colors.primary.copy(alpha = if (focused) 0.95f else 0.72f),
                                shape,
                            )
                            ladder.focusBorder != null -> Modifier.border(
                                Dimens.FocusBorderWidth,
                                ladder.focusBorder,
                                shape,
                            )
                            else -> Modifier
                        },
                    ),
                contentAlignment = if (expanded) Alignment.CenterStart else Alignment.Center,
            ) {
                val foreground = if (active) colors.onPrimaryContainer else ladder.icon
                if (expanded) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OwnTVIcon(
                            icon = section.waveIcon(),
                            tint = foreground,
                            modifier = Modifier.size(26.dp),
                            filled = active,
                        )
                        Text(
                            text = stringResource(section.labelRes),
                            style = MaterialTheme.typography.labelLarge,
                            color = foreground,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (count > 0 && section in setOf(MainSection.LIVE_TV, MainSection.MOVIES, MainSection.SERIES)) {
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    OwnTVIcon(
                        icon = section.waveIcon(),
                        tint = foreground,
                        modifier = Modifier.size(27.dp),
                        filled = active,
                    )
                }
            }
        }
    }
}

private fun MainSection.waveIcon(): OwnTVIcon = when (this) {
    MainSection.SEARCH -> OwnTVIcon.SEARCH
    MainSection.HOME -> OwnTVIcon.HOME
    MainSection.LIVE_TV -> OwnTVIcon.LIVE_TV
    MainSection.MOVIES -> OwnTVIcon.MOVIES
    MainSection.SERIES -> OwnTVIcon.SERIES
    MainSection.ONLINE -> OwnTVIcon.MOVIES
    MainSection.DOWNLOADS -> OwnTVIcon.DOWNLOADS
    MainSection.EPG -> OwnTVIcon.EPG
    MainSection.SETTINGS -> OwnTVIcon.SETTINGS
}
