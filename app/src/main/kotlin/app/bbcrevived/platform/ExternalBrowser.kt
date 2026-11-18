package app.bbcrevived.platform

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

/** Everything that has to leave the app: web pages go to a real browser, never back to us (we own BBC News links). */
object ExternalBrowser {
    /** Opens [url] in the user's default browser, explicitly skipping this app so a BBC link can't bounce back in. */
    fun open(ctx: Context, url: String) {
        val pm = ctx.packageManager
        // Probe with a non-BBC address so we find the default browser even though we handle bbc.* ourselves.
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)
        val default = pm.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        val pkg = default?.takeIf { it != "android" && it != ctx.packageName }
            ?: pm.queryIntentActivities(probe, PackageManager.MATCH_ALL).map { it.activityInfo.packageName }.firstOrNull { it != ctx.packageName }
        if (pkg == null) { Toast.makeText(ctx, "No browser found to open this link", Toast.LENGTH_LONG).show(); return }
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(i) }.onFailure { Toast.makeText(ctx, "Couldn't open the link", Toast.LENGTH_LONG).show() }
    }

    /** Which link addresses the user has switched on for this app (Settings > Open by default), or null if unknown. */
    fun linkStatus(ctx: Context): LinkStatus? = runCatching {
        val dvm = ctx.getSystemService(android.content.pm.verify.domain.DomainVerificationManager::class.java) ?: return null
        val st = dvm.getDomainVerificationUserState(ctx.packageName) ?: return null
        val off = st.hostToStateMap.filterValues { it == android.content.pm.verify.domain.DomainVerificationUserState.DOMAIN_STATE_NONE }.keys
        LinkStatus(st.isLinkHandlingAllowed, st.hostToStateMap.size, off.sorted())
    }.getOrNull()

    data class LinkStatus(val allowed: Boolean, val total: Int, val off: List<String>) {
        val allOn get() = allowed && off.isEmpty()
    }

    /**
     * Android 12+ only routes unverified web links to an app once the user ticks the domains in
     * Settings > Apps > Firsthand > Open by default. This jumps straight to that screen.
     */
    fun openDefaultLinkSettings(ctx: Context) {
        val i = Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(i) }.onFailure {
            runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}
