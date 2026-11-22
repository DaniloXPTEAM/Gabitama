package com.novoagentehb.app

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novoagentehb.app.app.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupAndLifecycleAndroidTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun backgroundResumeAndActivityRecreationPreserveHomeState() {
        AndroidTestHarness.ensureOnHomeScreen(compose)

        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        AndroidTestHarness.waitUntilText(compose, "Gabi · HB")
        compose.onNode(hasText("Gabi · HB")).assertIsDisplayed()

        compose.activityRule.scenario.recreate()
        AndroidTestHarness.waitUntilText(compose, "Gabi · HB")
        compose.activityRule.scenario.onActivity { activity ->
            assertEquals(Lifecycle.State.RESUMED, activity.lifecycle.currentState)
        }
        compose.onNode(hasText("Gabi · HB")).assertIsDisplayed()
    }

    @Test
    fun landscapeConfigurationKeepsHomeNavigableAndInputAccessible() {
        AndroidTestHarness.ensureOnHomeScreen(compose)
        try {
            compose.activityRule.scenario.onActivity { activity ->
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            compose.waitForIdle()
            AndroidTestHarness.waitUntilText(compose, "Gabi · HB")
            compose.onNode(hasText("Gabi · HB")).performScrollTo().assertIsDisplayed()
            compose.onNode(hasSetTextAction()).performScrollTo().assertIsDisplayed()
            AndroidTestHarness.captureScreenshot("02b_home_landscape")
        } finally {
            compose.activityRule.scenario.onActivity { activity ->
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            compose.waitForIdle()
        }
    }

    @Test
    fun deepLinkIntentNavigatesDirectlyToRequestedDestination() {
        AndroidTestHarness.ensureOnHomeScreen(compose)
        compose.activityRule.scenario.onActivity { activity ->
            val deepLinkIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("novoagentehb://chat"),
                activity,
                MainActivity::class.java
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            activity.startActivity(deepLinkIntent)
        }
        AndroidTestHarness.waitUntilText(compose, "Chat com a Gabi", timeoutMs = 20_000L)
        compose.onNode(hasText("Chat com a Gabi")).assertIsDisplayed()
        compose.activityRule.scenario.onActivity { activity ->
            assertFalse(activity.isFinishing)
            assertEquals(Lifecycle.State.RESUMED, activity.lifecycle.currentState)
        }
    }
}
