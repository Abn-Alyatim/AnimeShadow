package com.animeshadow.app.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.core.app.ApplicationProvider
import com.animeshadow.app.R
import com.animeshadow.app.data.RatingParts
import com.animeshadow.app.data.local.LibraryEntryEntity
import com.animeshadow.app.data.local.NotificationEntity
import com.animeshadow.app.data.local.WatchStatus
import com.animeshadow.app.data.settings.Settings
import com.animeshadow.app.domain.BrowseQuery
import com.animeshadow.app.testutil.Fixtures
import com.animeshadow.app.testutil.TestImages
import com.animeshadow.app.ui.browse.BrowseContent
import com.animeshadow.app.ui.browse.BrowseUi
import com.animeshadow.app.ui.browse.FilterSheet
import com.animeshadow.app.ui.details.DetailsActions
import com.animeshadow.app.ui.details.DetailsContent
import com.animeshadow.app.ui.details.DetailsUi
import com.animeshadow.app.ui.feed.FeedContent
import com.animeshadow.app.ui.feed.FeedUi
import com.animeshadow.app.ui.notifications.NotificationsContent
import com.animeshadow.app.ui.settings.SettingsActions
import com.animeshadow.app.ui.settings.SettingsContent
import com.animeshadow.app.ui.theme.AnimeShadowTheme
import com.animeshadow.app.ui.theme.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/** Behaviour of every v1.1 change, on the real composables (Arabic, 360x800 dp like the references). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ar-w360dp-h800dp-xhdpi")
class V11InteractionTest {
    @get:Rule val compose = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private fun s(id: Int, vararg args: Any) = ctx.getString(id, *args)

    @Before fun images() = TestImages.install(ctx)

    private fun set(content: @Composable () -> Unit) {
        compose.setContent { AnimeShadowTheme { Surface(color = Palette.Black) { Box(Modifier.fillMaxSize()) { content() } } } }
        compose.waitForIdle()
    }

    private fun bounds(n: SemanticsNodeInteraction): Rect = n.fetchSemanticsNode().boundsInRoot

    private fun SemanticsNodeInteraction.assertTextContainsIsolated(part: String) {
        val text = fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
        assertTrue("expected '$part' in '$text'", text.contains(part))
    }

    private fun ui(entry: LibraryEntryEntity? = LibraryEntryEntity(21, WatchStatus.WATCHING, 3, 9, 0, 0), watched: Set<Int> = (1..3).toSet()) =
        DetailsUi(anime = Fixtures.onePieceTranslated, entry = entry, watched = watched)

    // ---------- item 1: layout of ref 11 (positions, not just presence) ----------
    @Test fun details_poster_left_title_beside_rating_row_order() {
        set { DetailsContent(ui(), DetailsActions(), 0) }
        val poster = bounds(compose.onNodeWithTag("poster"))
        val title = bounds(compose.onNodeWithTag("title"))
        assertTrue("poster on the LEFT", poster.left < 60f && poster.right < 360f)
        assertTrue("title beside the poster, to its right", title.left >= poster.right)
        compose.onNodeWithTag("title").assertIsDisplayed().assertTextContainsIsolated("One Piece")
        // type | age line uses the MAL rating: «مسلسل | 13+»
        compose.onNodeWithTag("type_line").assertTextContainsIsolated("13+")
        // rating row left -> right: average, MAL, yours, my list
        val mal = bounds(compose.onNodeWithTag("mal_cell"))
        val mine = bounds(compose.onNodeWithTag("your_rating"))
        val list = bounds(compose.onNodeWithTag("my_list"))
        assertTrue(mal.left < mine.left && mine.left < list.left)
        compose.onNodeWithText("1,564,769").assertIsDisplayed() // MAL scorers
        compose.onNodeWithText(s(R.string.ws_WATCHING)).assertIsDisplayed()
        // v1.1.1: the grid ends with «العنوان الانجليزي» like ref 11
        compose.onNodeWithText(s(R.string.info_english)).assertExists()
    }

    @Test fun details_top_bar_icons_per_tab() {
        set { DetailsContent(ui(), DetailsActions(), 0) }
        compose.onNodeWithContentDescription(s(R.string.cd_comments)).assertIsDisplayed()
        compose.onNodeWithContentDescription(s(R.string.cd_more)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.tab_episodes)).performClick()
        compose.onNodeWithTag("eye_all").assertIsDisplayed()
        compose.onNodeWithTag("ep_sort").assertIsDisplayed()
        compose.onNodeWithContentDescription(s(R.string.cd_filter)).assertIsDisplayed()
        assertTrue(bounds(compose.onNodeWithTag("eye_all")).left > 360f) // small icon on the RIGHT, not a wide button
        assertTrue(bounds(compose.onNodeWithTag("eye_all")).width < 120f)
    }

    // ---------- item 2: Arabic synopsis (no label, like ref 11), English fallback, «جارٍ الترجمة…» only while translating ----------
    @Test fun synopsis_arabic_without_label() {
        set { DetailsContent(ui(), DetailsActions(), 0) }
        compose.onNodeWithTag("synopsis").assertTextContainsIsolated("ملك القراصنة")
        compose.onAllNodesWithTag("synopsis_note").assertCountEquals(0)
    }

    @Test fun synopsis_falls_back_to_english() {
        set { DetailsContent(ui().copy(anime = Fixtures.onePiece), DetailsActions(), 0) }
        compose.onNodeWithTag("synopsis").assertTextContainsIsolated("Gold Roger")
        compose.onAllNodesWithTag("synopsis_note").assertCountEquals(0)
    }

    @Test fun synopsis_shows_translating_note_while_it_runs() {
        set { DetailsContent(ui().copy(anime = Fixtures.onePiece, translating = true), DetailsActions(), 0) }
        compose.onNodeWithTag("synopsis_note").assertIsDisplayed()
    }

    // ---------- item 3: rating dialog with 4 sliders ----------
    @Test fun rating_dialog_four_sliders_overall_and_ok() {
        var saved: RatingParts? = null
        set { DetailsContent(ui(entry = null, watched = emptySet()), DetailsActions(onRating = { saved = it }), 0) }
        compose.onNodeWithTag("your_rating").performClick()
        compose.onNodeWithTag("rating_dialog").assertExists()
        listOf(R.string.rate_story, R.string.rate_characters, R.string.rate_animation, R.string.rate_music).forEach { compose.onNodeWithText(s(it)).assertExists() }
        compose.onNodeWithTag("rating_overall").assertTextContainsIsolated("0")
        compose.onNodeWithTag("slider_story").performSemanticsAction(SemanticsActions.SetProgress) { it(9f) }
        compose.onNodeWithTag("slider_characters").performSemanticsAction(SemanticsActions.SetProgress) { it(8f) }
        compose.onNodeWithTag("slider_animation").performSemanticsAction(SemanticsActions.SetProgress) { it(7f) }
        compose.onNodeWithTag("slider_music").performSemanticsAction(SemanticsActions.SetProgress) { it(6f) }
        compose.onNodeWithTag("rating_overall").assertTextContainsIsolated("7.5")
        compose.onNodeWithText(s(R.string.ok_ref)).assertExists()
        compose.onNodeWithText(s(R.string.cancel)).assertExists()
        compose.onNodeWithTag("rating_ok").performClick()
        assertEquals(RatingParts(9, 8, 7, 6), saved)
    }

    // ---------- the status is inline (anchored menu on the row), not a dialog ----------
    @Test fun status_changes_from_the_inline_menu() {
        var picked: WatchStatus? = null
        set { DetailsContent(ui(), DetailsActions(onStatus = { picked = it }), 0) }
        compose.onNodeWithTag("my_list").performClick()
        compose.onNodeWithTag("status_COMPLETED").performClick()
        assertEquals(WatchStatus.COMPLETED, picked)
    }

    // ---------- item 9: long-press copy, poster viewer ----------
    @Test fun long_press_title_copies_with_toast() {
        set { DetailsContent(ui(), DetailsActions(), 0) }
        compose.onNodeWithTag("title").performTouchInput { longClick() }
        compose.waitForIdle()
        val clip = ctx.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString()
        assertEquals("One Piece", clip)
        assertEquals(s(R.string.copied), ShadowToast.getTextOfLatestToast())
    }

    @Test fun long_press_english_title_in_grid_copies() {
        set { DetailsContent(ui(), DetailsActions(), 0) }
        compose.onNodeWithTag("details_list").performScrollToKey("info")
        compose.onNodeWithTag("english_title").performTouchInput { longClick() }
        compose.waitForIdle()
        val clip = ctx.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString()
        assertEquals("One Piece", clip)
        assertEquals(s(R.string.copied), ShadowToast.getTextOfLatestToast())
    }

    @Test fun tap_poster_opens_full_screen_viewer() {
        set { DetailsContent(ui(), DetailsActions(), 0) }
        compose.onNodeWithTag("poster").performClick()
        compose.onNodeWithTag("poster_viewer").assertExists()
        compose.onNodeWithContentDescription(s(R.string.cd_close)).performClick()
        compose.onAllNodesWithTag("poster_viewer").assertCountEquals(0)
    }

    // ---------- item 11: Arabic genre chips open the genre ----------
    @Test fun genre_chips_are_arabic_and_open_the_genre() {
        var genre: String? = null
        set { DetailsContent(ui(), DetailsActions(onGenre = { genre = it }), 0) }
        compose.onNodeWithText(s(R.string.genre_Action)).assertExists()
        compose.onNodeWithTag("genre_Adventure").performClick()
        assertEquals("Adventure", genre)
    }

    // ---------- item 13: episodes list ----------
    @Test fun episodes_newest_first_eye_toggles_comment_says_soon() {
        val toggled = mutableListOf<Pair<Int, Boolean>>()
        val a = Fixtures.onePieceTranslated.copy(episodes = 5, nextEpisode = null, status = "FINISHED")
        set { DetailsContent(DetailsUi(anime = a, watched = setOf(1, 2)), DetailsActions(onEpisode = { e, w -> toggled += e to w }), 1) }
        val first = bounds(compose.onNodeWithTag("ep_5"))
        val last = bounds(compose.onNodeWithTag("ep_1"))
        assertTrue("newest first", first.top < last.top)
        compose.onNodeWithText(s(R.string.episode_label, 5)).assertExists()
        compose.onNodeWithTag("eye_2").performClick() // watched -> unwatch
        compose.onNodeWithTag("eye_5").performClick() // unwatched -> watch
        assertEquals(listOf(2 to false, 5 to true), toggled)
        compose.onNodeWithTag("comments_4").performClick()
        compose.waitUntil(3_000) { compose.onAllNodesWithText(s(R.string.coming_soon)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("ep_sort").performClick()
        assertTrue("oldest first after sort", bounds(compose.onNodeWithTag("ep_1")).top < bounds(compose.onNodeWithTag("ep_5")).top)
    }

    // ---------- item 14: small eye = mark all, snackbar with undo ----------
    @Test fun eye_marks_all_with_undo_snackbar() {
        var undone = false
        var calls = 0
        val a = Fixtures.onePieceTranslated.copy(episodes = 5, nextEpisode = null, status = "FINISHED")
        set {
            var watched by androidx.compose.runtime.remember { mutableStateOf(setOf(1)) }
            DetailsContent(
                DetailsUi(anime = a, watched = watched),
                DetailsActions(
                    onToggleAll = { done -> calls++; val marking = watched.size < 5; watched = if (marking) (1..5).toSet() else emptySet(); done(marking) },
                    onUndoAll = { undone = true },
                ),
                1,
            )
        }
        compose.onNodeWithContentDescription(s(R.string.cd_mark_all)).performClick()
        compose.waitUntil(3_000) { compose.onAllNodesWithText(s(R.string.marked_all)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(s(R.string.cd_unmark_all)).assertExists() // now blue: tap again = unmark all
        compose.onNodeWithText(s(R.string.undo)).performClick()
        compose.waitForIdle()
        assertTrue(undone)
        compose.onNodeWithContentDescription(s(R.string.cd_unmark_all)).performClick()
        compose.waitUntil(3_000) { compose.onAllNodesWithText(s(R.string.unmarked_all)).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(2, calls)
    }

    // ---------- item 4: home top bar sides ----------
    @Test fun home_bar_drawer_and_title_left_search_and_toggle_right() {
        set { FeedContent(stringResource(R.string.nav_latest), FeedUi(Fixtures.airing, loaded = true), {}, {}, {}, {}, {}, {}) }
        val menu = bounds(compose.onNodeWithContentDescription(s(R.string.cd_menu)))
        val title = bounds(compose.onNode(hasText(s(R.string.nav_latest))))
        val search = bounds(compose.onNodeWithContentDescription(s(R.string.cd_search)))
        val toggle = bounds(compose.onNodeWithContentDescription(s(R.string.cd_list)))
        assertTrue(menu.left < 60f && title.left > menu.left && title.left < 360f)
        assertTrue(search.left > 360f && toggle.left > search.left)
    }

    // ---------- item 7: search bar ----------
    @Test fun search_bar_back_left_hint_sort_filter_right_no_box() {
        var typed = ""
        set { BrowseContent(BrowseUi(BrowseQuery(), Fixtures.search, 1, false), false, {}, {}, { typed = it }, {}, {}, {}, {}) }
        compose.onNodeWithText(s(R.string.search_hint)).assertIsDisplayed()
        val back = bounds(compose.onNodeWithContentDescription(s(R.string.cd_back)))
        val sort = bounds(compose.onNodeWithContentDescription(s(R.string.cd_sort)))
        val filter = bounds(compose.onNodeWithContentDescription(s(R.string.cd_filter)))
        assertTrue(back.left < 60f && sort.left > 360f && filter.left > sort.left)
        compose.onAllNodes(hasContentDescription(s(R.string.cd_grid)) or hasContentDescription(s(R.string.cd_list))).assertCountEquals(0)
        compose.onNodeWithTag("search_field").performTextInput("one piece")
        assertEquals("one piece", typed)
    }

    // ---------- item 10: age filter in search ----------
    @Test fun age_filter_in_the_filter_sheet() {
        var applied: BrowseQuery? = null
        // v1.4.0: the age filter only appears when «محتوى +18» is on in Settings
        set { FilterSheet(BrowseQuery(), { applied = it }, {}, adultAllowed = true) }
        compose.onNodeWithText(s(R.string.filter_age)).assertExists()
        compose.onNodeWithTag("age_true").performScrollTo().performClick()
        compose.onNodeWithText(s(R.string.apply)).performScrollTo().performClick()
        assertEquals(true, applied!!.adult)
        assertEquals(1, applied!!.activeFilterCount)
        assertEquals(false, BrowseQuery().adult) // default hides 18+ like v1.0
    }

    // ---------- item 8: notifications screen ----------
    @Test fun notifications_screen_read_unread_open_and_clear() {
        var opened: NotificationEntity? = null
        var cleared = false
        val items = listOf(
            NotificationEntity(1, 21, 1180, "ONE PIECE", null, "new_episode", Fixtures.NOW, read = false),
            NotificationEntity(2, 151807, 12, "Solo Leveling", null, "reminder", Fixtures.NOW - 1000, read = true),
        )
        set { NotificationsContent(items, {}, { opened = it }, {}, { cleared = true }) }
        compose.onNodeWithText(s(R.string.notifications_title)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.notif_item_new, "1180")).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.notif_item_reminder, "12")).assertIsDisplayed()
        compose.onAllNodesWithTag("unread_dot", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithTag("notif_1").performClick()
        assertEquals(21, opened!!.animeId)
        compose.onNodeWithTag("clear_all").performClick()
        assertTrue(cleared)
    }

    @Test fun notifications_empty_state() {
        set { NotificationsContent(emptyList(), {}, {}, {}, {}) }
        compose.onNodeWithText(s(R.string.notifications_empty)).assertExists()
    }

    // ---------- item 5: no language switch ----------
    @Test fun settings_has_no_language_option() {
        set { SettingsContent(Settings(), {}, SettingsActions()) }
        compose.onAllNodesWithText("اللغة").assertCountEquals(0)
        compose.onAllNodesWithText("English").assertCountEquals(0)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-w360dp-h800dp-xhdpi")
class ArabicOnlyOnEnglishDeviceTest {
    @Test fun ui_strings_are_arabic_even_on_an_english_phone() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("اخر التحديثات", ctx.getString(R.string.nav_latest))
        assertEquals("أدخل اسم الانمي..", ctx.getString(R.string.search_hint))
        assertNull(ctx.resources.getIdentifier("lang_en", "string", ctx.packageName).takeIf { it != 0 })
    }
}
