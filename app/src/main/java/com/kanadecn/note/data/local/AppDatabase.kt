package com.kanadecn.note.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Note::class, Mistake::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao

    abstract fun mistakeDao(): MistakeDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `mistakes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `imagePath` TEXT NOT NULL,
                        `question` TEXT NOT NULL,
                        `myAnswer` TEXT NOT NULL,
                        `correctAnswer` TEXT NOT NULL,
                        `isCorrect` INTEGER NOT NULL,
                        `errorType` TEXT NOT NULL,
                        `knowledgePoint` TEXT NOT NULL,
                        `explanation` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `notes` ADD COLUMN `relativePath` TEXT NOT NULL DEFAULT ''",
                )
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kanadeck.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
