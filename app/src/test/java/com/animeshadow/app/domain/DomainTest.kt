package com.animeshadow.app.domain

import com.animeshadow.app.data.TextClean
import com.animeshadow.app.data.local.AnimeEntity
import com.animeshadow.app.data.local.LibraryEntryEntity
import com.animeshadow.app.data.local.RecommendationEntity
import com.animeshadow.app.data.local.ScheduleRow
import com.animeshadow.app.data.local.WatchStatus
import com.animeshadow.app.sync.NewEpisodeDetector
import com.animeshadow.app.testutil.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class StatsCalculatorTest {
    private fun anime(id: Int, duration: Int?, genres: String = "Action") = AnimeEntity(id = id, duration = duration, genres = genres)

    @Test fun counts_statuses_and_episodes() {
        val items = listOf(
            Fixtures.entry(anime(1, 24), WatchStatus.COMPLETED, 12, 9),
            Fixtures.entry(anime(2, 24), WatchStatus.WATCHING, 5, 7),
            Fixtures.entry(anime(3, 24), WatchStatus.PLAN_TO_WATCH, 0),
            Fixtures.entry(anime(4, 24), WatchStatus.COMPLETED, 24),
        )
        val s = StatsCalculator.compute(items, defaultMinutes = 20)
        assertEquals(4, s.total)
        assertEquals(2, s.count(WatchStatus.COMPLETED))
        assertEquals(1, s.count(WatchStatus.WATCHING))
        assertEquals(1, s.count(WatchStatus.PLAN_TO_WATCH))
        assertEquals(0, s.count(WatchStatus.DROPPED))
        assertEquals(41, s.episodesWatched)
        assertEquals(41L * 24, s.totalMinutes)
        assertFalse(s.usesDefaultDuration)
        assertEquals(8.0, s.meanUserScore!!, 0.001)
    }

    @Test fun unknown_duration_falls_back_to_default_and_is_flagged() {
        val items = listOf(
            Fixtures.entry(anime(1, 24), WatchStatus.COMPLETED, 10),
            Fixtures.entry(anime(2, null), WatchStatus.WATCHING, 6),
            Fixtures.entry(anime(3, 0), WatchStatus.WATCHING, 4),
        )
        val s = StatsCalculator.compute(items, defaultMinutes = 23)
        assertEquals(240L, s.knownMinutes)
        assertEquals(10, s.episodesWithoutDuration)
        assertTrue(s.usesDefaultDuration)
        assertEquals(240L + 10 * 23, s.totalMinutes)
        assertEquals((240.0 + 230) / 60, s.totalHours, 0.0001)
    }

    @Test fun top_genres_sorted_by_count_then_name() {
        val items = listOf(
            Fixtures.entry(anime(1, 24, "Action|Drama"), WatchStatus.COMPLETED),
            Fixtures.entry(anime(2, 24, "Action|Comedy"), WatchStatus.COMPLETED),
            Fixtures.entry(anime(3, 24, "Comedy|Drama|Action"), WatchStatus.COMPLETED),
        )
        val s = StatsCalculator.compute(items, 24)
        assertEquals(listOf("Action" to 3, "Comedy" to 2, "Drama" to 2), s.topGenres)
    }

    @Test fun empty_library() {
        val s = StatsCalculator.compute(emptyList(), 24)
        assertEquals(0, s.total)
        assertEquals(0L, s.totalMinutes)
        assertNull(s.meanUserScore)
    }

    @Test fun real_fixture_library() {
        val s = StatsCalculator.compute(Fixtures.library, 24)
        assertEquals(18, s.total)
        assertEquals(Fixtures.library.sumOf { it.entry.progress }, s.episodesWatched)
        assertTrue(s.topGenres.isNotEmpty())
    }
}

class LibraryFilterTest {
    private val items = listOf(
        Fixtures.entry(AnimeEntity(1, titleEnglish = "Bleach", format = "TV", status = "FINISHED", seasonYear = 2004, score = 79, genres = "Action|Supernatural"), WatchStatus.COMPLETED, addedAt = 3),
        Fixtures.entry(AnimeEntity(2, titleEnglish = "akira", format = "MOVIE", status = "FINISHED", seasonYear = 1988, score = 80, genres = "Action|Sci-Fi"), WatchStatus.COMPLETED, addedAt = 1),
        Fixtures.entry(AnimeEntity(3, titleEnglish = "Frieren", format = "TV", status = "RELEASING", seasonYear = 2023, score = 91, genres = "Adventure|Drama|Fantasy"), WatchStatus.WATCHING, addedAt = 2),
        Fixtures.entry(AnimeEntity(4, titleEnglish = "Chibi", format = "TV_SHORT", status = "FINISHED", seasonYear = 2023, score = 60, genres = "Comedy"), WatchStatus.COMPLETED, addedAt = 4),
        Fixtures.entry(AnimeEntity(5, titleEnglish = "Trigun OVA", format = "OVA", status = "FINISHED", seasonYear = 2003, score = null, genres = "Action"), WatchStatus.DROPPED, addedAt = 5),
    )

