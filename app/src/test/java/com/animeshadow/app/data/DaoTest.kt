package com.animeshadow.app.data

import com.animeshadow.app.data.local.AiringEntity
import com.animeshadow.app.data.local.AppDatabase
import com.animeshadow.app.data.local.FeedItemEntity
import com.animeshadow.app.data.local.LibraryEntryEntity
import com.animeshadow.app.data.local.WatchStatus
import com.animeshadow.app.testutil.Fixtures
import com.animeshadow.app.testutil.TestDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DaoTest {
    private lateinit var db: AppDatabase

    @Before fun setUp() { db = TestDb.inMemory() }
    @After fun tearDown() = db.close()

    @Test fun feed_keeps_order() = runTest {
        val dao = db.animeDao()
        val items = Fixtures.airing.take(5)
        dao.upsertAll(items)
        dao.insertFeedItems(items.reversed().mapIndexed { i, a -> FeedItemEntity("f", a.id, i) })
        assertEquals(items.reversed().map { it.id }, dao.observeFeed("f").first().map { it.id })
        assertEquals(5, dao.feedSize("f"))
        dao.clearFeed("f")
        assertTrue(dao.observeFeed("f").first().isEmpty())
    }

    @Test fun library_relation_loads_anime() = runTest {
        val a = Fixtures.detail
        db.animeDao().upsert(a)
        db.libraryDao().upsert(LibraryEntryEntity(a.id, WatchStatus.WATCHING, 3, 8, 1, 1))
        val items = db.libraryDao().observeAll().first()
        assertEquals(1, items.size)
        assertEquals("Solo Leveling", items.first().anime?.displayTitle)
        assertEquals(listOf(a.id), db.libraryDao().watchingIds())
    }

    @Test fun library_entry_without_cached_anime_still_listed() = runTest {
        db.libraryDao().upsert(LibraryEntryEntity(999, WatchStatus.PLAN_TO_WATCH, 0, null, 1, 1))
        val items = db.libraryDao().observeAll().first()
        assertNull(items.single().anime)
    }

    @Test fun schedule_join_filters_by_time() = runTest {
        val dao = db.animeDao()
        val (a, b) = Fixtures.airing.take(2)
        dao.upsertAll(listOf(a, b))
        dao.upsertAiring(listOf(AiringEntity(a.id, 5, 1_000), AiringEntity(b.id, 2, 5_000), AiringEntity(a.id, 6, 9_000)))
        val rows = dao.observeSchedule(2_000, 10_000).first()
        assertEquals(listOf(b.id to 2, a.id to 6), rows.map { it.anime.id to it.airEpisode })
        dao.deleteAiringBefore(6_000)
        assertEquals(1, dao.observeSchedule(0, 10_000).first().size)
    }

    @Test fun local_search_matches_any_title() = runTest {
        db.animeDao().upsertAll(Fixtures.search + Fixtures.airing)
        assertTrue(db.animeDao().searchLocal("leveling").all { it.displayTitle.contains("Leveling", true) || it.titleRomaji.orEmpty().contains("Level", true) })
        assertTrue(db.animeDao().searchLocal("俺だけ").isNotEmpty())
    }

    @Test fun prune_keeps_referenced_rows() = runTest {
        val dao = db.animeDao()
        val (keepLib, keepFav, drop) = Fixtures.airing.take(3)
        dao.upsertAll(listOf(keepLib, keepFav, drop))
        db.libraryDao().upsert(LibraryEntryEntity(keepLib.id, WatchStatus.WATCHING, 0, null, 1, 1))
        db.libraryDao().insertFavoriteAnime(com.animeshadow.app.data.local.FavoriteAnimeEntity(keepFav.id, 1))
        assertEquals(1, dao.pruneUnreferenced())
        assertNotNull(dao.get(keepLib.id))
        assertNotNull(dao.get(keepFav.id))
        assertNull(dao.get(drop.id))
    }

    @Test fun merge_keeps_detail_fields_on_card_refresh() {
        val card = Fixtures.airing.first()
        val withDetail = card.copy(description = "story", banner = "b", malScore = 8.1, detailFetchedAt = 5)
        val merged = card.copy(score = 1).mergedWith(withDetail)
        assertEquals("story", merged.description)
        assertEquals(8.1, merged.malScore!!, 0.0)
        assertEquals(1, merged.score)
    }
}
