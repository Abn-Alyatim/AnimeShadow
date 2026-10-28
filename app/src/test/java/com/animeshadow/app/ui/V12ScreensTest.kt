package com.animeshadow.app.ui

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
import com.animeshadow.app.ui.details.DetailsActions
import com.animeshadow.app.ui.details.DetailsContent
import com.animeshadow.app.ui.details.DetailsUi
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
 * v1.2 screens at Madyan's phone settings (360 dp, font scale 1.075, Bold Text) — the "after" half of the
 * before/after images (same names as BeforeV12Test at v1.1.1) — plus the interaction checks: a pull gesture calls
 * the refresh (also on an empty list), the banner is on list screens only, no retry button, the AniList button
 * explains calmly when it isn't activated, and the developer row opens wa.me.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ar-w360dp-h800dp-xxhdpi")
class V12ScreensTest {
    @get:Rule val compose = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    @Before fun images() = TestImages.install(ctx)

    private fun set(content: @Composable () -> Unit) {
        val phone = ctx.createConfigurationContext(Configuration(ctx.resources.configuration).apply { fontWeightAdjustment = 300; fontScale = 1.075f })
        compose.setContent {
            CompositionLocalProvider(
                LocalFontFamilyResolver provides createFontFamilyResolver(phone),
                LocalDensity provides Density(LocalDensity.current.density, 1.075f),
                LocalAdPreview provides true,
                LocalAnimatedLoader provides false,
            ) {
                AnimeShadowTheme { Surface(color = Palette.Black) { Box(Modifier.fillMaxSize().padding(top = 24.dp)) { content() } } }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
    }

    private fun shot(name: String) = compose.onRoot().captureRoboImage("build/outputs/roborazzi/v12_after_$name.png")

    private val feed = FeedUi(Fixtures.airing, grid = true, loaded = true)

    // ---------------------------------------------------------------- screenshots (after)

    @Test fun home() { set { FeedContent(stringResource(R.string.nav_latest), feed, {}, {}, {}, {}, {}, {}) }; shot("home") }

    @Test fun home_offline() {
        set { FeedContent(stringResource(R.string.nav_latest), feed.copy(error = Refresh.Failed(true, null)), {}, {}, {}, {}, {}, {}) }
        // Golden rule: no retry button; the banner tells you to pull.
        compose.onNodeWithText("إعادة المحاولة").assertDoesNotExist()
        compose.onNodeWithText(ctx.getString(R.string.offline_cached)).assertExists()
        shot("home_offline")
    }

    @Test fun home_loading() { set { FeedContent(stringResource(R.string.nav_latest), FeedUi(), {}, {}, {}, {}, {}, {}) }; compose.onNodeWithTag("shadow_loop").assertExists(); shot("home_loading") }

    @Test fun home_refreshing() {
        set { FeedContent(stringResource(R.string.nav_latest), feed.copy(refreshing = true), {}, {}, {}, {}, {}, {}) }
        compose.mainClock.advanceTimeBy(900)
        compose.onNodeWithTag("refresh_indicator_on", useUnmergedTree = true).assertExists()
        shot("home_refreshing")
    }

    @Test fun top() { set { FeedContent(stringResource(R.string.nav_top), FeedUi(Fixtures.top, loaded = true), {}, {}, {}, {}, {}, {}) }; shot("top") }

    @Test fun seasons() {
        set { SeasonsContent(FeedUi(Fixtures.season, loaded = true), SeasonPick(2024, "SPRING"), 2026, {}, {}, {}, {}, {}, {}, {}) }
        shot("seasons")
    }

    @Test fun browse() {
        set { BrowseContent(BrowseUi(BrowseQuery(search = "solo leveling"), Fixtures.search, 1, false), false, {}, {}, {}, {}, {}, {}, {}) }
        shot("browse")
    }

    @Test fun my_list() {
        val lib = Fixtures.library
        set {
            MyListContent(
                MyListUi(WatchStatus.COMPLETED, items = lib.filter { it.entry.status == WatchStatus.COMPLETED }, counts = lib.groupingBy { it.entry.status }.eachCount()),
                {}, {}, {}, {}, {},
            )
        }
        shot("my_list")
    }

    @Test fun popular_characters() { set { PopularCharactersContent(Fixtures.popularCharacters, null, {}, {}, {}) }; shot("popular_characters") }

    @Test @Config(qualifiers = "+h1500dp") fun settings() {
        set { SettingsContent(Settings(notifications = true), {}, SettingsActions(), account = AniListAccount(configured = false)) }
        compose.onNodeWithTag("developer_row").assertExists()
        compose.onNodeWithText("مدين اليتيم / Madyan AL-Yatim").assertExists()
        shot("settings")
    }

    @Test fun splash() {
        set { ShadowSplash(visible = true) }
        compose.onNodeWithTag("shadow_icon").assertExists() // v1.4.0: small animated icon instead of the video
        compose.onNodeWithTag("shadow_splash").assertExists()
        shot("splash")
    }

    // ---------------------------------------------------------------- behaviour

    @Test fun pull_gesture_calls_refresh_on_a_list() {
        var pulls = 0
        set { FeedContent(stringResource(R.string.nav_latest), feed, {}, {}, {}, {}, {}, { pulls++ }) }
        compose.onNodeWithTag("pull_refresh").performTouchInput { swipeDown(startY = top + 20f, endY = bottom - 20f, durationMillis = 600) }
        compose.waitForIdle()
        assertEquals(1, pulls)
    }

    @Test fun pull_gesture_works_on_an_empty_list_too() {
        // v1.1: an empty list was not scrollable, so a pull there could never refresh.
        var pulls = 0
        set { FeedContent(stringResource(R.string.nav_latest), FeedUi(loaded = true, error = Refresh.Failed(true, null)), {}, {}, {}, {}, {}, { pulls++ }) }
        compose.onNodeWithTag("pull_refresh").performTouchInput { swipeDown(startY = top + 20f, endY = bottom - 20f, durationMillis = 600) }
        compose.waitForIdle()
        assertEquals(1, pulls)
    }

    @Test fun banner_on_list_screens_only() {
        set { FeedContent(stringResource(R.string.nav_latest), feed, {}, {}, {}, {}, {}, {}) }
        compose.onNodeWithTag("ad_banner").assertIsDisplayed()
    }

    @Test fun no_banner_on_details() {
        set { DetailsContent(DetailsUi(anime = Fixtures.detail, entry = LibraryEntryEntity(Fixtures.detail.id, WatchStatus.WATCHING, 1, null, 0, 0)), DetailsActions(), 0) }
        compose.onNodeWithTag("ad_banner").assertDoesNotExist()
    }

    @Test fun no_banner_on_settings() {
        set { SettingsContent(Settings(), {}, SettingsActions()) }
        compose.onNodeWithTag("ad_banner").assertDoesNotExist()
    }

    @Test fun settings_has_no_account_section_any_more() {
        // v1.3 item 1: the account moved to the Profile screen.
        set { SettingsContent(Settings(), {}, SettingsActions(), account = AniListAccount(configured = true)) }
        compose.onNodeWithTag("anilist_login").assertDoesNotExist()
        compose.onNodeWithText(ctx.getString(R.string.settings_account)).assertDoesNotExist()
    }

    @Test fun developer_row_opens_telegram() {
        var taps = 0
        set { SettingsContent(Settings(), {}, SettingsActions(onDeveloper = { taps++ })) }
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("developer_row"))
        compose.onNodeWithTag("developer_row").performClick()
        assertEquals(1, taps)
        compose.onNodeWithText("\u2066@abnalyatim\u2069").assertExists()
        compose.onNodeWithText("مدين اليتيم / Madyan AL-Yatim").assertExists()
        // v1.3 item 9: Telegram only, no WhatsApp and no phone number anywhere.
        compose.onNodeWithText("+967780647484", substring = true).assertDoesNotExist()
        openUrl(ctx, ctx.getString(R.string.developer_telegram_url))
        val started: Intent = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("https://t.me/abnalyatim", started.dataString)
    }
}
