package com.animeshadow.app.ui

import android.content.Context
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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.animeshadow.app.R
import com.animeshadow.app.data.auth.GoogleAccount
import com.animeshadow.app.data.local.LibraryEntryEntity
import com.animeshadow.app.data.local.WatchStatus
import com.animeshadow.app.manga.data.MangaLibraryEntity
import com.animeshadow.app.manga.data.ReadStatus
import com.animeshadow.app.manga.ui.ChapterRow
import com.animeshadow.app.manga.ui.MangaTitleActions
import com.animeshadow.app.manga.ui.MangaTitleContent
import com.animeshadow.app.manga.ui.MangaTitleUi
import com.animeshadow.app.social.Broadcast
import com.animeshadow.app.social.Conversation
import com.animeshadow.app.social.Conversations
import com.animeshadow.app.social.Friends
import com.animeshadow.app.social.FriendsActions
import com.animeshadow.app.social.FriendsContent
import com.animeshadow.app.social.FriendsUi
import com.animeshadow.app.social.ListItem
import com.animeshadow.app.social.ListsContent
import com.animeshadow.app.social.ListsUi
import com.animeshadow.app.social.Me
import com.animeshadow.app.social.Msg
import com.animeshadow.app.social.Pro
import com.animeshadow.app.social.PublicUser
import com.animeshadow.app.social.SharedList
import com.animeshadow.app.social.ThreadContent
import com.animeshadow.app.social.ThreadUi
import com.animeshadow.app.testutil.Fixtures
import com.animeshadow.app.testutil.MangaFixtures
import com.animeshadow.app.testutil.TestImages
import com.animeshadow.app.ui.ads.LocalAdPreview
import com.animeshadow.app.ui.auth.LoginContent
import com.animeshadow.app.ui.common.LocalAnimatedLoader
import com.animeshadow.app.ui.details.DetailsActions
import com.animeshadow.app.ui.details.DetailsContent
import com.animeshadow.app.ui.details.DetailsUi
import com.animeshadow.app.ui.status.BroadcastCard
import com.animeshadow.app.ui.status.MaintenanceScreen
import com.animeshadow.app.ui.theme.AnimeShadowTheme
import com.animeshadow.app.ui.theme.Palette
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v1.5.0 screens at Madyan's phone settings (360 dp, font 1.075, Bold Text), saved for review. */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ar-w360dp-h800dp-xxhdpi")
class V150ShotsTest {
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
        compose.mainClock.advanceTimeBy(800)
        compose.waitForIdle()
    }

    private fun shot(name: String) = compose.onRoot().captureRoboImage("build/outputs/roborazzi/v150_$name.png")
    private fun screen(name: String) { compose.mainClock.advanceTimeBy(600); compose.waitForIdle(); captureScreenRoboImage("build/outputs/roborazzi/v150_$name.png") }

    private val me = Me(id = "u1", email = "madyan@gmail.com", name = "Madyan", username = "madyan", code = "AS-7K2Q9", pro = Pro(true, "سنوي", "2027-10-30T00:00:00Z"))
    private val sara = PublicUser("u2", "sara_otaku", "Sara", pro = false)
    private val ali = PublicUser("u3", "ali.ye", "Ali", pro = true)

    @Test fun maintenance() {
        var retried = 0
        set(top = 0) { MaintenanceScreen("نحدّث الخادم لإضافة مزايا جديدة، نعود خلال نصف ساعة.", "الجمعة 18:30", checking = false, onRetry = { retried++ }) }
        compose.onNodeWithTag("maintenance_message").assertIsDisplayed()
        shot("maintenance")
        compose.onNodeWithTag("maintenance_retry").performClick()
        assertEquals(1, retried)
    }

    @Test fun broadcast_card() {
        var ok = false
        set { BroadcastCard(Broadcast("b1", "أنمي شادو 1.5.0", "أضفنا الدردشة مع الأصدقاء ومشاركة القوائم وقائمة فصول المانجا. شكراً لكم ❤️"), onDismiss = { ok = true }) }
        compose.onNodeWithTag("broadcast_body").assertIsDisplayed()
        screen("broadcast")
        compose.onNodeWithTag("broadcast_ok").performClick()
        assertTrue(ok)
    }

    @Test fun login_error_10_explains_what_is_missing() {
        set { LoginContent({}, {}, message = ctx.getString(R.string.login_error_10)) }
        compose.onNodeWithTag("login_message").assertIsDisplayed()
        shot("login_error_10")
    }

    @Test fun friends_signed_out() {
        set { FriendsContent(FriendsUi(), FriendsActions(), linked = false) }
        shot("friends_signed_out")
    }

    @Test fun friends_and_conversations() {
        val convs = Conversations(listOf(
            Conversation(sara, Msg(5, "u2", "u1", text = "شفت الحلقة الجديدة من Frieren؟ 😍", at = "2026-10-30T10:00:00Z"), unread = 2),
            Conversation(ali, Msg(4, "u1", "u3", kind = "list", list = SharedList("شاهدته", listOf(ListItem(1, "Naruto"))), at = "2026-10-30T09:00:00Z")),
        ), unreadTotal = 2)
        val friends = Friends(friends = listOf(sara, ali), incoming = listOf(PublicUser("u4", "hamza", "Hamza")))
        set { FriendsContent(FriendsUi(account = GoogleAccount("madyan@gmail.com", "Madyan", pro = true), me = me, friends = friends, conversations = convs), FriendsActions(), linked = true) }
        compose.onAllNodesWithTag("pro_badge", useUnmergedTree = true).assertCountEquals(2) // me + Ali
        shot("friends")
    }

    @Test fun chat_thread_with_a_shared_list() {
        val items = listOf(
            Msg(1, "u2", "u1", text = "السلام عليكم، أرسل لي قائمتك 👀", at = "2026-10-30T09:58:00Z"),
            Msg(2, "u1", "u2", text = "تفضلي", at = "2026-10-30T09:59:00Z"),
            Msg(3, "u1", "u2", kind = "list", list = SharedList("شاهدته", listOf(ListItem(21, "One Piece", s = "COMPLETED", sc = 9.0), ListItem(16498, "Attack on Titan", s = "COMPLETED", sc = 10.0))), at = "2026-10-30T10:00:00Z"),
            Msg(4, "u2", "u1", kind = "list", list = SharedList("سأشاهده", listOf(ListItem(154587, "Frieren"))), at = "2026-10-30T10:01:00Z"),
        )
        var imported: Msg? = null
        set { ThreadContent(ThreadUi(user = sara, me = "u1", items = items, friends = true), {}, {}, { imported = it }, {}, {}, {}) }
        shot("chat_thread")
    }

    @Test fun lists_export_and_share() {
        val counts = mapOf("anime:COMPLETED" to 128, "anime:DROPPED" to 9, "anime:NOT_INTERESTED" to 14, "anime:WATCHING" to 6, "anime:PLAN_TO_WATCH" to 40, "manga:READING" to 3)
        set { ListsContent(ListsUi(counts = counts, linked = true), {}, {}, {}, {}, {}) }
        shot("lists")
        compose.onNodeWithTag("lists_screen").performScrollToNode(hasTestTag("export_btn"))
        compose.onNodeWithTag("export_btn").assertExists()
        shot("lists_actions")
    }

    @Test fun airing_episodes_show_the_next_one_as_not_out_yet() {
        val ui = DetailsUi(anime = Fixtures.onePieceTranslated, entry = LibraryEntryEntity(21, WatchStatus.WATCHING, 1179, null, 0, 0), watched = (1..1179).toSet())
        set { DetailsContent(ui, DetailsActions(), initialTab = 1) }
        val next = Fixtures.onePieceTranslated.nextEpisode!!
        compose.onNodeWithTag("ep_upcoming_$next").assertExists()
        shot("airing_episodes")
    }

    @Test fun manga_chapter_tracking_list() {
        val m = MangaFixtures.manhwa
        var toggled: String? = null
        val rows = (1..40).reversed().map { ChapterRow.Tracked(it, it <= 31) }
        set {
            MangaTitleContent(
                MangaTitleUi(manga = m, entry = MangaLibraryEntity(m.id, ReadStatus.READING), providerAvailable = false, chapters = rows),
                MangaTitleActions(onToggleRead = { toggled = it.number }), initialTab = 1,
            )
        }
        compose.onNodeWithTag("chapter_40").assertExists()
        shot("manga_chapters")
        compose.onNodeWithTag("eye_40").performClick()
        assertEquals("40", toggled)
    }
}
