package dev.bidu.core.memory

import dev.bidu.core.model.MemoryFact

/** Persistent key/value facts about the owner. Implemented on Room in the app module. */
interface MemoryStore {
    suspend fun get(key: String): MemoryFact?
    suspend fun put(key: String, value: String): MemoryFact
    suspend fun remove(key: String): Boolean
    suspend fun all(): List<MemoryFact>
}

/** Well-known memory keys shared by the planner, the tools and the memory editor screen. */
object MemoryKeys {
    const val USER_NAME = "user_name"
    const val HOME_ADDRESS = "home_address"
    const val WORK_ADDRESS = "work_address"
    const val PREFERRED_CAB_PROVIDER = "preferred_cab_provider"
    const val CITY = "city"

    /** Prefix for contact aliases: `contact_alias.mummy` -> the display name to search for. */
    const val CONTACT_ALIAS_PREFIX = "contact_alias."

    /** Prefix for named places: `place.gym` -> address text. "ghar"/"home" resolve to [HOME_ADDRESS]. */
    const val PLACE_PREFIX = "place."

    fun contactAlias(alias: String): String = CONTACT_ALIAS_PREFIX + alias.trim().lowercase()
    fun place(name: String): String = PLACE_PREFIX + name.trim().lowercase()
}
