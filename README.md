# Firsthand

A private, sideloaded Android news reader built on BBC News content, with Kotlin, Jetpack Compose and Material You.

> **Formerly BBC Revived.** The project moved from `manor7777/BBC-Revived` to `manor7777/Firsthand`. The Android package ID is still `app.bbcrevived` on purpose, so existing installs update in place instead of installing as a second app. Settings > Updates now follows this repo. Version numbers continue from the old repo (see *Releasing*).

## Features
- Native reader for BBC News articles, live pages and videos (HLS playback in-app), plus Weather and Sport articles.
- Opens BBC links from other apps and Google share links (see below); *Share > Firsthand* works from any app.
- Follows: topics and writers appear in *My News* and *For you*. Tap a byline for a writer page, a topic tag for a topic page.
- Block writers: their articles, including co-written ones, are hidden from lists and alerts.
- Live BBC search, and breaking-news alerts (background checks, optional follows-only filter).
- Settings export/import (`firsthand-settings.json`) and in-app updates from GitHub Releases.

**Unofficial.** This project is not affiliated with, endorsed by or sponsored by the BBC. It uses its own icon, ships no BBC logos, and is never published. Content is fetched from the BBC's public feeds and pages for personal use only.

Builds: every push runs GitHub Actions and uploads an APK artifact (`versionName 0.1.<build number>`).

## Opening BBC links in the app
bbc.com and bbc.co.uk **News** links (`/news/articles/...`, `/news/live/...`, `/news/videos/...`, old `/news/<slug>-<id>` pages), plus **Weather and Sport articles** (same page format), tapped in other apps open in Firsthand's native reader; videos play in-app. Google share links (`share.google`, `search.app`) are followed first and only kept if they lead to one of those. Section pages and the rest of bbc.* still open in your browser. You can also Share any BBC link to Firsthand.
Android 12+ never auto-routes unverified web links to an app, and BBC's domains can't be verified by us, so switch it on once: **Settings > BBC links > Open link settings** (or system Settings > Apps > Firsthand > Open by default), turn on *Open supported links* and tick the BBC addresses. If the official BBC News app is installed, turn off its links too.

## Updates
**Settings > Updates** checks this repo's latest GitHub Release, downloads the APK, verifies its SHA-256 and installs it over the current app (Android's package installer; it asks once to allow "Install unknown apps"). The repository must be public for this to work (no token is stored).

### Releasing
1. Add four repository secrets (Settings > Secrets and variables > Actions): `KEYSTORE_B64` (base64 of the keystore), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. A stable key is required: Android refuses an update signed with a different key than the installed app. Without the secrets builds fall back to the debug key and are published only as pre-releases (never offered by the updater).
2. Actions > *Build APK* > Run workflow > tick **release**. It builds, tests, signs and publishes release `v0.1.<build number>` with `firsthand-0.1.<build number>.apk`; `versionCode` is the build number, so every build is a new patch version. In this repo the build number is the run number **+ 100**, so releases here always outrank the ones published from the old BBC-Revived repo (the updater only offers a higher number). The old repo's workflow uses the plain run number.
Keep the keystore safe and out of the repo: losing it means reinstalling once.

## Licence and FOSS compliance
GPL-3.0-or-later (see `LICENSE`). Every dependency is FOSS (AndroidX, Kotlin, kotlinx.serialization, OkHttp, Coil, Haze: Apache-2.0). No Google Play Services, analytics, ads, crash reporters or proprietary binaries; the APK embeds no dependency-info blob. BBC content is not covered by this licence and is not bundled.
