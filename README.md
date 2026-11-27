# Firsthand

A private, sideloaded Android news reader that pulls from the sources you choose, built with Kotlin, Jetpack Compose and Material You. There is no account and no server: the app fetches everything itself, straight from the publishers.

**Unofficial.** Firsthand is not affiliated with, endorsed by or sponsored by any publisher. It uses its own icon, ships no publisher logos, and is never published. Content is fetched from the public feeds and pages of the sources you add, for personal use only.

## Screenshots
<!-- screenshots:start -->
<table>
<tr><td align="center"><img src="docs/screenshots/01-home.png" width="190" alt="Home"><br><sub>Home</sub></td><td align="center"><img src="docs/screenshots/02-article.png" width="190" alt="Reader"><br><sub>Reader</sub></td><td align="center"><img src="docs/screenshots/03-my-news.png" width="190" alt="My News"><br><sub>My News</sub></td><td align="center"><img src="docs/screenshots/05-sources.png" width="190" alt="Your sources"><br><sub>Your sources</sub></td></tr>
<tr><td align="center"><img src="docs/screenshots/06-add-source.png" width="190" alt="Add a source"><br><sub>Add a source</sub></td><td align="center"><img src="docs/screenshots/08-my-news-source.png" width="190" alt="Another source's stories"><br><sub>Another source's stories</sub></td><td align="center"><img src="docs/screenshots/09-article-source.png" width="190" alt="Writers and share"><br><sub>Writers and share</sub></td></tr>
</table>
<!-- screenshots:end -->

