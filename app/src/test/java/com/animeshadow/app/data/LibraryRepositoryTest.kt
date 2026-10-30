package com.animeshadow.app.data

import com.animeshadow.app.data.local.AnimeEntity
import com.animeshadow.app.data.local.AppDatabase
import com.animeshadow.app.data.local.WatchStatus
import com.animeshadow.app.testutil.FakeClock
import com.animeshadow.app.testutil.TestDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: AppDatabase
    private lateinit var repo: LibraryRepository
    private val clock = FakeClock()

    private val finished24 = AnimeEntity(1, titleEnglish = "Finished 24", episodes = 24, status = "FINISHED", duration = 24)
    private val airing = AnimeEntity(2, titleEnglish = "Airing", episodes = null, status = "RELEASING", nextEpisode = 8)

    @Before fun setUp() = runTest {
        db = TestDb.onDisk(tmp.root.resolve("lib.db"))
        db.animeDao().upsertAll(listOf(finished24, airing))
        repo = LibraryRepository(db, clock)
    }

    @After fun tearDown() = db.close()

    @Test fun plus_marks_next_episode_and_adds_as_watching() = runTest {
        assertEquals(1, repo.increment(1))
        assertEquals(2, repo.increment(1))
        val e = repo.entry(1)!!
        assertEquals(WatchStatus.WATCHING, e.status)
        assertEquals(2, e.progress)
        assertEquals(listOf(1, 2), db.libraryDao().watched(1))
    }

    @Test fun minus_unmarks_current_episode() = runTest {
        repeat(5) { repo.increment(1) }
        assertEquals(4, repo.decrement(1))
        assertEquals(4, repo.entry(1)!!.progress)
        assertEquals(listOf(1, 2, 3, 4), db.libraryDao().watched(1))
        repeat(10) { repo.decrement(1) }
        assertEquals(0, repo.entry(1)!!.progress)
        assertTrue(db.libraryDao().watched(1).isEmpty())
    }

    @Test fun plus_never_passes_the_total() = runTest {
        db.libraryDao().upsert(com.animeshadow.app.data.local.LibraryEntryEntity(1, WatchStatus.WATCHING, 23, null, 0, 0))
        assertEquals(24, repo.increment(1))
        assertEquals(24, repo.increment(1))
        assertEquals(WatchStatus.COMPLETED, repo.entry(1)!!.status)
    }

    @Test fun episode_checkbox_sets_progress_to_highest_watched() = runTest {
        assertEquals(13, repo.setEpisodeWatched(1, 13, true))
        assertEquals(13, repo.setEpisodeWatched(1, 3, true))
        assertEquals(3, repo.setEpisodeWatched(1, 13, false))
        assertEquals(listOf(3), db.libraryDao().watched(1))
        assertEquals(0, repo.setEpisodeWatched(1, 3, false))
    }

    @Test fun watching_last_episode_of_finished_show_completes_it_and_unticking_reopens() = runTest {
        repo.markUpTo(1, 24)
        assertEquals(WatchStatus.COMPLETED, repo.entry(1)!!.status)
        repo.setEpisodeWatched(1, 24, false)
        assertEquals(WatchStatus.WATCHING, repo.entry(1)!!.status)
        assertEquals(23, repo.entry(1)!!.progress)
    }

    @Test fun airing_show_without_total_is_never_auto_completed() = runTest {
        repo.markUpTo(2, 7)
        assertEquals(WatchStatus.WATCHING, repo.entry(2)!!.status)
        // v1.5.0: episode 8 is announced but not out yet, so +1 stays at 7
        assertEquals(7, repo.increment(2))
    }

    @Test fun completed_status_marks_all_episodes() = runTest {
        repo.setStatus(1, WatchStatus.COMPLETED)
        assertEquals(24, repo.entry(1)!!.progress)
        assertEquals((1..24).toList(), db.libraryDao().watched(1))
    }

    @Test fun plan_to_watch_moves_to_watching_on_first_episode() = runTest {
        repo.setStatus(2, WatchStatus.PLAN_TO_WATCH)
        repo.increment(2)
        assertEquals(WatchStatus.WATCHING, repo.entry(2)!!.status)
    }

    @Test fun progress_and_checkboxes_survive_closing_and_reopening_the_database() = runTest {
        repeat(12) { repo.increment(1) }
        repo.setEpisodeWatched(1, 20, true)
        repo.setUserScore(1, 9)
        db.close()
        val reopened = TestDb.onDisk(tmp.root.resolve("lib.db"))
        val e = reopened.libraryDao().get(1)!!
        assertEquals(20, e.progress)
        assertEquals(9, e.userScore)
        assertEquals((1..12).toList() + 20, reopened.libraryDao().watched(1))
        reopened.close()
        db = TestDb.onDisk(tmp.root.resolve("lib.db"))
    }

    @Test fun remove_deletes_entry_and_episodes() = runTest {
        repo.increment(1)
        repo.remove(1)
        assertNull(repo.entry(1))
        assertTrue(db.libraryDao().watched(1).isEmpty())
    }

    @Test fun score_is_clamped_and_clearable() = runTest {
        repo.setUserScore(1, 14)
        assertEquals(10, repo.entry(1)!!.userScore)
        repo.setUserScore(1, null)
        assertNull(repo.entry(1)!!.userScore)
    }

    @Test fun favorites_and_history() = runTest {
        repo.setFavoriteAnime(1, true)
        assertTrue(repo.observeIsFavoriteAnime(1).first())
        repo.setFavoriteCharacter(7, "Jinwoo", null, 1, "Solo", true)
        assertEquals(listOf(7), repo.observeFavoriteCharacters().first().map { it.characterId })
        repo.setFavoriteCharacter(7, "Jinwoo", null, 1, "Solo", false)
        assertTrue(repo.observeFavoriteCharacters().first().isEmpty())

        repo.increment(1); clock.advance(1000)
        repo.increment(2); clock.advance(1000)
        repo.increment(1)
        val h = repo.observeHistory().first()
        assertEquals(listOf(1, 2), h.map { it.anime.id })
        assertEquals(2, h.first().lastEpisode)
    }
}
