package dev.bidu.app.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import dev.bidu.core.memory.MemoryKeys
import dev.bidu.core.memory.MemoryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ContactNumber(val number: String, val type: Int, val superPrimary: Boolean)

data class ContactEntry(val id: Long, val name: String, val numbers: List<ContactNumber>) {
    /** Default number: the one the user starred as default, else a mobile number, else the first. */
    fun preferredNumber(): ContactNumber? =
        numbers.firstOrNull { it.superPrimary } ?: numbers.firstOrNull { it.type == Phone.TYPE_MOBILE } ?: numbers.firstOrNull()
}

sealed interface ContactMatch {
    data class Found(val contact: ContactEntry, val aliasFrom: String?) : ContactMatch
    data class Ambiguous(val candidates: List<ContactEntry>) : ContactMatch
    data class NotFound(val searched: List<String>) : ContactMatch
    data object NoPermission : ContactMatch
}

/**
 * Finds a contact by spoken name. A saved memory alias (`contact_alias.mummy` -> "Sunita Sharma")
 * wins; otherwise relation words ("mummy", "papa", "मम्मी") also try the usual ways people save them.
 * Only contacts with a phone number are considered.
 */
class ContactsLookup(context: Context, private val memory: MemoryStore) {
    private val app = context.applicationContext

    suspend fun find(spoken: String): ContactMatch = withContext(Dispatchers.IO) {
        if (app.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return@withContext ContactMatch.NoPermission
        }
        val query = spoken.trim()
        val alias = memory.get(MemoryKeys.contactAlias(query))?.value?.trim()?.takeIf { it.isNotEmpty() }
        val terms = when {
            alias != null -> listOf(alias)
            else -> (listOf(query) + relationSynonyms(query)).distinct()
        }

        var candidates = terms.flatMap { filterQuery(it) }.mergeById()
        if (candidates.isEmpty()) candidates = fullScan()

        val scored = candidates
            .map { c -> c to terms.maxOf { TextMatch.score(it, c.name) } }
            .filter { it.second >= MIN_SCORE }
            .sortedByDescending { it.second }
        if (scored.isEmpty()) return@withContext ContactMatch.NotFound(terms)

        val exact = scored.filter { it.second >= 1.0 }
        if (exact.size == 1) return@withContext ContactMatch.Found(exact.single().first, alias?.let { query })
        val best = scored.first().second
        val close = scored.filter { best - it.second <= TIE_MARGIN }.map { it.first }
        if (close.size == 1) ContactMatch.Found(close.single(), alias?.let { query }) else ContactMatch.Ambiguous(close.take(5))
    }

    private fun filterQuery(term: String): List<ContactEntry> {
        if (term.isBlank()) return emptyList()
        val uri = Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, Uri.encode(term))
        return app.contentResolver.query(uri, PROJECTION, null, null, null)?.use { readRows(it, FILTER_ROW_LIMIT) }.orEmpty()
    }

    private fun fullScan(): List<ContactEntry> =
        app.contentResolver.query(Phone.CONTENT_URI, PROJECTION, null, null, null)?.use { readRows(it, SCAN_ROW_LIMIT) }.orEmpty()

    private fun readRows(cursor: Cursor, limit: Int): List<ContactEntry> {
        val idIndex = cursor.getColumnIndex(COL_CONTACT_ID)
        val nameIndex = cursor.getColumnIndex(COL_DISPLAY_NAME)
        val numberIndex = cursor.getColumnIndex(COL_NUMBER)
        val typeIndex = cursor.getColumnIndex(COL_TYPE)
        val primaryIndex = cursor.getColumnIndex(COL_SUPER_PRIMARY)
        if (idIndex < 0 || nameIndex < 0 || numberIndex < 0) return emptyList()

        val rows = mutableListOf<ContactEntry>()
        var read = 0
        while (cursor.moveToNext() && read < limit) {
            read++
            val name = cursor.getString(nameIndex)?.trim().orEmpty()
            val number = cursor.getString(numberIndex)?.trim().orEmpty()
            if (name.isEmpty() || number.isEmpty()) continue
            val type = if (typeIndex >= 0) cursor.getInt(typeIndex) else 0
            val superPrimary = primaryIndex >= 0 && cursor.getInt(primaryIndex) != 0
            rows += ContactEntry(cursor.getLong(idIndex), name, listOf(ContactNumber(number, type, superPrimary)))
        }
        return rows.mergeById()
    }

    private fun List<ContactEntry>.mergeById(): List<ContactEntry> =
        groupBy { it.id }.map { (id, entries) ->
            val numbers = entries.flatMap { it.numbers }.distinctBy { digitsOf(it.number).takeLast(10) }
            ContactEntry(id, entries.first().name, numbers)
        }

    companion object {
        const val MIN_SCORE = 0.75
        private const val TIE_MARGIN = 0.05
        private const val FILTER_ROW_LIMIT = 200
        private const val SCAN_ROW_LIMIT = 5_000

        // Column names of CommonDataKinds.Phone (inherited from protected interfaces, so spelled out).
        private const val COL_CONTACT_ID = "contact_id"
        private const val COL_DISPLAY_NAME = "display_name"
        private const val COL_NUMBER = "data1"
        private const val COL_TYPE = "data2"
        private const val COL_SUPER_PRIMARY = "is_super_primary"
        private val PROJECTION = arrayOf(COL_CONTACT_ID, COL_DISPLAY_NAME, COL_NUMBER, COL_TYPE, COL_SUPER_PRIMARY)

        private val RELATIONS: List<Set<String>> = listOf(
            setOf("mummy", "mumma", "mom", "mommy", "maa", "mother", "mammi", "ammi", "मम्मी", "माँ", "मां", "अम्मी"),
            setOf("papa", "pappa", "dad", "daddy", "father", "pitaji", "papaji", "abbu", "पापा", "पिताजी", "अब्बू"),
            setOf("bhai", "bhaiya", "bhaiyya", "brother", "bro", "भाई", "भैया"),
            setOf("didi", "sister", "di", "behen", "दीदी", "बहन"),
            setOf("wife", "biwi", "patni", "wifey", "पत्नी", "बीवी"),
            setOf("husband", "pati", "hubby", "पति"),
            setOf("dadi", "grandma", "दादी"),
            setOf("dada", "dadaji", "grandpa", "दादा"),
            setOf("nani", "नानी"),
            setOf("nana", "nanaji", "नाना"),
        )

        fun relationSynonyms(spoken: String): List<String> {
            val key = TextMatch.normalize(spoken).removeSuffix(" ji").trim()
            return RELATIONS.firstOrNull { key in it }?.toList().orEmpty()
        }

        fun digitsOf(number: String): String = number.filter { it.isDigit() }
    }
}
