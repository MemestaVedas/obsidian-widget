package com.obsidianwidget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class WidgetGestureReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(
            NoteGestureHandler.EXTRA_WIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        // Forward to NoteWidgetProvider via explicit broadcast
        val forwardIntent = Intent(context, NoteWidgetProvider::class.java).apply {
            action = intent.action
            putExtras(intent)
        }
        context.sendBroadcast(forwardIntent)
    }
}
