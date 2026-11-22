package com.novoagentehb.app.bridges

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.novoagentehb.app.R
import com.novoagentehb.app.app.MainActivity
import com.novoagentehb.app.app.NovoAgenteApplication
import com.novoagentehb.app.data.local.AppDatabase
import com.novoagentehb.app.data.local.ReminderDao
import com.novoagentehb.app.data.local.ReminderItemEntity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reminderDao: ReminderDao
) {
    suspend fun schedule(title: String, triggerAtMillis: Long): ReminderItemEntity {
        val insertedId = reminderDao.insert(
            ReminderItemEntity(title = title.trim(), triggerAtMillis = triggerAtMillis)
        )
        val entity = ReminderItemEntity(id = insertedId, title = title.trim(), triggerAtMillis = triggerAtMillis)
        armSystemAlarm(context, entity)
        return entity
    }

    companion object {
        fun armSystemAlarm(context: Context, reminder: ReminderItemEntity) {
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
                putExtra("reminder_id", reminder.id)
                putExtra("reminder_title", reminder.title)
            }
            val pi = PendingIntent.getBroadcast(
                context,
                reminder.id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val trigger = reminder.triggerAtMillis.coerceAtLeast(System.currentTimeMillis() + 5_000L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            }
        }
    }
}

class ReminderAlarmReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReceiverEntryPoint {
        fun database(): AppDatabase
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("reminder_id", 0L)
        val title = intent.getStringExtra("reminder_title") ?: "Lembrete agendado"

        val launchPi = PendingIntent.getActivity(
            context,
            id.toInt(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NovoAgenteApplication.CHANNEL_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.reminder_notification_title))
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(launchPi)
            .build()

        context.getSystemService(NotificationManager::class.java)
            ?.notify(id.toInt().coerceAtLeast(1001), notification)

        if (id > 0L) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    val ep = EntryPointAccessors.fromApplication(context.applicationContext, ReceiverEntryPoint::class.java)
                    ep.database().reminderDao().markTriggered(id)
                }
                pendingResult.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val ep = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    ReminderAlarmReceiver.ReceiverEntryPoint::class.java
                )
                ep.database().reminderDao().getPending().forEach { reminder ->
                    ReminderScheduler.armSystemAlarm(context, reminder)
                }
            }
            pendingResult.finish()
        }
    }
}
