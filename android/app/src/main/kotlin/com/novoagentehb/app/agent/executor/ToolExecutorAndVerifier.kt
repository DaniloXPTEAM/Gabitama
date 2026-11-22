package com.novoagentehb.app.agent.executor

import com.novoagentehb.app.agent.diagnostics.DiagnosticsEngine
import com.novoagentehb.app.agent.diagnostics.DiagnosticsInput
import com.novoagentehb.app.agent.memory.MemoryStore
import com.novoagentehb.app.agent.models.ModelChatMessage
import com.novoagentehb.app.agent.models.ModelRole
import com.novoagentehb.app.agent.models.ModelRouter
import com.novoagentehb.app.agent.models.ModelTurnResult
import com.novoagentehb.app.agent.models.ProviderFailure
import com.novoagentehb.app.agent.orchestrator.ToolExecutionRequest
import com.novoagentehb.app.agent.orchestrator.ToolExecutionResult
import com.novoagentehb.app.agent.orchestrator.ToolExecutionSemantics
import com.novoagentehb.app.bridges.AndroidDeviceBridge
import com.novoagentehb.app.bridges.ReminderScheduler
import com.novoagentehb.app.bridges.SafLibraryManager
import com.novoagentehb.app.bridges.WeatherClient
import com.novoagentehb.app.data.local.FeedEventDao
import com.novoagentehb.app.data.local.GoalDao
import com.novoagentehb.app.data.local.GoalItemEntity
import com.novoagentehb.app.data.local.MemoryCategory
import com.novoagentehb.app.data.prefs.AppPreferences
import com.novoagentehb.app.data.prefs.ProviderId
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Executor + Verificador de pós-condições:
 * - Distingue ação local executada/verificada, leitura de prévias de notificação e
 *   handoff via Intent (`HANDOFF_TO_USER`).
 */
