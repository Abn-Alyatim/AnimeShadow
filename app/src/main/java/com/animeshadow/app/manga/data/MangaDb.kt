package com.animeshadow.app.manga.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * v1.3 MANGA mode lives in its OWN database file (manga.db), next to the anime one, so the two modes never share a
 * table: switching modes reads a different file and nothing of the other mode is touched or lost.
 */

/** Cached AniList MANGA entry (details kept forever while chapters of it are downloaded → readable offline). */
@Entity(tableName = "manga")
data class MangaEntity(
    @PrimaryKey val id: Int,
    val titleRomaji: String? = null,
    val titleEnglish: String? = null,
    val titleNative: String? = null,
    val cover: String? = null,
    val coverXL: String? = null,
    val banner: String? = null,
    val color: String? = null,
    /** MANGA, ONE_SHOT, NOVEL. */
    val format: String? = null,
    /** FINISHED, RELEASING, NOT_YET_RELEASED, CANCELLED, HIATUS. */
    val status: String? = null,
    /** JP (manga), KR (manhwa), CN / TW (manhua). */
    val country: String? = null,
    val chapters: Int? = null,
    val volumes: Int? = null,
    val score: Int? = null,
    val popularity: Int? = null,
    val genres: String = "",
    val description: String? = null,
    val descriptionAr: String? = null,
    val source: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val isAdult: Boolean = false,
    val detailLoaded: Boolean = false,
    val cachedAt: Long = 0,
    // v1.3.1 (manga.db version 2, columns added by MIGRATION_1_2 — the library is kept):
    val idMal: Int? = null,
    /** Story / art authors, "|"-separated (info grid «المؤلف»). */
    val authors: String? = null,
    /** JSON list of [MangaRelation] («ذات صلة», السابق / التالي). */
    val relationsJson: String? = null,
    val malScore: Double? = null,
    val malScoredBy: Int? = null,
) {
    val displayTitle: String get() = titleEnglish ?: titleRomaji ?: titleNative ?: ""
    val authorList: List<String> get() = authors.orEmpty().split('|').filter { it.isNotBlank() }
    val relationList: List<MangaRelation> get() = MangaRelation.decode(relationsJson)
    val poster: String? get() = coverXL ?: cover
    val genreList: List<String> get() = genres.split('|').filter { it.isNotBlank() }
    val kind: MangaKind get() = MangaKind.of(country)
}

/** «مانجا» is the main name; the filter splits it by origin. */
enum class MangaKind(val countries: List<String>) {
    MANGA(listOf("JP")), MANHWA(listOf("KR")), MANHUA(listOf("CN", "TW"));

    companion object {
        fun of(country: String?): MangaKind = entries.firstOrNull { country in it.countries } ?: MANGA
    }
}

/** Ordered feed membership (trending / popular / top, per kind filter). */
@Entity(tableName = "manga_feed", primaryKeys = ["feed", "mangaId"])
data class MangaFeedEntity(val feed: String, val mangaId: Int, val position: Int, val updatedAt: Long)

enum class ReadStatus { READING, PLAN_TO_READ, COMPLETED, ON_HOLD, DROPPED }

/** The manga library: its own table, never the anime one. */
@Entity(tableName = "manga_library")
data class MangaLibraryEntity(
    @PrimaryKey val mangaId: Int,
    val status: ReadStatus? = null,
    val favorite: Boolean = false,
    val lastChapter: String? = null,
    /** Your own rating 0..10 («إضافة تقييم»). */
    val userScore: Int? = null,
    val updatedAt: Long = 0,
)

/** Reading history (one row per chapter read; the newest per title is shown). */
@Entity(tableName = "manga_history", indices = [Index("mangaId")])
data class MangaHistoryEntity(
    @PrimaryKey val chapterKey: String,
    val mangaId: Int,
    val chapterNumber: String,
    val page: Int,
    val pageCount: Int,
    val readAt: Long,
    val finished: Boolean,
)

enum class DownloadState { QUEUED, RUNNING, PAUSED, DONE, FAILED }

/**
 * One downloaded (or queued) chapter. Pages live in filesDir/manga/<mangaId>/<safe chapterKey>/NNN.<ext>; a page file
 * that exists is a page that is done, so a resumed download only fetches what is missing.
 */
@Entity(tableName = "manga_download", indices = [Index("mangaId"), Index("state")])
data class DownloadEntity(
    @PrimaryKey val chapterKey: String,
    val mangaId: Int,
    val providerId: String,
    val chapterId: String,
    val chapterNumber: String,
    val chapterTitle: String? = null,
    val state: DownloadState = DownloadState.QUEUED,
    val totalPages: Int = 0,
    val donePages: Int = 0,
    val position: Int = 0,
    val createdAt: Long = 0,
    val finishedAt: Long? = null,
    /** JSON of the page URLs (and headers), saved once so a resume needs no provider call. */
    val pagesJson: String? = null,
    val read: Boolean = false,
    val error: String? = null,
) {
    val progress: Float get() = if (totalPages <= 0) 0f else donePages.toFloat() / totalPages
    val numberValue: Double get() = chapterNumber.toDoubleOrNull() ?: 0.0
}

