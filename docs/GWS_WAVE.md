# GWS Wave

GWS Wave is the experimental TV-first branch of the Great White Streams Android player. It uses the existing playback, source-sync, profile, guide and catalog infrastructure while deliberately reworking the navigation and presentation around fast D-pad use.

## Product direction

The UX benchmark is the fast, low-friction behavior seen in strong Android TV IPTV clients. GWS Wave does **not** copy another application's source code, artwork, branding, layouts pixel-for-pixel, or proprietary assets. The goal is to reproduce useful interaction patterns with original GWS implementation and design.

## Primary TV flow

`Home -> Live TV / Guide / Movies / Series / Search -> content -> player`

The left rail stays compact while browsing and expands when focus moves onto it. Primary destinations are directly reachable instead of being buried behind secondary screens.

## Existing platform capabilities retained

Wave deliberately keeps the mature underlying implementation already present in this repository:

- Xtream Codes sources
- M3U playlist import and sync
- Stalker/portal source support
- XMLTV/EPG sources and guide grid
- Profiles and profile switching
- Favorites, history and playback progress
- Live TV preview and fast channel zapping
- Fullscreen guide/channel/history overlays
- Catch-up/timeshift support where the provider exposes it
- Movies and series catalogs
- Search
- Downloads
- ExoPlayer/Media3 and mpv playback paths
- Subtitle support
- Android TV home/Watch Next integration
- Remote/companion setup infrastructure
- Existing database, sync workers and update system

## Wave UX priorities

1. **Fast remote navigation** — predictable D-pad focus, minimal focus travel, no unnecessary intermediate screens.
2. **Immediate Home** — a real launch surface for continue-watching, recent/favorite live TV, trending and quick entry into each catalog.
3. **Live TV first** — category browsing, channel list, EPG context and channel switching remain available without repeatedly leaving playback.
4. **Direct Guide access** — Guide is a first-class destination and also remains available as an in-player overlay.
5. **Clean VOD flow** — Movies and Series keep category rails, poster browsing, details, resume state and engine fallback.
6. **Simple setup** — preserve username/password Xtream setup, M3U sources and remote/companion setup; make QR-assisted setup the preferred future onboarding path.
7. **Original GWS identity** — GWS Wave branding and theme, not a clone of another player's visual assets.

## Initial branch changes

- Gradle project renamed to `GWSWave`.
- Launcher/app label changed to `GWS Wave`.
- Home brand lockup changed to GWS Wave.
- Main navigation changed to Home, Live TV, Guide, Movies, Series, Search and Settings.
- Existing source, EPG, player, profile and catalog implementations remain intact.

## Next implementation passes

### Wave Home

Refine Home around a hero/continue row plus quick rails for Live TV, Favorites, Recently Watched, Movies and Series. Keep poster/card loading lazy and avoid expensive catalog work before first paint.

### Wave Live

Reduce the number of D-pad actions needed to move between category, channel and playback. Keep Left/Right player overlays for channel/category/history and expose Guide consistently.

### Wave onboarding

Build a GWS-branded setup path with three obvious choices: Xtream login, M3U URL/file and QR/remote setup. The QR path should transfer credentials/configuration securely rather than encode provider passwords in a persistent public URL.

### Wave polish

Create dedicated Wave launcher/banner assets, finalize the visual token set, tune focus animations for low-powered boxes, and test 720p/1080p/4K layouts on Android TV/Google TV/Fire TV style remotes.

## Safety / compatibility rule

During the Wave redesign, avoid replacing proven playback or sync code solely for visual reasons. UX work should be layered on top of the existing repositories/view models unless profiling or a reproducible bug shows the underlying implementation is the problem.
