package dev.bidu.core.policy

import dev.bidu.core.model.Language
import dev.bidu.core.model.RiskLevel
import dev.bidu.core.model.say
import dev.bidu.core.nlu.TextFolding
import dev.bidu.core.tool.BuiltinTools
import dev.bidu.core.tool.ToolCall
import dev.bidu.core.tool.ToolSpec
import java.util.Locale

/**
 * The risk table from the architecture doc. Settings may only add confirmations. On top of the declared
 * risk, a tool whose name says it pays, sends, shares or deletes is treated at least as risky as that
 * (protects against a mislabelled or cloud-proposed tool), and any argument that carries a credential is
 * refused outright.
 */
class DefaultAutonomyPolicy : AutonomyPolicy {

    override fun screen(utterance: String): RequestScreening {
        val text = TextFolding.fold(utterance).lowercase(Locale.ROOT)
        val hit = FORBIDDEN.firstOrNull { it.containsMatchIn(text) } ?: return RequestScreening.Allowed
        return RequestScreening.Forbidden("Credential-like request (${hit.pattern.take(24)})")
    }

    override fun decide(call: ToolCall, spec: ToolSpec, context: PolicyContext): PolicyDecision {
        if (call.tool != spec.name) return PolicyDecision.Deny("Call '${call.tool}' does not match spec '${spec.name}'")
        val leaked = call.args.asMap().values.any { value -> screen(value.toString()) is RequestScreening.Forbidden }
        if (leaked) return PolicyDecision.Deny("Arguments contain an OTP, PIN or password")

        val risk = maxOf(spec.risk, inferredRisk(spec.name))
        if (risk == RiskLevel.CRITICAL && spec.retry.maxAttempts > 1) {
            return PolicyDecision.Deny("Payment-class tools must never be retried")
        }
        val lang = context.language
        return when (risk) {
            RiskLevel.CRITICAL -> PolicyDecision.Confirm(prompt(call, lang, money = true), "CRITICAL: money moves; explicit confirmation required")
            RiskLevel.HIGH -> PolicyDecision.Confirm(prompt(call, lang, money = false), "HIGH: sends, shares or deletes; always confirmed")
            RiskLevel.MEDIUM ->
                if (context.settings.confirmBeforeHandoffs) PolicyDecision.Confirm(prompt(call, lang, money = false), "Hand-off confirmation enabled in settings")
                else PolicyDecision.Proceed
            RiskLevel.LOW ->
                if (spec.name == BuiltinTools.OPEN_APP_NAME && context.settings.confirmBeforeOpeningApps) {
                    PolicyDecision.Confirm(prompt(call, lang, money = false), "App-open confirmation enabled in settings")
                } else {
                    PolicyDecision.Proceed
                }
        }
    }

    private fun inferredRisk(name: String): RiskLevel {
        val parts = name.split('_').toSet()
        return when {
            parts.any { it in MONEY_WORDS } -> RiskLevel.CRITICAL
            parts.any { it in SENSITIVE_WORDS } -> RiskLevel.HIGH
            else -> RiskLevel.LOW
        }
    }

    private fun prompt(call: ToolCall, lang: Language, money: Boolean): String {
        val a = call.args
        fun q(s: String?) = s?.let { "'${it.take(60)}'" } ?: ""
        return when (call.tool) {
            BuiltinTools.DELETE_NOTE_NAME -> lang.say(
                "Note ${q(a.string("query") ?: a.string("id"))} delete kar doon?",
                "Delete the note ${q(a.string("query") ?: a.string("id"))}?",
                "नोट ${q(a.string("query") ?: a.string("id"))} हटा दूँ?",
            )
            BuiltinTools.CANCEL_REMINDER_NAME -> lang.say(
                "Reminder ${q(a.string("query") ?: a.string("id"))} cancel kar doon?",
                "Cancel the reminder ${q(a.string("query") ?: a.string("id"))}?",
                "रिमाइंडर ${q(a.string("query") ?: a.string("id"))} रद्द कर दूँ?",
            )
            BuiltinTools.FORGET_NAME -> lang.say(
                "${q(a.string("key"))} memory se hata doon?",
                "Forget ${q(a.string("key"))}?",
                "${q(a.string("key"))} याद से हटा दूँ?",
            )
            BuiltinTools.OPEN_APP_NAME -> lang.say(
                "${a.string("app") ?: "App"} khol doon?",
                "Open ${a.string("app") ?: "the app"}?",
                "${a.string("app") ?: "ऐप"} खोल दूँ?",
            )
            BuiltinTools.DIAL_CONTACT_NAME -> lang.say(
                "${a.string("name") ?: a.string("number") ?: "Number"} ke liye dialer khol doon?",
                "Open the dialer for ${a.string("name") ?: a.string("number") ?: "this number"}?",
                "${a.string("name") ?: a.string("number") ?: "नंबर"} के लिए डायलर खोल दूँ?",
            )
            else -> {
                val what = call.purpose.ifBlank { call.tool.replace('_', ' ') }
                if (money) {
                    lang.say("Isme paise lagenge: $what. Pakka karna hai?", "This spends money: $what. Are you sure?", "इसमें पैसे लगेंगे: $what। पक्का करना है?")
                } else {
                    lang.say("$what — kar doon?", "$what — go ahead?", "$what — कर दूँ?")
                }
            }
        }
    }

    private companion object {
        val MONEY_WORDS = setOf("pay", "payment", "payments", "purchase", "buy", "transfer", "checkout", "upi", "order", "recharge", "donate", "tip")
        val SENSITIVE_WORDS = setOf(
            "send", "share", "message", "sms", "email", "mail", "post", "publish", "delete", "remove", "forget", "erase", "wipe", "cancel",
            "upload", "forward", "reply",
        )

        private fun rx(p: String) = Regex(p, RegexOption.IGNORE_CASE)
        val FORBIDDEN: List<Regex> = listOf(
            rx("""(?<![\p{L}\d])o\.?\s?t\.?\s?p(?![\p{L}\d])"""),
            rx("""one[\s-]?time[\s-]?pass(word|code)?"""),
            rx("""(?<![\p{L}])(upi|atm|card|debit|credit|bank|banking)\s*-?\s*(ka\s+|ki\s+|ke\s+)?pin(?![\p{L}])"""),
            rx("""(?<![\p{L}])m-?pin(?![\p{L}])"""),
            rx("""(?<![\p{L}])pin\s*(number|no\.?|kya|batao|bata|bhej|share|daal|enter)(?![\p{L}])"""),
            rx("""(?<![\p{L}])pass\s?(word|wd|code)(?![\p{L}])"""),
            rx("""(?<![\p{L}])(cvv|cvc)(?![\p{L}])"""),
            rx("""(net|internet|mobile)\s*banking\s*(login|password|id|user)"""),
            rx("""(card|account)\s*(ka\s+|ki\s+|ke\s+)?(number|details|no\.?)\s*(bhej|share|send|bata|batao|daal|enter|de|do)"""),
            rx("""(bank|account)\s*(details|credentials|login)"""),
            rx("""ओटीपी|पासवर्ड|पिन\s*(नंबर|बताओ|भेजो)|यूपीआई\s*पिन|सीवीवी"""),
        )
    }
}
