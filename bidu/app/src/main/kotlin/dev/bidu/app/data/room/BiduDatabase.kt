package dev.bidu.app.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Local store for notes, reminders, conversation history, memory facts and the action log.
 * The schema is exported to `app/schemas/`; any version bump must ship a Migration (no destructive fallback,
 * this is the owner's personal data).
 */
@Database(
    entities = [NoteEntity::class, ReminderEntity::class, HistoryEntity::class, MemoryEntity::class, ActionLogEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class BiduDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun reminderDao(): ReminderDao
    abstract fun historyDao(): HistoryDao
    abstract fun memoryDao(): MemoryDao
    abstract fun actionLogDao(): ActionLogDao

    companion object {
        const val NAME = "bidu.db"

        fun create(context: Context): BiduDatabase =
            Room.databaseBuilder(context.applicationContext, BiduDatabase::class.java, NAME).build()
    }
}
