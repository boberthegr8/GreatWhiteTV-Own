from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


# 1) Sidebar: replace the old OwnTV PLAY/green-arrow app mark with the real GWS vector.
sidebar_path = ROOT / "app/src/main/java/tv/own/owntv/features/shell/components/Sidebar.kt"
sidebar = sidebar_path.read_text()
if "import androidx.compose.foundation.Image\n" not in sidebar:
    sidebar = replace_once(
        sidebar,
        "import androidx.compose.foundation.background\n",
        "import androidx.compose.foundation.Image\nimport androidx.compose.foundation.background\n",
        "Sidebar Image import",
    )
if "import androidx.compose.ui.res.painterResource\n" not in sidebar:
    sidebar = replace_once(
        sidebar,
        "import androidx.compose.ui.res.stringResource\n",
        "import androidx.compose.ui.res.painterResource\nimport androidx.compose.ui.res.stringResource\n",
        "Sidebar painterResource import",
    )
sidebar = sidebar.replace("import tv.own.owntv.ui.components.OwnTVIcon\n", "")
logo_pattern = re.compile(
    r"@Composable\nprivate fun AppLogo\(modifier: Modifier = Modifier\) \{.*?\n\}\n\n@Composable\nprivate fun ProfileCard",
    re.S,
)
logo_replacement = '''@Composable
private fun AppLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_splash_mark),
        contentDescription = stringResource(R.string.gws_app_name),
        modifier = modifier.size(58.dp),
    )
}

@Composable
private fun ProfileCard'''
sidebar, n = logo_pattern.subn(logo_replacement, sidebar, count=1)
if n != 1:
    raise SystemExit(f"Sidebar AppLogo: expected 1 replacement, got {n}")
sidebar_path.write_text(sidebar)


# 2) Navigation: Live TV owns the guide/EPG. Do not show a separate Guide icon.
shell_vm_path = ROOT / "app/src/main/java/tv/own/owntv/features/shell/ShellViewModel.kt"
shell_vm = shell_vm_path.read_text()
shell_vm = replace_once(
    shell_vm,
    "val browseOrder: List<MainSection> = listOf(HOME, LIVE_TV, MOVIES, SERIES, ONLINE, DOWNLOADS, EPG)",
    "val browseOrder: List<MainSection> = listOf(HOME, LIVE_TV, MOVIES, SERIES, ONLINE, DOWNLOADS)",
    "browseOrder",
)
shell_vm = replace_once(
    shell_vm,
    "if (hasLive) { add(LIVE_TV); add(EPG) }",
    "if (hasLive) add(LIVE_TV)",
    "dynamic live visibility",
)
shell_vm = shell_vm.replace(
    "Home always; Live & Guide when there are channels; Movies/Series when their tables have rows;",
    "Home always; Live TV (EPG view) when there are channels; Movies/Series when their tables have rows;",
)
shell_vm_path.write_text(shell_vm)


# 3) Shell: selecting Live TV renders the real EPG grid. All old routes that explicitly opened
#    the Guide destination now open Live TV instead. The hidden EPG enum is retained only for
#    compatibility with old state/deep links.
shell_path = ROOT / "app/src/main/java/tv/own/owntv/features/shell/OwnTVShell.kt"
shell = shell_path.read_text()

