package app.firsthand.platform

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

/**
 * Loads a page the way the phone's own browser would, for publishers that refuse a plain HTTP request (bot checks, scripts that
 * build the page). A hidden WebView runs the page's scripts and passes the same checks a visit from Chrome does; once it holds
 * article paragraphs, the rendered HTML is handed back for the normal text extractor. Used only when a plain fetch was refused or
 * came back with no article, and only for a story the user opened. Images and media are not loaded.
 */
object WebFetch {
    private val one = Mutex() // one hidden browser at a time

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun html(ctx: Context, url: String, timeoutMs: Long = 20_000): String? = one.withLock {
        withContext(Dispatchers.Main) {
            withTimeoutOrNull(timeoutMs + 5_000) {
                suspendCancellableCoroutine<String?> { cont ->
                    val handler = Handler(Looper.getMainLooper())
                    val web = WebView(ctx.applicationContext)
                    var finished = false
                    fun finish(html: String?) {
                        if (finished) return
                        finished = true
                        handler.removeCallbacksAndMessages(null)
                        runCatching { web.stopLoading(); web.destroy() }
                        if (cont.isActive) cont.resume(html)
                    }
                    web.settings.apply {
                        javaScriptEnabled = true; domStorageEnabled = true
                        blockNetworkImage = true; mediaPlaybackRequiresUserGesture = true
                        allowFileAccess = false; allowContentAccess = false; javaScriptCanOpenWindowsAutomatically = false
                    }
                    web.webViewClient = WebViewClient()
                    val started = SystemClock.uptimeMillis()
                    // Poll until the page holds a few real paragraphs (a bot check usually reloads into the article), or time runs out.
                    fun poll() {
                        if (finished) return
                        web.evaluateJavascript(
                            "(function(){var n=0,p=document.querySelectorAll('p');for(var i=0;i<p.length;i++){if((p[i].innerText||'').length>60)n++}return n})()",
                        ) { r ->
                            val paragraphs = r?.trim('"')?.toIntOrNull() ?: 0
                            if (paragraphs >= 3 || SystemClock.uptimeMillis() - started > timeoutMs) {
                                web.evaluateJavascript("document.documentElement.outerHTML") { html ->
                                    finish(html?.let { runCatching { Json.decodeFromString<String>(it) }.getOrNull() })
                                }
                            } else handler.postDelayed({ poll() }, 1_500)
                        }
                    }
                    cont.invokeOnCancellation { handler.post { finish(null) } }
                    web.loadUrl(url)
                    handler.postDelayed({ poll() }, 2_500)
                }
            }
        }
    }
}
