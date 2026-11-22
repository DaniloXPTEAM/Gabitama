package com.novoagentehb.app.bridges

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import java.net.URLEncoder
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class BridgeOutcome(
    val success: Boolean,
    val verified: Boolean,
    val message: String,
    val errorCode: String? = null
)

@Singleton
class AndroidDeviceBridge @Inject constructor(
    @ApplicationContext private val context: Context,
    private val spotifyBridge: SpotifyBridge
) {
    private val appAliases = mapOf(
        "whatsapp" to "com.whatsapp",
        "zap" to "com.whatsapp",
        "whatsapp business" to "com.whatsapp.w4b",
        "youtube" to "com.google.android.youtube",
        "spotify" to "com.spotify.music",
        "chrome" to "com.android.chrome",
        "navegador" to "com.android.chrome",
        "gmail" to "com.google.android.gm",
        "camera" to "android.media.action.IMAGE_CAPTURE",
        "câmera" to "android.media.action.IMAGE_CAPTURE",
        "configuracoes" to Settings.ACTION_SETTINGS,
        "configurações" to Settings.ACTION_SETTINGS,
        "ajustes" to Settings.ACTION_SETTINGS
    )

    fun openApp(appName: String): BridgeOutcome {
        val key = normalize(appName)
        val mapped = appAliases[key]
        if (mapped == Settings.ACTION_SETTINGS) return openSystemSettings()
        if (mapped == MediaStore.ACTION_IMAGE_CAPTURE) return openCamera()

        val pm = context.packageManager
        val pkgToLaunch = when {
            mapped != null && isInstalled(mapped) -> mapped
            mapped == "com.whatsapp" && isInstalled("com.whatsapp.w4b") -> "com.whatsapp.w4b"
            isInstalled(appName) -> appName
            else -> findInstalledPackageByLabel(key)
        } ?: return BridgeOutcome(false, false, "Aplicativo \"$appName\" não encontrado no aparelho.", "APP_NOT_FOUND")

        val intent = pm.getLaunchIntentForPackage(pkgToLaunch)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } ?: return BridgeOutcome(false, false, "O app $pkgToLaunch não possui tela inicial lançável.", "NO_LAUNCH_INTENT")

        return runCatching {
            context.startActivity(intent)
            BridgeOutcome(true, true, "Abri $appName ($pkgToLaunch).")
        }.getOrElse { err ->
            BridgeOutcome(false, false, "Falha ao abrir $appName: ${err.message}", "START_ACTIVITY_FAILED")
        }
    }

    fun callContact(contactName: String): BridgeOutcome {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return BridgeOutcome(false, false, "Permissão de Contatos necessária para buscar \"$contactName\".", "MISSING_READ_CONTACTS")
        }
        val phone = findContactPhone(contactName)
            ?: return BridgeOutcome(false, false, "Contato \"$contactName\" não encontrado com número válido.", "CONTACT_NOT_FOUND")
        return callPhoneNumber(phone, contactName)
    }

    fun callPhoneNumber(number: String, displayName: String? = null): BridgeOutcome {
        val clean = number.replace(Regex("[^+0-9*#]"), "")
        if (clean.isBlank()) {
            return BridgeOutcome(false, false, "Número de telefone inválido.", "INVALID_PHONE")
        }
        val canDirectCall = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val action = if (canDirectCall) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val intent = Intent(action, Uri.parse("tel:$clean")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            val label = displayName ?: clean
            val note = if (canDirectCall) "Ligando para $label." else "Discador aberto para $label (sem permissão CALL_PHONE)."
            BridgeOutcome(true, true, note)
        }.getOrElse { err ->
            BridgeOutcome(false, false, "Erro ao iniciar chamada: ${err.message}", "CALL_FAILED")
        }
    }

    fun setSystemAlarm(hour: Int?, minute: Int?, description: String): BridgeOutcome {
        val cal = Calendar.getInstance()
        val targetHour = hour ?: cal.get(Calendar.HOUR_OF_DAY)
        val targetMinute = minute ?: ((cal.get(Calendar.MINUTE) + 5) % 60)
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, targetHour.coerceIn(0, 23))
            putExtra(AlarmClock.EXTRA_MINUTES, targetMinute.coerceIn(0, 59))
            putExtra(AlarmClock.EXTRA_MESSAGE, description.ifBlank { "Alarme da Gabi" })
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            BridgeOutcome(true, true, "Alarme preparado para %02d:%02d.".format(targetHour, targetMinute))
        }.getOrElse { err ->
            BridgeOutcome(false, false, "Não consegui abrir o app de Relógio: ${err.message}", "ALARM_FAILED")
        }
    }

    fun searchWeb(query: String): BridgeOutcome {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$encoded")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            BridgeOutcome(true, true, "Pesquisa aberta no navegador para \"$query\".")
        }.getOrElse { err ->
            BridgeOutcome(false, false, "Falha ao abrir busca web: ${err.message}", "BROWSER_FAILED")
        }
    }

    fun searchYoutube(query: String): BridgeOutcome {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$encoded")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (isInstalled("com.google.android.youtube")) setPackage("com.google.android.youtube")
        }
        return runCatching {
            context.startActivity(intent)
            BridgeOutcome(true, true, "Busca no YouTube aberta para \"$query\".")
        }.getOrElse { err ->
            BridgeOutcome(false, false, "Falha ao abrir YouTube: ${err.message}", "YOUTUBE_FAILED")
        }
    }

    suspend fun searchSpotify(query: String): BridgeOutcome {
        val trackUri = spotifyBridge.resolveTrackUri(query)
        val uriToOpen = trackUri ?: "https://open.spotify.com/search/${URLEncoder.encode(query.trim(), "UTF-8")}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriToOpen)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (isInstalled("com.spotify.music")) setPackage("com.spotify.music")
        }
        return runCatching {
            context.startActivity(intent)
            if (trackUri != null) {
                BridgeOutcome(true, true, "Abrindo faixa correspondente no Spotify ($trackUri).")
            } else {
                BridgeOutcome(true, true, "Busca no Spotify aberta para \"$query\".")
            }
        }.getOrElse { err ->
            BridgeOutcome(false, false, "Não consegui abrir o Spotify: ${err.message}", "SPOTIFY_FAILED")
        }
    }

    fun pauseMedia(): BridgeOutcome {
        val audioManager = context.getSystemService(AudioManager::class.java)
            ?: return BridgeOutcome(false, false, "AudioManager indisponível.", "AUDIO_UNAVAILABLE")
        return runCatching {
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
            BridgeOutcome(true, true, "Comando de pausa de mídia enviado.")
        }.getOrElse { err ->
            BridgeOutcome(false, false, "Não consegui pausar mídia: ${err.message}", "MEDIA_PAUSE_FAILED")
        }
    }

    fun openWhatsAppChat(contactName: String): BridgeOutcome {
        val waPkg = when {
            isInstalled("com.whatsapp") -> "com.whatsapp"
            isInstalled("com.whatsapp.w4b") -> "com.whatsapp.w4b"
            else -> return BridgeOutcome(false, false, "WhatsApp não está instalado neste aparelho.", "WHATSAPP_MISSING")
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return BridgeOutcome(false, false, "Permissão de contatos necessária para identificar o destinatário.", "MISSING_READ_CONTACTS")
        }
        val phone = findContactPhone(contactName)
            ?: return BridgeOutcome(false, false, "Contato não encontrado exatamente ou possui números ambíguos; escolha o nome e número antes de continuar.", "CONTACT_AMBIGUOUS_OR_MISSING")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phone")).apply {
            setPackage(waPkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return runCatching {
            context.startActivity(intent)
            BridgeOutcome(true, false, "Solicitada abertura da conversa com $contactName; confirme o destinatário no WhatsApp. Nenhuma mensagem foi enviada.")
        }.getOrElse { err ->
            BridgeOutcome(false, false, "Erro ao abrir WhatsApp: ${err.message}", "WHATSAPP_ERROR")
        }
    }

    suspend fun sendWhatsAppMessage(contactName: String, message: String): BridgeOutcome {
        val service = AssistantAccessibilityService.getInstance()
            ?: return BridgeOutcome(
                false,
                false,
                "Acessibilidade desativada: abra Ajustes → Permissões para ativar antes de enviar WhatsApp.",
                "ACCESSIBILITY_DISABLED"
            )
        val openOutcome = openWhatsAppChat(contactName)
        if (!openOutcome.success) return openOutcome

        service.clearStopRequest()
        // Aguarda transição real da janela do WhatsApp antes de tentar preencher e enviar
        var sent = false
        for (attempt in 0 until 16) {
            if (service.isStopRequested()) break
            delay(150L)
            if (service.isStopRequested()) break
            if (service.attemptTypeAndSendWhatsApp(contactName, message)) {
                sent = true
                break
            }
        }
        return if (sent) {
            BridgeOutcome(true, false, "Clique em enviar solicitado via acessibilidade para $contactName; envio e entrega não foram verificados.", "WHATSAPP_SEND_UNVERIFIED")
        } else {
            BridgeOutcome(
                false,
                false,
                "Chat aberto, mas o campo/botão de envio do WhatsApp não pôde ser confirmado automaticamente. Não foi reenviado.",
                "WHATSAPP_SEND_UNVERIFIED"
            )
        }
    }

    fun openSystemSettings(): BridgeOutcome {
        val intent = Intent(Settings.ACTION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        return runCatching {
            context.startActivity(intent)
            BridgeOutcome(true, true, "Configurações do Android abertas.")
        }.getOrElse { err ->
            BridgeOutcome(false, false, "Erro ao abrir configurações: ${err.message}", "SETTINGS_FAILED")
        }
    }

    fun openCamera(): BridgeOutcome {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        return runCatching {
            context.startActivity(intent)
            BridgeOutcome(true, true, "Câmera aberta.")
        }.getOrElse { err ->
            BridgeOutcome(false, false, "Erro ao abrir câmera: ${err.message}", "CAMERA_FAILED")
        }
    }

    fun shareText(text: String): BridgeOutcome {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(shareIntent, "Compartilhar com a Gabi").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(chooser)
            BridgeOutcome(true, true, "Folha de compartilhamento aberta.")
        }.getOrElse { err ->
            BridgeOutcome(false, false, "Falha ao compartilhar: ${err.message}", "SHARE_FAILED")
        }
    }

    fun connectBluetooth(): BridgeOutcome {
        val manager = context.getSystemService(BluetoothManager::class.java)
        val adapter = manager?.adapter
        if (adapter == null) {
            return BridgeOutcome(false, false, "Bluetooth não suportado neste aparelho.", "BLUETOOTH_UNSUPPORTED")
        }
        val enabled = adapter.isEnabled
        val bonded = bondedBluetoothDevices()
        val scoRouted = if (enabled) routeAudioToBluetoothSco() else false
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        return runCatching {
            context.startActivity(intent)
            val detail = buildString {
                append(if (enabled) "Bluetooth ativo" else "Ajustes de Bluetooth abertos para ativação")
                if (bonded.isNotEmpty()) append(" · Pareados: ${bonded.take(4).joinToString(", ")}")
                if (scoRouted) append(" · Áudio SCO roteado")
            }
            BridgeOutcome(true, true, detail)
        }.getOrElse { err ->
            BridgeOutcome(false, false, "Não consegui abrir ajustes Bluetooth: ${err.message}", "BLUETOOTH_FAILED")
        }
    }

    @SuppressLint("MissingPermission")
    fun bondedBluetoothDevices(): List<String> {
        val hasPerm = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        if (!hasPerm) return emptyList()
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
        return runCatching {
            adapter.bondedDevices.mapNotNull { it.name?.takeIf(String::isNotBlank) }.sorted()
        }.getOrDefault(emptyList())
    }

    @Suppress("DEPRECATION")
    fun routeAudioToBluetoothSco(): Boolean {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val btDevice = am.availableCommunicationDevices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                }
                if (btDevice != null) {
                    am.mode = AudioManager.MODE_IN_COMMUNICATION
                    return@runCatching am.setCommunicationDevice(btDevice)
                }
            }
            if (!am.isBluetoothScoAvailableOffCall) return@runCatching false
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            am.startBluetoothSco()
            am.isBluetoothScoOn = true
            true
        }.getOrDefault(false)
    }

    @Suppress("DEPRECATION")
    fun stopBluetoothSco() {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.clearCommunicationDevice()
            } else if (am.isBluetoothScoOn) {
                am.isBluetoothScoOn = false
                am.stopBluetoothSco()
            }
            am.mode = AudioManager.MODE_NORMAL
        }
    }

    fun readUpcomingCalendar(): BridgeOutcome {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return BridgeOutcome(false, false, "Permissão de Agenda (leitura) necessária.", "MISSING_READ_CALENDAR")
        }
        val now = System.currentTimeMillis()
        val end = now + 7L * 24 * 3600 * 1000L
        val projection = arrayOf(CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART)
        val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
        val selectionArgs = arrayOf(now.toString(), end.toString())
        val items = mutableListOf<String>()
        val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

        runCatching {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${CalendarContract.Events.DTSTART} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext() && items.size < 6) {
                    val title = cursor.getString(0).orEmpty()
                    val dtStart = cursor.getLong(1)
                    if (title.isNotBlank()) items.add("• $title (${fmt.format(Date(dtStart))})")
                }
            }
        }
        return if (items.isEmpty()) {
            BridgeOutcome(true, true, "Nenhum compromisso encontrado nos próximos 7 dias.")
        } else {
            BridgeOutcome(true, true, "Próximos compromissos:\n${items.joinToString("\n")}")
        }
    }

    fun createCalendarEvent(title: String, startIso: String): BridgeOutcome {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return BridgeOutcome(false, false, "Permissão de Agenda (escrita) necessária.", "MISSING_WRITE_CALENDAR")
        }
        val startMillis = parseIsoOrRelativeMillis(startIso) ?: (System.currentTimeMillis() + 3600_000L)
        val calId = findPrimaryCalendarId() ?: 1L
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calId)
            put(CalendarContract.Events.TITLE, title.trim())
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, startMillis + 3600_000L)
            put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
        }
        return runCatching {
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            if (uri != null) {
                val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                BridgeOutcome(true, true, "Evento \"$title\" gravado na agenda para ${fmt.format(Date(startMillis))}.")
            } else {
                BridgeOutcome(false, false, "Não foi possível gravar o evento no provedor de calendário.", "CALENDAR_INSERT_NULL")
            }
        }.getOrElse { err ->
            BridgeOutcome(false, false, "Erro ao criar evento: ${err.message}", "CALENDAR_INSERT_FAILED")
        }
    }

    private fun findPrimaryCalendarId(): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        return runCatching {
            context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getLong(0) else null
            }
        }.getOrNull()
    }

    private fun findContactPhone(contactQuery: String): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        return runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, null
            )?.use { cursor ->
                val contacts = mutableListOf<Pair<String, String>>()
                while (cursor.moveToNext()) contacts.add(cursor.getString(0).orEmpty() to cursor.getString(1).orEmpty())
                ContactMatch.uniqueNumber(contactQuery, contacts)
            }
        }.getOrNull()
    }

    private fun findInstalledPackageByLabel(normalizedName: String): String? {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(mainIntent, 0)
        return apps.firstOrNull { ri ->
            normalize(ri.loadLabel(pm).toString()).contains(normalizedName)
        }?.activityInfo?.packageName
    }

    private fun isInstalled(packageName: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(packageName, 0); true }.getOrDefault(false)

    companion object {
        fun normalize(raw: String): String =
            Normalizer.normalize(raw.lowercase().trim(), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")

        fun parseIsoOrRelativeMillis(raw: String, nowMillis: Long = System.currentTimeMillis()): Long? {
            val clean = raw.trim()
            if (clean.isBlank()) return null
            val relMatch = Regex("(\\d+)\\s*(min|minuto|minutos|h|hora|horas)", RegexOption.IGNORE_CASE).find(clean)
            if (relMatch != null) {
                val qty = relMatch.groupValues[1].toLongOrNull() ?: return null
                val unit = relMatch.groupValues[2].lowercase()
                val factor = if (unit.startsWith("h")) 3600_000L else 60_000L
                return nowMillis + qty * factor
            }
            val formats = listOf("yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd HH:mm")
            for (pattern in formats) {
                val parsed = runCatching {
                    SimpleDateFormat(pattern, Locale.US).parse(clean)?.time
                }.getOrNull()
                if (parsed != null) return parsed
            }
            return null
        }

        /**
         * Parser offline de contingência em pt-BR quando não há modelo LLM configurado.
         * Preserva os comandos offline clássicos da Gabi (`voz-latencia`).
         */
        fun parseOfflineIntent(input: String): Pair<String, Map<String, String>>? {
            val norm = normalize(input)
            return when {
                // Comando especifico antes de abertura generica de apps.
                norm.contains("camera") || norm.contains("tirar foto") ->
                    "take_photo" to emptyMap()
                norm.startsWith("abrir ") || norm.startsWith("abre ") -> {
                    val app = input.trim().substringAfter(" ").trim()
                    "open_app" to mapOf("app_name" to app)
                }
                norm.contains("clima") || norm.contains("tempo") || norm.contains("vai chover") ->
                    "get_weather" to mapOf("city" to "")
                norm.contains("notificac") || norm.contains("avisos") ->
                    "read_notifications" to emptyMap()
                norm.contains("agenda") || norm.contains("compromisso") ->
                    "read_calendar" to emptyMap()
                norm.contains("lembrete") || norm.startsWith("lembra") -> {
                    val relMatch = Regex("(\\d+\\s*(?:minutos|minuto|min|horas|hora|h)\\b)", RegexOption.IGNORE_CASE).find(input)
                    val triggerAt = relMatch?.value?.trim() ?: "15 minutos"
                    val cleanedSubject = input.trim()
                        .replace(Regex("^(?i)(criar|cria|adicionar|adiciona|colocar|coloca|fazer|faz)?\\s*(um\\s+)?(lembrete|lembrar|lembra)(\\s+(de|da|do|para|pra))?\\s*"), "")
                        .trim()
                        .ifBlank { input.trim() }
                    "set_reminder" to mapOf("subject" to cleanedSubject, "trigger_at" to triggerAt)
                }
                norm.contains("alarme") || norm.contains("despertador") -> {
                    val timeMatch = Regex("\\b(\\d{1,2})[:h](\\d{2})?\\b", RegexOption.IGNORE_CASE).find(norm)
                    val hour = timeMatch?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 23)?.toString() ?: "8"
                    val minute = timeMatch?.groupValues?.getOrNull(2)?.toIntOrNull()?.coerceIn(0, 59)?.toString() ?: "0"
                    "set_alarm" to mapOf("hour" to hour, "minute" to minute, "description" to "Alarme da Gabi")
                }
                norm.contains("bluetooth") ->
                    "bluetooth_connect" to emptyMap()
                norm.contains("configurac") || norm.contains("ajustes do aparelho") ->
                    "open_settings" to emptyMap()
                norm.contains("compartilha") ->
                    "share_content" to mapOf("text" to input.trim())
                norm.contains("gmail") || norm.contains("e-mail") || norm.contains("email") ->
                    "read_gmail" to emptyMap()
                norm.contains("pausar musica") || norm.contains("para a musica") || norm.contains("pausa a musica") ->
                    "pause_music" to emptyMap()
                norm.startsWith("tocar ") || norm.contains("spotify") -> {
                    val q = input.replace(Regex("(?i)(tocar|no spotify|spotify)"), "").trim()
                    "search_spotify" to mapOf("query" to q.ifBlank { "Brasil" })
                }
                norm.startsWith("youtube ") || norm.contains("no youtube") -> {
                    val q = input.replace(Regex("(?i)(buscar|pesquisar|no youtube|youtube)"), "").trim()
                    "search_youtube" to mapOf("query" to q)
                }
                norm.startsWith("pesquis") || norm.startsWith("busca ") || norm.contains("no google") -> {
                    val q = input.replace(Regex("(?i)(pesquisar|pesquisa|buscar|busca|no google|google)"), "").trim()
                    if (q.isNotBlank()) "search_web" to mapOf("query" to q) else null
                }
                else -> null
            }
        }
    }
}