    private fun ids(f: LibraryFilter, s: WatchStatus? = null) = f.apply(items, s).map { it.entry.animeId }

    @Test fun status_tab() = assertEquals(listOf(4, 1, 2), ids(LibraryFilter(), WatchStatus.COMPLETED))
    @Test fun default_sort_is_newest_added_first() = assertEquals(listOf(5, 4, 1, 3, 2), ids(LibraryFilter()))
    @Test fun oldest_first() = assertEquals(listOf(2, 3, 1, 4, 5), ids(LibraryFilter(ascending = true)))
    @Test fun type_filter_treats_tv_short_as_tv() = assertEquals(listOf(4, 1, 3), ids(LibraryFilter(formats = setOf("TV"))))
    @Test fun several_types() = assertEquals(listOf(5, 2), ids(LibraryFilter(formats = setOf("MOVIE", "OVA"))))
    @Test fun airing_filter() = assertEquals(listOf(3), ids(LibraryFilter(airing = setOf("RELEASING"))))
    @Test fun year_filter() = assertEquals(listOf(4, 3), ids(LibraryFilter(year = 2023)))
    @Test fun min_score_excludes_unscored() = assertEquals(listOf(3, 2), ids(LibraryFilter(minScore = 8.0, sort = LibrarySort.SCORE)))
    @Test fun genres_must_all_match() = assertEquals(listOf(2), ids(LibraryFilter(genres = setOf("Action", "Sci-Fi"))))
    @Test fun name_sort_is_case_insensitive() = assertEquals(listOf(2, 1, 4, 3, 5), ids(LibraryFilter(sort = LibrarySort.NAME, ascending = true)))
    @Test fun year_sort_desc() = assertEquals(listOf(4, 3, 1, 5, 2).take(2).toSet(), ids(LibraryFilter(sort = LibrarySort.YEAR)).take(2).toSet())
    @Test fun combined() = assertEquals(listOf(1), ids(LibraryFilter(formats = setOf("TV"), airing = setOf("FINISHED"), genres = setOf("Action")), WatchStatus.COMPLETED))
    @Test fun active_flag() {
        assertFalse(LibraryFilter(sort = LibrarySort.NAME).isActive)
        assertTrue(LibraryFilter(year = 2020).isActive)
    }
}

class RecommendationEngineTest {
    private val action = AnimeEntity(1, titleEnglish = "Liked action", genres = "Action|Fantasy", score = 85)
    private val romance = AnimeEntity(2, titleEnglish = "Dropped romance", genres = "Romance|Drama", score = 80)
    private val candAction = AnimeEntity(10, titleEnglish = "More action", genres = "Action|Fantasy", score = 70)
    private val candRomance = AnimeEntity(11, titleEnglish = "More romance", genres = "Romance|Drama", score = 90)
    private val candMixed = AnimeEntity(12, titleEnglish = "Source pick", genres = "Mystery", score = 75)
    private val adult = AnimeEntity(13, titleEnglish = "Adult", genres = "Action|Fantasy", score = 90, isAdult = true)

    private val library = listOf(
        Fixtures.entry(action, WatchStatus.COMPLETED, 12, 10),
        Fixtures.entry(romance, WatchStatus.DROPPED, 2, 3),
    )

    @Test fun weights_follow_status_and_rating() {
        val hi = RecommendationEngine.entryWeight(Fixtures.entry(action, WatchStatus.COMPLETED, score = 10))
        val mid = RecommendationEngine.entryWeight(Fixtures.entry(action, WatchStatus.COMPLETED, score = 5))
        val plan = RecommendationEngine.entryWeight(Fixtures.entry(action, WatchStatus.PLAN_TO_WATCH))
        val dropped = RecommendationEngine.entryWeight(Fixtures.entry(action, WatchStatus.DROPPED, score = 2))
        assertTrue(hi > mid && mid > plan && plan > 0 && dropped < 0)
    }

    @Test fun liked_genres_win_and_disliked_are_excluded() {
        val recs = RecommendationEngine.recommend(library, listOf(candRomance, candAction, adult), emptyList())
        assertEquals(listOf(10), recs.map { it.anime.id })
        assertEquals(1, recs.first().because?.id)
        assertEquals(listOf("Action", "Fantasy"), recs.first().sharedGenres)
    }

