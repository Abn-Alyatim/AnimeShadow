package com.animeshadow.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.animeshadow.app.data.toCardEntity
import com.animeshadow.app.data.local.FavoriteCharacterEntity
import com.animeshadow.app.data.local.HistoryRow
import com.animeshadow.app.data.local.LibraryEntryEntity
import com.animeshadow.app.data.local.RecRow
import com.animeshadow.app.data.local.RelatedRow
import com.animeshadow.app.data.local.ScheduleRow
import com.animeshadow.app.data.local.WatchStatus
import com.animeshadow.app.data.settings.Profile
import com.animeshadow.app.data.settings.Settings
import com.animeshadow.app.domain.BrowseQuery
import com.animeshadow.app.domain.RecommendationEngine
import com.animeshadow.app.domain.StatsCalculator
import com.animeshadow.app.domain.WeekSchedule
import com.animeshadow.app.ui.browse.BrowseContent
import com.animeshadow.app.ui.browse.BrowseUi
import com.animeshadow.app.ui.browse.FilterSheet
import com.animeshadow.app.ui.collections.FavoriteCharactersContent
import com.animeshadow.app.ui.collections.HistoryContent
import com.animeshadow.app.ui.collections.PopularCharactersContent
import com.animeshadow.app.ui.details.DetailsActions
import com.animeshadow.app.ui.details.DetailsContent
import com.animeshadow.app.ui.details.DetailsUi
import com.animeshadow.app.ui.discover.RecommendationsContent
import com.animeshadow.app.ui.discover.ScheduleContent
import com.animeshadow.app.ui.discover.ScheduleUi
import com.animeshadow.app.ui.feed.FeedContent
import com.animeshadow.app.ui.feed.FeedUi
import com.animeshadow.app.ui.feed.SeasonPick
import com.animeshadow.app.ui.feed.SeasonsContent
import com.animeshadow.app.ui.library.MyListContent
import com.animeshadow.app.ui.library.MyListUi
import com.animeshadow.app.ui.person.CharacterContent
import com.animeshadow.app.ui.person.CharacterUi
import com.animeshadow.app.ui.person.StaffContent
import com.animeshadow.app.ui.person.StaffUi
import com.animeshadow.app.ui.profile.ProfileContent
import com.animeshadow.app.ui.profile.ProfileUi
import com.animeshadow.app.ui.settings.SettingsActions
import com.animeshadow.app.ui.settings.SettingsContent
import com.animeshadow.app.ui.theme.AnimeShadowTheme
import com.animeshadow.app.ui.theme.Palette
import com.animeshadow.app.testutil.Fixtures
import com.animeshadow.app.testutil.TestImages
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

/**
 * Roborazzi screenshots of every main screen with real AniList data. v1.1: Arabic only, composed left-to-right like
 * the reference app (golden rule), so the expected layout direction inside the theme is LTR.
 * Written to app/build/outputs/roborazzi/ and copied to files/animeshadow/screens/ by scripts/collect-screens.sh.
 */
abstract class ScreensBase(private val suffix: String, private val expected: LayoutDirection = LayoutDirection.Ltr) {
    @get:Rule val compose = createComposeRule()
    private var direction: LayoutDirection? = null

    @Before fun images() = TestImages.install(ApplicationProvider.getApplicationContext<Context>())

    protected fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            AnimeShadowTheme {
                direction = LocalLayoutDirection.current
                Surface(color = Palette.Black) { content() }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/${name}_$suffix.png")
        assertEquals(expected, direction)
    }

    private val zone = ZoneId.of("Asia/Aden")
    private val feed = FeedUi(Fixtures.airing, grid = true, loaded = true)

    @Test fun s01_home_grid() = shot("01_home") { HomeShell(feed) }
    @Test fun s02_home_list() = shot("02_home_list") { HomeShell(feed.copy(grid = false)) }
    @Test fun s03_drawer() = shot("03_drawer") {
        Box(Modifier.fillMaxSize()) { DrawerContent(Routes.LATEST, Profile(name = "Madyan", handle = "madyan"), {}) }
    }
    @Test fun s04_browse_search() = shot("04_browse_search") {
        BrowseContent(BrowseUi(BrowseQuery(search = "solo leveling"), Fixtures.search, 1, false), false, {}, {}, {}, {}, {}, {}, {})
    }
    @Test fun s05_filter_sheet() = shot("05_filter") {
        FilterSheet(BrowseQuery(status = "RELEASING", format = "TV", season = "FALL", genre = "Action"), {}, {})
    }
    @Test fun s06_seasons() = shot("06_seasons") {
        SeasonsContent(FeedUi(Fixtures.season, loaded = true), SeasonPick(2024, "SPRING"), 2026, {}, {}, {}, {}, {}, {}, {})
    }
    @Test fun s07_top() = shot("07_top_rated") {
        FeedContent(androidx.compose.ui.res.stringResource(com.animeshadow.app.R.string.nav_top), FeedUi(Fixtures.top, loaded = true), {}, {}, {}, {}, {}, {},
            cornerBadge = { a -> "#" + (Fixtures.top.indexOf(a) + 1) })
    }
    @Test fun s08_my_list() = shot("08_my_list") {
        val lib = Fixtures.library
        MyListContent(
            MyListUi(WatchStatus.COMPLETED, items = lib.filter { it.entry.status == WatchStatus.COMPLETED }, counts = lib.groupingBy { it.entry.status }.eachCount()),
            {}, {}, {}, {}, {},
        )
    }

