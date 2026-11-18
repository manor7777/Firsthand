package app.bbcrevived

import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.bbcrevived.data.DeepLinks
import app.bbcrevived.notify.Alerts
import app.bbcrevived.platform.ExternalBrowser
import app.bbcrevived.ui.AppRoot
import app.bbcrevived.ui.RevivedTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File

class MainActivity : ComponentActivity() {
    private val vm by lazy { androidx.lifecycle.ViewModelProvider(this)[AppViewModel::class.java] }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Alerts.storyFrom(intent)?.let { vm.pendingOpen.value = it } ?: handleLink(intent, launching = false)
    }

    /** A bbc.com / bbc.co.uk /news link tapped somewhere else. Anything we can't show goes to the browser, never back here. */
    private fun handleLink(i: android.content.Intent?, launching: Boolean) {
        if (i?.action != android.content.Intent.ACTION_VIEW) return
        when (val link = DeepLinks.parse(i.dataString)) {
            is DeepLinks.Link.Article, is DeepLinks.Link.Live -> vm.openLink(link)
            else -> {
                i.dataString?.let { ExternalBrowser.open(this, it) }
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
                    this.text = "BBC Revived crashed last time. Copy this and send it over. Relaunch the app to try again.\n\n$text"
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
            RevivedTheme(accent, mode) { AppRoot(vm) }
        }
    }
}
