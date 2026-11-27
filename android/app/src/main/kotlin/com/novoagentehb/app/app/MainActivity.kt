package com.novoagentehb.app.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.navigation.compose.rememberNavController
import com.novoagentehb.app.agent.browser.BrowserHistoryStore
import com.novoagentehb.app.agent.browser.BrowserSessionController
import com.novoagentehb.app.app.navigation.NovoAgenteNavGraph
import com.novoagentehb.app.app.navigation.Screen
import com.novoagentehb.app.design.LocalHBStage
import com.novoagentehb.app.design.NovoAgenteTheme
import com.novoagentehb.app.gabi.HBCharacterController
import com.novoagentehb.app.gabi.VoiceEngineCoordinator
import com.novoagentehb.app.ui.browser.InAppBrowserHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var hbController: HBCharacterController

    @Inject
    lateinit var browserController: BrowserSessionController

    @Inject
    lateinit var browserHistoryStore: BrowserHistoryStore

    @Inject
    lateinit var voiceCoordinator: VoiceEngineCoordinator

    private val autoStartListening = mutableStateOf(false)
    private val pendingRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        setContent {
            val hbState by hbController.state.collectAsState()
            NovoAgenteTheme {
                CompositionLocalProvider(LocalHBStage provides hbState.stage) {
                    val navController = rememberNavController()
                    NovoAgenteNavGraph(
                        navController = navController,
                        autoStartListening = autoStartListening,
                        pendingRoute = pendingRoute
                    )
                    // Navegador embutido: só aparece quando o agente ou a pessoa abre uma página.
                    InAppBrowserHost(controller = browserController, historyStore = browserHistoryStore)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Sair do app (ex.: abrir o Spotify) pausa a voz e não grava em segundo plano.
        // Rotação também passa por onStop: nesse caso a voz continua.
        if (!isChangingConfigurations) voiceCoordinator.onAppBackgrounded()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == ACTION_VOICE_TRIGGER) {
            autoStartListening.value = true
        }
        val uri = intent.data
        if (uri != null && uri.scheme == "novoagentehb") {
            val host = uri.host?.lowercase().orEmpty()
            pendingRoute.value = when (host) {
                "habitat", "quarto", "room" -> Screen.Habitat.route
                "chat" -> Screen.Chat.route
                "feed", "avisos", "notifications" -> Screen.Feed.route
                "goals", "metas" -> Screen.Goals.route
                "library", "arquivos", "files" -> Screen.Library.route
                "memory", "memoria" -> Screen.Memory.route
                "computer", "ferramentas", "tools" -> Screen.Computer.route
                "settings", "ajustes" -> Screen.Settings.route
                "permissions", "permissoes" -> Screen.Permissions.route
                else -> Screen.Home.route
            }
        }
    }

    companion object {
        const val ACTION_VOICE_TRIGGER = "com.novoagentehb.app.ACTION_VOICE_TRIGGER"
    }
}
