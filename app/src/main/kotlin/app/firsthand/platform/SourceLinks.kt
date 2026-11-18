package app.firsthand.platform

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import app.firsthand.data.Source
import app.firsthand.data.Sources

/**
 * Android only lets an app claim web domains that its manifest declares, and cannot add any at run time. So the manifest
 * declares one disabled alias per catalogue publisher (and one for BBC), each with that publisher's domains. Adding a source
 * enables its alias, which makes its domains show up under Settings > Open by default for the user to switch on;
 * removing the source disables it again. (Feeds added by address have no alias: their links open via Share > Firsthand.)
 */
object SourceLinks {
    fun sync(ctx: Context, added: List<Source>, bbc: Boolean) {
        val want = added.filter { !it.custom }.map { it.id }.toSet()
        for (s in Sources.catalog) set(ctx, Sources.aliasName(s), s.id in want)
        set(ctx, Sources.BBC_ALIAS, bbc)
    }

    private fun set(ctx: Context, name: String, enabled: Boolean) = runCatching {
        val pm = ctx.packageManager
        val cn = ComponentName(ctx.packageName, name)
        // A component left at its manifest default reports DEFAULT, not ENABLED/DISABLED, so compare against "explicitly enabled".
        val on = pm.getComponentEnabledSetting(cn).let { it == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || (it == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && name == Sources.BBC_ALIAS) }
        if (on != enabled) pm.setComponentEnabledSetting(
            cn, if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP,
        )
    }
}