data class DownloadWithManga(
    @androidx.room.Embedded val download: DownloadEntity,
    @androidx.room.Relation(parentColumn = "mangaId", entityColumn = "id") val manga: MangaEntity?,
)

data class DownloadedTitle(
    @androidx.room.Embedded val manga: MangaEntity,
    val chapterCount: Int,
)

data class LibraryRow(
    @androidx.room.Embedded val entry: MangaLibraryEntity,
    @androidx.room.Relation(parentColumn = "mangaId", entityColumn = "id") val manga: MangaEntity?,
)

data class HistoryRow(
    @androidx.room.Embedded val history: MangaHistoryEntity,
    @androidx.room.Relation(parentColumn = "mangaId", entityColumn = "id") val manga: MangaEntity?,
)

@Dao
interface MangaDao {
    @Upsert suspend fun upsert(items: List<MangaEntity>)
    @Upsert suspend fun upsertOne(item: MangaEntity)

    @Query("SELECT * FROM manga WHERE id = :id") fun observe(id: Int): Flow<MangaEntity?>
    @Query("SELECT * FROM manga WHERE id = :id") suspend fun get(id: Int): MangaEntity?
    @Query("SELECT * FROM manga WHERE id IN (:ids)") suspend fun getAll(ids: List<Int>): List<MangaEntity>

    @Query("SELECT m.* FROM manga_feed f JOIN manga m ON m.id = f.mangaId WHERE f.feed = :feed ORDER BY f.position")
    fun observeFeed(feed: String): Flow<List<MangaEntity>>

    @Query("DELETE FROM manga_feed WHERE feed = :feed") suspend fun clearFeed(feed: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertFeed(rows: List<MangaFeedEntity>)
    @Query("SELECT MAX(position) FROM manga_feed WHERE feed = :feed") suspend fun maxFeedPosition(feed: String): Int?

    @Transaction
    suspend fun replaceFeed(feed: String, items: List<MangaEntity>, now: Long, append: Boolean) {
        upsertKeepingDetails(items)
        if (!append) clearFeed(feed)
        val start = if (append) (maxFeedPosition(feed) ?: -1) + 1 else 0
        insertFeed(items.mapIndexed { i, m -> MangaFeedEntity(feed, m.id, start + i, now) })
    }

    /** Card data never wipes details (synopsis translation, dates) that a details visit already stored. */
    @Transaction
    suspend fun upsertKeepingDetails(items: List<MangaEntity>) {
        val old = getAll(items.map { it.id }).associateBy { it.id }
        upsert(items.map { n ->
            val o = old[n.id]
            if (o == null || !o.detailLoaded || n.detailLoaded) n.copy(descriptionAr = n.descriptionAr ?: o?.descriptionAr)
            else o.copy(score = n.score ?: o.score, status = n.status ?: o.status, chapters = n.chapters ?: o.chapters, coverXL = n.coverXL ?: o.coverXL, cachedAt = n.cachedAt)
            // (o keeps idMal / authors / relations / MAL score: card data never has them)
        })
    }

    @Query("UPDATE manga SET descriptionAr = :text WHERE id = :id") suspend fun setArabic(id: Int, text: String)

    // ------------------------------------------------------------------ library (manga only)
    @Query("SELECT * FROM manga_library WHERE mangaId = :id") fun observeEntry(id: Int): Flow<MangaLibraryEntity?>
    @Query("SELECT * FROM manga_library WHERE mangaId = :id") suspend fun entry(id: Int): MangaLibraryEntity?
    @Upsert suspend fun upsertEntry(e: MangaLibraryEntity)
    @Transaction @Query("SELECT * FROM manga_library WHERE status IS NOT NULL ORDER BY updatedAt DESC") fun observeLibrary(): Flow<List<LibraryRow>>
    @Transaction @Query("SELECT * FROM manga_library WHERE favorite = 1 ORDER BY updatedAt DESC") fun observeFavorites(): Flow<List<LibraryRow>>
    @Query("DELETE FROM manga_library WHERE status IS NULL AND favorite = 0 AND userScore IS NULL") suspend fun pruneEntries()

    // ------------------------------------------------------------------ history (manga only)
    @Upsert suspend fun upsertHistory(h: MangaHistoryEntity)
    @Query("SELECT * FROM manga_history WHERE chapterKey = :key") suspend fun history(key: String): MangaHistoryEntity?
    @Transaction
    @Query("SELECT * FROM manga_history h WHERE readAt = (SELECT MAX(readAt) FROM manga_history x WHERE x.mangaId = h.mangaId) ORDER BY readAt DESC")
    fun observeHistory(): Flow<List<HistoryRow>>
    @Query("SELECT * FROM manga_history WHERE mangaId = :id ORDER BY readAt DESC") fun observeTitleHistory(id: Int): Flow<List<MangaHistoryEntity>>
    @Query("DELETE FROM manga_history") suspend fun clearHistory()
    /** v1.3.1 manga profile: chapters watched to the end. */
    @Query("SELECT COUNT(*) FROM manga_history WHERE finished = 1") fun observeFinishedChapterCount(): Flow<Int>
}

@Dao
interface DownloadDao {
    @Upsert suspend fun upsert(d: DownloadEntity)
    @Upsert suspend fun upsertAll(d: List<DownloadEntity>)
    @Query("SELECT * FROM manga_download WHERE chapterKey = :key") suspend fun get(key: String): DownloadEntity?
    @Query("SELECT * FROM manga_download WHERE chapterKey = :key") fun observe(key: String): Flow<DownloadEntity?>

