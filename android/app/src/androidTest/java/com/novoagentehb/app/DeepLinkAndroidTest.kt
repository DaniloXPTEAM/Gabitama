package com.novoagentehb.app

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.novoagentehb.app.app.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A single ActivityScenario owns the Activity started by the deep-link intent. */
@RunWith(AndroidJUnit4::class)
class DeepLinkAndroidTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun deepLinkIntentNavigatesDirectlyToRequestedDestination() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("novoagentehb://chat"), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            compose.waitUntil(30_000) {
                compose.onAllNodes(hasText("Chat com a Gabi")).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNode(hasText("Chat com a Gabi")).assertIsDisplayed()
            scenario.onActivity {
                assertFalse(it.isFinishing)
                assertEquals(Lifecycle.State.RESUMED, it.lifecycle.currentState)
            }
            // Check the deep-link destination remains usable after navigation.
            compose.onNode(hasText("Início") and hasClickAction()).performClick()
            compose.waitUntil(30_000) {
                compose.onAllNodes(hasText("Gabi · HB")).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNode(hasText("Gabi · HB")).assertIsDisplayed()
        } // closes the same scenario that launched the Activity; failures propagate
    }
}
