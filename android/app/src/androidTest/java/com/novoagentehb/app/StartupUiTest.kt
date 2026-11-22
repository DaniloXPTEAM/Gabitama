package com.novoagentehb.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novoagentehb.app.app.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real Application, Activity and navigation, with no external actions. */
@RunWith(AndroidJUnit4::class)
class StartupUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun onboardingHomeChatAndBackRemainAlive() {
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasText("Continuar")).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodes(hasText("Gabi · HB")).fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodes(hasText("Continuar")).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Continuar").performClick()
        }
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasText("Gabi · HB")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Gabi · HB").assertIsDisplayed()
        Thread.sleep(8_000)
        compose.activityRule.scenario.onActivity {
            assertEquals(Lifecycle.State.RESUMED, it.lifecycle.currentState)
        }
        compose.onNodeWithText("Gabi", useUnmergedTree = true).performClick()
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasText("Chat com a Gabi")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Chat com a Gabi").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasText("Gabi · HB")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Gabi · HB").assertIsDisplayed()
    }
}
