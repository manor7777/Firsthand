# Firsthand

Android news reader (formerly BBC Revived). Package ID is `app.firsthand`; it was `app.bbcrevived` before v0.1.10x, so the old app is a separate install.

- **Keep `README.md` up to date.** Whenever a feature is added, changed or removed (or the name, release process, sources or settings change), update the README's *Features* / *How it gets the news* sections and any affected section in the same commit.
- News comes from RSS feeds. Added publishers are in `data/Sources.kt` (catalogue + custom feeds); BBC is a source that can be switched off.
- Hiding stories: blocked writers (by byline) and blocked keywords (`data/Blocking.kt`: *Subject* = headline or tags, *Story* = also summary) both feed `AppViewModel.hidden`, which every list, search and alert goes through. Keep new story lists behind `visible(...)`.
- Link handling: each catalogue source has hosts in `Sources.kt` and a disabled `<activity-alias>` in `AndroidManifest.xml` (generated). After editing `Sources.catalog`, regenerate with `REGEN_MANIFEST=1 ./gradlew testDebugUnitTest --tests '*ManifestAliasesTest*'` and commit the manifest. Never put `--` inside an XML comment.
- Releases come from the *Build APK* workflow (`workflow_dispatch`, tick `release`). Build number = run number + 100 in this repo. Signing secrets keep the `bbcrevived` alias; do not rename them.
- The Android SDK can't be downloaded in the cloud container, so the app builds only in CI. The pure-Kotlin data layer (`data/*.kt` except `Repository`/`Prefs`) can be unit-tested on a plain JVM.
- Develop on `main` in this repo.
