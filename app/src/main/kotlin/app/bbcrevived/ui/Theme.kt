package app.bbcrevived.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

/** Accent presets (name to hex). "dynamic" means wallpaper colours (Material You). */
val AccentPresets = listOf(
    "Indigo" to "#3B3FA8", "Blue" to "#1565C0", "Teal" to "#00796B", "Green" to "#2E7D32",
    "Orange" to "#E65100", "Red" to "#C62828", "Pink" to "#AD1457", "Purple" to "#6A1B9A",
)

fun parseAccent(hex: String): Color? = runCatching {
    Color(android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex"))
}.getOrNull()

/** A light/dark colour scheme derived from one seed colour (an approximation of tonal palettes). */
private fun seedScheme(seed: Color, dark: Boolean): ColorScheme {
    val base = if (dark) Color(0xFF121212) else Color(0xFFFFFBFF)
    val surface = lerp(base, seed, if (dark) 0.07f else 0.03f)
    val primary = if (dark) lerp(seed, Color.White, 0.45f) else seed
    val onPrimary = if (primary.luminance() > 0.5f) Color.Black else Color.White
    val container = lerp(surface, seed, if (dark) 0.38f else 0.16f)
    val onSurface = if (dark) Color(0xFFE6E1E5) else Color(0xFF1C1B1F)
    val variant = lerp(onSurface, surface, 0.3f)
    val secondary = lerp(primary, if (dark) Color.White else Color.Gray, 0.35f)
    return if (dark) darkColorScheme(
        primary = primary, onPrimary = onPrimary, primaryContainer = container, onPrimaryContainer = onSurface,
        secondary = secondary, background = surface, surface = surface, onSurface = onSurface, onSurfaceVariant = variant,
        surfaceContainer = lerp(surface, seed, 0.10f), surfaceContainerHigh = lerp(surface, seed, 0.14f),
    ) else lightColorScheme(
        primary = primary, onPrimary = onPrimary, primaryContainer = container, onPrimaryContainer = onSurface,
        secondary = secondary, background = surface, surface = surface, onSurface = onSurface, onSurfaceVariant = variant,
        surfaceContainer = lerp(surface, seed, 0.07f), surfaceContainerHigh = lerp(surface, seed, 0.11f),
    )
}

/** Material You by default; a chosen accent overrides it. [mode] is "system", "light" or "dark". */
@Composable
fun RevivedTheme(accent: String = "dynamic", mode: String = "system", content: @Composable () -> Unit) {
    val dark = when (mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    val seed = if (accent == "dynamic") null else parseAccent(accent)
    val scheme = when {
        seed != null -> seedScheme(seed, dark)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        else -> seedScheme(Color(0xFF3B3FA8), dark)
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
