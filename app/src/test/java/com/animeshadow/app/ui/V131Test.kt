package com.animeshadow.app.ui

import com.animeshadow.app.data.auth.GoogleAccount
import com.animeshadow.app.data.auth.GoogleAccountRepository
import com.animeshadow.app.data.auth.GoogleSignInResult
import com.animeshadow.app.data.settings.AppMode
import com.animeshadow.app.data.settings.Profile
import com.animeshadow.app.manga.data.*
import com.animeshadow.app.manga.reader.*
import com.animeshadow.app.manga.ui.*
import com.animeshadow.app.testutil.MangaFixtures
import com.animeshadow.app.ui.auth.LoginContent
import com.animeshadow.app.ui.auth.LoginMessage
import com.animeshadow.app.ui.auth.LoginViewModel
import com.animeshadow.app.ui.profile.EditProfileContent
import com.animeshadow.app.ui.profile.ProfileContent
import com.animeshadow.app.ui.profile.ProfileUi
import com.animeshadow.app.ui.details.DetailsContent
import com.animeshadow.app.ui.details.DetailsActions
import com.animeshadow.app.ui.details.DetailsUi
import androidx.compose.ui.test.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.animeshadow.app.R
import com.animeshadow.app.data.Refresh
import com.animeshadow.app.data.auth.AniListAccount
import com.animeshadow.app.data.local.LibraryEntryEntity
import com.animeshadow.app.data.local.WatchStatus
import com.animeshadow.app.data.settings.Settings
import com.animeshadow.app.domain.BrowseQuery
import com.animeshadow.app.testutil.Fixtures
import com.animeshadow.app.testutil.TestImages
import com.animeshadow.app.ui.ads.LocalAdPreview
import com.animeshadow.app.ui.browse.BrowseContent
import com.animeshadow.app.ui.browse.BrowseUi
import com.animeshadow.app.ui.collections.PopularCharactersContent
import com.animeshadow.app.ui.common.LocalAnimatedLoader
import com.animeshadow.app.ui.feed.FeedContent
import com.animeshadow.app.ui.feed.FeedUi
import com.animeshadow.app.ui.feed.SeasonPick
import com.animeshadow.app.ui.feed.SeasonsContent
import com.animeshadow.app.ui.library.MyListContent
import com.animeshadow.app.ui.library.MyListUi
import com.animeshadow.app.ui.settings.SettingsActions
import com.animeshadow.app.ui.settings.SettingsContent
import com.animeshadow.app.ui.settings.openUrl
import com.animeshadow.app.ui.theme.AnimeShadowTheme
import com.animeshadow.app.ui.theme.Palette
import com.github.takahirom.roborazzi.captureRoboImage
import com.animeshadow.app.ui.common.AppTopBar
import com.animeshadow.app.ui.common.LoadingBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.room.Room

