# Great White Streams TV

Great White Streams TV is a customized Android TV IPTV client built from the open-source [OwnTV](https://github.com/ahXN00/OwnTV) codebase.

## Fork policy

- Upstream Kotlin namespace stays `tv.own.owntv` to keep future OwnTV merges manageable.
- The shipped Android application ID is `com.greatwhitestreams.tv`, so Great White Streams TV can be installed beside the older GWS applications and beside stock OwnTV.
- Great White releases are published from this repository and the in-app updater checks this repository only.
- Production releases must always use the same permanent Great White signing key. Losing or replacing that key prevents Android from accepting future in-place updates over existing installs.
- The app checks for updates shortly after startup by default. Android/Fire TV still controls the final install confirmation.

## Branding

Great White uses a blue-black `#0A0E14` base and aqua `#2DE2C4` brand/focus color, with a TV + abstract shark-fin mark.

## Licensing

OwnTV is GPLv3 software. This fork remains GPLv3 and retains the upstream license and source history. Great White customizations distributed as part of this fork are distributed under the same GPLv3 terms.
