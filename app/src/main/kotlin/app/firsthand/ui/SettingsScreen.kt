package app.firsthand.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import app.firsthand.AppViewModel.UpdateState
import app.firsthand.platform.ExternalBrowser
import app.firsthand.update.UpdateChecker
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import app.firsthand.data.Source
import app.firsthand.data.Sources

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    accent: String, mode: String,
    onAccent: (String) -> Unit, onMode: (String) -> Unit,
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
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            Heading("Theme")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (k, l) ->
                    FilterChip(mode == k, { onMode(k) }, { Text(l) })
                }
            }
            Heading("Accent colour")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Swatch("Wallpaper (Material You)", null, accent == "dynamic") { onAccent("dynamic") }
                AccentPresets.forEach { (name, h) -> Swatch(name, parseAccent(h), accent.equals(h, true)) { onAccent(h) } }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(hex, { hex = it.take(7) }, Modifier.weight(1f), singleLine = true, label = { Text("Custom hex, e.g. #FF6A00") })
                FilledTonalButton({ if (parseAccent(hex) != null && Regex("#?[0-9A-Fa-f]{6}").matches(hex)) onAccent(if (hex.startsWith("#")) hex.uppercase() else "#" + hex.uppercase()) }) { Text("Apply") }
            }
            Heading("News sources")
            Text("Add publishers that offer RSS feeds: local papers, national titles, world news. Their sections appear in My News under the publisher's name, and their writers can be followed or blocked like BBC ones.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            ToggleRow("BBC News", if (sources.bbcOn) "On: Home headlines, topics, BBC writers, search and alerts." else "Off: nothing from the BBC is shown or loaded. Your BBC topics are kept and come back when you switch it on.", sources.bbcOn, sources.setBbc)
            if (sources.added.isEmpty()) Text("No other sources added yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            sources.added.forEach { src ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(src.name, style = MaterialTheme.typography.bodyLarge)
                        Text(if (src.custom) "Added by address" else "${src.feeds.size} sections · ${src.group}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton({ sources.remove(src) }) { Text("Remove") }
                }
            }
            SourcePicker(sources.added, sources.add)
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
            Heading("Notifications")
            // Whatever needs notifications asks for the permission first, then carries on. If Android won't show the
            // prompt any more (denied before), open this app's notification settings instead.
            var afterGrant by remember { mutableStateOf<(() -> Unit)?>(null) }
            val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
                if (ok) afterGrant?.invoke()
                else {
                    status = "Notifications are off for Firsthand. Turn them on here, then try again."
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
            ToggleRow("Breaking news alerts", "Checks in the background and notifies you.", alerts.enabled) { on ->
                if (!on) alerts.setEnabled(false) else withNotifications { alerts.setEnabled(true) }
            }
            ToggleRow("Only from topics I follow", "Skip breaking stories outside My News.", alerts.onlyFollows, alerts.setOnlyFollows)
            Text("Alert on", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(alerts.mode == "breaking", { alerts.setMode("breaking") }, { Text("Breaking only") })
                FilterChip(alerts.mode == "top", { alerts.setMode("top") }, { Text("Top story changes") })
            }
            Text("Check every", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15 to "15 min", 30 to "30 min", 60 to "1 h", 180 to "3 h").forEach { (m, l) ->
                    FilterChip(alerts.interval == m, { alerts.setInterval(m) }, { Text(l) })
                }
            }
            OutlinedButton({ withNotifications { alerts.sendTest() } }) { Text("Send a test alert") }
            Text("Firsthand has no server, so it checks for news itself on the schedule above. Android may still hold a check back a few minutes while the phone is asleep; setting this app's battery use to Unrestricted makes it the most prompt. BBC doesn't publish a breaking flag, so \"Top story changes\" is the fallback. Alerts cover BBC News only.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton({ runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", ctx.packageName, null))) } }) { Text("Open app settings (battery)") }
            Heading("BBC links")
            Text("Open bbc.com and bbc.co.uk News links (plus Weather and Sport articles, and Google share links to them) from other apps in Firsthand. Android needs this switched on once: tap below, tap \"Add link\" and tick every address (the BBC ones, plus share.google and search.app for links shared from Google). Android doesn't let unofficial apps switch this on themselves. If the official BBC News app is installed, turn its links off there. You can also share any BBC link to Firsthand.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Heading("Backup")
            Text("Export your followed topics, sections, writers, keywords, added sources, blocked writers, theme and accent to a file, or restore them from one.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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

class BlockedUi(val names: List<String>, val unblock: (String) -> Unit)

class SourcesUi(
    val added: List<Source>, val add: (Source) -> Unit,
    val addCustom: (String, String, (String) -> Unit) -> Unit, val remove: (Source) -> Unit,
    val bbcOn: Boolean, val setBbc: (Boolean) -> Unit,
)

/** Drop-down of catalogue publishers not added yet, grouped (Reach titles, UK national, world, tech). Picking one adds it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourcePicker(added: List<Source>, onAdd: (Source) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val available = remember(added) { Sources.catalog.filter { c -> added.none { it.id == c.id } } }
    ExposedDropdownMenuBox(open, { open = it }) {
        OutlinedTextField(
            "", {}, Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(), readOnly = true,
            label = { Text("Add a source") }, placeholder = { Text("Choose a publisher") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
        )
        ExposedDropdownMenu(open, { open = false }) {
            if (available.isEmpty()) Text("Everything in the list is added.", Modifier.padding(16.dp))
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
