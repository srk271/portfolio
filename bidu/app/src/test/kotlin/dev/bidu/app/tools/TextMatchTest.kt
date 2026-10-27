package dev.bidu.app.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextMatchTest {
    @Test fun normalizeFoldsCaseAndPunctuation() {
        assertEquals("rahul sharma", TextMatch.normalize("  Rahul-Sharma!! "))
        assertEquals("cafe", TextMatch.normalize("Café"))
        assertEquals("मम्मी", TextMatch.normalize("मम्मी"))
    }

    @Test fun levenshteinBasics() {
        assertEquals(0, TextMatch.levenshtein("abc", "abc"))
        assertEquals(1, TextMatch.levenshtein("abc", "abd"))
        assertEquals(3, TextMatch.levenshtein("", "abc"))
    }

    @Test fun scoreOrdersSensibly() {
        assertEquals(1.0, TextMatch.score("Whats App", "whatsapp"), 0.0)
        assertTrue(TextMatch.score("rahul", "Rahul Sharma") >= 0.85)
        assertTrue(TextMatch.score("xyz", "Rahul Sharma") < 0.5)
    }

    @Test fun secretGuardBlocksCredentials() {
        assertTrue(SecretGuard.looksSecret("mera otp 482910 hai"))
        assertTrue(SecretGuard.looksSecret("upi pin hai 1234"))
        assertTrue(SecretGuard.looksSecret("password: tiger123"))
        assertTrue(SecretGuard.looksSecret("card 4111 1111 1111 1111"))
        assertTrue(SecretGuard.isSecretKey("bank_password"))
        assertTrue(SecretGuard.isSecretKey("atm pin"))
    }

    @Test fun secretGuardAllowsOrdinaryText() {
        assertFalse(SecretGuard.looksSecret("wifi password change karna hai"))
        assertFalse(SecretGuard.looksSecret("pincode 110001 wala parcel"))
        assertFalse(SecretGuard.looksSecret("mummy ka number +91 98765 43210"))
        assertFalse(SecretGuard.looksSecret("kal subah 7 baje doodh lena"))
        assertFalse(SecretGuard.isSecretKey("home_address"))
        assertFalse(SecretGuard.isSecretKey("contact_alias.mummy"))
    }

    @Test fun uberLinkCarriesEncodedDestination() {
        assertEquals(
            "https://m.uber.com/ul/?action=setPickup&pickup=my_location&dropoff[formatted_address]=12%20MG%20Road%2C%20Bengaluru",
            CabLinks.uberUrl("12 MG Road, Bengaluru"),
        )
        assertEquals("https://m.uber.com/ul/?action=setPickup&pickup=my_location", CabLinks.uberUrl(null))
    }
}
