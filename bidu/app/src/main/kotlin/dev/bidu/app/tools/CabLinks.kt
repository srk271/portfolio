package dev.bidu.app.tools

import java.net.URLEncoder

/** URL building for cab providers; pure so it can be unit-tested. */
object CabLinks {
    const val UBER = "uber"
    const val OLA = "ola"
    const val RAPIDO = "rapido"
    val PROVIDERS = listOf(UBER, OLA, RAPIDO)

    fun packageOf(id: String): String = when (id) {
        UBER -> "com.ubercab"
        OLA -> "com.olacabs.customer"
        RAPIDO -> "com.rapido.passenger"
        else -> throw IllegalArgumentException("Unknown provider $id")
    }

    fun labelOf(id: String): String = when (id) {
        UBER -> "Uber"
        OLA -> "Ola"
        RAPIDO -> "Rapido"
        else -> id
    }

    fun uberUrl(destination: String?): String {
        val base = "https://m.uber.com/ul/?action=setPickup&pickup=my_location"
        return if (destination.isNullOrBlank()) base else "$base&dropoff[formatted_address]=${encode(destination)}"
    }

    fun playStoreUrl(packageName: String): String = "https://play.google.com/store/apps/details?id=${encode(packageName)}"

    fun encode(text: String): String = URLEncoder.encode(text, Charsets.UTF_8.name()).replace("+", "%20")
}
