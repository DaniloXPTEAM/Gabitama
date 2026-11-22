package com.novoagentehb.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novoagentehb.app.app.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FunctionalFlowsAndPersistenceAndroidTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun chatImeInputDiagnosticsPersistsAcrossRestartAndClears() {
        AndroidTestHarness.ensureOnHomeScreen(compose)
        AndroidTestHarness.clickBottomTab(compose, "Gabi")
        AndroidTestHarness.waitUntilText(compose, "Chat com a Gabi")

        if (AndroidTestHarness.hasAnyNode(compose, hasContentDescription("Limpar conversa") and hasClickAction())) {
            compose.onNode(hasContentDescription("Limpar conversa") and hasClickAction()).performClick()
            AndroidTestHarness.waitUntilText(compose, "Pronta para conversar")
        }

        val inputField = compose.onNode(hasSetTextAction())
        inputField.performClick()
        inputField.performTextInput("/diagnostico")
        AndroidTestHarness.dismissImeIfVisible(compose)
        compose.onNode(hasContentDescription("Enviar") and hasClickAction()).performClick()

        AndroidTestHarness.waitUntilText(compose, "DIAGNÓSTICO LOCAL", substring = true)
        compose.onAllNodes(hasText("NOVO_AGENTE_HB", substring = true)).onLast().assertIsDisplayed()
        compose.onAllNodes(hasText("camada_pet_excluida: true", substring = true)).onLast().assertIsDisplayed()
        AndroidTestHarness.captureScreenshot("04b_chat_diagnostics")

        // Verifica persistência no Room após recriar a Activity
        compose.activityRule.scenario.recreate()
        AndroidTestHarness.waitUntilText(compose, "Chat com a Gabi")
        AndroidTestHarness.waitUntilText(compose, "DIAGNÓSTICO LOCAL", substring = true)

        compose.onNode(hasContentDescription("Limpar conversa") and hasClickAction()).performClick()
        AndroidTestHarness.waitUntilText(compose, "Pronta para conversar")
    }

    @Test
    fun goalsRemindersMemoryAndLibraryPersistRealMutations() {
        AndroidTestHarness.ensureOnHomeScreen(compose)

        // Metas: criar, avançar progresso e criar lembrete rápido
        compose.onNode(hasText("METAS")).performScrollTo().performClick()
        AndroidTestHarness.waitUntilText(compose, "Metas & Lembretes")
        compose.onAllNodes(hasSetTextAction()).onFirst().performClick()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Estudar Compose UI")
        AndroidTestHarness.dismissImeIfVisible(compose)
        compose.onNode(hasContentDescription("Adicionar meta") and hasClickAction()).performClick()

        AndroidTestHarness.waitUntilText(compose, "Estudar Compose UI (0%)")
        compose.onAllNodes(hasContentDescription("Avançar") and hasClickAction()).onFirst().performClick()
        AndroidTestHarness.waitUntilText(compose, "Estudar Compose UI (25%)")

        // Lembrete rápido (+15 min)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("+15m"))
        compose.onAllNodes(hasSetTextAction()).onLast().performClick()
        compose.onAllNodes(hasSetTextAction()).onLast().performTextInput("Beber água")
        AndroidTestHarness.dismissImeIfVisible(compose)
        compose.onNode(hasText("+15m") and hasClickAction()).performClick()
        AndroidTestHarness.waitUntilText(compose, "Beber água")

        // Recria e confirma persistência de meta e lembrete no Room
        compose.activityRule.scenario.recreate()
        AndroidTestHarness.waitUntilText(compose, "Metas & Lembretes")
        AndroidTestHarness.waitUntilText(compose, "Estudar Compose UI (25%)")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Beber água"))
        compose.onAllNodes(hasText("Beber água")).onFirst().assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Estudar Compose UI (25%)"))
        compose.onAllNodes(hasContentDescription("Excluir") and hasClickAction()).onFirst().performClick()
        compose.waitForIdle()

        // Memória: registrar fato, sair e retornar, depois remover
        val before = AndroidTestHarness.captureNavigationDiagnostic(compose, "goals_before_inicio")
        AndroidTestHarness.clickBottomTab(compose, "Início")
        val after = AndroidTestHarness.captureNavigationDiagnostic(compose, "goals_after_inicio")
        try {
            AndroidTestHarness.waitUntilText(compose, "Gabi · HB")
        } catch (failure: Throwable) {
            val timeout = AndroidTestHarness.captureNavigationDiagnostic(compose, "goals_timeout")
            throw AssertionError("$before | $after | $timeout", failure)
        }
        compose.onNode(hasText("MEMÓRIA")).performScrollTo().performClick()
        AndroidTestHarness.waitUntilText(compose, "Memória da Gabi")

        val memFields = compose.onAllNodes(hasSetTextAction())
        memFields[0].performClick()
        memFields[0].performTextInput("cidade_teste")
        memFields[1].performClick()
        memFields[1].performTextInput("Fortaleza")
        AndroidTestHarness.dismissImeIfVisible(compose)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Salvar fato"))
        compose.onNode(hasText("Salvar fato") and hasClickAction()).performClick()

        AndroidTestHarness.waitUntilText(compose, "cidade_teste (FACT)")
        compose.onNode(hasText("Fortaleza")).assertIsDisplayed()
        AndroidTestHarness.pressSystemBack(compose)
        AndroidTestHarness.waitUntilText(compose, "Gabi · HB")
        compose.onNode(hasText("MEMÓRIA")).performScrollTo().performClick()
        AndroidTestHarness.waitUntilText(compose, "cidade_teste (FACT)")
        compose.onAllNodes(hasContentDescription("Remover") and hasClickAction()).onFirst().performClick()
        compose.waitForIdle()

        // Biblioteca: criar arquivo no workspace, abrir prévia e excluir
        AndroidTestHarness.clickBottomTab(compose, "Início")
        AndroidTestHarness.waitUntilText(compose, "Gabi · HB")
        compose.onNode(hasText("ARQUIVOS")).performScrollTo().performClick()
        AndroidTestHarness.waitUntilText(compose, "Arquivos & SAF")

        val libFields = compose.onAllNodes(hasSetTextAction())
        libFields[0].performClick()
        libFields[0].performTextInput("smoke_r142.txt")
        libFields[1].performClick()
        libFields[1].performTextInput("Conteudo verificado R1.4.2")
        AndroidTestHarness.dismissImeIfVisible(compose)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Salvar no Workspace", substring = true))
        compose.onNode(hasText("Salvar no Workspace", substring = true) and hasClickAction()).performClick()

        AndroidTestHarness.waitUntilText(compose, "smoke_r142.txt", substring = true)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("smoke_r142.txt", substring = true))
        compose.onNode(hasText("smoke_r142.txt", substring = true)).performClick()
        AndroidTestHarness.waitUntilText(compose, "Prévia do arquivo")
        compose.onNode(hasText("Conteudo verificado R1.4.2", substring = true)).assertIsDisplayed()
        compose.onNode(hasText("Fechar") and hasClickAction()).performClick()
        compose.onAllNodes(hasContentDescription("Remover") and hasClickAction()).onFirst().performClick()
        compose.waitForIdle()
    }

    @Test
    fun quartoDialogsFeedStageWardrobeEnvironmentBibleAndIntroWorkEndToEnd() {
        AndroidTestHarness.ensureOnHomeScreen(compose)
        AndroidTestHarness.clickBottomTab(compose, "Quarto")
        AndroidTestHarness.dismissQuartoIntroIfVisible(compose, dontShowAgain = false)

        // Alimentar e tocar na HB
        compose.onNode(hasText("Alimentar") and hasClickAction()).performClick()
        AndroidTestHarness.waitUntilText(compose, "Gabi alimentada agora!")
        compose.onNode(hasContentDescription("Personagem HB no Quarto da Gabi:", substring = true)).performClick()
        compose.waitForIdle()

        // Fases da HB (Pré-adolescente -> validação -> restaura Infância) + Bíblia
        compose.onNode(hasContentDescription("Fase da HB")).performClick()
        AndroidTestHarness.waitUntilText(compose, "Pré-adolescente (idade1)")
        AndroidTestHarness.captureScreen(compose, "05b_quarto_stage_dialog", "habitat", "Pré-adolescente (idade1)")
        compose.onNode(hasText("Pré-adolescente (idade1)")).performClick()
        AndroidTestHarness.waitUntilText(compose, "Cena completa (pre_adolescent)", substring = true)

        compose.onNode(hasContentDescription("Fase da HB")).performClick()
        AndroidTestHarness.waitUntilText(compose, "Ver Bíblia & Spritesheet da HB")
        compose.onNode(hasText("Ver Bíblia & Spritesheet da HB") and hasClickAction()).performClick()
        AndroidTestHarness.waitUntilText(compose, "Bíblia & Referências da HB")
        AndroidTestHarness.captureScreenshot("05c_bible_dialog")
        compose.onNode(hasText("Fechar") and hasClickAction()).performClick()

        compose.onNode(hasContentDescription("Fase da HB")).performClick()
        AndroidTestHarness.waitUntilText(compose, "Infância (Padrão)")
        compose.onNode(hasText("Infância (Padrão)")).performClick()
        AndroidTestHarness.waitUntilText(compose, "Base original")

        // Guarda-roupa: seleciona Vestido Lilás, confirma Arte pendente visível, restaura Rotina do dia
        compose.onNode(hasContentDescription("Guarda-roupa")).performClick()
        AndroidTestHarness.waitUntilText(compose, "Guarda-roupa da Gabi")
        compose.onNode(hasText("Vestido Lilás · Leitura")).assertIsDisplayed()
        AndroidTestHarness.captureScreenshot("05d_wardrobe_dialog")
        compose.onNode(hasText("Vestido Lilás · Leitura")).performClick()
        AndroidTestHarness.waitUntilText(compose, "Look alterado para Vestido Lilás · Leitura.")

        compose.onNode(hasContentDescription("Guarda-roupa")).performClick()
        AndroidTestHarness.waitUntilText(compose, "Rotina do dia")
        compose.onNode(hasText("Rotina do dia")).performClick()
        AndroidTestHarness.waitUntilText(compose, "Modo Rotina do dia ativado.")

        // Ambiente do Quarto + Rever abertura (Não mostrar novamente)
        compose.onNode(hasContentDescription("Ambiente do Quarto")).performClick()
        AndroidTestHarness.waitUntilText(compose, "Quarto Iluminado")
        compose.onNode(hasText("Quarto Iluminado")).performClick()
        AndroidTestHarness.waitUntilText(compose, "Ambiente: Quarto Iluminado.")

        compose.onNode(hasContentDescription("Ambiente do Quarto")).performClick()
        AndroidTestHarness.waitUntilText(compose, "Rever abertura do Quarto")
        compose.onNode(hasText("Rever abertura do Quarto")).performClick()
        AndroidTestHarness.dismissQuartoIntroIfVisible(compose, dontShowAgain = true)
    }

    @Test
    fun feedComputerAndSettingsControlsUpdateStateWithoutExternalSideEffects() {
        AndroidTestHarness.ensureOnHomeScreen(compose)

        // Feed tabs
        AndroidTestHarness.clickBottomTab(compose, "Avisos")
        AndroidTestHarness.waitUntilText(compose, "Avisos & Recibos")
        compose.onNode(hasText("Recibos (", substring = true)).performClick()
        compose.onNode(hasText("Notificações (", substring = true)).performClick()

        // Computer: diagnóstico, cadastro de skill local segura
        AndroidTestHarness.clickBottomTab(compose, "Quarto")
        AndroidTestHarness.dismissQuartoIntroIfVisible(compose, dontShowAgain = true)
        compose.onNode(hasContentDescription("Computador e Skills")).performClick()
        AndroidTestHarness.waitUntilText(compose, "Computador & Skills")
        compose.onNode(hasText("Diagnóstico") and hasClickAction()).performClick()
        AndroidTestHarness.waitUntilText(compose, "Comando /diagnostico enviado para a fila do orquestrador.")

        // Settings: alterna provedor, abre diagnóstico local e restaura Gemini
        AndroidTestHarness.clickBottomTab(compose, "Ajustes")
        AndroidTestHarness.waitUntilText(compose, "Ajustes · Gemini & Voz")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Groq"))
        compose.onNode(hasText("Groq")).performClick()
        AndroidTestHarness.waitUntilText(compose, "Provedor ativo: Groq", substring = true)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Google Gemini"))
        compose.onNode(hasText("Google Gemini")).performClick()
        AndroidTestHarness.waitUntilText(compose, "✓ Google Gemini")
        // The status banner is a lazy item, published asynchronously after the
        // provider selection. Keep retrying its scroll until it exists, with a
        // hard deadline; the final assertion is not suppressed.
        compose.waitUntil(15_000) {
            try {
                compose.onNode(hasScrollAction()).performScrollToNode(hasText("Provedor ativo: Google Gemini", substring = true))
                true
            } catch (_: AssertionError) {
                false
            }
        }
        compose.onNode(hasText("Provedor ativo: Google Gemini", substring = true)).assertIsDisplayed()

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Diagnóstico"))
        compose.onNode(hasText("Diagnóstico") and hasClickAction()).performClick()
        AndroidTestHarness.waitUntilText(compose, "Diagnóstico Local")
        compose.onNode(hasText("NOVO_AGENTE_HB", substring = true)).assertIsDisplayed()
        compose.onNode(hasText("Fechar") and hasClickAction()).performClick()
    }
}