    private fun detailsUi(): DetailsUi {
        val a = Fixtures.detail
        return DetailsUi(
            anime = a,
            entry = LibraryEntryEntity(a.id, WatchStatus.WATCHING, 8, 9, 0, 0),
            watched = (1..8).toSet(),
            favorite = true,
            characters = Fixtures.characters,
            staff = Fixtures.staff,
            relations = Fixtures.relations.map { RelatedRow(it.first, it.second) },
            recommendations = Fixtures.recommendations.map { RecRow(it, 100) },
            similar = RecommendationEngine.similar(a, Fixtures.airing + Fixtures.top),
        )
    }

    @Test @Config(qualifiers = "+h1700dp") fun s09_details() = shot("09_details") { DetailsContent(detailsUi(), DetailsActions(), 0) }
    @Test fun s10_details_episodes() = shot("10_details_episodes") { DetailsContent(detailsUi(), DetailsActions(), 1) }
    @Test fun s11_details_stats() = shot("11_details_stats") { DetailsContent(detailsUi(), DetailsActions(), 2) }
    @Test fun s12_details_characters() = shot("12_details_characters") { DetailsContent(detailsUi(), DetailsActions(), 3) }
    @Test fun s13_character() = shot("13_character") { CharacterContent(CharacterUi(Fixtures.character, favorite = true), {}, {}, {}, {}, {}) }
    @Test fun s14_voice_actor() = shot("14_voice_actor") { StaffContent(StaffUi(Fixtures.staffMember), {}, {}, {}, {}) }
    @Test fun s15_favorite_characters() = shot("15_favorite_characters") {
        FavoriteCharactersContent(Fixtures.characters.take(9).map { FavoriteCharacterEntity(it.characterId, it.name, it.image, it.animeId, "Solo Leveling", 0) }, {}, {})
    }
    @Test fun s16_popular_characters() = shot("16_popular_characters") { PopularCharactersContent(Fixtures.popularCharacters, null, {}, {}, {}) }
    @Test fun s17_history() = shot("17_history") {
        HistoryContent(Fixtures.library.filter { it.entry.progress > 0 }.take(8).mapIndexed { i, it -> HistoryRow(it.anime!!, it.entry.progress, Fixtures.NOW - i * 3_600_000L) }, {}, {})
    }
    @Test fun s18_recommendations() = shot("18_recommendations") {
        val candidates = (Fixtures.airing + Fixtures.top + Fixtures.season + Fixtures.recommendations).distinctBy { it.id }
        RecommendationsContent(RecommendationEngine.recommend(Fixtures.library, candidates, emptyList()), {}, {})
    }
    @Test fun s19_schedule() = shot("19_schedule") {
        val rows = Fixtures.schedule.filter { it.media != null && it.media.isAdult != true }.map {
            ScheduleRow(Fixtures.airing.firstOrNull { a -> a.id == it.media!!.id } ?: it.media!!.toCardEntity(Fixtures.NOW), it.episode, it.airingAt * 1000)
        }
        val today = WeekSchedule.dayOf(rows.first().airAt, zone)
        ScheduleContent(ScheduleUi(WeekSchedule.group(rows, zone), com.animeshadow.app.data.Refresh.Updated, today), {}, {}, {})
    }
    @Test @Config(qualifiers = "+h1300dp") fun s20_stats_profile() = shot("20_stats_profile") {
        val lib = Fixtures.library
        ProfileContent(ProfileUi(Profile("Madyan", "madyan", ""), StatsCalculator.compute(lib, 24), 6, Fixtures.detail), {}, {})
    }
    @Test @Config(qualifiers = "+h1400dp") fun s21_settings() = shot("21_settings") { SettingsContent(Settings(notifications = true), {}, SettingsActions()) }

    @Composable
    private fun HomeShell(ui: FeedUi) {
        FeedContent(androidx.compose.ui.res.stringResource(com.animeshadow.app.R.string.nav_latest), ui, {}, {}, {}, {}, {}, {})
    }

}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ar-w393dp-h852dp-xhdpi")
class ArabicScreensTest : ScreensBase("ar")