@Singleton
class ToolExecutorAndVerifier @Inject constructor(
    private val deviceBridge: AndroidDeviceBridge,
    private val weatherClient: WeatherClient,
    private val reminderScheduler: ReminderScheduler,
    private val safLibraryManager: SafLibraryManager,
    private val memoryStore: MemoryStore,
    private val goalDao: GoalDao,
    private val feedEventDao: FeedEventDao,
    private val prefs: AppPreferences,
    private val modelRouter: ModelRouter,
    private val diagnosticsEngine: DiagnosticsEngine
) {
    var lastFailureSnapshot: ProviderFailure? = null

    suspend fun executeAndVerify(request: ToolExecutionRequest): ToolExecutionResult {
        val args = request.args
        return when (request.capabilityName) {
            "open_app" -> {
                val res = deviceBridge.openApp(args["app_name"].orEmpty())
                ToolExecutionResult(res.success, res.verified, res.message, errorCode = res.errorCode, semantics = ToolExecutionSemantics.HANDOFF_TO_USER)
            }
            "call_contact" -> {
                val res = deviceBridge.callContact(args["contact_name"].orEmpty())
                ToolExecutionResult(res.success, res.verified, res.message, errorCode = res.errorCode, semantics = ToolExecutionSemantics.HANDOFF_TO_USER)
            }
            "call_number" -> {
                val res = deviceBridge.callPhoneNumber(args["phone_number"].orEmpty())
                ToolExecutionResult(res.success, res.verified, res.message, errorCode = res.errorCode, semantics = ToolExecutionSemantics.HANDOFF_TO_USER)
            }
            "set_alarm" -> {
                val res = deviceBridge.setSystemAlarm(
                    args["hour"]?.toIntOrNull(),
                    args["minute"]?.toIntOrNull(),
                    args["description"].orEmpty()
                )
                ToolExecutionResult(res.success, res.verified, res.message, errorCode = res.errorCode, semantics = ToolExecutionSemantics.HANDOFF_TO_USER)
            }
            "search_web" -> {
                val res = deviceBridge.searchWeb(args["query"].orEmpty())
                ToolExecutionResult(res.success, res.verified, res.message, errorCode = res.errorCode, semantics = ToolExecutionSemantics.HANDOFF_TO_USER)
            }
            "search_youtube" -> {
                val res = deviceBridge.searchYoutube(args["query"].orEmpty())
                ToolExecutionResult(res.success, res.verified, res.message, errorCode = res.errorCode, semantics = ToolExecutionSemantics.HANDOFF_TO_USER)
            }
            "search_spotify" -> {
                val res = deviceBridge.searchSpotify(args["query"].orEmpty())
                val msg = if (res.success) {
                    "${res.message} (HANDOFF_TO_USER: tela aberta no Spotify — reprodução efetiva não garantida automaticamente)."
                } else {
                    res.message
                }
                ToolExecutionResult(res.success, res.verified, msg, errorCode = res.errorCode, semantics = ToolExecutionSemantics.HANDOFF_TO_USER)
            }
            "pause_music" -> {
                val res = deviceBridge.pauseMedia()
                ToolExecutionResult(res.success, res.verified, res.message, errorCode = res.errorCode, semantics = ToolExecutionSemantics.HANDOFF_TO_USER)
            }
            "open_whatsapp_chat" -> {
                val res = deviceBridge.openWhatsAppChat(args["contact_name"].orEmpty())
                ToolExecutionResult(res.success, res.verified, res.message, errorCode = res.errorCode, semantics = ToolExecutionSemantics.HANDOFF_TO_USER)
            }
            "send_whatsapp_message" -> {
                val res = deviceBridge.sendWhatsAppMessage(
                    args["contact_name"].orEmpty(),
                    args["message"].orEmpty()
                )
                ToolExecutionResult(res.success, res.verified, res.message, errorCode = res.errorCode, semantics = ToolExecutionSemantics.ACCESSIBILITY_AUTOMATED)
            }
            "read_notifications" -> {
                val items = feedEventDao.observeRecent(10).first()
                if (items.isEmpty()) {
                    ToolExecutionResult(
                        true,
                        true,
                        "Nenhuma notificação recente registrada no feed (somente listener Android).",
                        semantics = ToolExecutionSemantics.NOTIFICATION_PREVIEW_ONLY
                    )
                } else {
                    val lines = items.take(5).joinToString("\n") { "• [${it.sourceLabel}] ${it.title}: ${it.body}" }
                    ToolExecutionResult(
                        true,
                        true,
                        "Últimas notificações capturadas pelo listener Android:\n$lines",
                        semantics = ToolExecutionSemantics.NOTIFICATION_PREVIEW_ONLY
                    )
                }
            }
            "read_gmail" -> {
                val gmailItems = feedEventDao.observeRecent(40).first()
                    .filter { it.sourcePackage == "com.google.android.gm" }
                if (gmailItems.isEmpty()) {
                    ToolExecutionResult(
                        true,
                        true,
                        "Nenhuma prévia de notificação do Gmail capturada (esta ferramenta lê apenas notificações Android, não acessa a caixa via Gmail API).",
                        semantics = ToolExecutionSemantics.NOTIFICATION_PREVIEW_ONLY
                    )
                } else {
                    val lines = gmailItems.take(5).joinToString("\n") { "• ${it.title} — ${it.body}" }
                    ToolExecutionResult(
                        true,
                        true,
                        "Prévias de notificação do Gmail (não é leitura completa da caixa via API):\n$lines",
                        semantics = ToolExecutionSemantics.NOTIFICATION_PREVIEW_ONLY
                    )
                }
            }
            "open_settings" -> {
                val res = deviceBridge.openSystemSettings()
                ToolExecutionResult(res.success, res.verified, res.message, errorCode = res.errorCode, semantics = ToolExecutionSemantics.HANDOFF_TO_USER)
            }
            "take_photo" -> {
                val res = deviceBridge.openCamera()
                ToolExecutionResult(res.success, res.verified, res.message, errorCode = res.errorCode, semantics = ToolExecutionSemantics.HANDOFF_TO_USER)
            }
            "get_weather" -> {
                val city = args["city"].orEmpty().ifBlank { prefs.defaultCity.first() }
                val summary = weatherClient.getCurrentWeather(city)
                if (summary != null) {
                    ToolExecutionResult(
                        true,
                        true,
                        "${summary.emoji} Clima em ${summary.cityName}: ${summary.tempC}°C (${summary.description}), sensação ${summary.feelsLikeC}°C, umidade ${summary.humidityPercent}%.",
                        semantics = ToolExecutionSemantics.READ_VERIFIED
                    )
                } else {
                    ToolExecutionResult(false, false, "Não consegui consultar o clima para $city agora.", errorCode = "WEATHER_UNAVAILABLE", semantics = ToolExecutionSemantics.READ_VERIFIED)
                }
            }
            "set_reminder" -> {
                val subject = args["subject"].orEmpty().ifBlank { "Lembrete" }
                val triggerAt = AndroidDeviceBridge.parseIsoOrRelativeMillis(args["trigger_at"].orEmpty())
                    ?: (System.currentTimeMillis() + 30 * 60_000L)
                val saved = reminderScheduler.schedule(subject, triggerAt)
                ToolExecutionResult(
                    success = saved.id > 0L,
                    verified = saved.id > 0L,
                    summary = "Lembrete \"${saved.title}\" salvo e alarme local armado.",
                    semantics = ToolExecutionSemantics.EXECUTED_AND_VERIFIED
                )
            }
            "share_content" -> {
                val res = deviceBridge.shareText(args["text"].orEmpty())
                ToolExecutionResult(res.success, res.verified, res.message, errorCode = res.errorCode, semantics = ToolExecutionSemantics.HANDOFF_TO_USER)
            }
            "bluetooth_connect" -> {
                val res = deviceBridge.connectBluetooth()
                ToolExecutionResult(res.success, res.verified, res.message, errorCode = res.errorCode, semantics = ToolExecutionSemantics.HANDOFF_TO_USER)
            }
            "read_calendar" -> {
                val res = deviceBridge.readUpcomingCalendar()
                ToolExecutionResult(res.success, res.verified, res.message, errorCode = res.errorCode, semantics = ToolExecutionSemantics.READ_VERIFIED)
            }
            "create_calendar_event" -> {
                val res = deviceBridge.createCalendarEvent(
                    args["title"].orEmpty(),
                    args["start_at"].orEmpty()
                )
                ToolExecutionResult(res.success, res.verified, res.message, errorCode = res.errorCode, semantics = ToolExecutionSemantics.EXECUTED_AND_VERIFIED)
            }
            "ask_claude" -> {
                val q = args["query"].orEmpty()
                when (val out = modelRouter.generateTurn(
                    role = ModelRole.TASK_SYNTHESIS,
                    systemPrompt = "Responda em português brasileiro de forma clara e objetiva.",
                    history = listOf(ModelChatMessage("user", q)),
                    tools = emptyList(),
                    explicitProvider = ProviderId.ANTHROPIC
                )) {
                    is ModelTurnResult.TextReply -> ToolExecutionResult(true, true, out.text, semantics = ToolExecutionSemantics.READ_VERIFIED)
                    is ModelTurnResult.ToolCallReply -> ToolExecutionResult(true, true, "Claude sugeriu ferramenta externa.", semantics = ToolExecutionSemantics.READ_VERIFIED)
                    is ModelTurnResult.Failure -> {
                        lastFailureSnapshot = out.failure
                        ToolExecutionResult(false, false, out.failure.userSummary, errorCode = out.failure.category.name, semantics = ToolExecutionSemantics.READ_VERIFIED)
                    }
                }
            }
            "save_memory" -> {
                val cat = runCatching {
                    MemoryCategory.valueOf(args["category"].orEmpty().uppercase())
                }.getOrDefault(MemoryCategory.FACT)
                val item = memoryStore.upsertFact(
                    rawKey = args["key"].orEmpty(),
                    value = args["value"].orEmpty(),
                    category = cat
                )
                ToolExecutionResult(
                    success = item.id > 0L,
                    verified = item.id > 0L,
                    summary = "Memória salva (${item.memoryKey}).",
                    semantics = ToolExecutionSemantics.EXECUTED_AND_VERIFIED
                )
            }
            "search_memory" -> {
                val found = memoryStore.search(args["query"].orEmpty())
                if (found.isEmpty()) {
                    ToolExecutionResult(true, true, "Nenhuma memória encontrada para essa busca.", semantics = ToolExecutionSemantics.READ_VERIFIED)
                } else {
                    val txt = found.take(6).joinToString("\n") { "• ${it.memoryKey}: ${it.value}" }
                    ToolExecutionResult(true, true, "Memórias localizadas:\n$txt", semantics = ToolExecutionSemantics.READ_VERIFIED)
                }
            }
            "upsert_goal" -> {
                val title = args["title"].orEmpty().trim()
                if (title.isBlank()) {
                    ToolExecutionResult(false, false, "Título de meta vazio.", errorCode = "EMPTY_GOAL_TITLE")
                } else {
                    val goalId = goalDao.upsert(
                        GoalItemEntity(
                            title = title,
                            description = args["description"].orEmpty(),
                            progressPercent = (args["progress"]?.toIntOrNull() ?: 0).coerceIn(0, 100)
                        )
                    )
                    ToolExecutionResult(goalId > 0L, goalId > 0L, "Meta \"$title\" salva com ID #$goalId.", semantics = ToolExecutionSemantics.EXECUTED_AND_VERIFIED)
                }
            }
            "workspace_write_file" -> {
                val w = safLibraryManager.writeWorkspaceFile(args["filename"].orEmpty(), args["content"].orEmpty())
                val readBack = if (w.success) safLibraryManager.readWorkspaceFile(w.relativePath) else null
                val shaConfirmed = readBack != null &&
                    SafLibraryManager.sha256Hex(readBack.toByteArray(Charsets.UTF_8)) == w.sha256
                ToolExecutionResult(
                    success = w.success && shaConfirmed,
                    verified = w.success && shaConfirmed,
                    summary = if (w.success && shaConfirmed) {
                        "Arquivo ${w.relativePath} gravado, relido e verificado (${w.sizeBytes} bytes, sha256=${w.sha256.take(12)}…)."
                    } else {
                        "Falha ao gravar/verificar arquivo: ${w.error ?: "leitura pós-gravação não conferiu"}"
                    },
                    errorCode = if (w.success && shaConfirmed) null else "WRITE_VERIFY_FAILED",
                    semantics = ToolExecutionSemantics.EXECUTED_AND_VERIFIED
                )
            }
            "workspace_read_file" -> {
                val text = safLibraryManager.readWorkspaceFile(args["filename"].orEmpty())
                if (text != null) {
                    ToolExecutionResult(true, true, text.take(1200), semantics = ToolExecutionSemantics.READ_VERIFIED)
                } else {
                    ToolExecutionResult(false, false, "Arquivo não encontrado no workspace do agente.", errorCode = "FILE_NOT_FOUND", semantics = ToolExecutionSemantics.READ_VERIFIED)
                }
            }
            "diagnostics_snapshot" -> {
                val report = diagnosticsEngine.buildReport(
                    DiagnosticsInput(
                        primaryProvider = prefs.primaryProvider.first(),
                        chatModel = prefs.chatModel.first(),
                        voiceMode = prefs.voiceMode.first(),
                        prosodyProfile = prefs.prosodyProfile.first(),
                        lastFailure = lastFailureSnapshot,
                        petComponentsPresent = false
                    )
                )
                ToolExecutionResult(true, true, report, semantics = ToolExecutionSemantics.READ_VERIFIED)
            }
            else -> ToolExecutionResult(false, false, "Capacidade desconhecida: ${request.capabilityName}", errorCode = "UNKNOWN_CAPABILITY", semantics = ToolExecutionSemantics.NOT_IMPLEMENTED)
        }
    }
}
