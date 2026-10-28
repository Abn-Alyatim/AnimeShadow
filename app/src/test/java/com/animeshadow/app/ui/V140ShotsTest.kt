package com.animeshadow.app.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.animeshadow.app.chat.ChatCard
import com.animeshadow.app.chat.ChatContent
import com.animeshadow.app.chat.ChatConversation
import com.animeshadow.app.chat.ChatHomeContent
import com.animeshadow.app.chat.ChatHomeUi
import com.animeshadow.app.chat.ChatMessage
import com.animeshadow.app.chat.ChatRepository
import com.animeshadow.app.chat.ChatSettingsContent
import com.animeshadow.app.chat.ChatUi
import com.animeshadow.app.chat.WireStatus
import com.animeshadow.app.data.settings.ChatPrefs
import com.animeshadow.app.testutil.TestImages
import com.animeshadow.app.ui.ads.LocalAdPreview
import com.animeshadow.app.ui.browse.CategoriesContent
import com.animeshadow.app.ui.common.LocalAnimatedLoader
import com.animeshadow.app.ui.theme.AnimeShadowTheme
import com.animeshadow.app.ui.theme.Palette
import com.animeshadow.app.ui.watch.OfficialLink
import com.animeshadow.app.ui.watch.OfficialLinks
import com.animeshadow.app.ui.watch.ReadOfficialSection
import com.animeshadow.app.ui.watch.WatchSection
import com.github.takahirom.roborazzi.captureRoboImage
import com.animeshadow.app.data.auth.AniListAccount
import com.animeshadow.app.data.settings.Profile
import com.animeshadow.app.data.settings.Settings
import com.animeshadow.app.domain.BrowseQuery
import com.animeshadow.app.ui.auth.LoginContent
import com.animeshadow.app.ui.browse.FilterSheet
import com.animeshadow.app.ui.common.LoadingBox
import com.animeshadow.app.ui.settings.SettingsActions
import com.animeshadow.app.ui.settings.SettingsContent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v1.4.0 shots at Madyan's phone settings (360 dp, font 1.075, Bold Text). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ar-w360dp-h800dp-xxhdpi")
class V140ShotsTest {
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

    private fun shot(name: String) = compose.onRoot().captureRoboImage("build/outputs/roborazzi/v140_$name.png")

    private val convs = listOf(
        ChatConversation("a", "ترشيحات تشبه Attack on Titan", updatedAt = 1_793_150_000_000, preview = "أنصحك بـ Parasyte و Tokyo Ghoul، وذوقك هو الأهم!"),
        ChatConversation("b", "تقييم قائمتي", updatedAt = 1_793_140_000_000, preview = "قائمتك قوية، بس ناقصها شوية رومانسية 😄"),
        ChatConversation("c", "وش هذا الأنمي؟", updatedAt = 1_793_100_000_000, preview = "هذا Frieren: Beyond Journey's End"),
    )

    @Test fun chat_home() { set { ChatHomeContent(ChatHomeUi(convs, WireStatus(ready = true, used = 3, remaining = 17)), {}, {}, {}, {}, {}) }; shot("chat_home") }

    @Test fun chat_home_not_ready() {
        set { ChatHomeContent(ChatHomeUi(emptyList(), WireStatus(ready = false)), {}, {}, {}, {}, {}) }
        compose.onNodeWithTag("chat_quota").assertIsDisplayed()
        shot("chat_home_not_ready")
    }

    @Test fun chat_conversation_with_cards() {
        val msgs = listOf(
            ChatMessage("1", "user", "رشّح لي أنمي يشبه اللي أحبه"),
            ChatMessage("2", "assistant", "مدين، إذا تحب Attack on Titan و Death Note، أنصحك بـ Parasyte و Tokyo Ghoul و Demon Slayer. كلهم أكشن وقصص مشوقة، بس هذا رأيي وذوقك هو الأهم! 😉",
                cards = listOf(ChatCard(20623, "ANIME", "Parasyte -the maxim-", cover = com.animeshadow.app.testutil.Fixtures.search[0].posterUrl, year = 2014, score = 83), ChatCard(20605, "ANIME", "Tokyo Ghoul", cover = com.animeshadow.app.testutil.Fixtures.search[1].posterUrl, year = 2014, score = 75), ChatCard(101922, "ANIME", "Demon Slayer: Kimetsu no Yaiba", cover = com.animeshadow.app.testutil.Fixtures.search[2].posterUrl, year = 2019, score = 83))),
            ChatMessage("3", "user", "حل لي واجب رياضيات"),
            ChatMessage("4", "assistant", "آسف، أنا شادو سينباي متخصص في الأنمي والمانجا بس 🙏 تبي أرشح لك أنمي عن الرياضيات؟ 😄"),
        )
        set { ChatContent(ChatUi(ChatConversation("a", "ترشيحات تشبه ذوقي"), msgs, ready = true, remaining = 17), {}, {}, {}, {}, {}, {}, {}) }
        compose.onAllNodesWithTag("chat_card")[0].assertIsDisplayed()
        shot("chat_conversation")
    }

    @Test fun chat_new_welcome() { set { ChatContent(ChatUi(ready = true, remaining = 20), {}, {}, {}, {}, {}, {}, {}) }; shot("chat_new") }

