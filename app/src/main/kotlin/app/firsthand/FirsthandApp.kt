package app.firsthand

import android.app.Application
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** Local-only crash capture: saves the last stack trace to a file so the next launch can show it. Nothing leaves the device. */
class FirsthandApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val sw = StringWriter(); e.printStackTrace(PrintWriter(sw))
                File(filesDir, "crash.txt").writeText("Thread: ${t.name}\n$sw")
            }
            prev?.uncaughtException(t, e)
        }
    }
}
