package app.firsthand

import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.firsthand.data.DeepLinks
import app.firsthand.notify.Alerts
import app.firsthand.platform.ExternalBrowser
import app.firsthand.ui.AppRoot
import app.firsthand.ui.FirsthandTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm by lazy { androidx.lifecycle.ViewModelProvider(this)[AppViewModel::class.java] }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Alerts.storyFrom(intent)?.let { vm.pendingOpen.value = it } ?: handleLink(intent, launching = false)
    }

    /**
     * A link tapped elsewhere (bbc.com / bbc.co.uk /news, or a share.google / search.app link), or text shared to the
     * app. Google share links are resolved first. Anything we can't show goes to the browser, never back here.
     */
    private fun handleLink(i: android.content.Intent?, launching: Boolean) {
        if (i == null) return
        val raw = when (i.action) {
            android.content.Intent.ACTION_VIEW -> i.dataString
            android.content.Intent.ACTION_SEND -> DeepLinks.firstUrl(i.getStringExtra(android.content.Intent.EXTRA_TEXT))
            else -> return
        }
        if (raw == null) {
            android.widget.Toast.makeText(this, "No link found in what was shared", android.widget.Toast.LENGTH_LONG).show()
            if (launching) finish(); return
        }
        if (DeepLinks.parse(raw) == null && DeepLinks.isShortLink(raw)) {
            lifecycleScope.launch { route(vm.resolveShortLink(raw), launching) }
        } else route(raw, launching)
    }

    private fun route(url: String, launching: Boolean) {
        val link = DeepLinks.parse(url)?.takeIf { vm.bbcEnabled.value } // BBC removed: its links go to the browser
        val src = if (link == null) vm.linkSource(url) else null
        when {
            link != null && link !is DeepLinks.Link.Unsupported -> vm.openLink(link)
            src != null -> vm.openSourceLink(src, url)
            else -> {
                ExternalBrowser.open(this, url)
                if (launching) finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // If the previous run crashed, show the trace (selectable) once instead of launching, then clear it.
        val crash = File(filesDir, "crash.txt")
        if (crash.exists()) {
            val text = crash.readText(); crash.delete()
            setContentView(ScrollView(this).apply {
                setPadding(32, 96, 32, 32)
                addView(TextView(context).apply {
                    this.text = "Firsthand crashed last time. Copy this and send it over. Relaunch the app to try again.\n\n$text"
                    setTextIsSelectable(true); textSize = 11f
                })
            })
            return
        }
        enableEdgeToEdge()
        if (savedInstanceState == null) Alerts.storyFrom(intent)?.let { vm.pendingOpen.value = it } ?: handleLink(intent, launching = true)
        setContent {
            val accent by vm.accent.collectAsStateWithLifecycle()
            val mode by vm.themeMode.collectAsStateWithLifecycle()
            FirsthandTheme(accent, mode) { AppRoot(vm) }
        }
    }
}
