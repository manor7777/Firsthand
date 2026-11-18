package app.bbcrevived.update

import org.junit.Assert.*
import org.junit.Test

class UpdateCheckerTest {
    private val json = """
        {"tag_name":"v0.1.42","body":"Fixes things.","assets":[
          {"name":"notes.txt","browser_download_url":"https://x/notes.txt","size":3},
          {"name":"firsthand-0.1.42.apk","browser_download_url":"https://github.com/o/r/releases/download/v0.1.42/firsthand-0.1.42.apk","size":9000000,
           "digest":"sha256:${"a".repeat(64)}"}]}
    """.trimIndent()

    @Test fun parsesTagApkAndDigest() {
        val r = UpdateChecker.parse(json)!!
        assertEquals(42L, r.versionCode)
        assertEquals("0.1.42", r.versionName)
        assertEquals("firsthand-0.1.42.apk", r.apkName)
        assertEquals("a".repeat(64), r.sha256)
        assertEquals("Fixes things.", r.notes)
    }

    @Test fun comparesAgainstInstalledVersionCode() {
        val r = UpdateChecker.parse(json)!!
        assertTrue(UpdateChecker.isNewer(r, 41)); assertFalse(UpdateChecker.isNewer(r, 42)); assertFalse(UpdateChecker.isNewer(r, 50))
    }

    @Test fun rejectsReleasesWeCantUse() {
        assertNull(UpdateChecker.parse("""{"tag_name":"nightly","assets":[]}"""))
        assertNull(UpdateChecker.parse("""{"tag_name":"v0.1.5","assets":[{"name":"a.zip","browser_download_url":"u"}]}"""))
        assertNull(UpdateChecker.parse("not json"))
    }
}