live_pattern = re.compile(
    r"\n\s*selectedSection == MainSection\.LIVE_TV -> LiveScreen\(.*?\n\s*\)\n\n\s*selectedSection == MainSection\.MOVIES -> MoviesScreen\(",
    re.S,
)
live_replacement = '''

                        selectedSection == MainSection.LIVE_TV -> EpgScreen(
                            onBack = { runCatching { sidebarFocus.requestFocus() } },
                            onFullscreen = { openFullscreen() },
                            onPlayChannel = { ch, _ ->
                                restoreFocus = false
                                liveVm.watchFromGuide(ch)
                                zapSource = MainSection.LIVE_TV
                                homeVm.stopPreview()
                                if (playerMode != PlayerMode.MINI && !liveVm.externalPlayerOn.value) playerMode = PlayerMode.FULLSCREEN
                            },
                            onPlayCatchup = { ch, prog ->
                                restoreFocus = false
                                liveVm.playCatchupProgramme(ch, prog)
                                zapSource = MainSection.LIVE_TV
                                homeVm.stopPreview()
                                if (playerMode != PlayerMode.MINI) playerMode = PlayerMode.FULLSCREEN
                            },
                            onAddEpg = { openEpgAdd = true; onSelectSection(MainSection.SETTINGS) },
                            restoreFocus = restoreFocus,
                            onRestored = { restoreFocus = false },
                            onContentScrolled = { contentScrolled = it },
                            modifier = Modifier
                                .fillMaxSize()
                                .onFocusChanged { if (it.hasFocus) focusedLayer = ShellLayer.CONTENT }
                                .focusGroup(),
                        )

                        selectedSection == MainSection.MOVIES -> MoviesScreen('''
shell, n = live_pattern.subn(live_replacement, shell, count=1)
if n != 1:
    raise SystemExit(f"OwnTVShell LiveScreen->EPG: expected 1 replacement, got {n}")

# Every UI action that used to open the separate Guide destination now opens Live TV, whose view is EPG.
shell = shell.replace("onSelectSection(MainSection.EPG)", "onSelectSection(MainSection.LIVE_TV)")

shell_path.write_text(shell)


# 4) The in-player Live TV EPG drawer must collapse with the same outward D-pad gesture as the
#    channel/category drawers, not only with Back.
guide_path = ROOT / "app/src/main/java/tv/own/owntv/features/shell/components/GuideDrawerOverlay.kt"
guide = guide_path.read_text()
if "import androidx.compose.ui.input.key.KeyEventType\n" not in guide:
    guide = guide.replace(
        "import androidx.compose.ui.graphics.Color\n",
        "import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.input.key.KeyEventType\nimport androidx.compose.ui.input.key.key\nimport androidx.compose.ui.input.key.onPreviewKeyEvent\nimport androidx.compose.ui.input.key.type\n",
        1,
    )
if "import tv.own.owntv.core.i18n.HorizontalDirection\n" not in guide:
    guide = guide.replace(
        "import androidx.compose.ui.unit.dp\n",
        "import androidx.compose.ui.unit.dp\nimport tv.own.owntv.core.i18n.HorizontalDirection\nimport tv.own.owntv.core.i18n.horizontalDirection\n",
        1,
    )
guide = guide.replace(
    " * Near-full-screen Guide drawer used while Live TV remains playing behind the shell.\n * The full Guide destination still exists for normal navigation; this wrapper is specifically\n * the appliance-style in-player layer, leaving a strip of video visible on the trailing edge.",
    " * Near-full-screen Live TV EPG drawer used while the current channel keeps playing behind it.\n * Live TV owns the EPG; this drawer is the appliance-style collapsible in-player layer, leaving\n * a strip of video visible on the trailing edge.",
)
guide = guide.replace("val panelWidth = maxWidth * 0.80f", "val panelWidth = maxWidth * 0.88f")
needle = '''                .fillMaxHeight()
                .width(panelWidth)
                .clip(RoundedCornerShape(0.dp, 22.dp, 22.dp, 0.dp))'''
replacement = '''                .fillMaxHeight()
                .width(panelWidth)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown &&
                        event.key.horizontalDirection(layoutDirection) == HorizontalDirection.START
                    ) {
                        onDismiss()
                        true
                    } else {
                        false
                    }
                }
                .clip(RoundedCornerShape(0.dp, 22.dp, 22.dp, 0.dp))'''
guide = replace_once(guide, needle, replacement, "Guide outward collapse")
guide_path.write_text(guide)


# 5) Version bump.
version_path = ROOT / "GWS_VERSION"
version_path.write_text("1.0.18\n")

print("Applied GWS Online v1.0.18 Live TV = EPG hotfix")
