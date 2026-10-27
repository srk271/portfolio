package dev.bidu.app.data.room

import dev.bidu.core.memory.MemoryStore
import dev.bidu.core.model.Clock
import dev.bidu.core.model.MemoryFact
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.Locale

/**
 * Key/value memory. Keys are normalised (trimmed, lower-case, inner whitespace collapsed) so
 * "Home_Address " and "home_address" are the same fact. Created time survives updates.
 */
class RoomMemoryStore(private val dao: MemoryDao, private val clock: Clock) : MemoryStore {
    private val writeLock = Mutex()

    override suspend fun get(key: String): MemoryFact? {
        val normalized = normalizeKey(key) ?: return null
        return dao.get(normalized)?.toFact()
    }

    override suspend fun put(key: String, value: String): MemoryFact {
        val normalized = requireNotNull(normalizeKey(key)) { "Memory key must not be blank" }
        val cleanValue = value.trim()
        require(cleanValue.isNotEmpty()) { "Memory value must not be blank" }
        require(cleanValue.length <= MAX_VALUE_LENGTH) { "Memory value is longer than $MAX_VALUE_LENGTH characters" }
        return writeLock.withLock {
            val now = clock.now().toEpochMilli()
            val existing = dao.get(normalized)
            val entity = MemoryEntity(normalized, cleanValue, createdAt = existing?.createdAt ?: now, updatedAt = now)
            dao.upsert(entity)
            entity.toFact()
        }
    }

    override suspend fun remove(key: String): Boolean {
        val normalized = normalizeKey(key) ?: return false
        return writeLock.withLock { dao.delete(normalized) > 0 }
    }

    override suspend fun all(): List<MemoryFact> = dao.all().map { it.toFact() }

    /** For the memory editor screen; same rows as [all], kept live. */
    fun observeAll(): Flow<List<MemoryFact>> = dao.observeAll().map { rows -> rows.map { it.toFact() } }

    /** Creation time of a fact, which [MemoryFact] does not carry. */
    suspend fun createdAt(key: String): Instant? = normalizeKey(key)?.let { dao.get(it) }?.let { Instant.ofEpochMilli(it.createdAt) }

    private fun MemoryEntity.toFact() = MemoryFact(key = key, value = value, updatedAt = Instant.ofEpochMilli(updatedAt))

    companion object {
        const val MAX_KEY_LENGTH = 100
        const val MAX_VALUE_LENGTH = 500
        private val WHITESPACE = Regex("""\s+""")

        fun normalizeKey(key: String): String? =
            key.trim().lowercase(Locale.ROOT).replace(WHITESPACE, " ").take(MAX_KEY_LENGTH).takeIf { it.isNotEmpty() }
    }
}
