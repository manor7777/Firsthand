# Firsthand

A private, sideloaded Android news reader built on BBC News content, with Kotlin, Jetpack Compose and Material You.

> **Formerly BBC Revived.** The project moved from `manor7777/BBC-Revived` to `manor7777/Firsthand`, and the Android package ID changed from `app.bbcrevived` to `app.firsthand`. Android treats a new package ID as a different app, so it installs next to the old one rather than updating it: export your settings from the old app (Settings > Backup), install Firsthand, import them, then uninstall the old one. From here on, updates come from this repo. Version numbers continue from the old repo (see *Releasing*).

## Features
- **News sources.** BBC News plus any publisher that offers RSS: a built-in list (Example Post and the other Reach titles, Guardian, Sky News, Independent, Mail, Metro, Al Jazeera, NPR, NYT and more) or any feed address you paste in. Add and remove them in **Settings > News sources**; each source's sections appear in My News under its name.
- **BBC News is optional.** Switch it off in Settings > News sources and nothing from the BBC is shown or loaded (Home becomes a "For you" page built from your sources). Your BBC topics are kept and come back when you switch it on.
- Native reader for articles, live pages and videos (BBC: HLS playback in-app), plus BBC Weather and Sport articles. Other publishers' stories are shown as readable text, with a button to open the original if a page can't be read.
- **Writers.** Credited writers are tappable on BBC and added sources alike: open their page, follow them, or block them (their articles, including co-written ones, are hidden everywhere and from alerts). For added sources, a writer's page lists their stories from the feeds you follow.
- Follows: topics, sections, writers and keywords appear in *My News* and *For you*.
- Opens BBC links from other apps and Google share links (see below); *Share > Firsthand* works from any app.
- Live BBC search, and breaking-news alerts for BBC News (see *Alerts*).
- Settings export/import (`firsthand-settings.json`, includes your sources) and in-app updates from GitHub Releases.

## How it gets the news
There is no Firsthand server and no account: the app fetches everything itself, straight from the publishers.
- **RSS is the backbone.** Every added source is just an RSS (or Atom) feed. Each story's headline, summary, picture, time and credited writers come from the feed. Reach titles (Example Post etc.) serve any section as a feed by adding `?service=rss` to the section's address, which is how their sections are listed. Where a feed includes the full article text it is used directly; otherwise the story's page is fetched when you open it and the article text is read from it (no per-site rules, so odd layouts may fall back to the summary and an *Open original* button).
- **BBC News** uses its public RSS feeds for sections and topics, plus the data embedded in bbc.co.uk pages for the Breaking / Most read lists, articles, live pages and videos, and BBC search for keywords and writer pages. BBC has no writer pages or breaking-news feed, so writer pages are built from search results that really credit the writer.
- Everything fetched is cached on the phone so it still opens offline.

## Alerts
Breaking-news alerts cover BBC News only. Without a server to push them, the app checks on a schedule. It arms an Android alarm that is allowed to fire while the phone is idle, then runs an expedited background check; WorkManager's periodic job stays as a backup, and a reboot or app update re-arms the alarm. This does not need battery optimisation turned off, but Android can still hold a check back a few minutes while the phone sleeps: setting the app's battery use to *Unrestricted* is the most prompt, and truly instant delivery would need a push server.

**Unofficial.** This project is not affiliated with, endorsed by or sponsored by the BBC. It uses its own icon, ships no BBC logos, and is never published. Content is fetched from the BBC's and your chosen publishers' public feeds and pages for personal use only. Not affiliated with or endorsed by any publisher.

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
GPL-3.0-or-later (see `LICENSE`). Every dependency is FOSS (AndroidX, Kotlin, kotlinx.serialization, OkHttp, Coil, Haze: Apache-2.0; jsoup: MIT). No Google Play Services, analytics, ads, crash reporters or proprietary binaries; the APK embeds no dependency-info blob. BBC and other publishers' content is not covered by this licence and is not bundled.
