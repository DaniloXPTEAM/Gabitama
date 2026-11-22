package com.novoagentehb.app.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.novoagentehb.app.R
import com.novoagentehb.app.agent.orchestrator.TaskOrchestrator
import com.novoagentehb.app.gabi.HBCharacterController
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NovoAgenteApplication : Application() {

    // Inicializa os coordenadores globais no startup para manter voz, Live tool-calls e HB sincronizados
    @Inject
    lateinit var taskOrchestrator: TaskOrchestrator

    @Inject
    lateinit var hbCharacterController: HBCharacterController

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return

        val voiceChannel = NotificationChannel(
            CHANNEL_VOICE,
            getString(R.string.notification_channel_voice),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_voice_desc)
        }

        val reminderChannel = NotificationChannel(
            CHANNEL_REMINDERS,
            getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.reminder_channel_desc)
        }

        val proactiveChannel = NotificationChannel(
            CHANNEL_PROACTIVE,
            getString(R.string.notification_channel_proactive),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.notification_channel_proactive_desc)
        }

        manager.createNotificationChannels(listOf(voiceChannel, reminderChannel, proactiveChannel))
    }

    companion object {
        const val CHANNEL_VOICE = "novo_agente_hb_voice"
        const val CHANNEL_REMINDERS = "novo_agente_hb_reminders"
        const val CHANNEL_PROACTIVE = "novo_agente_hb_proactive"
    }
}
