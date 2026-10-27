package dev.bidu.core.memory

/** An app the user can ask Bidu to open. [aliases] are spoken names, lower-case. */
data class KnownApp(
    val label: String,
    val packageName: String,
    val aliases: List<String> = emptyList(),
)

/** Installed launchable apps, provided by the app module from PackageManager. */
interface AppCatalog {
    suspend fun apps(): List<KnownApp>
}

/**
 * Spoken-name hints for apps common on Indian phones. Package names are candidates only: the
 * open-app tool checks what is installed and falls back to matching launcher labels, so an
 * outdated entry degrades to a label search rather than a wrong launch.
 */
object KnownApps {
    val hints: List<KnownApp> = listOf(
        KnownApp("WhatsApp", "com.whatsapp", listOf("whatsapp", "whats app", "व्हाट्सएप", "वॉट्सऐप")),
        KnownApp("Uber", "com.ubercab", listOf("uber", "ऊबर", "उबर")),
        KnownApp("Ola", "com.olacabs.customer", listOf("ola", "ओला")),
        KnownApp("Rapido", "com.rapido.passenger", listOf("rapido", "रैपिडो")),
        KnownApp("BookMyShow", "com.bt.bms", listOf("bookmyshow", "book my show", "बुकमायशो")),
        KnownApp("District", "com.zomato.district", listOf("district", "डिस्ट्रिक्ट")),
        KnownApp("Zomato", "com.application.zomato", listOf("zomato", "जोमैटो")),
        KnownApp("Swiggy", "in.swiggy.android", listOf("swiggy", "स्विगी")),
        KnownApp("Google Maps", "com.google.android.apps.maps", listOf("maps", "google maps", "map", "मैप", "मैप्स")),
        KnownApp("YouTube", "com.google.android.youtube", listOf("youtube", "यूट्यूब")),
        KnownApp("Instagram", "com.instagram.android", listOf("instagram", "insta", "इंस्टाग्राम", "इंस्टा")),
        KnownApp("Chrome", "com.android.chrome", listOf("chrome", "browser", "क्रोम")),
        KnownApp("Gmail", "com.google.android.gm", listOf("gmail", "mail", "जीमेल")),
        KnownApp("Spotify", "com.spotify.music", listOf("spotify", "स्पॉटिफाई")),
        KnownApp("Telegram", "org.telegram.messenger", listOf("telegram", "टेलीग्राम")),
        KnownApp("PhonePe", "com.phonepe.app", listOf("phonepe", "phone pe", "फोनपे")),
        KnownApp("Paytm", "net.one97.paytm", listOf("paytm", "पेटीएम")),
        KnownApp("Google Pay", "com.google.android.apps.nbu.paisa.user", listOf("gpay", "google pay", "जीपे")),
        KnownApp("Amazon", "in.amazon.mShop.android.shopping", listOf("amazon", "अमेज़न", "अमेज़ॉन")),
        KnownApp("Flipkart", "com.flipkart.android", listOf("flipkart", "फ्लिपकार्ट")),
        KnownApp("Camera", "com.sec.android.app.camera", listOf("camera", "कैमरा")),
        KnownApp("Clock", "com.sec.android.app.clockpackage", listOf("clock", "alarm app", "घड़ी")),
        KnownApp("Calculator", "com.sec.android.app.popupcalculator", listOf("calculator", "कैलकुलेटर")),
        KnownApp("Messages", "com.samsung.android.messaging", listOf("messages", "sms", "मैसेज")),
        KnownApp("Settings", "com.android.settings", listOf("settings", "सेटिंग्स", "सेटिंग")),
    )
}
