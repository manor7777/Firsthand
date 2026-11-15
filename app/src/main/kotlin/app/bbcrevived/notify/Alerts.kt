package app.bbcrevived.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import app.bbcrevived.MainActivity
import app.bbcrevived.R
import app.bbcrevived.data.Categories
import app.bbcrevived.data.Prefs
import app.bbcrevived.data.Repository
import app.bbcrevived.data.Story
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

object Alerts {
    private const val CHANNEL = "breaking"
    private const val WORK = "breaking-check"

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Breaking news", NotificationManager.IMPORTANCE_HIGH))
    }

    /** (Re)schedules the periodic check. WorkManager minimum is 15 minutes and it survives reboots. */
    fun schedule(ctx: Context, enabled: Boolean, minutes: Int) {
        val wm = WorkManager.getInstance(ctx)
        if (!enabled) { wm.cancelUniqueWork(WORK); return }
        val req = PeriodicWorkRequestBuilder<BreakingWorker>(minutes.coerceAtLeast(15).toLong(), TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun canNotify(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun notify(ctx: Context, s: Story, title: String) {
        if (!canNotify(ctx)) return
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, s.url.hashCode(), intentFor(ctx, s), // unique request code per story so extras aren't merged
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title).setContentText(s.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(s.title))
            .setContentIntent(open).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(s.url.hashCode(), n) }
    }

    private const val X_URL = "story_url"
    private const val X_TITLE = "story_title"
    private const val X_SUMMARY = "story_summary"
    private const val X_IMAGE = "story_image"
    private const val X_CAT = "story_category"
    private const val X_LIVE = "story_live"

    /** Launch intent that opens the app straight onto this story. The test alert just opens the app. */
    fun intentFor(ctx: Context, s: Story): Intent = Intent(ctx, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (s.id == "test") return@apply
        data = android.net.Uri.parse(s.url)
        putExtra(X_URL, s.url); putExtra(X_TITLE, s.title); putExtra(X_SUMMARY, s.summary)
        putExtra(X_IMAGE, s.imageUrl); putExtra(X_CAT, s.category); putExtra(X_LIVE, s.isLive)
    }

    /** Rebuilds the story from an alert's intent, or null if the intent isn't an alert tap. */
    fun storyFrom(i: Intent?): Story? {
        val url = i?.getStringExtra(X_URL) ?: return null
        if (!url.startsWith("https://www.bbc.co.uk/")) return null
        return Story(url, i.getStringExtra(X_TITLE).orEmpty(), i.getStringExtra(X_SUMMARY).orEmpty(), url,
            i.getStringExtra(X_IMAGE), i.getStringExtra(X_CAT) ?: "News", 0L, i.getBooleanExtra(X_LIVE, false))
    }

    fun test(ctx: Context) = notify(ctx, Story("test", "This is how a breaking news alert will look", "", "https://www.bbc.co.uk/news", null, "News", 0L, breaking = true), "Breaking news (test)")
}

class BreakingWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        if (!prefs.alertEnabled.first()) return Result.success()
        val repo = Repository(applicationContext)
        val mode = prefs.alertMode.first()
        val home = repo.home()
        // "breaking": stories flagged breaking. "top": the lead story, for when no flag is exposed.
        var candidates = if (mode == "top") home.breaking.take(1) else home.breaking.filter { it.breaking }

        if (prefs.alertOnlyFollows.first() && candidates.isNotEmpty()) {
            val follows = prefs.categories.first()
            val urls = follows.filter { !Categories.isKeyword(it) }.flatMap { repo.followStories(it) }.map { it.url }.toSet()
            val kws = follows.filter(Categories::isKeyword).map { it.removePrefix(Categories.KW).lowercase() }
            candidates = candidates.filter { s -> s.url in urls || kws.any { (s.title + " " + s.summary).lowercase().contains(it) } }
        }

        val seen = prefs.alertSeen.first()
        val fresh = candidates.filter { it.url !in seen }
        // First ever run only records what's current, so enabling alerts doesn't fire a burst.
        if (seen.isNotEmpty()) fresh.take(3).forEach { Alerts.notify(applicationContext, it, if (it.breaking) "Breaking news" else "Top story") }
        prefs.setAlertSeen((seen + candidates.map { it.url }.filter { it !in seen }).takeLast(200).ifEmpty { listOf("-") })
        return Result.success()
    }
}
