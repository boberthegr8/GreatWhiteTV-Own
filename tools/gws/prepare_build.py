#!/usr/bin/env python3
"""Apply Great White Streams TV build customizations without renaming OwnTV's Kotlin packages.

Keeping the upstream namespace/package tree intact makes future OwnTV merges far less painful.
The Android applicationId changes at build time so Great White can live beside older GWS/OwnTV apps.
Customer-facing provider presets are also injected into OwnTV's LAN companion setup page.
"""
from pathlib import Path
import re


def replace_pattern(path: str, pattern: str, replacement, *, flags=re.MULTILINE) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    changed, count = re.subn(pattern, replacement, text, count=1, flags=flags)
    if count != 1:
        raise SystemExit(f"Expected exactly one match in {path!r}, found {count}: {pattern!r}")
    p.write_text(changed, encoding="utf-8")


def replace_optional(path: str, pattern: str, replacement, *, flags=re.MULTILINE) -> bool:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    changed, count = re.subn(pattern, replacement, text, count=1, flags=flags)
    if count:
        p.write_text(changed, encoding="utf-8")
        return True
    return False


# A separate application ID is what makes Android/Fire TV install this beside the old GWS app.
replace_pattern(
    "app/build.gradle.kts",
    r'^(\s*)applicationId\s*=\s*"[^"]+"\s*$',
    lambda m: f'{m.group(1)}applicationId = "com.greatwhitestreams.tv"',
)

# Keep an explicit launcher label in the manifest even if upstream moves app_name again.
replace_pattern(
    "app/src/main/AndroidManifest.xml",
    r'^(\s*)android:label\s*=\s*(?:"@string/app_name"|"Great White Streams TV")\s*$',
    lambda m: f'{m.group(1)}android:label="Great White Streams TV"',
)

# Great White provider presets for the QR / phone companion form. The real server field remains
# editable, so Custom/Other providers and emergency DNS changes still work without a new APK.
companion_path = "app/src/main/java/tv/own/owntv/core/companion/CompanionHtml.kt"
replace_pattern(
    companion_path,
    r'(?P<i>\s*)<label>\$\{c\.serverUrl\.h\(\)\} <input name="server" placeholder="\$\{c\.serverExample\.h\(\)\}" required></label>',
    lambda m: (
        f'{m.group("i")}<label>Great White provider\n'
        f'{m.group("i")}  <select id="gwsProvider" onchange="var s=document.getElementById(\'gwsServer\'),n=document.getElementsByName(\'name\')[0]; if(this.value){{s.value=this.value; if(n&&!n.value.trim()) n.value=this.options[this.selectedIndex].text;}}">\n'
        f'{m.group("i")}    <option value="">Custom / Other</option>\n'
        f'{m.group("i")}    <option value="http://hush.iptv:80">HUSH</option>\n'
        f'{m.group("i")}    <option value="http://cvapp.tv:8000">CCTV</option>\n'
        f'{m.group("i")}  </select>\n'
        f'{m.group("i")}</label>\n'
        f'{m.group("i")}<label>${{c.serverUrl.h()}} <input id="gwsServer" name="server" placeholder="${{c.serverExample.h()}}" required></label>'
    ),
)

# Make the phone page visually match the Great White blue-black / aqua TV identity.
p = Path(companion_path)
companion = p.read_text(encoding="utf-8")
companion = companion.replace("--bg:#040E0B", "--bg:#0A0E14")
companion = companion.replace("--card:#1B211F", "--card:#121821")
companion = companion.replace("--card-2:#252B29", "--card-2:#1B2430")
companion = companion.replace("--line:#3F4945", "--line:#3D4A59")
companion = companion.replace("--text:#DEE4E1", "--text:#F2F6FA")
companion = companion.replace("--muted:#BFC9C4", "--muted:#A6B3C2")
companion = companion.replace("--accent:#8CEE2B", "--accent:#2DE2C4")
companion = companion.replace("--accent-ink:#123A06", "--accent-ink:#002F29")
companion = companion.replace("rgba(140,238,43", "rgba(45,226,196")
companion = companion.replace("background:#52DBC8", "background:#2DE2C4")
companion = companion.replace("color:#003730", "color:#002F29")
companion = companion.replace("background:#1E2E0C", "background:#123A36")
companion = companion.replace("color:#EAFFD0", "color:#D9FFF8")
companion = companion.replace(
    '<button type="button" class="tab active" data-k="xtream">${c.xtream.h()}</button>',
    '<button type="button" class="tab active" data-k="xtream">Great White Login</button>',
)
p.write_text(companion, encoding="utf-8")

# OwnTV already has a robust persisted source editor. Surface its Edit action as the Great White
# DNS/server override instead of creating a second database path that could drift from upstream.
replace_optional(
    "app/src/main/res/values/strings_settings.xml",
    r'(<string\s+name="settings_sources_edit"[^>]*>)(.*?)(</string>)',
    r'\1Change DNS / Edit\3',
)

print("Great White build customizations applied:")
print("  applicationId: com.greatwhitestreams.tv")
print("  app name: Great White Streams TV")
print("  QR/mobile presets: HUSH + CCTV + Custom")
print("  HUSH default: http://hush.iptv:80")
print("  CCTV default: http://cvapp.tv:8000")
print("  source Edit action: Change DNS / Edit")
