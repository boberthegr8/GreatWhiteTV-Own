#!/usr/bin/env python3
"""Apply Great White Streams TV build identity without renaming OwnTV's Kotlin packages.

Keeping the upstream namespace/package tree intact makes future OwnTV merges far less painful.
The Android applicationId is changed at build time, which is what Android uses to decide whether
this is a separate installed app.
"""
from pathlib import Path
import re


def replace_pattern(path: str, pattern: str, replacement) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    changed, count = re.subn(pattern, replacement, text, count=1, flags=re.MULTILINE)
    if count != 1:
        raise SystemExit(f"Expected exactly one match in {path!r}, found {count}: {pattern!r}")
    p.write_text(changed, encoding="utf-8")


replace_pattern(
    "app/build.gradle.kts",
    r'^(\s*)applicationId\s*=\s*"[^"]+"\s*$',
    lambda m: f'{m.group(1)}applicationId = "com.greatwhitestreams.tv"',
)

# OwnTV's localized string layout changes upstream. Brand the Android launcher label directly in
# the manifest instead of depending on where app_name happens to live in a particular upstream tag.
replace_pattern(
    "app/src/main/AndroidManifest.xml",
    r'^(\s*)android:label\s*=\s*"@string/app_name"\s*$',
    lambda m: f'{m.group(1)}android:label="Great White Streams TV"',
)

print("Great White build identity applied:")
print("  applicationId: com.greatwhitestreams.tv")
print("  app name: Great White Streams TV")