    @Test fun chat_not_ready_is_graceful() {
        set { ChatContent(ChatUi(ready = false), {}, {}, {}, {}, {}, {}, {}) }
        compose.onNodeWithTag("chat_not_ready").assertIsDisplayed()
        compose.onNodeWithTag("chat_input").assertIsNotEnabled()
        shot("chat_not_ready")
    }

    @Test fun chat_errors_explained_no_retry_button() {
        val msgs = listOf(ChatMessage("1", "user", "هلا"), ChatMessage("2", "assistant", error = "وصلت حد اليوم (20 رسالة). يتجدد بعد منتصف الليل."))
        set { ChatContent(ChatUi(ChatConversation("x", "سلام"), msgs, ready = true, remaining = 0), {}, {}, {}, {}, {}, {}, {}) }
        compose.onNodeWithTag("chat_error").assertIsDisplayed()
        shot("chat_limit")
    }

    @Test fun chat_customisation_name_locked() {
        set { ChatSettingsContent(ChatPrefs("مَدْيَن", "تكلم بلهجة يمنية، خلك مختصر ومرح."), {}, {}) }
        compose.onNodeWithTag("chat_name_locked").assertIsNotEnabled()
        shot("chat_settings")
    }

    @Test fun categories_anime() { set { CategoriesContent(adult = false, manga = false, onMenu = {}, onBack = null, onGenre = {}, onTag = {}) }; shot("categories_anime") }

    @Test fun categories_manga_adult_on() { set { CategoriesContent(adult = true, manga = true, onMenu = {}, onBack = null, onGenre = {}, onTag = {}) }; shot("categories_manga_adult") }

    @Test fun watch_and_read_sections() {
        val anime = OfficialLinks("tR8YH0G67Rk", listOf(OfficialLink("YouTube", "https://www.youtube.com/playlist?list=PLwLSw1_eDZl10YPPR7qDsVf10wZfYnIMK", "STREAMING")),
            listOf(OfficialLink("Crunchyroll", "https://www.crunchyroll.com/x", "STREAMING"), OfficialLink("Netflix", "https://www.netflix.com/x", "STREAMING"), OfficialLink("Bilibili TV", "https://www.bilibili.tv/x", "STREAMING")))
        val manga = OfficialLinks(null, emptyList(), listOf(OfficialLink("MANGA Plus", "https://mangaplus.shueisha.co.jp/titles/100020", "STREAMING", "English"), OfficialLink("VIZ", "https://www.viz.com/one-piece", "STREAMING", "English"), OfficialLink("Shonen Jump Plus", "https://shonenjumpplus.com/x", "STREAMING", "Japanese")))
        set {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
                WatchSection(154587, preview = anime)
                ReadOfficialSection(30013, preview = manga)
            }
        }
        compose.onNodeWithTag("watch_trailer").assertIsDisplayed()
        shot("watch_read_sections")
    }

    private val me = Profile(name = "Madyan", handle = "madyan")

    @Test fun drawer_anime() {
        set(0) { DrawerContent(Routes.CHAT, me, {}, unread = 3) }
        compose.onNodeWithText("التصنيفات").assertIsDisplayed()
        compose.onNodeWithText("شادو سينباي").assertIsDisplayed()
        shot("drawer_anime")
    }

    @Test fun drawer_manga() { set(0) { DrawerContent(Routes.M_CATEGORIES, me, {}, unread = 0, entries = MangaDrawerEntries, accountName = "Madyan AL-Yatim") }; shot("drawer_manga") }

    @Test fun drawer_is_narrower_with_smaller_font_and_softer_purple() {
        assertEquals(280f, DRAWER_WIDTH.value, 0.1f)
        assertEquals(14.5f, DRAWER_FONT.value, 0.01f)
        val c = Palette.DrawerAccent
        val sat = { r: Float, g: Float, b: Float -> (maxOf(r, g, b) - minOf(r, g, b)) / maxOf(r, g, b) }
        assertTrue(sat(c.red, c.green, c.blue) < sat(Palette.Accent.red, Palette.Accent.green, Palette.Accent.blue))
    }

    @Test fun splash_icon() { set(0) { ShadowSplash(visible = true) }; compose.onNodeWithTag("shadow_icon").assertIsDisplayed(); shot("splash") }

    @Test fun login_icon() { set(0) { LoginContent({}, {}) }; shot("login") }

    @Test fun loader_icon() { set { LoadingBox() }; shot("loader") }

    @Test fun settings_adult_off_then_confirm() {
        var got: Pair<Boolean, Boolean>? = null
        set { SettingsContent(Settings(), {}, SettingsActions(), account = AniListAccount(configured = false), adult = false, adultConfirmed = false, onAdult = { a, b -> got = a to b }) }
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("adult_switch"))
        shot("settings_adult_off")
        compose.onNodeWithTag("adult_switch").performClick()
        compose.onNodeWithTag("adult_confirm").assertIsDisplayed()
        shot("settings_adult_confirm")
        compose.onNodeWithTag("adult_yes").performClick()
        assertEquals(true to true, got)
    }

    @Test fun filter_sheet_tags_no_age_filter_by_default() {
        set { FilterSheet(BrowseQuery(tag = "Isekai"), {}, {}) }
        compose.onNodeWithTag("ftag_Isekai").assertExists()
        compose.onNodeWithTag("age_true").assertDoesNotExist()
        compose.onNodeWithTag("fgenre_Hentai").assertDoesNotExist()
        shot("filter_sheet")
    }
}