    @Test fun source_recommendation_of_a_liked_title_boosts_candidate() {
        val recs = RecommendationEngine.recommend(library, listOf(candAction, candMixed), listOf(RecommendationEntity(1, 12, 100, 0)))
        val pick = recs.first { it.anime.id == 12 }
        assertTrue(pick.fromSource)
        assertEquals(1, pick.because?.id)
    }

    @Test fun library_titles_are_never_suggested() {
        val recs = RecommendationEngine.recommend(library, listOf(action, candAction), emptyList())
        assertFalse(recs.any { it.anime.id == 1 })
    }

    @Test fun empty_library_gives_nothing() = assertTrue(RecommendationEngine.recommend(emptyList(), listOf(candAction), emptyList()).isEmpty())

    @Test fun similar_uses_genre_overlap() {
        val sim = RecommendationEngine.similar(action, listOf(candRomance, candAction, candMixed, adult))
        assertEquals(listOf(10), sim.map { it.id })
    }

    @Test fun real_fixture_library_produces_ranked_explained_results() {
        val candidates = (Fixtures.airing + Fixtures.top + Fixtures.season + Fixtures.recommendations).distinctBy { it.id }
        val recs = RecommendationEngine.recommend(Fixtures.library, candidates, emptyList())
        assertTrue(recs.isNotEmpty())
        assertEquals(recs.sortedByDescending { it.score }, recs)
        val libIds = Fixtures.library.map { it.entry.animeId }.toSet()
        assertTrue(recs.none { it.anime.id in libIds })
    }
}

class WeekScheduleTest {
    private val zone = ZoneId.of("Asia/Aden")
    private fun at(y: Int, m: Int, d: Int, h: Int) = ZonedDateTime.of(y, m, d, h, 0, 0, 0, zone).toInstant().toEpochMilli()

    @Test fun groups_by_local_weekday_saturday_first() {
        val a = AnimeEntity(1); val b = AnimeEntity(2)
        val rows = listOf(
            ScheduleRow(a, 5, at(2026, 10, 31, 20)), // Saturday
            ScheduleRow(b, 9, at(2026, 10, 30, 1)), // Friday
            ScheduleRow(a, 6, at(2026, 10, 31, 22)), // same anime same day -> once
        )
        val g = WeekSchedule.group(rows, zone)
        assertEquals(WeekSchedule.ORDER, g.keys.toList())
        assertEquals(DayOfWeek.SATURDAY, g.keys.first())
        assertEquals(listOf(1), g[DayOfWeek.SATURDAY]!!.map { it.anime.id })
        assertEquals(listOf(2), g[DayOfWeek.FRIDAY]!!.map { it.anime.id })
        assertTrue(g[DayOfWeek.MONDAY]!!.isEmpty())
    }

    @Test fun timezone_moves_the_day() {
        val utcLate = ZonedDateTime.of(2026, 10, 30, 23, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
        assertEquals(DayOfWeek.FRIDAY, WeekSchedule.dayOf(utcLate, ZoneId.of("UTC")))
        assertEquals(DayOfWeek.SATURDAY, WeekSchedule.dayOf(utcLate, zone))
    }
}

class NewEpisodeDetectorTest {
    private fun e(id: Int, seen: Int?) = LibraryEntryEntity(id, WatchStatus.WATCHING, 0, null, 0, 0, lastSeenEpisode = seen)

    @Test fun detects_only_newer_episodes() {
        val fresh = listOf(
            AnimeEntity(1, status = "RELEASING", nextEpisode = 8), // latest 7
            AnimeEntity(2, status = "RELEASING", nextEpisode = 5), // latest 4
            AnimeEntity(3, status = "FINISHED", episodes = 12),
            AnimeEntity(4, status = "RELEASING", nextEpisode = 3),
        )
        val news = NewEpisodeDetector.detect(listOf(e(1, 6), e(2, 4), e(3, 11), e(4, null)), fresh)
        assertEquals(listOf(1 to 7, 3 to 12), news.map { it.anime.id to it.episode })
    }

    @Test fun first_sync_is_silent() = assertTrue(NewEpisodeDetector.detect(listOf(e(1, null)), listOf(AnimeEntity(1, nextEpisode = 4))).isEmpty())
}

class TextCleanTest {
    @Test fun strips_html_markdown_and_spoilers() {
        val raw = "Jinwoo is <i>weak</i>.<br><br>__Class:__ Mage ~!secret!~ see [Park](https://anilist.co/x) &amp; more"
        assertEquals("Jinwoo is weak.\n\nClass: Mage  see Park & more", TextClean.clean(raw))
    }

    @Test fun real_character_bio_is_clean() {
        val bio = Fixtures.character.description.orEmpty()
        assertFalse(bio.contains("~!"))
        assertFalse(bio.contains("<br"))
        assertFalse(bio.contains("](https"))
    }
}
