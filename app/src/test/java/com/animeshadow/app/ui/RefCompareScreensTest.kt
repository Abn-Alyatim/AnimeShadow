package com.animeshadow.app.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import com.animeshadow.app.ui.feed.GenreUi
import com.animeshadow.app.ui.feed.StudioContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.animeshadow.app.R
import com.animeshadow.app.data.local.LibraryEntryEntity
import com.animeshadow.app.data.local.NotificationEntity
import com.animeshadow.app.data.local.WatchStatus
import com.animeshadow.app.data.settings.Profile
import com.animeshadow.app.domain.BrowseQuery
import com.animeshadow.app.testutil.Fixtures
import com.animeshadow.app.testutil.TestImages
import com.animeshadow.app.ui.browse.BrowseContent
import com.animeshadow.app.ui.browse.BrowseUi
import com.animeshadow.app.ui.details.DetailsActions
import com.animeshadow.app.ui.details.DetailsContent
import com.animeshadow.app.ui.details.DetailsUi
import com.animeshadow.app.ui.feed.FeedContent
import com.animeshadow.app.ui.feed.FeedUi
import com.animeshadow.app.ui.notifications.NotificationsContent
import com.animeshadow.app.ui.theme.AnimeShadowTheme
import com.animeshadow.app.ui.theme.Palette
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Screens for the side-by-side compare against Madyan's references (refs/v1.1/NN_*.jpg). v1.1.1 calibration, measured
 * on his own phone screenshots: the phone is 360 x 800 dp (1080 x 2400 px; the refs are scaled to 540 x 1200, 1.5 px/dp),
 * its text size is ~1.075 and Android's Bold Text is ON (+300 weight). The screens are rendered exactly like that here,
 * with a 24 dp strip standing in for the status bar, so ref and ours line up 1:1. Files: build/outputs/roborazzi/cmp_*.png
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ar-w360dp-h800dp-xxhdpi")
class RefCompareScreensTest {
    @get:Rule val compose = createComposeRule()

    @Before fun images() = TestImages.install(ApplicationProvider.getApplicationContext<Context>())

    private fun set(content: @Composable () -> Unit) {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val phone = ctx.createConfigurationContext(Configuration(ctx.resources.configuration).apply { fontWeightAdjustment = 300; fontScale = 1.075f })
        compose.setContent {
            CompositionLocalProvider(
                LocalFontFamilyResolver provides createFontFamilyResolver(phone),
                LocalDensity provides Density(LocalDensity.current.density, 1.075f),
            ) {
                AnimeShadowTheme { Surface(color = Palette.Black) { Box(Modifier.fillMaxSize().padding(top = 24.dp)) { content() } } }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
    }

    private fun shot(name: String) = compose.onRoot().captureRoboImage("build/outputs/roborazzi/cmp_$name.png")
    private fun screen(name: String) {
        compose.mainClock.advanceTimeBy(800)
        compose.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/cmp_$name.png")
    }

    private fun onePieceUi() = DetailsUi(
        anime = Fixtures.onePieceTranslated,
        entry = LibraryEntryEntity(21, WatchStatus.WATCHING, 1155, 9, 0, 0),
        watched = (1..1155).toSet(),
        favorite = false,
    )

    @Test fun details_onepiece() {
        set { DetailsContent(onePieceUi(), DetailsActions(), 0) }
        shot("details_onepiece")
    }

    @Test fun details_onepiece_english_fallback() {
        set { DetailsContent(onePieceUi().copy(anime = Fixtures.onePiece), DetailsActions(), 0) }
        shot("details_onepiece_english_fallback")
    }

    @Test fun status_inline_menu() {
        set { DetailsContent(onePieceUi(), DetailsActions(), 0) }
        compose.onNodeWithTag("my_list").performClick()
        screen("status_inline_menu")
    }

    @Test fun rating_dialog() {
        set { DetailsContent(onePieceUi().copy(entry = LibraryEntryEntity(21, WatchStatus.WATCHING, 0, null, 0, 0)), DetailsActions(), 0) }
        compose.onNodeWithTag("your_rating").performClick()
        screen("rating_dialog")
    }

    @Test fun episodes() {
        val ui = onePieceUi().copy(watched = (1..1178).toSet())
        set { DetailsContent(ui, DetailsActions(), 1) }
        shot("episodes")
    }

    @Test fun home() {
        set {
            FeedContent(stringResource(R.string.nav_latest), FeedUi(Fixtures.airing, grid = true, loaded = true), {}, {}, {}, {}, {}, {})
        }
        shot("home")
    }

    /** Item 6 (font): ref 05's own strings in our real home grid, for the font_ref05_vs_ours compare. */
    @Test fun font_specimen_home() {
        val names = listOf("Dragon Ball Super: Beerus" to 85, "Kanata kara" to 81, "Tensei Kizoku no Isekai Boukenroku" to 81)
        val items = Fixtures.airing.take(6).mapIndexed { i, a ->
            val (n, sc) = names[i % 3]
            a.copy(titleEnglish = n, titleRomaji = n, score = sc, malScore = sc / 10.0 + 0.02, status = "RELEASING", nextEpisode = 4 + i % 3)
        }
        set { FeedContent(stringResource(R.string.nav_latest), FeedUi(items, grid = true, loaded = true), {}, {}, {}, {}, {}, {}) }
        shot("font_specimen_home")
    }

    @Test fun search() {
        set { BrowseContent(BrowseUi(BrowseQuery(), Fixtures.search, 1, false), false, {}, {}, {}, {}, {}, {}, {}) }
        shot("search")
    }

    @Test fun notifications() {
        val a = Fixtures.airing
        val items = a.take(5).mapIndexed { i, x ->
            NotificationEntity(i + 1L, x.id, (x.latestEpisode ?: 1), x.displayTitle, x.coverLarge, if (i == 3) "reminder" else "new_episode", Fixtures.NOW - i * 3_600_000L, read = i >= 2)
        }
        set { NotificationsContent(items, {}, {}, {}, {}) }
        shot("notifications")
    }

    @Test fun studio() {
        set { StudioContent(GenreUi(genre = "Toei Animation", items = Fixtures.airing.take(9), page = 1), {}, {}, {}, {}, {}) }
        shot("studio")
    }

    @Test fun details_english_while_translating() {
        set { DetailsContent(onePieceUi().copy(anime = Fixtures.onePiece, translating = true), DetailsActions(), 0) }
        shot("details_translating")
    }

    @Test fun drawer() {
        set { DrawerContent(Routes.LATEST, Profile(name = "Madyan", handle = "madyan"), {}, unread = 2) }
        shot("drawer")
    }
}
