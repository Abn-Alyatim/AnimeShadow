package com.animeshadow.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.animeshadow.app.data.local.AppDatabase
import com.animeshadow.app.data.local.WatchStatus
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Updating from v1.0 must keep the user's library: builds a real version-1 database from the exported v1 schema,
 * then opens it with v1.1 (Room validates every table against the v2 schema) and checks the data is still there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    @get:Rule val tmp = TemporaryFolder()

    /** v1.1.0 (DB 2) -> v1.1.1 (DB 3): the cached Arabic synopsis and the user's list survive; new columns start empty. */
    @Test fun v2_database_migrates_to_v3_keeping_user_data_and_arabic() = runTest {
        val file = tmp.root.resolve("v2.db")
        val schema = Json.parseToJsonElement(File("schemas/com.animeshadow.app.data.local.AppDatabase/2.json").readText()).jsonObject
        val database = schema["database"]!!.jsonObject
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            database["entities"]!!.jsonArray.forEach { e ->
                val o = e.jsonObject
                val table = o["tableName"]!!.jsonPrimitive.content
                db.execSQL(o["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                o["indices"]?.jsonArray?.forEach { i -> db.execSQL(i.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
            }
            database["setupQueries"]!!.jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
            db.execSQL("INSERT INTO anime (id, titleEnglish, genres, cardUpdatedAt, isAdult, description, descriptionAr, descriptionArSrc) VALUES (21, 'ONE PIECE', 'Action', 0, 0, 'x', 'س', 120)")
            db.execSQL("INSERT INTO library (animeId, status, progress, userScore, addedAt, updatedAt, remind, scoreStory) VALUES (21, 'WATCHING', 5, 9, 1, 2, 0, 8)")
            db.version = 2
        }
        val room = Room.databaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java, file.absolutePath)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
        try {
            val e = room.libraryDao().get(21)!!
            assertEquals(5, e.progress)
            assertEquals(8, e.scoreStory)
            val a = room.animeDao().get(21)!!
            assertEquals("س", a.descriptionAr)
            assertNull(a.charNames)
            assertNull(a.arFailedAt)
        } finally {
            room.close()
        }
    }

    @Test fun v1_database_migrates_to_v2_keeping_user_data() = runTest {
        val file = tmp.root.resolve("v1.db")
        val schema = Json.parseToJsonElement(File("schemas/com.animeshadow.app.data.local.AppDatabase/1.json").readText()).jsonObject
        val database = schema["database"]!!.jsonObject
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            database["entities"]!!.jsonArray.forEach { e ->
                val o = e.jsonObject
                val table = o["tableName"]!!.jsonPrimitive.content
                db.execSQL(o["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                o["indices"]?.jsonArray?.forEach { i -> db.execSQL(i.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
            }
            database["setupQueries"]!!.jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
            db.execSQL("INSERT INTO anime (id, titleEnglish, genres, cardUpdatedAt, isAdult) VALUES (21, 'ONE PIECE', 'Action', 0, 0)")
            db.execSQL("INSERT INTO library (animeId, status, progress, userScore, addedAt, updatedAt, remind) VALUES (21, 'WATCHING', 5, 9, 1, 2, 0)")
            db.execSQL("INSERT INTO watched_episode (animeId, episode, watchedAt) VALUES (21, 5, 3)")
            db.version = 1
        }

        val room = Room.databaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java, file.absolutePath)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
        try {
            val e = room.libraryDao().get(21)!!
            assertEquals(WatchStatus.WATCHING, e.status)
            assertEquals(5, e.progress)
            assertEquals(9, e.userScore)
            assertNull(e.scoreStory)
            assertEquals(listOf(5), room.libraryDao().watched(21))
            val a = room.animeDao().get(21)!!
            assertEquals("ONE PIECE", a.titleEnglish)
            assertNull(a.malPoster)
            assertTrue(room.notificationDao().all().isEmpty())
        } finally {
            room.close()
        }
    }
}
