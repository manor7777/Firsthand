package app.bbcrevived

import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.bbcrevived.ui.AppRoot
import app.bbcrevived.ui.RevivedTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File

class MainActivity : ComponentActivity() {
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
        setContent {
            val vm: AppViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            val accent by vm.accent.collectAsStateWithLifecycle()
            val mode by vm.themeMode.collectAsStateWithLifecycle()
            RevivedTheme(accent, mode) { AppRoot(vm) }
        }
    }
}
