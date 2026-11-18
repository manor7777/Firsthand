package app.bbcrevived.update

import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request

/** Reads the repo's latest GitHub Release (public repo, no token) and says whether it is newer than the installed build. */
object UpdateChecker {
    const val REPO = "manor7777/BBC-Revived"

    data class Release(
        val versionCode: Long, val versionName: String, val notes: String,
        val apkUrl: String, val apkName: String, val size: Long, val sha256: String?,
    )

    private val tag = Regex("v?(\\d+\\.\\d+\\.(\\d+))")

    /** Release JSON -> [Release], or null if it has no usable tag/APK. Tag `v0.1.N` means versionCode N (matches the Gradle build). */
    fun parse(json: String): Release? = runCatching {
        val o = Json.parseToJsonElement(json).jsonObject
        val m = tag.matchEntire(o["tag_name"]!!.jsonPrimitive.content) ?: return null
        val apk = o["assets"]!!.jsonArray.map { it.jsonObject }.firstOrNull { it["name"]!!.jsonPrimitive.content.endsWith(".apk") } ?: return null
        Release(
            versionCode = m.groupValues[2].toLong(), versionName = m.groupValues[1],
            notes = (o["body"] as? JsonPrimitive)?.contentOrNull.orEmpty().trim(),
            apkUrl = apk["browser_download_url"]!!.jsonPrimitive.content, apkName = apk["name"]!!.jsonPrimitive.content,
            size = apk["size"]?.jsonPrimitive?.longOrNull ?: 0L,
            sha256 = (apk["digest"] as? JsonPrimitive)?.contentOrNull?.removePrefix("sha256:")?.takeIf { it.length == 64 },
        )
    }.getOrNull()

    fun isNewer(r: Release, installedCode: Long) = r.versionCode > installedCode

    sealed interface Result {
        data class Found(val release: Release) : Result
        data object UpToDate : Result
        data class Failed(val message: String) : Result
    }

    /** Blocking network call; run on an IO dispatcher. */
    fun check(client: OkHttpClient, installedCode: Long): Result = try {
        val req = Request.Builder().url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json").header("User-Agent", "BBCRevived").build()
        client.newCall(req).execute().use { r ->
            when {
                r.code == 404 -> Result.Failed("No release found. The repository must be public and have at least one release.")
                !r.isSuccessful -> Result.Failed("GitHub answered HTTP ${r.code}.")
                else -> {
                    val rel = parse(r.body!!.string())
                    when {
                        rel == null -> Result.Failed("The latest release has no APK attached.")
                        isNewer(rel, installedCode) -> Result.Found(rel)
                        else -> Result.UpToDate
                    }
                }
            }
        }
    } catch (e: Exception) {
        Result.Failed("Couldn't reach GitHub. Check your connection.")
    }
}
