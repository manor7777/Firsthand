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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import app.firsthand.MainActivity
import app.firsthand.R
import app.firsthand.data.Categories
import app.firsthand.data.DeepLinks
import app.firsthand.data.Prefs
import app.firsthand.data.Repository
import app.firsthand.data.Story
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
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

    /** Posts an alert, loading its picture first: the story's image, else the page's preview image, else a generic breaking-news graphic. */
    suspend fun notify(ctx: Context, s: Story, title: String) {
        if (!canNotify(ctx)) return
        val picture = withContext(Dispatchers.IO) { loadPicture(ctx, s) } ?: genericPicture(title)
        post(ctx, s, title, picture)
    }

    /** Builds and shows the notification (big picture when there is one). Does no network work, so it is safe anywhere. */
    fun post(ctx: Context, s: Story, title: String, picture: Bitmap?) {
        if (!canNotify(ctx)) return
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, s.url.hashCode(), intentFor(ctx, s), // unique request code per story so extras aren't merged
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title).setContentText(s.title)
            .setContentIntent(open).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH)
        if (picture != null) b.setLargeIcon(picture) // thumbnail when collapsed
            .setStyle(NotificationCompat.BigPictureStyle().bigPicture(picture).bigLargeIcon(null as Bitmap?).setSummaryText(s.title))
        else b.setStyle(NotificationCompat.BigTextStyle().bigText(s.title))
        runCatching { NotificationManagerCompat.from(ctx).notify(s.url.hashCode(), b.build()) }
    }

    private val http by lazy { okhttp3.OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS).build() }

    /** The story's own picture, or failing that the preview image from its page (BBC's is the branded share image). Null if neither loads. */
    private suspend fun loadPicture(ctx: Context, s: Story): Bitmap? {
        s.imageUrl?.let { u -> download(u)?.let { return it } }
        val link = DeepLinks.parse(s.url) ?: return null
        val og = runCatching { Repository(ctx).storyFromLink(link).imageUrl }.getOrNull() ?: return null
        return download(og)
    }

    /** Fetches and decodes an image, scaled down so a large photo can't bloat the notification (limit ~1024 px wide). */
    private fun download(url: String): Bitmap? = runCatching {
        if (!url.startsWith("https://")) return null
        val bytes = http.newCall(okhttp3.Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Android) Firsthand/personal").build())
            .execute().use { r -> if (r.isSuccessful) r.body?.bytes() else null } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, it) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 1024) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    /** A generic breaking-news card drawn in the app (no publisher artwork): used when a story has no usable picture. */
    fun genericPicture(label: String): Bitmap {
        val w = 1024; val h = 512
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), 0xFF2B1F4A.toInt(), 0xFF7B5BC4.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null
        p.color = 0xFFFFFFFF.toInt(); p.textAlign = Paint.Align.LEFT; p.isFakeBoldText = true
        p.textSize = 40f; c.drawText("FIRSTHAND", 64f, 110f, p)
        p.textSize = 104f; c.drawText(if ("test" in label.lowercase()) "TEST ALERT" else "BREAKING", 64f, 300f, p)
        p.textSize = 104f; c.drawText("NEWS", 64f, 410f, p)
        p.color = 0xFFFFD54F.toInt(); c.drawRect(64f, 440f, 320f, 456f, p)
        return bmp
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

    /** The test alert shows the generic picture straight away (no network), so you can see what a picture-less story looks like. */
    fun test(ctx: Context) = post(ctx, Story("test", "This is how a breaking news alert will look", "", "https://www.bbc.co.uk/news", null, "News", 0L, breaking = true), "Breaking news (test)", genericPicture("test"))
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
        if (seen.isNotEmpty()) for (story in fresh.take(3)) Alerts.notify(ctx, story, if (story.breaking) "Breaking news" else "Top story")
        prefs.setAlertSeen((seen + candidates.map { it.url }.filter { it !in seen }).takeLast(200).ifEmpty { listOf("-") })
    }
}
