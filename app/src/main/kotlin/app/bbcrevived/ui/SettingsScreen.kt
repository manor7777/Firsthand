package app.bbcrevived.ui

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    accent: String, mode: String,
    onAccent: (String) -> Unit, onMode: (String) -> Unit,
    exportJson: () -> String, importJson: (String) -> Boolean, alerts: AlertState, onBack: () -> Unit,
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
        }.getOrNull()?.let { if (importJson(it)) "Settings imported." else "That file isn't a BBC Revived settings export." } ?: "Couldn't read that file."
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
            Heading("Notifications")
            val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
                if (ok) alerts.setEnabled(true) else status = "Notifications are blocked for this app. Allow them in system settings."
            }
            ToggleRow("Breaking news alerts", "Checks in the background and notifies you.", alerts.enabled) { on ->
                if (!on) alerts.setEnabled(false)
                else if (android.os.Build.VERSION.SDK_INT >= 33 && !app.bbcrevived.notify.Alerts.canNotify(ctx)) askPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                else alerts.setEnabled(true)
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
            OutlinedButton({ alerts.sendTest() }) { Text("Send a test alert") }
            Text("Android runs background checks roughly on schedule but may delay them to save battery. BBC doesn't publish a breaking flag, so \"Top story changes\" is the fallback.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Heading("Backup")
            Text("Export your followed topics, keywords, theme and accent to a file, or restore them from one.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ export.launch("bbc-revived-settings.json") }) { Text("Export") }
                OutlinedButton({ import.launch(arrayOf("application/json", "text/*", "*/*")) }) { Text("Import") }
            }
            status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Heading("About")
            Text("BBC Revived is an unofficial, private reader. It isn't affiliated with or endorsed by the BBC. Open source under GPL-3.0-or-later; no tracking, ads or analytics.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
