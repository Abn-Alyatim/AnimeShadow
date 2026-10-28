package com.animeshadow.app.ui

import android.content.Intent
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.animeshadow.app.MainActivity
import com.animeshadow.app.R
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Boots the real app (Hilt graph, Room, WorkManager, navigation) and visits every drawer destination
 * plus a details page opened from a notification intent. Fails on any crash during composition or navigation.
 * Network is whatever the test machine has; screens must render cached/empty/error states without crashing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-w393dp-h852dp-xhdpi")
class AppSmokeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @org.junit.Before fun noSplash() { SplashConfig.enabled = false }

    @Test fun every_drawer_destination_opens() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForIdle()
            compose.onNode(hasText(ctx.getString(R.string.mode_anime)) and hasClickAction()).performClick()
            compose.waitForIdle()
            val labels = DrawerEntries.map { ctx.getString(it.label) }
            for (label in labels) {
                compose.onNode(hasContentDescription(ctx.getString(R.string.cd_menu)) and hasClickAction()).performClick()
                compose.waitForIdle()
                compose.onNode(hasText(label) and hasClickAction()).performClick()
                compose.waitForIdle()
                compose.mainClock.advanceTimeBy(600)
                compose.waitForIdle()
            }
            compose.onRoot().captureRoboImage("build/outputs/roborazzi/smoke_last_destination.png")
        }
    }

    /** v1.3: the «أنمي | مانجا» toggle switches instantly; every manga drawer destination opens; anime keeps its place. */
    @Test fun manga_mode_switch_and_every_manga_destination_opens() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForIdle()
            compose.onNode(hasText(ctx.getString(R.string.mode_manga)) and hasClickAction()).performClick()
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(600)
            compose.waitForIdle()
            compose.onNodeWithTag("kind_chips").assertExists()
            for (label in MangaDrawerEntries.map { ctx.getString(it.label) }) {
                compose.onNode(hasContentDescription(ctx.getString(R.string.cd_menu)) and hasClickAction()).performClick()
                compose.waitForIdle()
                compose.onNode(hasText(label) and hasClickAction() and !hasAnyAncestor(hasTestTag("mode_toggle")) and !hasAnyAncestor(hasTestTag("kind_chips")) and !hasTestTag("kind_manga")).performClick()
                compose.waitForIdle()
                compose.mainClock.advanceTimeBy(600)
                compose.waitForIdle()
                // The queue screen has a back arrow instead of the menu: go back to manga home.
                if (compose.onAllNodes(hasContentDescription(ctx.getString(R.string.cd_menu)) and hasClickAction()).fetchSemanticsNodes().isEmpty()) {
                    compose.onNode(hasContentDescription(ctx.getString(R.string.cd_back)) and hasClickAction()).performClick()
                    compose.waitForIdle()
                }
            }
            compose.onRoot().captureRoboImage("build/outputs/roborazzi/smoke_manga_last.png")
            // Back to anime (and leave the stored mode as the other tests expect it).
            compose.onNode(hasContentDescription(ctx.getString(R.string.cd_menu)) and hasClickAction()).performClick()
            compose.waitForIdle()
            compose.onNode(hasText(ctx.getString(R.string.manga_home)) and hasClickAction() and !hasAnyAncestor(hasTestTag("mode_toggle")) and !hasTestTag("kind_manga")).performClick()
            compose.waitForIdle()
            compose.onNode(hasText(ctx.getString(R.string.mode_anime)) and hasClickAction()).performClick()
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(600)
            compose.waitForIdle()
            compose.onNodeWithTag("kind_chips").assertDoesNotExist()
        }
    }

    /** v1.0 bug (ref 09): the drawer bell opened the episode schedule. It must open the Notifications screen. */
    @Test fun drawer_bell_opens_notifications_screen() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForIdle()
            compose.onNode(hasContentDescription(ctx.getString(R.string.cd_menu)) and hasClickAction()).performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("drawer_bell").performClick()
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(600)
            compose.waitForIdle()
            compose.onNodeWithTag("notifications_screen").assertExists()
            compose.onNode(hasText(ctx.getString(R.string.notifications_title)) and !hasClickAction()).assertExists()
            compose.onNodeWithText(ctx.getString(R.string.notifications_empty)).assertExists()
            compose.onRoot().captureRoboImage("build/outputs/roborazzi/smoke_bell_notifications.png")
        }
    }

    @Test fun notification_intent_opens_details() {
        val intent = Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_ANIME_ID, 151807)
        ActivityScenario.launch<MainActivity>(intent).use {
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(600)
            compose.waitForIdle()
            compose.onNode(hasText(ctx.getString(R.string.tab_episodes)) and hasClickAction()).performClick()
            compose.waitForIdle()
        }
    }
}
