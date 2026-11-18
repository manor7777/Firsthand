# Firsthand

Android news reader (formerly BBC Revived). Package ID stays `app.bbcrevived` so installs update in place.

- **Keep `README.md` up to date.** Whenever a feature is added, changed or removed (or the name, release process or settings change), update the README's *Features* list and any affected section in the same commit.
- Releases come from the *Build APK* workflow (`workflow_dispatch`, tick `release`). Build number = run number + 100 in this repo. Signing secrets keep the `bbcrevived` alias; do not rename them.
- Develop on `main` in this repo.
