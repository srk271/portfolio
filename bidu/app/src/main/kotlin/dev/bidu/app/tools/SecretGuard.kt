package dev.bidu.app.tools

/**
 * Refuses to persist OTPs, PINs, passwords, CVVs and card/account numbers in notes, memory or
 * reminders. Deliberately narrow: "wifi password change karna" is fine, "password hai tiger123" is not.
 */
object SecretGuard {
    private const val KEYWORDS = """otp|upi\s*pin|m\s*pin|atm\s*pin|pin|password|passcode|passwd|cvv|cvc|netbanking\s*password|ओटीपी|पिन|पासवर्ड"""
    private val KEYWORD_WITH_VALUE = Regex("""(?i)(?<![\p{L}\p{N}])($KEYWORDS)(?![\p{L}\p{N}])\s*(?:hai|he|is|=|:|-|था|है)\s*\S+""")
    private val KEYWORD_WITH_DIGITS = Regex("""(?i)(?<![\p{L}\p{N}])($KEYWORDS)(?![\p{L}\p{N}])\s*\S*\d\S*""")
    private val SECRET_KEY = Regex("""(?i)(?<![\p{L}\p{N}])($KEYWORDS|card\s*number|account\s*number|cvv)(?![\p{L}\p{N}])""")
    private val LONG_DIGIT_RUN = Regex("""\d(?:[\s-]?\d){13,}""")

    fun looksSecret(text: String): Boolean =
        KEYWORD_WITH_VALUE.containsMatchIn(text) || KEYWORD_WITH_DIGITS.containsMatchIn(text) || LONG_DIGIT_RUN.containsMatchIn(text)

    /** A memory key that names a credential is refused whatever the value is. */
    fun isSecretKey(key: String): Boolean = SECRET_KEY.containsMatchIn(key.replace('_', ' ').replace('.', ' '))

    const val REFUSAL = "Bidu never stores OTPs, PINs, passwords, CVVs or card/account numbers."
}
