package app.firsthand.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import app.firsthand.MainActivity
import app.firsthand.R
import app.firsthand.data.Categories
import app.firsthand.data.Prefs
import app.firsthand.data.Repository
import app.firsthand.data.Story
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

object Alerts {
    private const val CHANNEL = "breaking"
    private const val WORK = "breaking-check"

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Breaking news", NotificationManager.IMPORTANCE_HIGH))
    }

    private const val WORK_NOW = "breaking-check-now"
    const val ACTION_CHECK = "app.firsthand.CHECK_BREAKING"

    private fun alarmIntent(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 1, Intent(ctx, AlertReceiver::class.java).setAction(ACTION_CHECK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * Arms the next check as an alarm that may fire while the phone is idle (Doze) with no battery exemption and no special
     * permission. WorkManager's periodic job alone is deferred into rare "maintenance windows" when the phone sits still,
     * which is why alerts used to turn up only once the phone was picked up. Each alarm re-arms the next one.
     */
    fun scheduleAlarm(ctx: Context, minutes: Int) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + minutes.coerceAtLeast(15) * 60_000L, alarmIntent(ctx))
    }

    private fun cancelAlarm(ctx: Context) { ctx.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent(ctx)) }

    /** Runs a check right now as expedited work (allowed to start promptly even in Doze), once at a time. */
    fun checkNow(ctx: Context) {
        val req = OneTimeWorkRequestBuilder<BreakingWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(WORK_NOW, ExistingWorkPolicy.KEEP, req)
    }

    /** (Re)schedules checks: the alarm chain for promptness, plus WorkManager's periodic job (min 15 min, survives reboots) as a safety net. */
    fun schedule(ctx: Context, enabled: Boolean, minutes: Int) {
        val wm = WorkManager.getInstance(ctx)
        if (!enabled) { wm.cancelUniqueWork(WORK); wm.cancelUniqueWork(WORK_NOW); cancelAlarm(ctx); return }
        scheduleAlarm(ctx, minutes)
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

/** Fires when an alarm goes off (or after a reboot / app update, which clear alarms): re-arms the next one, then checks now. */
class AlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        val pending = goAsync()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            try {
                val prefs = Prefs(ctx)
                if (prefs.alertEnabled.first()) {
                    Alerts.scheduleAlarm(ctx, prefs.alertInterval.first()) // first, so one failed check never breaks the chain
                    if (intent.action == Alerts.ACTION_CHECK) Alerts.checkNow(ctx)
                }
            } finally { pending.finish() }
        }
    }
}

class BreakingWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result { BreakingCheck.run(applicationContext); return Result.success() }
}

object BreakingCheck {
    /** One check at a time, so the alarm, the expedited job and the periodic safety net never alert twice for the same story. */
    private val lock = kotlinx.coroutines.sync.Mutex()

    suspend fun run(ctx: Context) = lock.withLock {
        val prefs = Prefs(ctx)
        if (!prefs.alertEnabled.first() || !prefs.bbcEnabled.first()) return@withLock // alerts cover BBC News only
        val repo = Repository(ctx)
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
        // Never alert about articles by a blocked writer (or co-written by one).
        val blocked = prefs.blockedAuthors.first().map({ app.firsthand.data.Author.key(it) }).toSet()
        if (blocked.isNotEmpty()) candidates = candidates.filterNot { s -> repo.byline(s.url).any { app.firsthand.data.Author.key(it.name) in blocked } }

        val seen = prefs.alertSeen.first()
        val fresh = candidates.filter { it.url !in seen }
        // First ever run only records what's current, so enabling alerts doesn't fire a burst.
        if (seen.isNotEmpty()) fresh.take(3).forEach { Alerts.notify(ctx, it, if (it.breaking) "Breaking news" else "Top story") }
        prefs.setAlertSeen((seen + candidates.map { it.url }.filter { it !in seen }).takeLast(200).ifEmpty { listOf("-") })
    }
}
