package com.animeshadow.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.animeshadow.app.data.local.AnimeEntity
import com.animeshadow.app.data.local.AppDatabase
import com.animeshadow.app.data.local.WatchStatus
import com.animeshadow.app.data.settings.AppLanguage
import com.animeshadow.app.data.settings.Profile
import com.animeshadow.app.data.settings.SettingsRepository
import com.animeshadow.app.domain.LibraryFilter
import com.animeshadow.app.domain.LibrarySort
import com.animeshadow.app.testutil.FakeClock
import com.animeshadow.app.testutil.TestDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

class SettingsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun <T> withStore(file: File, block: suspend (SettingsRepository) -> T): T {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val store = PreferenceDataStoreFactory.create(scope = scope) { file }
        return try { runBlocking { block(SettingsRepository(store)) } } finally {
            scope.cancel()
            runBlocking { scope.coroutineContext[kotlinx.coroutines.Job]!!.join() }
        }
    }

    @Test fun defaults_are_arabic_amoled_24min() {
        val s = withStore(tmp.root.resolve("a.preferences_pb")) { it.current() }
        assertEquals(AppLanguage.ARABIC, s.language)
        assertEquals(24, s.defaultEpisodeMinutes)
        assertFalse(s.notifications)
        assertTrue(s.gridMode)
    }

    @Test fun language_and_settings_persist_across_restarts() {
        val f = tmp.root.resolve("b.preferences_pb")
        withStore(f) {
            it.setLanguage(AppLanguage.ENGLISH)
            it.setDefaultEpisodeMinutes(500)
            it.setNotifications(true)
            it.setGridMode(false)
            it.setProfile(Profile(" Madyan ", "madyan", "AS fan", null))
            it.setSavedFilter(LibraryFilter(formats = setOf("TV"), sort = LibrarySort.NAME, ascending = true))
        }
        // A brand-new DataStore instance on the same file = the app after a restart.
        val s = withStore(f) { it.current() }
        assertEquals(AppLanguage.ENGLISH, s.language)
        assertEquals(180, s.defaultEpisodeMinutes)
        assertTrue(s.notifications)
        assertFalse(s.gridMode)
        assertEquals("Madyan", s.profile.name)
        assertEquals(setOf("TV"), s.savedFilter.formats)
        assertEquals(LibrarySort.NAME, s.savedFilter.sort)
        withStore(f) { it.setLanguage(AppLanguage.ARABIC) }
        assertEquals(AppLanguage.ARABIC, withStore(f) { it.current() }.language)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupManagerTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun seed(db: AppDatabase) = runBlocking {
        db.animeDao().upsertAll(listOf(AnimeEntity(1, titleEnglish = "One", episodes = 12, status = "FINISHED"), AnimeEntity(2, titleEnglish = "Two")))
        val lib = LibraryRepository(db, FakeClock())
        repeat(5) { lib.increment(1) }
        lib.setUserScore(1, 8)
        lib.setStatus(2, WatchStatus.PLAN_TO_WATCH)
        lib.setFavoriteAnime(2, true)
        lib.setFavoriteCharacter(7, "Jinwoo", "img", 1, "One", true)
    }

    @Test fun export_then_import_into_an_empty_install_restores_everything() = runTest {
        val src = TestDb.inMemory()
        seed(src)
        val out = ByteArrayOutputStream()
        val file = BackupManager(src).export(out, 123)
        assertEquals(2, file.library.size)
        src.close()

        val dst = TestDb.inMemory() // fresh install: no anime cached at all
        val summary = BackupManager(dst).import(ByteArrayInputStream(out.toByteArray()))
        assertEquals(2, summary.library)
        assertEquals(5, summary.episodes)
        val items = dst.libraryDao().all().associateBy { it.entry.animeId }
        assertEquals(5, items[1]!!.entry.progress)
        assertEquals(8, items[1]!!.entry.userScore)
        assertEquals("One", items[1]!!.anime!!.displayTitle) // titles shown offline right after import
        assertEquals(WatchStatus.PLAN_TO_WATCH, items[2]!!.entry.status)
        assertEquals((1..5).toList(), dst.libraryDao().watched(1))
        assertEquals(listOf(2), dst.libraryDao().allFavoriteAnime().map { it.animeId })
        assertEquals("Jinwoo", dst.libraryDao().allFavoriteCharacters().single().name)
        dst.close()
    }

    @Test fun rejects_foreign_files_without_touching_data() = runTest {
        val db = TestDb.inMemory()
        seed(db)
        val before = db.libraryDao().all().size
        for (bad in listOf("not json", "{\"app\":\"Other\",\"exportedAt\":1,\"anime\":[],\"library\":[],\"episodes\":[],\"favoriteAnime\":[],\"favoriteCharacters\":[]}")) {
            val r = runCatching { BackupManager(db).import(ByteArrayInputStream(bad.toByteArray())) }
            assertTrue(r.isFailure)
        }
        assertEquals(before, db.libraryDao().all().size)
        db.close()
    }
}
