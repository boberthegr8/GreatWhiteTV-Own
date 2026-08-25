package tv.own.owntv.features.shell.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
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
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.NavAccentBar
import tv.own.owntv.ui.components.NavDuotoneIcon
import tv.own.owntv.ui.components.OwnTVAvatar
import tv.own.owntv.ui.components.RailPanelFill
import tv.own.owntv.ui.components.rememberNavLadderColors
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.theme.Dimens
import tv.own.owntv.ui.theme.GlassSurface
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.theme.glass

/**
 * GWS Online navigation drawer. It stays as the approved compact icon rail while browsing content,
 * then slides open when D-pad focus enters it so the icon labels are readable. The content panes keep
 * their approved fixed widths; only the navigation reservation animates between the existing rail and
 * drawer dimensions.
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

    val focusSection = when {
        selected == MainSection.SEARCH -> MainSection.HOME
        selected == MainSection.SETTINGS -> MainSection.SETTINGS
        selected in visibleSections -> selected
        else -> MainSection.browseOrder.firstOrNull { it in visibleSections } ?: MainSection.SETTINGS
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .onFocusChanged {
                val entered = it.hasFocus && !hasFocus
                hasFocus = it.hasFocus
                if (it.hasFocus) onFocused()
                if (entered) scope.launch { runCatching { selectedItemFocusRequester.requestFocus() } }
            }
            .focusGroup()
            .width(sidebarWidth)
            .padding(start = 6.dp, top = topInset, end = 6.dp, bottom = 6.dp)
            .roundedPanel(fillColor = RailPanelFill, surface = GlassSurface.SIDEBAR)
            .padding(top = 12.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppLogo()
        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (expanded) {
                    SectionLabel(stringResource(R.string.common_browse))
                    Spacer(Modifier.height(6.dp))
                }

                MainSection.browseOrder.filter { it in visibleSections }.forEach { section ->
                    NavItem(
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

                NavItem(
                    section = MainSection.SETTINGS,
                    active = selected == MainSection.SETTINGS,
                    expanded = expanded,
                    count = 0,
                    onClick = { onSelect(MainSection.SETTINGS) },
                    modifier = if (focusSection == MainSection.SETTINGS) {
                        Modifier.focusRequester(selectedItemFocusRequester)
                    } else Modifier,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .height(1.dp)
                .background(colors.outlineVariant),
        )
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
private fun AppLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_splash_mark),
        contentDescription = stringResource(R.string.gws_app_name),
        modifier = modifier.size(58.dp),
    )
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
            sizeDp = 56,
            onClick = onSwitchProfile,
            onLongClick = onPickAvatar,
        )
        return
    }

    val colors = OwnTVTheme.colors
    val sourceLabel = sourceSummary ?: stringResource(R.string.shell_no_source)
    val shape = RoundedCornerShape(16.dp)
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
            OwnTVAvatar(avatarId = avatarId, modifier = Modifier.size(42.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    profileName.ifBlank { stringResource(R.string.common_own_tv_user) },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (focused) colors.onSurface else colors.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (focused) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier,
                )
                Text(
                    sourceLabel,
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
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = OwnTVTheme.colors.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().padding(start = 18.dp),
    )
}

@Composable
private fun NavItem(
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
                        else Modifier.width(52.dp)
                    )
                    .height(42.dp)
                    .clip(shape)
                    .glass(surface = GlassSurface.SIDEBAR, baseFill = ladder.container, shape = shape)
                    .then(
                        if (active) Modifier.background(
                            Brush.linearGradient(
                                listOf(
                                    colors.primary.copy(alpha = 0.58f),
                                    colors.primaryContainer.copy(alpha = 0.70f),
                                ),
                            ),
                            shape,
                        ) else Modifier
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
                        }
                    ),
                contentAlignment = if (expanded) Alignment.CenterStart else Alignment.Center,
            ) {
                if (expanded) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        NavDuotoneIcon(
                            section = section,
                            color = if (active) colors.onPrimaryContainer else ladder.icon,
                            modifier = Modifier.size(28.dp),
                        )
                        Text(
                            text = stringResource(section.labelRes),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (active) colors.onPrimaryContainer else ladder.icon,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (count > 0) {
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    NavDuotoneIcon(
                        section = section,
                        color = if (active) colors.onPrimaryContainer else ladder.icon,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
}
