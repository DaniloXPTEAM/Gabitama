package com.novoagentehb.app

import android.graphics.Bitmap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.novoagentehb.app.app.MainActivity
import java.io.File
import java.io.FileOutputStream

typealias MainComposeRule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

object AndroidTestHarness {

    fun waitUntilNodeExists(
        compose: MainComposeRule,
        matcher: SemanticsMatcher,
        timeoutMs: Long = 25_000L
    ) {
        compose.waitUntil(timeoutMs) {
            compose.onAllNodes(matcher, useUnmergedTree = false)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    fun waitUntilText(
        compose: MainComposeRule,
        text: String,
        substring: Boolean = false,
        timeoutMs: Long = 25_000L
    ) {
        waitUntilNodeExists(compose, hasText(text, substring = substring), timeoutMs)
    }

    fun ensureOnHomeScreen(compose: MainComposeRule) {
        compose.waitUntil(30_000L) {
            hasAnyNode(compose, hasText("Continuar")) || hasAnyNode(compose, hasText("Gabi · HB"))
        }
        if (hasAnyNode(compose, hasText("Continuar"))) {
            compose.onNode(hasText("Permissões da Gabi · HB")).assertIsDisplayed()
            captureScreenshot("01_permissions_onboarding")
            compose.onNode(hasText("Continuar") and hasClickAction()).performClick()
        }
        waitUntilText(compose, "Gabi · HB", timeoutMs = 30_000L)
        compose.onNode(hasText("Gabi · HB")).assertIsDisplayed()
        captureScreenshot("02_home")
    }

    fun dismissQuartoIntroIfVisible(
        compose: MainComposeRule,
        dontShowAgain: Boolean = false
    ) {
        compose.waitUntil(20_000L) {
            hasAnyNode(compose, hasText("Pular")) ||
                hasAnyNode(compose, hasText("Não mostrar novamente")) ||
                hasAnyNode(compose, hasText("Quarto da Gabi"))
        }
        if (hasAnyNode(compose, hasText("Pular")) || hasAnyNode(compose, hasText("Não mostrar novamente"))) {
            captureScreenshot("05a_quarto_intro")
            val btnText = if (dontShowAgain) "Não mostrar novamente" else "Pular"
            compose.onNode(hasText(btnText) and hasClickAction()).performClick()
        }
        waitUntilText(compose, "Quarto da Gabi", timeoutMs = 20_000L)
        compose.onNode(hasText("Quarto da Gabi")).assertIsDisplayed()
    }

    fun clickBottomTab(compose: MainComposeRule, label: String) {
        val matcher = hasText(label) and hasClickAction()
        waitUntilNodeExists(compose, matcher, timeoutMs = 15_000L)
        compose.onAllNodes(matcher).onLast().performClick()
    }

    fun pressSystemBack(compose: MainComposeRule) {
        compose.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        compose.waitForIdle()
    }

    fun hasAnyNode(compose: MainComposeRule, matcher: SemanticsMatcher): Boolean =
        compose.onAllNodes(matcher, useUnmergedTree = false)
            .fetchSemanticsNodes()
            .isNotEmpty()

    fun captureScreenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot()) { "Screenshot unavailable" }
        val dir = requireNotNull(instrumentation.targetContext.getExternalFilesDir("test-screenshots"))
        check(dir.exists() || dir.mkdirs()) { "Cannot create screenshot directory" }
        FileOutputStream(File(dir, "$name.png")).use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)) { "Cannot save screenshot" }
        }
        bitmap.recycle()
    }
}
