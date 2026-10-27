package dev.bidu.app.tools

import dev.bidu.core.memory.KnownApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppMatcherTest {
    private val apps = listOf(
        KnownApp("WhatsApp", "com.whatsapp", listOf("whatsapp", "whats app", "wa", "व्हाट्सएप")),
        KnownApp("WhatsApp Business", "com.whatsapp.w4b", listOf("whatsapp business")),
        KnownApp("YouTube", "com.google.android.youtube", listOf("youtube", "yt", "यूट्यूब")),
        KnownApp("YouTube Music", "com.google.android.apps.youtube.music", listOf("youtube music", "yt music")),
        KnownApp("Instagram", "com.instagram.android", listOf("instagram", "insta")),
        KnownApp("Google Pay", "com.google.android.apps.nbu.paisa.user", listOf("gpay", "google pay")),
        KnownApp("Samsung Notes", "com.samsung.android.app.notes", listOf("samsung notes")),
        KnownApp("Google Keep", "com.google.android.keep", listOf("google keep")),
    )

    private fun found(spoken: String): String = (AppMatcher.match(spoken, apps) as AppMatch.Found).app.packageName

    @Test fun exactAliasWins() {
        assertEquals("com.whatsapp", found("whatsapp"))
        assertEquals("com.google.android.youtube", found("yt"))
        assertEquals("com.google.android.youtube", found("YouTube"))
        assertEquals("com.instagram.android", found("insta"))
        assertEquals("com.google.android.apps.nbu.paisa.user", found("gpay"))
    }

    @Test fun devanagariAlias() {
        assertEquals("com.whatsapp", found("व्हाट्सएप"))
        assertEquals("com.google.android.youtube", found("यूट्यूब"))
    }

    @Test fun trailingAppWordIgnored() {
        assertEquals("com.instagram.android", found("insta app"))
    }

    @Test fun misspellingStillMatches() {
        assertEquals("com.whatsapp", found("whatsap"))
        assertEquals("com.instagram.android", found("instagran"))
    }

    @Test fun ambiguousNameAsksUser() {
        val result = AppMatcher.match("notes", apps)
        assertTrue(result is AppMatch.Found || result is AppMatch.Ambiguous)
        val google = AppMatcher.match("google", apps)
        assertTrue(google is AppMatch.Ambiguous)
    }

    @Test fun unknownAppNotFound() {
        assertEquals(AppMatch.NotFound, AppMatcher.match("zzzz qqqq", apps))
        assertEquals(AppMatch.NotFound, AppMatcher.match("", apps))
    }
}