    /** The queue screen: everything not finished yet, in the user's order. */
    @Transaction
    @Query("SELECT * FROM manga_download WHERE state != 'DONE' ORDER BY position, createdAt")
    fun observeQueue(): Flow<List<DownloadWithManga>>

    @Query("SELECT * FROM manga_download WHERE state IN ('QUEUED','RUNNING') ORDER BY position, createdAt LIMIT 1")
    suspend fun nextQueued(): DownloadEntity?

    @Query("SELECT COUNT(*) FROM manga_download WHERE state IN ('QUEUED','RUNNING')") suspend fun activeCount(): Int
    @Query("SELECT COUNT(*) FROM manga_download WHERE state IN ('QUEUED','RUNNING')") fun observeActiveCount(): Flow<Int>
    @Query("SELECT MAX(position) FROM manga_download") suspend fun maxPosition(): Int?

    @Query("UPDATE manga_download SET donePages = :done, totalPages = :total WHERE chapterKey = :key")
    suspend fun progress(key: String, done: Int, total: Int)
    @Query("UPDATE manga_download SET state = :state, error = :error WHERE chapterKey = :key")
    suspend fun setState(key: String, state: DownloadState, error: String? = null)
    @Query("UPDATE manga_download SET state = 'DONE', finishedAt = :at, error = NULL WHERE chapterKey = :key")
    suspend fun markDone(key: String, at: Long)
    @Query("UPDATE manga_download SET pagesJson = :json, totalPages = :total WHERE chapterKey = :key")
    suspend fun setPages(key: String, json: String, total: Int)
    @Query("UPDATE manga_download SET position = :position WHERE chapterKey = :key") suspend fun setPosition(key: String, position: Int)
    @Query("UPDATE manga_download SET read = :read WHERE chapterKey = :key") suspend fun setRead(key: String, read: Boolean)

    @Query("UPDATE manga_download SET state = 'PAUSED' WHERE state IN ('QUEUED','RUNNING','FAILED')") suspend fun pauseAll()
    @Query("UPDATE manga_download SET state = 'QUEUED', error = NULL WHERE state IN ('PAUSED','FAILED')") suspend fun resumeAll()
    /** After a crash or a killed process a RUNNING row is simply queued again (its finished pages stay). */
    @Query("UPDATE manga_download SET state = 'QUEUED' WHERE state = 'RUNNING'") suspend fun requeueRunning()

    @Query("SELECT * FROM manga_download WHERE state != 'DONE'") suspend fun unfinished(): List<DownloadEntity>
    @Query("DELETE FROM manga_download WHERE state != 'DONE'") suspend fun deleteUnfinished()
    @Query("DELETE FROM manga_download WHERE chapterKey = :key") suspend fun delete(key: String)

    @Query("SELECT * FROM manga_download WHERE mangaId = :id AND state = 'DONE'") fun observeDownloadedChapters(id: Int): Flow<List<DownloadEntity>>
    @Query("SELECT * FROM manga_download WHERE mangaId = :id") fun observeTitleDownloads(id: Int): Flow<List<DownloadEntity>>

    @Query(
        "SELECT m.*, COUNT(d.chapterKey) AS chapterCount FROM manga m JOIN manga_download d ON d.mangaId = m.id " +
            "WHERE d.state = 'DONE' GROUP BY m.id ORDER BY MAX(d.finishedAt) DESC",
    )
    fun observeDownloadedTitles(): Flow<List<DownloadedTitle>>
}

@Database(
    entities = [MangaEntity::class, MangaFeedEntity::class, MangaLibraryEntity::class, MangaHistoryEntity::class, DownloadEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class MangaDatabase : RoomDatabase() {
    abstract fun manga(): MangaDao
    abstract fun downloads(): DownloadDao

    companion object {
        const val NAME = "manga.db"

        /** 1.3.0 → 1.3.1: five nullable detail columns; every existing row (library, history, downloads) stays. */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `manga` ADD COLUMN `idMal` INTEGER")
                db.execSQL("ALTER TABLE `manga` ADD COLUMN `authors` TEXT")
                db.execSQL("ALTER TABLE `manga` ADD COLUMN `relationsJson` TEXT")
                db.execSQL("ALTER TABLE `manga` ADD COLUMN `malScore` REAL")
                db.execSQL("ALTER TABLE `manga` ADD COLUMN `malScoredBy` INTEGER")
            }
        }
    }
}
