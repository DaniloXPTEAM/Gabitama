package com.novoagentehb.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novoagentehb.app.app.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AllTenScreensNavigationAndroidTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun navigateAllTenScreensAndVerifyBackNavigation() {
        // 1. Permissions (se inicial) -> 2. Home
        AndroidTestHarness.ensureOnHomeScreen(compose)

        // 3. Feed ("Avisos")
        AndroidTestHarness.clickBottomTab(compose, "Avisos")
        AndroidTestHarness.waitUntilText(compose, "Avisos & Recibos")
        compose.onNode(hasText("Avisos & Recibos")).assertIsDisplayed()
        AndroidTestHarness.captureScreen(compose, "03_feed", "feed", "Avisos & Recibos")

        // 4. Chat ("Gabi")
        AndroidTestHarness.clickBottomTab(compose, "Gabi")
        AndroidTestHarness.waitUntilText(compose, "Chat com a Gabi")
        compose.onNode(hasText("Chat com a Gabi")).assertIsDisplayed()
        AndroidTestHarness.captureScreen(compose, "04_chat", "chat", "Chat com a Gabi")

        // 5. Quarto / Habitat ("Quarto")
        AndroidTestHarness.clickBottomTab(compose, "Quarto")
        AndroidTestHarness.dismissQuartoIntroIfVisible(compose, dontShowAgain = false)
        AndroidTestHarness.captureScreen(compose, "05_quarto", "habitat", "Quarto da Gabi")

        // 6. Computer (via ícone do Quarto) + Back
        compose.onNode(hasContentDescription("Computador e Skills")).performClick()
        AndroidTestHarness.waitUntilText(compose, "Computador & Skills")
        compose.onNode(hasText("Computador & Skills")).assertIsDisplayed()
        AndroidTestHarness.captureScreen(compose, "06_computer", "computer", "Computador & Skills")
        AndroidTestHarness.pressSystemBack(compose)
        AndroidTestHarness.waitUntilText(compose, "Quarto da Gabi")

        // 7. Settings ("Ajustes") -> revisit Permissions -> Back
        AndroidTestHarness.clickBottomTab(compose, "Ajustes")
        AndroidTestHarness.waitUntilText(compose, "Ajustes · Gemini & Voz")
        compose.onNode(hasText("Ajustes · Gemini & Voz")).assertIsDisplayed()
        AndroidTestHarness.captureScreen(compose, "07_settings", "settings", "Ajustes · Gemini & Voz")

        compose.onNode(hasText("Permissões") and hasClickAction()).performClick()
        AndroidTestHarness.waitUntilText(compose, "Permissões da Gabi · HB")
        compose.onNode(hasText("Solicitar padrão")).assertIsDisplayed()
        AndroidTestHarness.captureScreen(compose, "11_permissions", "permissions", "Permissões da Gabi · HB")
        AndroidTestHarness.pressSystemBack(compose)
        AndroidTestHarness.waitUntilText(compose, "Ajustes · Gemini & Voz")

        // Return to Home
        AndroidTestHarness.clickBottomTab(compose, "Início")
        AndroidTestHarness.waitUntilText(compose, "Gabi · HB")

        // 8. Goals + Back
        compose.onNode(hasText("METAS")).performScrollTo().performClick()
        AndroidTestHarness.waitUntilText(compose, "Metas & Lembretes")
        compose.onNode(hasText("Metas & Lembretes")).assertIsDisplayed()
        AndroidTestHarness.captureScreen(compose, "08_goals", "goals", "Metas & Lembretes")
        AndroidTestHarness.pressSystemBack(compose)
        AndroidTestHarness.waitUntilText(compose, "Gabi · HB")

        // 9. Memory + Back
        compose.onNode(hasText("MEMÓRIA")).performScrollTo().performClick()
        AndroidTestHarness.waitUntilText(compose, "Memória da Gabi")
        compose.onNode(hasText("Memória da Gabi")).assertIsDisplayed()
        AndroidTestHarness.captureScreen(compose, "09_memory", "memory", "Memória da Gabi")
        AndroidTestHarness.pressSystemBack(compose)
        AndroidTestHarness.waitUntilText(compose, "Gabi · HB")

        // 10. Library + Back
        compose.onNode(hasText("ARQUIVOS")).performScrollTo().performClick()
        AndroidTestHarness.waitUntilText(compose, "Arquivos & SAF")
        compose.onNode(hasText("Arquivos & SAF")).assertIsDisplayed()
        AndroidTestHarness.captureScreen(compose, "10_library", "library", "Arquivos & SAF")
        AndroidTestHarness.pressSystemBack(compose)
        AndroidTestHarness.waitUntilText(compose, "Gabi · HB")
        compose.onNode(hasText("Gabi · HB")).performScrollTo().assertIsDisplayed()
    }
}
