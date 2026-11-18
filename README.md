# BBC Revived

A private, sideloaded Android news reader in the style of BBC News, built with Kotlin, Jetpack Compose and Material You.

**Unofficial.** This project is not affiliated with, endorsed by or sponsored by the BBC. It uses its own icon, ships no BBC logos, and is never published. Content is fetched from the BBC's public feeds and pages for personal use only.

Builds: every push runs GitHub Actions and uploads an APK artifact (`versionName 0.1.<run number>`).

## Opening BBC links in the app
bbc.com and bbc.co.uk **News** links (`/news/articles/...`, `/news/live/...`, old `/news/<slug>-<id>` pages) tapped in other apps open in BBC Revived's native reader. Videos, section pages and the rest of bbc.* still open in your browser.
Android 12+ never auto-routes unverified web links to an app, and BBC's domains can't be verified by us, so switch it on once: **Settings > BBC links > Open link settings** (or system Settings > Apps > BBC Revived > Open by default), turn on *Open supported links* and tick the BBC addresses. If the official BBC News app is installed, turn off its links too.

## Updates
**Settings > Updates** checks this repo's latest GitHub Release, downloads the APK, verifies its SHA-256 and installs it over the current app (Android's package installer; it asks once to allow "Install unknown apps"). The repository must be public for this to work (no token is stored).

### Releasing
1. Add four repository secrets (Settings > Secrets and variables > Actions): `KEYSTORE_B64` (base64 of the keystore), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. A stable key is required: Android refuses an update signed with a different key than the installed app. Without the secrets builds fall back to the debug key and are published only as pre-releases (never offered by the updater).
2. Actions > *Build APK* > Run workflow > tick **release**. It builds, tests, signs and publishes release `v0.1.<run number>` with `bbc-revived-0.1.<run number>.apk`; `versionCode` is the run number, so every build is a new patch version.
Keep the keystore safe and out of the repo: losing it means reinstalling once.

## Licence and FOSS compliance
GPL-3.0-or-later (see `LICENSE`). Every dependency is FOSS (AndroidX, Kotlin, kotlinx.serialization, OkHttp, Coil, Haze: Apache-2.0). No Google Play Services, analytics, ads, crash reporters or proprietary binaries; the APK embeds no dependency-info blob. BBC content is not covered by this licence and is not bundled.
