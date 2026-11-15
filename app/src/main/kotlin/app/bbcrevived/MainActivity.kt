package app.bbcrevived

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.bbcrevived.ui.AppRoot
import app.bbcrevived.ui.RevivedTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { RevivedTheme { AppRoot() } }
    }
}
