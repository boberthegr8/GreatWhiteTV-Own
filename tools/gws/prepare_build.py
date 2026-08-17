#!/usr/bin/env python3
"""Apply Great White Streams TV build identity without renaming OwnTV's Kotlin packages.

Keeping the upstream namespace/package tree intact makes future OwnTV merges far less painful.
The Android applicationId is changed at build time, which is what Android uses to decide whether
this is a separate installed app.
"""
from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one match in {path!r}, found {count}: {old!r}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")


replace_once(
    "app/build.gradle.kts",
    'applicationId = "tv.own.owntv"',
    'applicationId = "com.greatwhitestreams.tv"',
)

replace_once(
    "app/src/main/res/values/strings.xml",
    '<string name="app_name">OwnTV</string>',
    '<string name="app_name">Great White Streams TV</string>',
)

print("Great White build identity applied:")
print("  applicationId: com.greatwhitestreams.tv")
print("  app name: Great White Streams TV")