/**
 * v1.3.1 checks: manga words (feminine watching verbs, no reading words), manga profile numbers, white icons,
 * the seamless clip (aspect), and a loader that can never be an empty box.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ar-w360dp-h800dp-xxhdpi")
class V131Test {
    @get:Rule val compose = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    @Before fun images() = TestImages.install(ctx)

    private fun set(top: Int = 24, content: @Composable () -> Unit) {
        val phone = ctx.createConfigurationContext(Configuration(ctx.resources.configuration).apply { fontWeightAdjustment = 300; fontScale = 1.075f })
        compose.setContent {
            CompositionLocalProvider(
                LocalFontFamilyResolver provides createFontFamilyResolver(phone),
                LocalDensity provides Density(LocalDensity.current.density, 1.075f),
                LocalAdPreview provides true,
                LocalAnimatedLoader provides false,
            ) {
                AnimeShadowTheme { Surface(color = Palette.Black) { Box(Modifier.fillMaxSize().padding(top = top.dp)) { content() } } }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
    }

    private fun shot(name: String) = compose.onRoot().captureRoboImage("build/outputs/roborazzi/v131_$name.png")
    private val me = Profile(name = "Madyan", handle = "madyan")

    // ------------------------------------------------------------------ manga words (Madyan 4:50–4:52)

    @Test fun manga_status_labels_are_feminine_watching_words() {
        assertEquals("أشاهدها", ctx.getString(R.string.read_reading))
        assertEquals("شاهدتها", ctx.getString(R.string.read_completed))
        assertEquals("أخطط لمشاهدتها", ctx.getString(R.string.read_plan))
        assertEquals("توقفت عن مشاهدتها", ctx.getString(R.string.read_dropped))
        assertEquals("آخر المشاهدات", ctx.getString(R.string.manga_history))
        // Anime mode is unchanged.
        assertEquals("اشاهدها حاليا", ctx.getString(R.string.ws_WATCHING))
        assertEquals("تم مشاهدتها", ctx.getString(R.string.ws_COMPLETED))
    }

    /** Every string the manga side shows (used under manga/ or named manga_/read_/reader_) has no قرأ-family word. */
    @Test fun no_reading_word_in_any_manga_mode_string() {
        val reading = Regex("قرأ|قراء|أقرأ|اقرأ|إقرأ|يقرأ|تقرأ|نقرأ|قارئ|مقروء|لقراء")
        val src = File("src/main/java/com/animeshadow/app/manga")
        assertTrue("manga sources not found from " + File(".").absolutePath, src.isDirectory)
        val used = src.walkTopDown().filter { it.extension == "kt" }
            .flatMap { f -> Regex("R\\.string\\.([A-Za-z0-9_]+)").findAll(f.readText()).map { it.groupValues[1] } }.toSet()
        val named = R.string::class.java.fields.map { it.name }.filter { it.startsWith("manga_") || it.startsWith("read_") || it.startsWith("reader_") || it == "cd_mark_read" }
        val names = (used + named + listOf("mode_manga")).toSortedSet()
        assertTrue(names.size > 60)
        val bad = names.mapNotNull { n ->
            val id = ctx.resources.getIdentifier(n, "string", ctx.packageName)
            if (id == 0) null else ctx.getString(id).let { t -> if (reading.containsMatchIn(t)) "$n = $t" else null }
        }
        assertTrue("reading words left in manga mode: $bad", bad.isEmpty())
    }

    // ------------------------------------------------------------------ manga profile = manga numbers

    @Test fun manga_stats_count_manga_only() {
        val ms = MangaFixtures.list
        val lib = listOf(
            LibraryRow(MangaLibraryEntity(ms[0].id, ReadStatus.READING, userScore = 8), ms[0]),
            LibraryRow(MangaLibraryEntity(ms[1].id, ReadStatus.READING), ms[1]),
            LibraryRow(MangaLibraryEntity(ms[2].id, ReadStatus.COMPLETED, favorite = true, userScore = 6), ms[2]),
            LibraryRow(MangaLibraryEntity(ms[3].id, ReadStatus.DROPPED), ms[3]),
        )
        val fav = listOf(lib[2], LibraryRow(MangaLibraryEntity(ms[4].id, null, favorite = true), ms[4]))
        val s = MangaStatsCalculator.compute(lib, fav, chaptersWatched = 37)
        assertEquals(4, s.total)
        assertEquals(2, s.count(ReadStatus.READING))
        assertEquals(1, s.count(ReadStatus.COMPLETED))
        assertEquals(0, s.count(ReadStatus.PLAN_TO_READ))
        assertEquals(2, s.favorites)
        assertEquals(37, s.chaptersWatched)
        assertEquals(7.0, s.meanUserScore!!, 0.001)
    }

    @Test fun finished_chapters_are_counted_from_manga_history() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ctx, MangaDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = db.manga()
            dao.upsertHistory(MangaHistoryEntity("a:1", 1, "1", 10, 10, 1L, finished = true))
            dao.upsertHistory(MangaHistoryEntity("a:2", 1, "2", 3, 10, 2L, finished = false))
            dao.upsertHistory(MangaHistoryEntity("b:1", 2, "1", 9, 9, 3L, finished = true))
            assertEquals(2, dao.observeFinishedChapterCount().first())
        } finally { db.close() }
    }

    private fun mangaProfileUi(): ProfileUi {
        val ms = MangaFixtures.list
        val lib = ms.take(9).mapIndexed { i, m -> LibraryRow(MangaLibraryEntity(m.id, ReadStatus.entries[i % 5], favorite = i % 3 == 0, userScore = 6 + i % 4), m) }
        val stats = MangaStatsCalculator.compute(lib, lib.filter { it.entry.favorite }, 214)
        return ProfileUi(me, com.animeshadow.app.domain.StatsCalculator.compute(Fixtures.library, 24), 6, Fixtures.detail, stats)
    }

    @Test @Config(qualifiers = "+h1500dp") fun profile_in_manga_mode_shows_manga_numbers() {
        set { ProfileContent(mangaProfileUi(), {}, {}, manga = true) }
        compose.onNodeWithTag("manga_stats_chapters").assertExists()
        compose.onNodeWithText(ctx.getString(R.string.manga_stats_chapters, 214)).assertExists()
        compose.onNodeWithText(ctx.getString(R.string.read_reading), substring = true).assertExists()
        // none of the anime numbers
        compose.onNodeWithText(ctx.getString(R.string.stats_watch_time)).assertDoesNotExist()
        compose.onAllNodesWithText(ctx.getString(R.string.ws_WATCHING), substring = true).assertCountEquals(0)
        shot("profile_manga")
    }

    @Test @Config(qualifiers = "+h1500dp") fun profile_in_anime_mode_is_unchanged() {
        set { ProfileContent(mangaProfileUi(), {}, {}) }
        compose.onNodeWithText(ctx.getString(R.string.stats_watch_time)).assertExists()
        compose.onNodeWithTag("manga_stats_chapters").assertDoesNotExist()
    }

    // ------------------------------------------------------------------ icons white, bell outline

    @Test fun icons_are_pure_white() {
        assertEquals(androidx.compose.ui.graphics.Color.White, Palette.RefIcon)
        set(0) { DrawerContent(Routes.M_HOME, me, {}, unread = 2, entries = MangaDrawerEntries) }
        compose.onNodeWithTag("drawer_bell_icon", useUnmergedTree = true).assertExists()
    }

    // ------------------------------------------------------------------ clip + loader

    @Test fun clip_is_the_new_seamless_aspect_and_loader_always_draws_its_still() {
        assertEquals(1056f / 720f, com.animeshadow.app.ui.common.SHADOW_CLIP_ASPECT, 0.0001f)
        set { LoadingBox() }
        compose.onNodeWithTag("shadow_loop").assertExists()
        compose.onNodeWithTag("shadow_still", useUnmergedTree = true).assertExists()
        val b = compose.onNodeWithTag("shadow_loop").fetchSemanticsNode().boundsInRoot
        assertEquals(1056f / 720f, b.width / b.height, 0.02f)
    }

    @Test fun manga_loader_uses_the_same_clip_as_the_splash() {
        // The loader frame and the splash poster are cut from the same (new) clip: same first frame, scaled.
        val still = android.graphics.BitmapFactory.decodeResource(ctx.resources, R.drawable.shadow_still)
        val poster = android.graphics.BitmapFactory.decodeResource(ctx.resources, R.drawable.splash_poster)
        assertEquals(528, still.width); assertEquals(360, still.height)
        assertEquals(1056, poster.width); assertEquals(720, poster.height)
    }

    // ------------------------------------------------------------------ manga details = anime details (Madyan 5:04–5:07)

    private val blueLock get() = MangaFixtures.manhwa.copy(
        titleEnglish = "Blue Lock", titleRomaji = "Blue Lock", titleNative = "ブルーロック",
        descriptionAr = "كان يويتشي إيساغي على بعد لحظات من تسجيل هدف يقود فريقه إلى البطولة الوطنية، لكنه مرّر الكرة لزميله. " +
            "بعد الخسارة تصله دعوة إلى «بلو لوك»: سجن تدريبي يجمع ثلاثمئة مهاجم ليصنع منهم أنانياً واحداً هو أفضل مهاجم في العالم. " +
            "هناك يتعلم أن الهدف لا يأتي إلا لمن يريده أكثر من أي أحد.",
        idMal = 114745, malScore = 8.31, malScoredBy = 61234, authors = "Muneyuki Kaneshiro|Yuusuke Nomura",
        relationsJson = MangaRelation.encode(
            listOf(
                MangaRelation(MangaFixtures.list[3].id, "SEQUEL", "MANGA", "MANGA", "Blue Lock: Episode Nagi", MangaFixtures.list[3].poster, null, 81),
                MangaRelation(MangaFixtures.list[4].id, "PREQUEL", "MANGA", "ONE_SHOT", "Blue Lock 0", MangaFixtures.list[4].poster, null, 77),
                MangaRelation(MangaFixtures.list[5].id, "SIDE_STORY", "MANGA", "MANGA", "Blue Lock: Wild Card", MangaFixtures.list[5].poster, null, 75),
                MangaRelation(99, "ADAPTATION", "ANIME", "TV", "BLUE LOCK", null, null, 80),
            ),
        ),
    )

    @Test @Config(qualifiers = "+h1250dp") fun manga_details_look_like_the_anime_page() {
        val e = MangaLibraryEntity(blueLock.id, ReadStatus.READING, userScore = 8)
        set { MangaTitleContent(MangaTitleUi(manga = blueLock, entry = e), MangaTitleActions()) }
        compose.onNodeWithTag("m_rating_row").assertExists()
        compose.onNodeWithTag("mal_cell", useUnmergedTree = true).assertExists()
        compose.onNodeWithText(ctx.getString(R.string.your_score), useUnmergedTree = true).assertExists()
        compose.onNodeWithText(ctx.getString(R.string.my_list), useUnmergedTree = true).assertExists()
        compose.onNodeWithText("أشاهدها", useUnmergedTree = true).assertExists()
        compose.onNodeWithText(ctx.getString(R.string.manga_add_rating)).assertDoesNotExist()
        compose.onNodeWithTag("second_title", useUnmergedTree = true).assertDoesNotExist() // "Blue Lock" twice → once
        compose.onNodeWithTag("synopsis").assertExists()
        compose.onNodeWithText(blueLock.descriptionAr!!, useUnmergedTree = true).assertExists()
        compose.onAllNodesWithTag("author_chip", useUnmergedTree = true).assertCountEquals(2)
        shot("manga_details_top")
        compose.onNodeWithTag("manga_details").performScrollToNode(hasTestTag("m_related"))
        compose.onNodeWithTag("m_rel_PREQUEL", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("m_rel_SEQUEL", useUnmergedTree = true).assertExists()
        compose.onAllNodesWithText("BLUE LOCK", useUnmergedTree = true).assertCountEquals(0) // the anime adaptation is not a manga page
        shot("manga_details_scrolled")
    }

    @Test fun manga_details_hide_sections_without_data() {
        val bare = MangaFixtures.list[1].copy(relationsJson = null, authors = null, malScore = null, genres = "")
        set { MangaTitleContent(MangaTitleUi(manga = bare), MangaTitleActions()) }
        compose.onNodeWithTag("mal_cell", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("m_related").assertDoesNotExist()
        compose.onNodeWithTag("author_chip", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun manga_rating_uses_the_anime_rating_dialog() {
        var saved: Int? = -1
        set { MangaTitleContent(MangaTitleUi(manga = blueLock), MangaTitleActions(onUserScore = { saved = it })) }
        compose.onNodeWithTag("m_rate").performClick()
        compose.onNodeWithTag("rating_dialog").assertExists()
        compose.onNodeWithTag("slider_story").assertExists()
        compose.onNodeWithTag("slider_art").assertExists()
        compose.onNodeWithTag("slider_music").assertDoesNotExist()
        compose.onNodeWithTag("slider_story").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(9f) }
        compose.onNodeWithTag("slider_characters").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(8f) }
        compose.onNodeWithTag("slider_art").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(7f) }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/v131_manga_rating_dialog.png")
        compose.onNodeWithTag("rating_ok").performClick()
        assertEquals(8, saved)
    }

    @Test fun anime_header_english_on_top_romaji_below() {
        val aot = Fixtures.detail.copy(titleEnglish = "Attack on Titan", titleRomaji = "Shingeki no Kyojin", titleNative = "進撃の巨人")
        set { DetailsContent(DetailsUi(anime = aot), DetailsActions(), 0) }
        compose.onNodeWithTag("title", useUnmergedTree = true).assertTextEquals("Attack on Titan")
        compose.onNodeWithTag("second_title", useUnmergedTree = true).assertTextEquals("Shingeki no Kyojin")
        shot("anime_header_two_titles")
    }

    @Test fun anime_header_same_titles_shown_once() {
        val op = Fixtures.detail.copy(titleEnglish = "ONE PIECE", titleRomaji = "ONE PIECE")
        set { DetailsContent(DetailsUi(anime = op), DetailsActions(), 0) }
        compose.onNodeWithTag("second_title", useUnmergedTree = true).assertDoesNotExist()
    }
}
