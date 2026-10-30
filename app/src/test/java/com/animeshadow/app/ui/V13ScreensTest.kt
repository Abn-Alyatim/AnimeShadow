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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config


/**
 * v1.3 screens at Madyan's phone settings (360 dp, font scale 1.075, Bold Text): the "after" half of the
 * before/after images for edits 1–9 (same names as the v13_before_* set) and every new manga screen, plus the
 * interaction checks for the new behaviour.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ar-w360dp-h800dp-xxhdpi")
class V13ScreensTest {
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

    private fun shot(name: String) = compose.onRoot().captureRoboImage("build/outputs/roborazzi/v13_$name.png")

    private val me = Profile(name = "Madyan", handle = "madyan")
    private val manhwa get() = MangaFixtures.manhwa
    private val mangaJp get() = MangaFixtures.list.first { it.kind == MangaKind.MANGA }

    // ================================================================ edits 1–9 (after)

    @Test fun after_drawer() {
        set(0) { DrawerContent(Routes.LATEST, me, {}) }
        compose.onNodeWithText("Your Anime. Your Journey.").assertDoesNotExist()
        compose.onNodeWithText("Madyan").assertExists()
        shot("after_drawer")
    }

    @Test fun after_drawer_manga_mode() {
        set(0) { DrawerContent(Routes.M_HOME, me, {}, entries = MangaDrawerEntries, accountName = "Madyan AL-Yatim") }
        compose.onNodeWithText(ctx.getString(R.string.manga_downloaded)).assertExists()
        compose.onNodeWithText(ctx.getString(R.string.manga_queue)).assertExists()
        shot("after_drawer_manga")
    }

    private val profileUi get() = ProfileUi(me, com.animeshadow.app.domain.StatsCalculator.compute(Fixtures.library, 24), 6, Fixtures.detail)

    @Test @Config(qualifiers = "+h1500dp") fun after_profile_signed_out() {
        set { ProfileContent(profileUi, {}, {}) }
        compose.onNodeWithTag("account_login").assertExists()
        // Item 4: counts only, no percentages anywhere on the profile.
        compose.onAllNodes(hasText("%", substring = true)).assertCountEquals(0)
        // Item 5: the avatar itself does nothing; the photo is changed inside Edit Profile.
        compose.onNodeWithTag("profile_avatar").assert(!hasClickAction())
        shot("after_profile")
    }

    @Test @Config(qualifiers = "+h1500dp") fun after_profile_signed_in() {
        var out = 0
        set { ProfileContent(profileUi, {}, {}, account = GoogleAccount("madyan.alyatim@gmail.com", "Madyan AL-Yatim"), onLogout = { out++ }) }
        compose.onNodeWithTag("account_signed_in").assertExists()
        compose.onNodeWithText("madyan.alyatim@gmail.com", substring = true).assertExists()
        compose.onNodeWithTag("google_logout").performClick()
        assertEquals(1, out)
        shot("after_profile_signed_in")
    }

    @Test fun after_edit_profile() {
        var pick = 0; var del = 0
        compose.mainClock.autoAdvance = false
        set { EditProfileContent(me.copy(bio = "", avatarPath = "/x/avatar.jpg"), {}, {}, { pick++ }, { del++ }) }
        compose.onNodeWithTag("avatar_change").performClick()
        compose.onNodeWithTag("avatar_delete").performClick()
        assertEquals(1, pick); assertEquals(1, del)
        compose.mainClock.advanceTimeBy(400)
        shot("after_edit_profile")
    }

    @Test @Config(qualifiers = "+h1400dp") fun after_settings() {
        set { SettingsContent(Settings(notifications = true), {}, SettingsActions(), account = AniListAccount(configured = true)) }
        compose.onNodeWithText(ctx.getString(R.string.settings_account)).assertDoesNotExist()
        compose.onNodeWithTag("developer_row").assertExists()
        shot("after_settings")
    }

    @Test fun after_login() {
        var taps = 0
        set(0) { LoginContent({}, { taps++ }) }
        compose.onNodeWithTag("shadow_icon").assertExists()
        compose.onNodeWithText(ctx.getString(R.string.app_name)).assertExists()
        compose.onNodeWithTag("google_button").performClick()
        assertEquals(1, taps)
        shot("after_login")
    }

    @Test fun after_splash() {
        set(0) { ShadowSplash(visible = true) }
        compose.onNodeWithTag("shadow_icon").assertExists()
        shot("after_splash")
    }

    @Test fun after_details_episodes_tab_is_a_swipe_page() {
        val ui = DetailsUi(anime = Fixtures.onePieceTranslated, entry = com.animeshadow.app.data.local.LibraryEntryEntity(21, com.animeshadow.app.data.local.WatchStatus.WATCHING, 3, 9, 0, 0), watched = setOf(1))
        set { DetailsContent(ui, DetailsActions(), 1) }
        compose.onNodeWithTag("details_pager").assertExists()
        shot("after_details_episodes")
    }

    @Test fun mode_toggle_switches_mode() {
        var mode = AppMode.ANIME
        set { ModeToggle(AppMode.ANIME, { mode = it }) }
        compose.onNodeWithText(ctx.getString(R.string.mode_manga)).performClick()
        assertEquals(AppMode.MANGA, mode)
    }

    @Test fun login_says_not_activated_calmly_and_never_offers_retry() = runBlocking {
        val repo = GoogleAccountRepository(ctx.getSharedPreferences("google_test", 0), "")
        val vm = LoginViewModel(repo)
        vm.source = com.animeshadow.app.data.auth.GoogleSignInSource { _, _ -> GoogleSignInResult.NotConfigured }
        vm.signIn(ctx)
        assertEquals(LoginMessage.NOT_CONFIGURED, withTimeout(5_000) { vm.events.first() })
        vm.source = com.animeshadow.app.data.auth.GoogleSignInSource { _, _ -> GoogleSignInResult.Success(GoogleAccount("a@b.c", "A")) }
        vm.signIn(ctx)
        assertEquals(LoginMessage.DONE, withTimeout(5_000) { vm.events.first() })
        assertEquals("a@b.c", repo.account.first().email)
        repo.signOut()
        assertTrue(!repo.account.first().signedIn)
    }

    // ================================================================ manga mode

    @Test fun manga_home() {
        val items = MangaFixtures.list
        set { MangaHomeContent(MangaHomeUi(feeds = mapOf(MangaFeed.TRENDING to items)), {}, {}, {}, {}, {}, {}, {}, {}, toggle = { ModeToggle(AppMode.MANGA, {}) }) }
        compose.onNodeWithTag("mode_toggle").assertExists()
        compose.onNodeWithTag("kind_manhwa").assertExists()
        compose.onNodeWithTag("manga_home_pager").assertExists()
        shot("manga_home")
    }

    @Test fun manga_home_kind_filter() {
        var kind: MangaKind? = null
        set { MangaHomeContent(MangaHomeUi(kind = MangaKind.MANHWA, feeds = mapOf(MangaFeed.TRENDING to MangaFixtures.list.filter { it.kind == MangaKind.MANHWA })), {}, {}, { kind = it }, {}, {}, {}, {}, {}) }
        compose.onNodeWithTag("kind_manhua").performClick()
        assertEquals(MangaKind.MANHUA, kind)
        shot("manga_home_manhwa")
    }

    private val entry get() = MangaLibraryEntity(manhwa.id, ReadStatus.READING, favorite = true, userScore = 9)

    @Test @Config(qualifiers = "+h1100dp") fun manga_title_details() {
        set { MangaTitleContent(MangaTitleUi(manga = manhwa, entry = entry), MangaTitleActions()) }
        compose.onNodeWithTag("manga_details").assertExists()
        shot("manga_title_details")
    }

    private fun localRows(read: Set<Int> = setOf(1, 2, 3, 4, 5)) = MangaFixtures.downloads(manhwa, (1..14).toList(), read).map { ChapterRow.Local(it, it.read) }.reversed()

    @Test fun manga_title_chapters_downloaded() {
        val rows = localRows()
        var read: String? = null; var deleted: String? = null
        set {
            MangaTitleContent(
                MangaTitleUi(manga = manhwa, entry = entry, downloadedOnly = true, chapters = rows, continueRow = rows.first { it.number == "6" }),
                MangaTitleActions(onRead = { read = it.number }, onDelete = { deleted = it }), initialTab = 1,
            )
        }
        compose.onNodeWithTag("continue_fab").assertExists()
        compose.onNodeWithText(ctx.getString(R.string.manga_continue, "6")).assertExists()
        shot("manga_title_chapters")
        compose.onNodeWithTag("continue_fab").performClick()
        assertEquals("6", read)
        compose.onNodeWithTag("trash_14").performClick()
        assertEquals("demo:${manhwa.id}-14", deleted)
    }

    @Test fun manga_chapters_say_soon_without_a_source_and_swipe_from_details() {
        set { MangaTitleContent(MangaTitleUi(manga = manhwa, entry = entry, providerAvailable = false), MangaTitleActions()) }
        compose.onNodeWithTag("manga_title_pager").performTouchInput { swipeLeft() }
        compose.waitForIdle(); compose.mainClock.advanceTimeBy(800); compose.waitForIdle()
        compose.onNodeWithTag("chapters_soon").assertExists()
        // v1.5.0: no chapter count known → a calm note (no «قريباً», no error)
        compose.onNodeWithText(ctx.getString(R.string.manga_chapters_unknown)).assertExists()
        compose.onAllNodes(hasText("إعادة", substring = true)).assertCountEquals(0)
        shot("manga_title_chapters_soon")
    }

    private fun readerUi(m: MangaEntity, settings: ReaderSettings = ReaderSettings.DEFAULT) = ReaderUi(
        manga = m, chapter = ReaderChapter("demo:${m.id}-12", "12", MangaFixtures.pages, offline = true),
        prevKey = "demo:${m.id}-11", nextKey = "demo:${m.id}-13", settings = settings, loading = false,
    )

    @Test fun reader_clean() {
        set(0) { ReaderContent(readerUi(manhwa), ReaderActions()) }
        compose.onNodeWithTag("reader_vertical").assertExists()
        compose.onNodeWithTag("reader_top").assertDoesNotExist()
        compose.onNodeWithTag("reader_bottom").assertDoesNotExist()
        shot("reader_clean")
    }

    @Test fun reader_center_tap_shows_controls_then_they_hide() {
        set(0) { ReaderContent(readerUi(manhwa), ReaderActions()) }
        compose.onNodeWithTag("reader").performTouchInput { click(center) }
        compose.waitForIdle(); compose.mainClock.advanceTimeBy(400); compose.waitForIdle()
        compose.onNodeWithTag("reader_top").assertExists()
        compose.onNodeWithTag("reader_bottom").assertExists()
        compose.onNodeWithTag("reader_settings_button").assertExists()
        shot("reader_controls")
        compose.mainClock.advanceTimeBy(ReaderSettings.AUTO_HIDE_MS + 800); compose.waitForIdle()
        compose.onNodeWithTag("reader_top").assertDoesNotExist()
    }

    @Test fun reader_manga_rtl_paged_with_controls() {
        set(0) { ReaderContent(readerUi(mangaJp), ReaderActions(), initialControls = true) }
        compose.onNodeWithTag("reader_paged").assertExists()
        shot("reader_manga_rtl_controls")
    }

    @Test fun reader_without_clean_screen_keeps_controls() {
        set(0) { ReaderContent(readerUi(manhwa, ReaderSettings.DEFAULT.copy(cleanScreen = false)), ReaderActions()) }
        compose.mainClock.advanceTimeBy(ReaderSettings.AUTO_HIDE_MS + 800); compose.waitForIdle()
        compose.onNodeWithTag("reader_top").assertExists()
    }

    @Test @Config(qualifiers = "+h1500dp") fun reader_settings_sheet() {
        var reset = 0
        var s = ReaderSettings.DEFAULT
        set(0) { ReaderSettingsPanel(readerUi(manhwa), ReaderActions(onSettings = { t -> s = t(s) }, onReset = { reset++ })) }
        compose.onNodeWithText(ctx.getString(R.string.reader_clean_screen)).assertExists()
        compose.onNodeWithTag("clean_screen").performClick()
        assertTrue(!s.cleanScreen)
        compose.onNodeWithTag("reader_reset").performClick()
        assertEquals(1, reset)
        shot("reader_settings")
    }

    private fun queueRows(): List<DownloadWithManga> {
        val ms = MangaFixtures.list
        val states = listOf(DownloadState.RUNNING to 3, DownloadState.QUEUED to 0, DownloadState.QUEUED to 0, DownloadState.PAUSED to 9, DownloadState.QUEUED to 0, DownloadState.QUEUED to 0)
        return states.mapIndexed { i, (st, done) ->
            val m = ms[i % ms.size]
            DownloadWithManga(DownloadEntity("demo:${m.id}-${344 + i}", m.id, "demo", "${m.id}", "${344 + i}", state = st, totalPages = if (done > 0 || i == 1) 17 else 0, donePages = done, position = i), m)
        }
    }

    @Test fun download_queue() {
        var paused = 0; var top: String? = null
        set { DownloadQueueContent(queueRows(), QueueActions(onPauseAll = { paused++ }, onTop = { top = it })) }
        compose.onNodeWithText("\u20663/17\u2069").assertExists()
        compose.onNodeWithTag("drag_344").assertExists()
        shot("download_queue")
        compose.onNodeWithTag("queue_pause_all").performClick()
        assertEquals(1, paused)
        compose.onNodeWithTag("queue_more_345").performClick()
        compose.onNodeWithText(ctx.getString(R.string.manga_move_top)).performClick()
        assertTrue(top!!.endsWith("-345"))
    }

    @Test fun download_queue_drag_reorders() {
        var order: List<String>? = null
        set { DownloadQueueContent(queueRows(), QueueActions(onReorder = { order = it })) }
        compose.onNodeWithTag("drag_344").performTouchInput { down(center); moveBy(androidx.compose.ui.geometry.Offset(0f, 260f)); up() }
        compose.waitForIdle()
        assertTrue("order=$order", order != null && !order!!.first().endsWith("-344"))
    }

    @Test fun downloaded_grid() {
        val titles = MangaFixtures.list.take(9).mapIndexed { i, m -> DownloadedTitle(m, 3 + i) }
        var opened: Int? = null
        set { DownloadedContent(DownloadedUi(titles), {}, {}, {}, { opened = it }) }
        compose.onNodeWithTag("manga_grid").assertExists()
        shot("downloaded_grid")
        compose.onNodeWithText(titles[0].manga.displayTitle).performClick()
        assertEquals(titles[0].manga.id, opened)
    }

    @Test fun downloaded_list_view() {
        val titles = MangaFixtures.list.take(6).mapIndexed { i, m -> DownloadedTitle(m, 3 + i) }
        set { DownloadedContent(DownloadedUi(titles, grid = false), {}, {}, {}, {}) }
        compose.onNodeWithText(ctx.getString(R.string.manga_downloaded_count, 3)).assertExists()
        shot("downloaded_list")
    }

    @Test fun manga_library_swipe_tabs() {
        val ms = MangaFixtures.list
        set { MangaLibraryContent(MangaLibraryUi(byStatus = mapOf(ReadStatus.READING to ms.take(5), ReadStatus.PLAN_TO_READ to ms.drop(5).take(4))), {}, {}, {}) }
        compose.onNodeWithTag("manga_library_pager").assertExists()
        shot("manga_library")
    }
}
