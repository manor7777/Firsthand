package app.firsthand.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveable
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import app.firsthand.AppViewModel.UpdateState
import app.firsthand.platform.ExternalBrowser
import app.firsthand.update.UpdateChecker
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import app.firsthand.data.BlockScope
import app.firsthand.data.Blocking
import app.firsthand.data.Categories
import app.firsthand.data.Source
import app.firsthand.data.Sources

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    accent: String, mode: String, homeDefault: String,
    onAccent: (String) -> Unit, onMode: (String) -> Unit, onHomeDefault: (String) -> Unit,
    exportJson: () -> String, importJson: (String) -> Boolean, alerts: AlertState, updates: UpdateUi, blocked: BlockedUi, sources: SourcesUi, onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    var status by remember { mutableStateOf<String?>(null) }
    var hex by remember { mutableStateOf("") }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) status = runCatching {
            ctx.contentResolver.openOutputStream(uri)!!.use { it.write(exportJson().toByteArray()) }
        }.fold({ "Settings exported." }, { "Export failed." })
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) status = runCatching {
            ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        }.getOrNull()?.let { if (importJson(it)) "Settings imported." else "That file isn't a Firsthand settings export." } ?: "Couldn't read that file."
    }
    BackHandlerCompat(onBack)
    // Settings are grouped into glass tabs (the same sliding pill as Home) so it isn't one long list.
    val tabs = listOf("Sources", "Blocking", "Alerts", "General")
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val hz = remember { HazeState() }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val headerH = 116.dp // back button + title row, the tab pill and the gaps between them
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            key(tab) { // each tab opens scrolled to the top
                Column(
                    Modifier.fillMaxSize().haze(hz).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)
                        .padding(top = topInset + headerH + 8.dp, bottom = 32.dp).navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    when (tab) {
                        0 -> {
                    Heading("News sources")
                    Text("Add publishers that offer RSS feeds: local papers, national titles, world news. Their sections appear in My News under the publisher's name, and their writers can be followed or blocked like BBC ones.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!sources.bbcOn && sources.added.isEmpty()) Text("No sources added. Pick one below.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (sources.bbcOn) SourceRow("BBC News", "Default source · Home headlines, topics, writers, search and alerts", sources.removeBbc)
                    sources.added.forEach { src ->
                        SourceRow(src.name, if (src.custom) "Added by address" else "${src.feeds.size} sections · ${src.group}") { sources.remove(src) }
                    }
                    // After adding a publisher, offer to switch on opening its links here (Android needs the user to do that once).
                    var linkPrompt by remember { mutableStateOf<String?>(null) }
                    SourcePicker(
                        sources.added, sources.bbcOn,
                        onAdd = { sources.add(it); linkPrompt = it.name },
                        onAddBbc = { sources.setBbc(true); linkPrompt = "BBC News" },
                    )
                    linkPrompt?.let { name ->
                        AlertDialog(
                            onDismissRequest = { linkPrompt = null },
                            title = { Text("Open $name links here?") },
                            text = { Text("So an article you tap in Google Discover, a browser or another app opens in Firsthand, Android needs you to switch it on once: tap Open settings, then \"Add link\" and tick the $name addresses. You can skip this and still read $name from My News.") },
                            confirmButton = { TextButton({ linkPrompt = null; ExternalBrowser.openDefaultLinkSettings(ctx) }) { Text("Open settings") } },
                            dismissButton = { TextButton({ linkPrompt = null }) { Text("Not now") } },
                        )
                    }
                    var feedUrl by remember { mutableStateOf("") }
                    var feedName by remember { mutableStateOf("") }
                    var feedMsg by remember { mutableStateOf<String?>(null) }
                    var checking by remember { mutableStateOf(false) }
                    OutlinedTextField(feedUrl, { feedUrl = it.trim() }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Or add any RSS feed address") },
                        placeholder = { Text("https://example.com/feed.xml") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                    OutlinedTextField(feedName, { feedName = it.take(80) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Name (optional)") })
                    Button(
                        { checking = true; feedMsg = null
                            sources.addCustom(feedUrl, feedName) { m -> feedMsg = m; checking = false; if (m.startsWith("Added")) { feedUrl = ""; feedName = "" } } },
                        enabled = feedUrl.isNotBlank() && !checking,
                    ) { Text(if (checking) "Checking…" else "Add feed") }
                    feedMsg?.let { Text(it, color = if (it.startsWith("Added")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
                    Heading("Opening links")
                    Text("Open article links from BBC News and the publishers you've added (and Google share links to them) in Firsthand when you tap them elsewhere, such as in Google Discover. Android needs this switched on once: tap below, tap \"Add link\" and tick every address (the publishers' ones, plus share.google and search.app for links shared from Google). Android doesn't let unofficial apps switch this on themselves, and it only offers publishers you've added. If the official BBC News app is installed, turn its links off there. Feeds added by address can't be opened this way, but sharing one of their articles to Firsthand works.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Read shared websites", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text("Share any web page to Firsthand to read it here without cookie notices, ads or pop-ups. Pages that can't be read, and social, video and map links, open in your browser. Subscriber-only articles can't be unlocked.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(sources.readAny, sources.setReadAny)
                    }
                    var links by remember { mutableStateOf(ExternalBrowser.linkStatus(ctx)) }
                    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { links = ExternalBrowser.linkStatus(ctx) }
                    links?.let { l ->
                        val text = when {
                            l.allOn -> "On: all ${l.total} addresses open here."
                            !l.allowed -> "Off: \"Open supported links\" is switched off."
                            else -> "Partly on: ${l.total - l.off.size} of ${l.total}. Still off: ${l.off.joinToString(", ")}"
                        }
                        Text(text, color = if (l.allOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                    }
                    Button({ ExternalBrowser.openDefaultLinkSettings(ctx) }) { Text("Open link settings") }
                        }
                        1 -> {
                    Heading("Blocked writers")
                    if (blocked.names.isEmpty()) Text("None. Block a writer from their page (tap their name under a headline).", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else {
                        Text("Articles they wrote or co-wrote are hidden everywhere.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        blocked.names.forEach { n ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(n, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                OutlinedButton({ blocked.unblock(n) }) { Text("Unblock") }
                            }
                        }
                    }
                    Heading("Blocked keywords")
                    Text("Hide stories about something you don't want to see. Choose how strictly to match:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Subject: the word is in the headline or the story's topic tags, so it's what the story is about. A story that only mentions it in passing stays.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Story: the word appears anywhere in the headline, summary or tags, even in passing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    var keyword by remember { mutableStateOf("") }
                    var keywordScope by remember { mutableStateOf(BlockScope.Subject) }
                    var keywordMsg by remember { mutableStateOf<String?>(null) }
                    OutlinedTextField(keyword, { keyword = it.take(60); keywordMsg = null }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Keyword or phrase, e.g. Justin Bieber") })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        BlockScope.entries.forEach { sc -> FilterChip(keywordScope == sc, { keywordScope = sc }, { Text(sc.label) }) }
                        Spacer(Modifier.weight(1f))
                        Button({ val m = blocked.addTerm(keyword, keywordScope); if (m == null) keyword = "" else keywordMsg = m }, enabled = keyword.isNotBlank()) { Text("Block") }
                    }
                    keywordMsg?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Blocking.parseAll(blocked.terms).forEach { rule ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(rule.term, style = MaterialTheme.typography.bodyLarge)
                                Text(if (rule.scope == BlockScope.Subject) "Subject: headline or tags" else "Story: headline, summary or tags", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedButton({ blocked.removeTerm(rule.line) }) { Text("Remove") }
                        }
                    }
                        }
                        2 -> {
                    AlertsTab(alerts) { status = it }
                        }
                        else -> {
                    Heading("Theme")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (k, l) ->
                            FilterChip(mode == k, { onMode(k) }, { Text(l) })
                        }
                    }
                    Heading("Open Home on")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("foryou" to "For you", "breaking" to "Breaking").forEach { (k, l) ->
                            FilterChip(homeDefault == k, { onHomeDefault(k) }, { Text(l) })
                        }
                    }
                    if (!sources.bbcOn) Text("Breaking is a BBC News page: with BBC News removed, Home is just For you.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Heading("Accent colour")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Swatch("Wallpaper (Material You)", null, accent == "dynamic") { onAccent("dynamic") }
                        AccentPresets.forEach { (name, h) -> Swatch(name, parseAccent(h), accent.equals(h, true)) { onAccent(h) } }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(hex, { hex = it.take(7) }, Modifier.weight(1f), singleLine = true, label = { Text("Custom hex, e.g. #FF6A00") })
                        FilledTonalButton({ if (parseAccent(hex) != null && Regex("#?[0-9A-Fa-f]{6}").matches(hex)) onAccent(if (hex.startsWith("#")) hex.uppercase() else "#" + hex.uppercase()) }) { Text("Apply") }
                    }
                    Heading("Updates")
                    Text("Installed version ${updates.installedName}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    when (val u = updates.state) {
                        UpdateState.Idle -> OutlinedButton(updates.check) { Text("Check for updates") }
                        UpdateState.Checking -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Text("Checking…")
                        }
                        UpdateState.UpToDate -> { Text("You're up to date.", color = MaterialTheme.colorScheme.primary); OutlinedButton(updates.check) { Text("Check again") } }
                        is UpdateState.Available -> {
                            Text("Version ${u.release.versionName} is available.", fontWeight = FontWeight.SemiBold)
                            if (u.release.notes.isNotBlank()) Text(u.release.notes.take(400), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button({ updates.install(u.release) }) { Text("Download and install") }
                        }
                        is UpdateState.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Downloading… ${(u.progress * 100).toInt()}%")
                            LinearProgressIndicator({ u.progress }, Modifier.fillMaxWidth())
                        }
                        UpdateState.Installing -> Text("Installing…", color = MaterialTheme.colorScheme.primary)
                        is UpdateState.Error -> { Text(u.message, color = MaterialTheme.colorScheme.error); OutlinedButton(updates.check) { Text("Try again") } }
                    }
                    updates.installResult?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                    Heading("Backup")
                    Text("Export your followed topics, sections, writers, keywords, added sources, blocked writers and keywords, theme and accent to a file, or restore them from one.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ export.launch("firsthand-settings.json") }) { Text("Export") }
                        OutlinedButton({ import.launch(arrayOf("application/json", "text/*", "*/*")) }) { Text("Import") }
                    }
                    status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                    Heading("About")
                    Text("Firsthand is an unofficial, private reader. It isn't affiliated with or endorsed by the BBC or any publisher you add. Open source under GPL-3.0-or-later; no tracking, ads or analytics.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            EdgeScrim(hz, atTop = true, Modifier.align(Alignment.TopCenter).height(topInset + headerH + 28.dp))
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PageHeaderRow("Settings", leading = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
                SlidingPill(hz, tabs.size, tab, Modifier.fillMaxWidth(), height = 46.dp) { i, sel ->
                    Box(Modifier.weight(1f).fillMaxHeight().tap { tab = i }, contentAlignment = Alignment.Center) {
                        Text(
                            tabs[i], fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                            color = if (sel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable private fun Heading(t: String) =
    Text(t.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))

@Composable
private fun Swatch(label: String, color: Color?, selected: Boolean, onClick: () -> Unit) {
    val fill = color ?: MaterialTheme.colorScheme.tertiary
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(fill).clickable(onClickLabel = label, onClick = onClick)
            .then(if (selected) Modifier.padding(0.dp) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (color == null) Text("✦", color = MaterialTheme.colorScheme.onTertiary)
        if (selected) Box(Modifier.size(44.dp).clip(CircleShape).background(Color.Transparent).then(Modifier), contentAlignment = Alignment.Center) {
            Surface(Modifier.size(44.dp), shape = CircleShape, color = Color.Transparent, border = BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface)) {}
            Text("✓", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable private fun BackHandlerCompat(onBack: () -> Unit) = androidx.activity.compose.BackHandler(onBack = onBack)

class BlockedUi(
    val names: List<String>, val unblock: (String) -> Unit,
    /** Stored keyword rules ("subject|Justin Bieber"); [addTerm] returns an error message or null; [removeTerm] takes a stored line. */
    val terms: List<String>, val addTerm: (String, BlockScope) -> String?, val removeTerm: (String) -> Unit,
)

class SourcesUi(
    val added: List<Source>, val add: (Source) -> Unit,
    val addCustom: (String, String, (String) -> Unit) -> Unit, val remove: (Source) -> Unit,
    val bbcOn: Boolean, val setBbc: (Boolean) -> Unit,
    val readAny: Boolean = true, val setReadAny: (Boolean) -> Unit = {},
) {
    val removeBbc: () -> Unit get() = { setBbc(false) }
}

@Composable
private fun SourceRow(name: String, sub: String, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onRemove) { Text("Remove") }
    }
}

/** Drop-down of catalogue publishers not added yet, grouped (Reach titles, UK national, world, tech). Picking one adds it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourcePicker(added: List<Source>, bbcOn: Boolean, onAdd: (Source) -> Unit, onAddBbc: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val available = remember(added) { Sources.catalog.filter { c -> added.none { it.id == c.id } } }
    ExposedDropdownMenuBox(open, { open = it }) {
        OutlinedTextField(
            "", {}, Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(), readOnly = true,
            label = { Text("Add a source") }, placeholder = { Text("Choose a publisher") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
        )
        ExposedDropdownMenu(open, { open = false }) {
            if (available.isEmpty() && bbcOn) Text("Everything in the list is added.", Modifier.padding(16.dp))
            // Default sources come first: BBC News is added on a fresh install and shows up here once removed.
            if (!bbcOn) {
                Text("DEFAULT SOURCES", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                DropdownMenuItem({ Text("BBC News") }, { open = false; onAddBbc() })
            }
            available.groupBy { it.group }.forEach { (group, list) ->
                Text(group.uppercase(), Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                list.forEach { s -> DropdownMenuItem({ Text(s.name) }, { open = false; onAdd(s) }) }
            }
        }
    }
}

class UpdateUi(
    val installedName: String, val state: UpdateState, val installResult: String?,
    val check: () -> Unit, val install: (UpdateChecker.Release) -> Unit,
)

class AlertState(
    val enabled: Boolean, val onlyFollows: Boolean, val mode: String, val interval: Int,
    val setEnabled: (Boolean) -> Unit, val setOnlyFollows: (Boolean) -> Unit,
    val setMode: (String) -> Unit, val setInterval: (Int) -> Unit, val sendTest: () -> Unit,
    val breaking: Boolean, val setBreaking: (Boolean) -> Unit,
    /** Sources that can send alerts: id to name (BBC News first when added, then the added catalogue sources). */
    val sources: List<Pair<String, String>>, val offSources: Set<String>, val toggleSource: (String) -> Unit,
    /** Shared categories (Tech, World, ...) to alert on across every source. */
    val categories: Set<String>, val toggleCategory: (String) -> Unit,
    /** Keyword alerts as stored lines ("subject|Bieber"); [addKeyword] returns an error message or null. */
    val keywords: List<String>, val addKeyword: (String, BlockScope) -> String?, val removeKeyword: (String) -> Unit,
    val quietEnabled: Boolean, val quietStart: Int, val quietEnd: Int, val setQuiet: (Boolean, Int, Int) -> Unit,
)

@Composable
private fun ToggleRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

/** A titled card, so a tab with many options reads as a few clear groups. */
@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun HourRow(label: String, hour: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        OutlinedButton({ onChange((hour + 23) % 24) }) { Text("\u2212") }
        Text("%02d:00".format(hour), Modifier.padding(horizontal = 14.dp), style = MaterialTheme.typography.titleMedium)
        OutlinedButton({ onChange((hour + 1) % 24) }) { Text("+") }
    }
}

/**
 * The Alerts tab. What can alert you, per card: the schedule, breaking news (per source), topics shared across sources,
 * keywords, and quiet hours. Anything that needs notifications asks for the permission first, then carries on.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlertsTab(alerts: AlertState, onStatus: (String) -> Unit) {
    val ctx = LocalContext.current
    var afterGrant by remember { mutableStateOf<(() -> Unit)?>(null) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) afterGrant?.invoke()
        else {
            // Android won't show the prompt again once denied: send the user to this app's notification settings.
            onStatus("Notifications are off for Firsthand. Turn them on here, then try again.")
            runCatching {
                ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName))
            }
        }
        afterGrant = null
    }
    fun withNotifications(action: () -> Unit) {
        if (app.firsthand.notify.Alerts.canNotify(ctx)) action()
        else { afterGrant = action; askPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
    }

    SectionCard("Alerts") {
        ToggleRow("Notify me about news", "Firsthand checks in the background and notifies you.", alerts.enabled) { on ->
            if (!on) alerts.setEnabled(false) else withNotifications { alerts.setEnabled(true) }
        }
        Text("Check every", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15 to "15 min", 30 to "30 min", 60 to "1 h", 180 to "3 h").forEach { (m, l) -> FilterChip(alerts.interval == m, { alerts.setInterval(m) }, { Text(l) }) }
        }
        OutlinedButton({ withNotifications { alerts.sendTest() } }) { Text("Send a test alert") }
        Text("Firsthand has no server, so it checks for news itself on the schedule above. Android may still hold a check back a few minutes while the phone is asleep; setting this app's battery use to Unrestricted makes it the most prompt.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton({ runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", ctx.packageName, null))) } }) { Text("Open app settings (battery)") }
    }

    SectionCard("Breaking news") {
        ToggleRow("Breaking news alerts", "BBC News uses its own breaking flag. For other sources, a headline or tag that says BREAKING.", alerts.breaking, alerts.setBreaking)
        if (alerts.breaking) {
            Text("Alert on", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(alerts.mode == "breaking", { alerts.setMode("breaking") }, { Text("Breaking only") })
                FilterChip(alerts.mode == "top", { alerts.setMode("top") }, { Text("Top story changes") })
            }
            ToggleRow("Only from topics I follow", "Skip breaking stories outside My News.", alerts.onlyFollows, alerts.setOnlyFollows)
        }
    }

    SectionCard("Sources") {
        Text("Alerts can come from BBC News and any source you've added from the list (not feeds added by address). Switch off the ones you don't want alerts from.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (alerts.sources.isEmpty()) Text("No sources to alert from yet. Add some in the Sources tab.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        alerts.sources.forEach { (id, name) -> ToggleRow(name, "Include in alerts", id !in alerts.offSources) { alerts.toggleSource(id) } }
    }

    SectionCard("Topics") {
        Text("Alert me about any new story in these topics, from every source above. Each source's matching section counts (BBC Tech, Sky Technology, and so on).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Categories.sharedCategories.forEach { c -> FilterChip(c in alerts.categories, { alerts.toggleCategory(c) }, { Text(c) }, Modifier.padding(bottom = 4.dp)) }
        }
    }

    SectionCard("Keywords") {
        Text("Alert me when a story mentions a word or name, in any source. Subject matches the headline or tags; Story also matches the summary.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        var word by remember { mutableStateOf("") }
        var wordScope by remember { mutableStateOf(BlockScope.Subject) }
        var wordMsg by remember { mutableStateOf<String?>(null) }
        OutlinedTextField(word, { word = it.take(60); wordMsg = null }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Keyword or phrase, e.g. Bank of England") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            BlockScope.entries.forEach { sc -> FilterChip(wordScope == sc, { wordScope = sc }, { Text(sc.label) }) }
            Spacer(Modifier.weight(1f))
            Button({ val m = alerts.addKeyword(word, wordScope); if (m == null) word = "" else wordMsg = m }, enabled = word.isNotBlank()) { Text("Add") }
        }
        wordMsg?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Blocking.parseAll(alerts.keywords).forEach { rule ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(rule.term, style = MaterialTheme.typography.bodyLarge)
                    Text(if (rule.scope == BlockScope.Subject) "Subject: headline or tags" else "Story: headline, summary or tags", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton({ alerts.removeKeyword(rule.line) }) { Text("Remove") }
            }
        }
    }

    SectionCard("Quiet hours") {
        ToggleRow("Mute alerts at night", "Stories that arrive during quiet hours are skipped, not saved for later.", alerts.quietEnabled) { alerts.setQuiet(it, alerts.quietStart, alerts.quietEnd) }
        if (alerts.quietEnabled) {
            HourRow("From", alerts.quietStart) { alerts.setQuiet(true, it, alerts.quietEnd) }
            HourRow("Until", alerts.quietEnd) { alerts.setQuiet(true, alerts.quietStart, it) }
        }
    }
}
