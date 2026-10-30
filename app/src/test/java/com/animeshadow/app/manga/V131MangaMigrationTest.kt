package com.animeshadow.app.manga

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.animeshadow.app.data.translate.MyMemoryTranslator
import com.animeshadow.app.manga.data.MangaDatabase
import com.animeshadow.app.manga.data.MangaMediaDto
import com.animeshadow.app.manga.data.MangaRelation
import com.animeshadow.app.manga.data.ReadStatus
import com.animeshadow.app.ui.common.titlePair
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v1.3.1: the manga.db 1→2 migration keeps the library, history and downloads (1.3.0 users update in place). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class V131MangaMigrationTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    /** The exact 1.3.0 (version 1) schema, from Room's generated code of that release. */
    private val v1Schema = listOf(
            """CREATE TABLE IF NOT EXISTS `manga` (`id` INTEGER NOT NULL, `titleRomaji` TEXT, `titleEnglish` TEXT, `titleNative` TEXT, `cover` TEXT, `coverXL` TEXT, `banner` TEXT, `color` TEXT, `format` TEXT, `status` TEXT, `country` TEXT, `chapters` INTEGER, `volumes` INTEGER, `score` INTEGER, `popularity` INTEGER, `genres` TEXT NOT NULL, `description` TEXT, `descriptionAr` TEXT, `source` TEXT, `startDate` TEXT, `endDate` TEXT, `isAdult` INTEGER NOT NULL, `detailLoaded` INTEGER NOT NULL, `cachedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))""",
            """CREATE TABLE IF NOT EXISTS `manga_feed` (`feed` TEXT NOT NULL, `mangaId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`feed`, `mangaId`))""",
            """CREATE TABLE IF NOT EXISTS `manga_library` (`mangaId` INTEGER NOT NULL, `status` TEXT, `favorite` INTEGER NOT NULL, `lastChapter` TEXT, `userScore` INTEGER, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`mangaId`))""",
            """CREATE TABLE IF NOT EXISTS `manga_history` (`chapterKey` TEXT NOT NULL, `mangaId` INTEGER NOT NULL, `chapterNumber` TEXT NOT NULL, `page` INTEGER NOT NULL, `pageCount` INTEGER NOT NULL, `readAt` INTEGER NOT NULL, `finished` INTEGER NOT NULL, PRIMARY KEY(`chapterKey`))""",
            """CREATE INDEX IF NOT EXISTS `index_manga_history_mangaId` ON `manga_history` (`mangaId`)""",
            """CREATE TABLE IF NOT EXISTS `manga_download` (`chapterKey` TEXT NOT NULL, `mangaId` INTEGER NOT NULL, `providerId` TEXT NOT NULL, `chapterId` TEXT NOT NULL, `chapterNumber` TEXT NOT NULL, `chapterTitle` TEXT, `state` TEXT NOT NULL, `totalPages` INTEGER NOT NULL, `donePages` INTEGER NOT NULL, `position` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `finishedAt` INTEGER, `pagesJson` TEXT, `read` INTEGER NOT NULL, `error` TEXT, PRIMARY KEY(`chapterKey`))""",
            """CREATE INDEX IF NOT EXISTS `index_manga_download_mangaId` ON `manga_download` (`mangaId`)""",
            """CREATE INDEX IF NOT EXISTS `index_manga_download_state` ON `manga_download` (`state`)"""
    )

    @Test fun migration_1_to_2_keeps_library_history_and_adds_detail_columns() {
        val name = "manga_mig_test.db"
        ctx.deleteDatabase(name)
        val f = ctx.getDatabasePath(name).also { it.parentFile?.mkdirs() }
        val old = SQLiteDatabase.openOrCreateDatabase(f, null)
        v1Schema.forEach { old.execSQL(it) }
        old.execSQL("INSERT INTO manga (id, titleRomaji, titleEnglish, isAdult, detailLoaded, cachedAt, genres) VALUES (7, 'Blue Lock', 'Blue Lock', 0, 1, 1, 'Sports')")
        old.execSQL("INSERT INTO manga_library (mangaId, status, favorite, userScore, updatedAt) VALUES (7, 'READING', 1, 9, 5)")
        old.execSQL("INSERT INTO manga_history (chapterKey, mangaId, chapterNumber, page, pageCount, readAt, finished) VALUES ('d:7-1', 7, '1', 20, 20, 9, 1)")
        old.version = 1
        old.close()
        val room = Room.databaseBuilder(ctx, MangaDatabase::class.java, name).addMigrations(MangaDatabase.MIGRATION_1_2, MangaDatabase.MIGRATION_2_3)
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE).allowMainThreadQueries().build()
        try {
            val db = room.openHelper.writableDatabase // runs the migration and Room's schema check
            assertEquals(3, db.version) // 1.5.0 adds manga_read on top of 1→2
            db.query("SELECT COUNT(*) FROM manga_read").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
            db.query("SELECT status, favorite, userScore FROM manga_library WHERE mangaId = 7").use { c ->
                assertTrue(c.moveToFirst()); assertEquals("READING", c.getString(0)); assertEquals(1, c.getInt(1)); assertEquals(9, c.getInt(2))
            }
            db.query("SELECT COUNT(*) FROM manga_history WHERE finished = 1").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
            db.query("SELECT titleEnglish, idMal, authors, relationsJson, malScore, malScoredBy FROM manga WHERE id = 7").use { c ->
                assertTrue(c.moveToFirst()); assertEquals("Blue Lock", c.getString(0))
                (1..5).forEach { assertTrue(c.isNull(it)) }
            }
        } finally { room.close(); ctx.deleteDatabase(name) }
    }

}
