package com.novoagentehb.app.bridges

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.novoagentehb.app.R
import com.novoagentehb.app.app.MainActivity

class HomeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { widgetId ->
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_VOICE_TRIGGER
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pi = PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val views = RemoteViews(context.packageName, R.layout.widget_novo_agente).apply {
                setOnClickPendingIntent(R.id.widget_root, pi)
            }
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}
