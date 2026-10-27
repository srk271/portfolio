package dev.bidu.app.tools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import dev.bidu.core.memory.AppCatalog
import dev.bidu.core.memory.KnownApp
import dev.bidu.core.memory.KnownApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Launchable apps from the launcher-intent query (visible through the manifest's `<queries>`), with
 * spoken aliases from [KnownApps.hints] plus a few short forms ("yt", "gpay", "insta"). Cached briefly
 * because installs are rare and a voice request should not walk PackageManager every time.
 */
class InstalledAppCatalog(context: Context) : AppCatalog {
    private val app = context.applicationContext
    private val lock = Mutex()
    @Volatile private var cached: List<KnownApp> = emptyList()
    @Volatile private var cachedAt: Long = 0L

    override suspend fun apps(): List<KnownApp> = lock.withLock {
        val now = android.os.SystemClock.elapsedRealtime()
        if (cached.isEmpty() || now - cachedAt > CACHE_TTL_MILLIS) {
            cached = withContext(Dispatchers.IO) { load() }
            cachedAt = now
        }
        cached
    }

    fun invalidate() {
        cachedAt = 0L
    }

    /** Whether [packageName] has a launcher entry right now (no cache). */
    fun isLaunchable(packageName: String): Boolean = app.packageManager.getLaunchIntentForPackage(packageName) != null

    private fun load(): List<KnownApp> {
        val pm = app.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val infos: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcher, 0)
        }
        val hints = KnownApps.hints.associateBy { it.packageName }
        return infos
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == app.packageName) return@mapNotNull null
                val label = info.loadLabel(pm)?.toString()?.trim().orEmpty().ifEmpty { pkg }
                val aliases = buildList {
                    add(TextMatch.normalize(label))
                    hints[pkg]?.let { addAll(it.aliases) }
                    EXTRA_ALIASES[pkg]?.let { addAll(it) }
                }.map { it.lowercase() }.filter { it.isNotBlank() }.distinct()
                KnownApp(label = label, packageName = pkg, aliases = aliases)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    companion object {
        private const val CACHE_TTL_MILLIS = 2 * 60_000L

        /** Short and misheard forms on top of [KnownApps.hints]; keyed by package. */
        val EXTRA_ALIASES: Map<String, List<String>> = mapOf(
            "com.google.android.youtube" to listOf("yt", "you tube", "utube"),
            "com.google.android.apps.youtube.music" to listOf("yt music", "youtube music"),
            "com.whatsapp" to listOf("wa", "watsapp", "whatsap", "whats up"),
            "com.whatsapp.w4b" to listOf("whatsapp business", "business whatsapp"),
            "com.instagram.android" to listOf("insta", "ig", "insta gram"),
            "com.facebook.katana" to listOf("fb", "facebook"),
            "com.google.android.apps.nbu.paisa.user" to listOf("gpay", "g pay", "google pe", "gpe"),
            "com.phonepe.app" to listOf("phone pay", "fone pe"),
            "com.android.chrome" to listOf("google chrome"),
            "com.google.android.apps.maps" to listOf("google map", "naksha"),
            "com.android.vending" to listOf("play store", "playstore", "google play"),
            "com.samsung.android.dialer" to listOf("phone", "dialer", "dialler"),
            "com.samsung.android.app.notes" to listOf("samsung notes"),
            "com.sec.android.gallery3d" to listOf("gallery", "photos", "गैलरी"),
            "com.google.android.apps.photos" to listOf("google photos", "photos"),
            "com.spotify.music" to listOf("spotify"),
            "in.swiggy.android" to listOf("swiggy"),
            "com.application.zomato" to listOf("zomato"),
            "com.ubercab" to listOf("uber"),
            "com.olacabs.customer" to listOf("ola cabs"),
            "com.rapido.passenger" to listOf("rapido bike"),
        )
    }
}
