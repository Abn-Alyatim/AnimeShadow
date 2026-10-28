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


/**
 * v1.3.1 before/after shots at Madyan's phone settings (360 dp, font 1.075, Bold Text). Uses only APIs that exist in
 * 1.3.0 too, so the same file renders the "before" set on 1.3.0 and the "after" set on 1.3.1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ar-w360dp-h800dp-xxhdpi")
class V131ShotsTest {
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

    @Test fun drawer_anime() { set(0) { DrawerContent(Routes.LATEST, me, {}, unread = 3) }; shot("drawer_anime") }

    @Test fun drawer_manga() {
        set(0) { DrawerContent(Routes.M_HOME, me, {}, unread = 3, entries = MangaDrawerEntries, accountName = "Madyan AL-Yatim") }
        shot("drawer_manga")
    }

    @Test fun top_bar_and_loader_anime() {
        set(0) { androidx.compose.material3.Scaffold(containerColor = Palette.Black, topBar = { AppTopBar(ctx.getString(R.string.nav_latest), onMenu = {}) }) { p -> LoadingBox(Modifier.padding(p)) } }
        shot("topbar_loader_anime")
    }

    @Test fun top_bar_and_loader_manga() {
        set(0) { androidx.compose.material3.Scaffold(containerColor = Palette.Black, topBar = { AppTopBar(ctx.getString(R.string.manga_home), onMenu = {}) }) { p -> LoadingBox(Modifier.padding(p)) } }
        shot("topbar_loader_manga")
    }

    @Test fun manga_list() {
        val ms = MangaFixtures.list
        set {
            MangaLibraryContent(
                MangaLibraryUi(byStatus = mapOf(ReadStatus.READING to ms.take(4), ReadStatus.PLAN_TO_READ to ms.drop(4).take(3), ReadStatus.COMPLETED to ms.drop(7).take(2), ReadStatus.DROPPED to ms.drop(9).take(1))),
                {}, {}, {},
            )
        }
        shot("manga_list")
    }

    @Test @Config(qualifiers = "+h1500dp") fun profile_default() {
        set { ProfileContent(ProfileUi(me, com.animeshadow.app.domain.StatsCalculator.compute(Fixtures.library, 24), 6, Fixtures.detail), {}, {}) }
        shot("profile_anime")
    }

    @Test fun login() { set(0) { LoginContent({}, {}) }; shot("login") }

    @Test fun splash() { set(0) { ShadowSplash(visible = true) }; shot("splash") }
}
