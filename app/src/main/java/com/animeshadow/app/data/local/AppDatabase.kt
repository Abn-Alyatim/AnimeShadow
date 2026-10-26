package com.animeshadow.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AnimeEntity::class, FeedItemEntity::class, FeedMetaEntity::class, AiringEntity::class,
        LibraryEntryEntity::class, WatchedEpisodeEntity::class, FavoriteAnimeEntity::class,
        FavoriteCharacterEntity::class, AnimeCharacterEntity::class, AnimeStaffEntity::class,
        RelationEntity::class, RecommendationEntity::class, CharacterEntity::class, StaffEntity::class,
        NotificationEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun animeDao(): AnimeDao
    abstract fun libraryDao(): LibraryDao
    abstract fun notificationDao(): NotificationDao

    companion object {
        const val NAME = "anime_shadow.db"

        /** v1.0 -> v1.1: MAL enrichment + Arabic synopsis columns, rating sliders, notification history. User data is kept. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf(
                    "malPoster TEXT", "malRating TEXT", "malScoredBy INTEGER", "malSource TEXT", "malFetchedAt INTEGER",
                    "descriptionAr TEXT", "descriptionArSrc INTEGER",
                ).forEach { db.execSQL("ALTER TABLE `anime` ADD COLUMN $it") }
                listOf("scoreStory", "scoreCharacters", "scoreAnimation", "scoreMusic").forEach {
                    db.execSQL("ALTER TABLE `library` ADD COLUMN `$it` INTEGER")
                }
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `notification_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`animeId` INTEGER NOT NULL, `episode` INTEGER NOT NULL, `title` TEXT NOT NULL, `image` TEXT, " +
                        "`kind` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `read` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notification_log_createdAt` ON `notification_log` (`createdAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notification_log_read` ON `notification_log` (`read`)")
            }
        }

        /** v1.1 -> v1.1.1: names kept untranslated in the synopsis + last failed translation time. User data is kept. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `anime` ADD COLUMN `charNames` TEXT")
                db.execSQL("ALTER TABLE `anime` ADD COLUMN `arFailedAt` INTEGER")
            }
        }
    }
}
