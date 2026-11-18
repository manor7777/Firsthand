package app.bbcrevived.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request

/** Downloads a release APK, checks its SHA-256, and installs it with the system PackageInstaller. */
object Installer {
    /** Last install outcome from the system, for the Settings screen (null = nothing to report). */
    val result = MutableStateFlow<String?>(null)

    /** Blocking; run on IO. Returns the verified APK file. */
    fun download(ctx: Context, client: OkHttpClient, r: UpdateChecker.Release, onProgress: (Float) -> Unit): File {
        val dir = File(ctx.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val out = File(dir, r.apkName)
        val req = Request.Builder().url(r.apkUrl).header("User-Agent", "BBCRevived").build()
        val md = MessageDigest.getInstance("SHA-256")
        client.newCall(req).execute().use { resp ->
            check(resp.isSuccessful) { "Download failed (HTTP ${resp.code})." }
            val body = resp.body!!
            val total = body.contentLength().takeIf { it > 0 } ?: r.size
            var done = 0L
            body.byteStream().use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        o.write(buf, 0, n); md.update(buf, 0, n); done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        val got = md.digest().joinToString("") { "%02x".format(it) }
        if (r.sha256 != null && !got.equals(r.sha256, true)) { out.delete(); error("The download is corrupt (checksum mismatch). Try again.") }
        return out
    }

    fun canInstall(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()

    /** Sends the user to the per-app "Install unknown apps" switch (needed once for any app that installs APKs). */
    fun openInstallPermission(ctx: Context) {
        runCatching {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun install(ctx: Context, apk: File) {
        val pi = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED) // silent for updates where allowed
        }
        val id = pi.createSession(params)
        pi.openSession(id).use { s ->
            apk.inputStream().use { i -> s.openWrite("update.apk", 0, apk.length()).use { o -> i.copyTo(o); s.fsync(o) } }
            val intent = Intent(ctx, InstallReceiver::class.java).setPackage(ctx.packageName)
            val pending = PendingIntent.getBroadcast(ctx, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            s.commit(pending.intentSender)
        }
    }
}

/** Receives PackageInstaller status: shows the system confirm screen when Android needs one, reports failures. */
class InstallReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(ctx: Context, i: Intent) {
        when (val status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (android.os.Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirm?.let { runCatching { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                Installer.result.value = "Waiting for you to confirm the install…"
            }
            PackageInstaller.STATUS_SUCCESS -> Installer.result.value = "Updated. BBC Revived will restart."
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                Installer.result.value = "Android refused the update because it was signed with a different key. Uninstall the app once and install the latest release."
            else -> Installer.result.value = "Install failed (${i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "code $status"})."
        }
    }
}
