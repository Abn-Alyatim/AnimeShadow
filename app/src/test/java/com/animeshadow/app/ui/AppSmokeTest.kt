package com.animeshadow.app.ui

import android.content.Intent
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
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

    @Test fun every_drawer_destination_opens() {
        ActivityScenario.launch(MainActivity::class.java).use {
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
