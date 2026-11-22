package com.novoagentehb.app

import android.graphics.Bitmap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onRoot
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


    /** AVD-only diagnostic: tree structure and known screen labels, never arbitrary text. */
    fun captureNavigationDiagnostic(compose: MainComposeRule, name: String): String {
        captureScreenshot(name)
        val labels = listOf("Início", "Gabi · HB", "Metas & Lembretes", "Memória da Gabi", "Arquivos & SAF", "Continuar")
        val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        val lines = mutableListOf<String>()
        fun walk(node: SemanticsNode, depth: Int) {
            val matched = labels.filter { hasText(it).matches(node) }
            lines.add("depth=$depth id=${node.id} bounds=${node.boundsInRoot} labels=${matched.joinToString()}")
            node.children.forEach { walk(it, depth + 1) }
        }
        walk(root, 0)
        val merged = compose.onAllNodes(hasText("Início") and hasClickAction()).fetchSemanticsNodes().size
        val home = compose.onAllNodes(hasText("Gabi · HB")).fetchSemanticsNodes().size
        val goals = compose.onAllNodes(hasText("Metas & Lembretes")).fetchSemanticsNodes().size
        var state = "unknown"
        compose.activityRule.scenario.onActivity { state = it.lifecycle.currentState.toString() }
        val summary = "checkpoint=$name inicio_clickable=$merged home=$home goals=$goals lifecycle=$state"
        val dir = requireNotNull(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("test-screenshots"))
        File(dir, "$name-tree-sanitized.txt").writeText(summary + "\n" + lines.joinToString("\n"))
        println("GABIDIAG $summary")
        return summary
    }

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
