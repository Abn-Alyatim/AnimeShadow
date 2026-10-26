package com.animeshadow.app.sync

import android.app.NotificationManager
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.animeshadow.app.data.AnimeRepository
import com.animeshadow.app.data.LibraryRepository
import com.animeshadow.app.data.NotificationRepository
import com.animeshadow.app.data.local.AnimeEntity
import com.animeshadow.app.data.local.AppDatabase
import com.animeshadow.app.data.local.LibraryEntryEntity
import com.animeshadow.app.data.local.WatchStatus
import com.animeshadow.app.data.remote.PoliteInterceptor
import com.animeshadow.app.data.settings.SettingsRepository
import com.animeshadow.app.di.Network
import com.animeshadow.app.testutil.FakeClock
import com.animeshadow.app.testutil.Fixtures
import com.animeshadow.app.testutil.TestDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The background sync end to end: recorded AniList batch response -> new episode detected -> notification posted. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32]) // below 33: no runtime POST_NOTIFICATIONS permission needed
class EpisodeSyncWorkerTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var server: MockWebServer
    private lateinit var db: AppDatabase
    private lateinit var anime: AnimeRepository
    private lateinit var library: LibraryRepository
    private lateinit var settings: SettingsRepository
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        db = TestDb.inMemory()
        val client = Network.client(PoliteInterceptor(100, 60_000, 0, maxRetries = 0))
        anime = AnimeRepository(db, Network.aniList(server.url("/").toString(), client), null, FakeClock())
        library = LibraryRepository(db, FakeClock())
        settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { tmp.root.resolve("s.preferences_pb") })
    }

    @After fun tearDown() {
        db.close(); server.shutdown(); scope.cancel()
    }

    private fun worker() = TestListenableWorkerBuilder<EpisodeSyncWorker>(context)
        .setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                EpisodeSyncWorker(appContext, workerParameters, anime, library, settings, NotificationRepository(db.notificationDao(), FakeClock()))
        })
        .build()

    @Test fun notifies_new_episode_of_followed_anime_once() = runBlocking {
        // The recorded batch response contains ONE PIECE (AniList 21), airing with a next episode.
        val batch = Fixtures.page("anilist_batch.json").media
        val op = batch.first { it.id == 21 }
        val latest = op.nextAiringEpisode!!.episode - 1
        db.animeDao().upsert(AnimeEntity(21, titleEnglish = "ONE PIECE"))
        db.libraryDao().upsert(LibraryEntryEntity(21, WatchStatus.WATCHING, 10, null, 0, 0, lastSeenEpisode = latest - 1))
        settings.setNotifications(true)
        server.enqueue(MockResponse().setBody(Fixtures.text("anilist_batch.json")))

        assertEquals(ListenableWorker.Result.success(), worker().doWork())

        val nm = shadowOf(context.getSystemService(NotificationManager::class.java))
        assertEquals(1, nm.allNotifications.size)
        assertTrue(shadowOf(nm.allNotifications.first()).contentText.toString().contains(latest.toString()))
        assertEquals(latest, library.entry(21)!!.lastSeenEpisode)
        // v1.1: the alert is also in the Notifications screen history, unread.
        val log = db.notificationDao().all()
        assertEquals(1, log.size)
        assertEquals(21, log.first().animeId)
        assertEquals(latest, log.first().episode)
        assertEquals(false, log.first().read)

        // Second sync with the same data: nothing new, no second notification.
        server.enqueue(MockResponse().setBody(Fixtures.text("anilist_batch.json")))
        context.getSystemService(NotificationManager::class.java).cancelAll()
        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        assertEquals(0, nm.allNotifications.size)
        assertEquals(1, db.notificationDao().all().size)
    }

    @Test fun does_nothing_when_notifications_are_off() = runBlocking {
        db.libraryDao().upsert(LibraryEntryEntity(21, WatchStatus.WATCHING, 10, null, 0, 0, lastSeenEpisode = 1))
        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        assertEquals(0, server.requestCount)
    }

    @Test fun network_failure_asks_for_retry() = runBlocking {
        db.libraryDao().upsert(LibraryEntryEntity(21, WatchStatus.WATCHING, 10, null, 0, 0, lastSeenEpisode = 1))
        settings.setNotifications(true)
        server.shutdown()
        assertEquals(ListenableWorker.Result.retry(), worker().doWork())
    }
}
