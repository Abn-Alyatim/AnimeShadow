package com.animeshadow.app.data

import com.animeshadow.app.data.local.AnimeEntity
import com.animeshadow.app.data.local.AppDatabase
import com.animeshadow.app.data.local.LibraryEntryEntity
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v1.1: rating sliders (item 3), mark-all / unmark-all + undo (item 14), notification history (item 8). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryV11Test {
    private lateinit var db: AppDatabase
    private lateinit var repo: LibraryRepository
    private lateinit var notifications: NotificationRepository
    private val clock = FakeClock()
    private val finished12 = AnimeEntity(1, titleEnglish = "Finished 12", episodes = 12, status = "FINISHED")
    private val airing = AnimeEntity(2, titleEnglish = "Airing", status = "RELEASING", nextEpisode = 9)

    @Before fun setUp() = runTest {
        db = TestDb.inMemory()
        db.animeDao().upsertAll(listOf(finished12, airing))
        repo = LibraryRepository(db, clock)
        notifications = NotificationRepository(db.notificationDao(), clock)
    }

    @After fun tearDown() = db.close()

    // ---------- item 3: four sliders ----------
    @Test fun rating_sliders_store_parts_and_rounded_overall() = runTest {
        repo.setRating(1, RatingParts(story = 9, characters = 8, animation = 7, music = 6))
        val e = repo.entry(1)!!
        assertEquals(listOf(9, 8, 7, 6), listOf(e.scoreStory, e.scoreCharacters, e.scoreAnimation, e.scoreMusic))
        assertEquals(7.5, e.overallScore!!, 0.0001)
        assertEquals(8, e.userScore) // 7.5 rounds half up
        assertEquals(WatchStatus.PLAN_TO_WATCH, e.status) // rating a title not in the list adds it as plan-to-watch (as v1.0)
        assertEquals(RatingParts(9, 8, 7, 6), RatingParts.of(e))
    }

    @Test fun all_zero_clears_the_rating() = runTest {
        repo.setRating(1, RatingParts(10, 10, 10, 10))
        assertEquals(10, repo.entry(1)!!.userScore)
        repo.setRating(1, RatingParts())
        val e = repo.entry(1)!!
        assertNull(e.userScore)
        assertNull(e.scoreStory)
        assertNull(e.overallScore)
    }

    @Test fun v10_whole_score_prefills_the_sliders() {
        val e = LibraryEntryEntity(1, WatchStatus.WATCHING, 0, 7, 0, 0)
        assertEquals(RatingParts(7, 7, 7, 7), RatingParts.of(e))
        assertEquals(7.0, e.overallScore!!, 0.0)
    }

    // ---------- item 14: small eye = mark all / unmark all, with undo ----------
    @Test fun mark_all_then_undo_restores_exactly() = runTest {
        repo.setEpisodeWatched(1, 3, true)
        repo.setEpisodeWatched(1, 5, true)
        val before = repo.entry(1)!!
        val snap = repo.markAllWatched(1, 12)
        assertEquals((1..12).toList(), db.libraryDao().watched(1))
        assertEquals(12, repo.entry(1)!!.progress)
        assertEquals(WatchStatus.COMPLETED, repo.entry(1)!!.status) // finished show, last episode -> completed (v1.0 rule)

        repo.restore(snap)
        assertEquals(listOf(3, 5), db.libraryDao().watched(1))
        assertEquals(before, repo.entry(1))
    }

    @Test fun unmark_all_then_undo() = runTest {
        repo.markAllWatched(1, 12)
        val snap = repo.unmarkAllWatched(1)
        assertTrue(db.libraryDao().watched(1).isEmpty())
        assertEquals(0, repo.entry(1)!!.progress)
        assertEquals(WatchStatus.WATCHING, repo.entry(1)!!.status)
        repo.restore(snap)
        assertEquals((1..12).toList(), db.libraryDao().watched(1))
        assertEquals(WatchStatus.COMPLETED, repo.entry(1)!!.status)
    }

    @Test fun undo_of_mark_all_on_a_title_not_in_the_list_removes_it_again() = runTest {
        val snap = repo.markAllWatched(2, 8)
        assertEquals(8, repo.entry(2)!!.progress)
        assertEquals(WatchStatus.WATCHING, repo.entry(2)!!.status) // airing: never auto-completed
        repo.restore(snap)
        assertNull(repo.entry(2))
        assertTrue(db.libraryDao().watched(2).isEmpty())
    }

    @Test fun backup_keeps_the_rating_sliders() = runTest {
        repo.setRating(1, RatingParts(9, 8, 7, 6))
        val out = java.io.ByteArrayOutputStream()
        BackupManager(db).export(out, clock.now())
        val other = TestDb.inMemory()
        try {
            BackupManager(other).import(out.toByteArray().inputStream())
            val e = other.libraryDao().get(1)!!
            assertEquals(listOf(9, 8, 7, 6), listOf(e.scoreStory, e.scoreCharacters, e.scoreAnimation, e.scoreMusic))
            assertEquals(8, e.userScore)
        } finally {
            other.close()
        }
    }

    // ---------- item 8: notification history ----------
    @Test fun notification_history_read_unread_and_clear() = runTest {
        val id1 = notifications.log(finished12, 11, NotificationRepository.KIND_NEW_EPISODE)
        clock.advance(1000)
        notifications.log(airing, 8, NotificationRepository.KIND_REMINDER)
        assertEquals(2, notifications.observeUnreadCount().first())
        val all = notifications.observeAll().first()
        assertEquals(listOf(2, 1), all.map { it.animeId }) // newest first
        notifications.markRead(id1)
        assertEquals(1, notifications.observeUnreadCount().first())
        notifications.markAllRead()
        assertEquals(0, notifications.observeUnreadCount().first())
        notifications.clear()
        assertTrue(notifications.observeAll().first().isEmpty())
    }

    @Test fun notification_history_is_bounded() = runTest {
        repeat(305) { notifications.log(airing, it + 1, NotificationRepository.KIND_NEW_EPISODE); clock.advance(10) }
        val all = db.notificationDao().all()
        assertEquals(300, all.size)
        assertEquals(305, all.first().episode)
    }
}