## Features
- **News sources.** Pick your own: a built-in list (regional and national UK papers such as the Reach titles, Guardian, Sky News, Independent, Mail and Metro, plus world and tech sources such as Ars Technica, The Verge and The Register) or any RSS/Atom feed address you paste in. Add and remove them in **Settings > News sources**; each source's sections appear in *My News* under its name. Every story card shows its source as a pill ("BBC News", "Sky News"), with its section and age, plus BREAKING or LIVE pills when a source flags them.
- **BBC News is the default source.** A fresh install starts with it added. Remove it like any other source and nothing from it is shown or loaded (Home becomes a "For you" page built from your other sources, and its links go to your browser). It sits first in the *Add a source* list under *Default sources*; adding it back restores your topics.
- **Reader.** A native reader for articles, with live pages and in-app video where a source provides them. Pages that can't be read as text fall back to the story's summary and an *Open original* button. Live pages show their posts natively as text and pictures (no embedded website), refreshing every 30 seconds. Links inside a video page's write-up, such as BBC's *More on this story*, are tappable and open the linked article in the reader.
- **Select, share and block from the reader.** Press and hold to select text in an article, live page or video page and adjust the handles as usual; the menu offers Copy, Select all, Share and **Block keyword** (choose Subject or Story scope, then it's added to *Settings > Blocked keywords*). A Share button beside the story counter shares the headline and link. Note that Share and Block keyword pass the selection through the clipboard, so it replaces whatever you last copied.
- **Writers.** Credited writers are tappable: open their page, follow them, or block them (their articles, including co-written ones, are hidden everywhere and from alerts). For most sources a writer's page lists their stories from the feeds you follow.
- **Blocked keywords.** Hide stories about something you don't want to see, in **Settings > Blocked keywords**, with a scope per keyword:
  - *Subject* matches the headline or the story's topic tags, i.e. what the story is about. Blocking "Justin Bieber" hides a story about him, but not one that only mentions him in passing in its summary.
  - *Story* matches anywhere in the headline, summary or tags, even in passing.

  Matching is whole-word and ignores case ("Bieber" won't hide "Bieberstein"). Blocked stories are hidden in every list, search, and from alerts.
- **Bottom bar.** One wide glass bar with **For you**, **Breaking** (BBC News' breaking and most-read lists; only shown while BBC News is a source), **My News** and **Search**. **Press and hold a button and drag it to rearrange the bar**; the order is saved (*Settings > General > Bottom bar > Reset button order* restores the default). The first button is your home page, or the second if Search is first. A one-time tip explains this the first time you open the app after installing or updating. Each page's title names the tab you are on, and the Settings cog is at the top right of each page. Like My News, For you has chips under the title to filter by topic, and Breaking has chips to show just *Breaking now* or *Most read*. Tapping Search turns the bar into the search field.
- **Follows.** Topics, sections, writers and keywords appear in *My News* and *For you*.
- **Categories are merged across sources.** In *My News*, sections that mean the same thing are one group: BBC *Tech*, Sky *Technology* and Guardian *Technology* become a single **Tech** list, newest first (likewise World, Business, which also takes Money and Business Affairs, Football/Soccer, Culture with TV, Film and Celebrity, Science, Health, Crime, and more). Sections with no common category, such as a front page or a local section, stay on their own. Whenever more than one source is in use, every story card carries a pill naming its source.
- **Opens links from your sources.** Tap one of your sources' articles in Google Discover, a browser or another app and it opens in Firsthand's reader (see *Opening links in the app*). *Share > Firsthand* works too.
- **Videos on other sites.** A page that carries its own video (a standard `VideoObject` in the page data, a `<video>` tag or an `og:video` file link, as news sites such as NBC News do) shows a native player above the headline, with the same controls as BBC clips; if the stream won't play there is an *Open original* button. Embedded players from YouTube and the like aren't picked up.
- **Instagram posts and ads on other sites.** An Instagram post embedded in a shared article (the standard `instagram-media` quote or an Instagram frame) shows as the live post inside the story. Banner ads and sponsored images in a shared page (ad slots, ad-network pictures, stock banner sizes) are left out.
- **Read any website.** *Share > Firsthand* from a browser or any app opens that page in the reader, from any site (not only your sources), without its cookie notices, ads or pop-ups: the page is fetched and only the article text and pictures are shown (see *How it gets the news*). The source name comes from the page itself ("Financial Times", not the web address). When a page gives no text (a paywall, usually) the app tries the newest saved copy on archive.ph; best effort, many sites still won't give it up and then say so and show an *Open original* button. Social, video and map links open in your browser. Switch it off in *Settings > Links > Read shared websites*.
- **Search** across what's loaded (and live search where a source supports it), and customisable **alerts** with pictures (see *Alerts*).
- **Tidy settings.** Settings are grouped into five glass tabs: *General* (theme, bottom bar order, accent colour, updates, backup, about), *Sources* (your sources), *Links* (opening links in the app and reading shared websites), *Blocking* (writers and keywords) and *Alerts* (all alert options, as cards).
- **Backup.** Export/import your settings (`firsthand-settings.json`: sources, follows, blocked writers and keywords, theme), and in-app updates from GitHub Releases.

## How it gets the news
- **RSS is the backbone.** Every added source is an RSS (or Atom) feed. Each story's headline, summary, picture, time, topic tags and credited writers come from the feed. The Reach titles serve any section as a feed by adding `?service=rss` to the section's address, which is how their sections are listed. Feeds that don't tag their stories still get sections where the article addresses contain one (a dated `/security/2026/11/18/...` link is shown as *Security*). Where a feed includes the full article text it is used directly; otherwise the story's page is fetched when you open it and the article text is read from it. There are no per-site rules, so unusual layouts may fall back to the summary. Some publishers refuse automatic page requests (bot checks): pages are fetched with a mainstream mobile-browser user agent, and if a site still refuses or sends back no article, Firsthand loads that one page in a hidden in-app browser, the same way the phone's own browser would, and reads the text from the rendered result. This applies to every source, feeds added by address included. Text found that way is kept so the story opens instantly next time, and nothing is loaded in the hidden browser while you're offline.
- **The default source** (BBC News) uses its public RSS feeds for sections and topics, plus the data embedded in its web pages for the headline lists, articles, live pages and videos, and its search for keywords and writer pages.
- Everything fetched is cached on the phone so it still opens offline.

## Alerts
Alerts work across BBC News and every source you've added from the list (feeds added by address are left out, since there's no reliable way to tell what's breaking on them). Everything is set up in **Settings > Alerts**, one card per topic:
- **Alerts:** the master switch, how often to check, and a test alert.
- **Breaking news:** BBC News uses its own breaking flag. Other feeds have none, so a story counts as breaking when its headline starts with *BREAKING:*, *Breaking news -*, *Just in*, *Urgent* or *News alert*, or it's tagged that way. Or choose *Top story changes*, which alerts when a source's lead story changes. Optionally only for topics you follow.
- **Sources:** switch each source on or off for alerts.
- **Topics:** alert on any new story in a shared category (Tech, World, Business, Sport, Football and so on) from every source at once, using each source's matching section.
- **Keywords:** alert when a headline or tag (Subject) or the summary as well (Story) mentions a word or name, in any source.
- **Quiet hours:** mute alerts overnight; stories that arrive meanwhile are skipped, not queued.

Blocked writers and blocked keywords are never alerted. Switching a rule on only learns what is current, so you don't get a burst, and a story is only ever notified once however many rules match it. Alerts show the story's picture (large when expanded, a thumbnail when collapsed); if it has none, the alert uses the page's preview image, or a generic card drawn by the app, so an alert is never text-only.

Without a server to push them, the app checks on a schedule. It arms an Android alarm that is allowed to fire while the phone is idle, then runs an expedited background check; WorkManager's periodic job stays as a backup, and a reboot or app update re-arms the alarm. This does not need battery optimisation turned off, but Android can still hold a check back a few minutes while the phone sleeps: setting the app's battery use to *Unrestricted* is the most prompt, and truly instant delivery would need a push server.

## Opening links in the app
Article links from every source you've added open in Firsthand's native reader. Google share links (`share.google`, `search.app`) are followed first and kept only if they lead to one of your sources. Home and section pages, and anything else, still open in your browser. You can also Share any such link to Firsthand.

Android 12+ never auto-routes unverified web links to an app, and Android can't be told about new domains while an app is running, so this works in two steps:
1. The app declares each built-in source's domains in its manifest as a *disabled* alias, and enables the alias when you add that source (and disables it when you remove it). That is what makes the source appear in Android's list. After you add a source the app offers to take you to the right screen.
2. You switch it on once: **Settings > Links > Open link settings** (or system Settings > Apps > Firsthand > Open by default), turn on *Open supported links*, tap *Add link* and tick the addresses. If another app from the same publisher is installed, turn off its links there.

Any other website can be shared to Firsthand and is read the same way (*Read shared websites*); its name in the story is taken from its address.

Feeds added **by address** can't be picked up automatically (their domain isn't known when the app is built), but sharing one of their articles to Firsthand opens it in the reader.

## Updates
**Settings > Updates** checks this repo's latest GitHub Release, downloads the APK, verifies its SHA-256 and installs it over the current app (Android's package installer; it asks once to allow "Install unknown apps"). The repository must be public for this to work (no token is stored).

### Releasing
1. Add four repository secrets (Settings > Secrets and variables > Actions): `KEYSTORE_B64` (base64 of the keystore), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. A stable key is required: Android refuses an update signed with a different key than the installed app. Without the secrets builds fall back to the debug key and are published only as pre-releases (never offered by the updater).
2. Actions > *Build APK* > Run workflow > tick **release**. It builds, tests, signs and publishes release `v0.1.<build number>` with `firsthand-0.1.<build number>.apk`. The build number is the CI run number plus a fixed offset and becomes the app's `versionCode`, so every build is a new patch version.

Every push also runs the unit tests and builds an APK artifact. The **Screenshots** workflow (run by hand from the Actions tab, dark or light) boots an Android emulator, tours the main screens, commits the pictures to `docs/screenshots/` and refreshes the section at the top of this README. Keep the keystore safe and out of the repo: losing it means reinstalling once.

## Licence and FOSS compliance
GPL-3.0-or-later (see `LICENSE`). Every dependency is FOSS (AndroidX, Kotlin, kotlinx.serialization, OkHttp, Coil, Haze: Apache-2.0; jsoup: MIT). No Google Play Services, analytics, ads, crash reporters or proprietary binaries; the APK embeds no dependency-info blob. Publishers' content is not covered by this licence and is not bundled.
