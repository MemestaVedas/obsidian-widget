package com.obsidianwidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.content.ContextCompat

class NoteWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val OVERLAY_DISMISS_DELAY = 3000L
        const val EXTRA_WIDGET_ID = "com.obsidianwidget.EXTRA_WIDGET_ID"

        private val DOT_IDS = intArrayOf(
            R.id.dot_0, R.id.dot_1, R.id.dot_2, R.id.dot_3, R.id.dot_4,
            R.id.dot_5, R.id.dot_6, R.id.dot_7, R.id.dot_8, R.id.dot_9
        )

        fun updateWidget(context: Context, appWidgetId: Int) {
            val intent = Intent(context, NoteWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
            }
            context.sendBroadcast(intent)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        // Standard lifecycle events that don't need background work
        if (action == AppWidgetManager.ACTION_APPWIDGET_DELETED ||
            action == AppWidgetManager.ACTION_APPWIDGET_ENABLED ||
            action == AppWidgetManager.ACTION_APPWIDGET_DISABLED
        ) {
            super.onReceive(context, intent)
            return
        }

        // Everything else runs on a background thread via goAsync()
        val pendingResult = goAsync()
        Thread {
            try {
                dispatchAction(context, intent)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private fun dispatchAction(context: Context, intent: Intent) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val appWidgetId = intent.getIntExtra(
            NoteGestureHandler.EXTRA_WIDGET_ID,
            intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                intent.getIntExtra(
                    EXTRA_WIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
            )
        )

        when (intent.action) {
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                    ?: return
                for (id in ids) updateAppWidget(context, appWidgetManager, id)
            }
            AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED -> {
                val id = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val options = appWidgetManager.getAppWidgetOptions(id)
                    handleOptionsChanged(context, appWidgetManager, id, options)
                }
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                val ids = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, NoteWidgetProvider::class.java)
                )
                for (id in ids) updateAppWidget(context, appWidgetManager, id)
            }
            ACTION_PREV_NOTE -> handlePrevNote(context, appWidgetId)
            ACTION_NEXT_NOTE -> handleNextNote(context, appWidgetId)
            NoteGestureHandler.ACTION_PAGE_CHANGE -> handlePageChange(context, appWidgetId, intent)
            NoteGestureHandler.ACTION_OPEN_NOTE -> handleOpenNote(context, appWidgetId)
            NoteGestureHandler.ACTION_TOGGLE_COMPACT -> handleToggleCompact(context, appWidgetId)
            NoteGestureHandler.ACTION_SHOW_OVERLAY -> handleShowOverlay(context, appWidgetId)
            NoteGestureHandler.ACTION_HIDE_OVERLAY -> handleHideOverlay(context, appWidgetId)
            ACTION_ADD_NOTE -> handleAddNote(context, appWidgetId)
            ACTION_REMOVE_NOTE -> handleRemoveNote(context, appWidgetId)
            ACTION_OPEN_SETTINGS -> handleOpenSettings(context, appWidgetId)
            ACTION_ITEM_CLICK -> handleItemClick(context, intent, appWidgetId)
        }
    }

    private fun handleOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        val minWidth = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 252)
        val minHeight = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 252)

        val prefs = context.getSharedPreferences("obsidian_widget_prefs", Context.MODE_PRIVATE)
        val isCompact = minWidth < 200 || minHeight < 200
        prefs.edit().putBoolean("is_compact_$appWidgetId", isCompact).apply()

        updateAppWidget(context, appWidgetManager, appWidgetId)
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val rv = RemoteViews(context.packageName, R.layout.widget_layout)
        val prefs = context.getSharedPreferences("obsidian_widget_prefs", Context.MODE_PRIVATE)
        val repository = NoteRepository(context)
        val notes = repository.getAllNotes()
        val currentPage = prefs.getInt("current_page_$appWidgetId", 0)
            .coerceIn(0, (notes.size - 1).coerceAtLeast(0))

        // Set up the ListView adapter for the current note's content
        val serviceIntent = Intent(context, NoteWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            // Unique data URI to prevent adapter reuse across updates
            data = Uri.parse("widget://note_content/$appWidgetId/$currentPage/${System.currentTimeMillis()}")
        }
        rv.setRemoteAdapter(R.id.note_list_view, serviceIntent)

        // Set up the pending intent template for clicking list items (opens note in Obsidian)
        val clickIntentTemplate = Intent(context, NoteWidgetProvider::class.java).apply {
            action = ACTION_ITEM_CLICK
            putExtra(EXTRA_WIDGET_ID, appWidgetId)
        }
        val clickPendingIntentTemplate = PendingIntent.getBroadcast(
            context,
            appWidgetId * 1000,
            clickIntentTemplate,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        rv.setPendingIntentTemplate(R.id.note_list_view, clickPendingIntentTemplate)

        // Set up Prev button
        val prevIntent = Intent(context, NoteWidgetProvider::class.java).apply {
            action = ACTION_PREV_NOTE
            putExtra(EXTRA_WIDGET_ID, appWidgetId)
        }
        rv.setOnClickPendingIntent(R.id.btn_prev, PendingIntent.getBroadcast(
            context, appWidgetId * 100 + 1, prevIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ))

        // Set up Next button
        val nextIntent = Intent(context, NoteWidgetProvider::class.java).apply {
            action = ACTION_NEXT_NOTE
            putExtra(EXTRA_WIDGET_ID, appWidgetId)
        }
        rv.setOnClickPendingIntent(R.id.btn_next, PendingIntent.getBroadcast(
            context, appWidgetId * 100 + 2, nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ))

        // Page indicator text (● ○ ○ ○)
        if (notes.size > 1) {
            val indicator = buildIndicatorString(notes.size, currentPage)
            rv.setTextViewText(R.id.page_indicator_text, indicator)
            rv.setViewVisibility(R.id.nav_bar, View.VISIBLE)
        } else if (notes.size == 1) {
            rv.setTextViewText(R.id.page_indicator_text, "●")
            rv.setViewVisibility(R.id.btn_prev, View.INVISIBLE)
            rv.setViewVisibility(R.id.btn_next, View.INVISIBLE)
            rv.setViewVisibility(R.id.nav_bar, View.VISIBLE)
        } else {
            rv.setViewVisibility(R.id.nav_bar, View.GONE)
        }

        // Warning badge
        val warningVisibility = if (ObsidianLauncher.isObsidianInstalled(context)) View.GONE else View.VISIBLE
        rv.setViewVisibility(R.id.warning_badge, warningVisibility)

        // Setup overlay action buttons
        setupOverlayActions(context, rv, appWidgetId)

        // Hide overlay by default
        rv.setViewVisibility(R.id.action_overlay, View.GONE)

        // Hide old dots container
        rv.setViewVisibility(R.id.page_dots_container, View.GONE)

        appWidgetManager.updateAppWidget(appWidgetId, rv)
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.note_list_view)
    }

    private fun buildIndicatorString(total: Int, current: Int): String {
        val sb = StringBuilder()
        for (i in 0 until total) {
            if (i > 0) sb.append("  ")
            sb.append(if (i == current) "●" else "○")
        }
        return sb.toString()
    }

    private fun setupOverlayActions(context: Context, rv: RemoteViews, appWidgetId: Int) {
        val actions = listOf(
            R.id.action_add_note to ACTION_ADD_NOTE,
            R.id.action_remove_current to ACTION_REMOVE_NOTE,
            R.id.action_reorder to ACTION_REORDER,
            R.id.action_settings to ACTION_OPEN_SETTINGS,
        )
        for ((index, pair) in actions.withIndex()) {
            val (viewId, action) = pair
            val intent = Intent(context, NoteWidgetProvider::class.java).apply {
                this.action = action
                putExtra(NoteGestureHandler.EXTRA_WIDGET_ID, appWidgetId)
            }
            rv.setOnClickPendingIntent(
                viewId,
                PendingIntent.getBroadcast(
                    context, appWidgetId * 10 + index, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }
    }

    // ---- Navigation Handlers ----

    private fun handlePrevNote(context: Context, appWidgetId: Int) {
        navigateNote(context, appWidgetId, -1)
    }

    private fun handleNextNote(context: Context, appWidgetId: Int) {
        navigateNote(context, appWidgetId, +1)
    }

    private fun navigateNote(context: Context, appWidgetId: Int, direction: Int) {
        val prefs = context.getSharedPreferences("obsidian_widget_prefs", Context.MODE_PRIVATE)
        val repository = NoteRepository(context)
        val noteCount = repository.getNoteCount()
        if (noteCount == 0) return

        val currentPage = prefs.getInt("current_page_$appWidgetId", 0)
        val newPage = (currentPage + direction + noteCount) % noteCount
        prefs.edit().putInt("current_page_$appWidgetId", newPage).apply()

        val appWidgetManager = AppWidgetManager.getInstance(context)
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }

    private fun handlePageChange(context: Context, appWidgetId: Int, intent: Intent) {
        val direction = intent.getStringExtra(NoteGestureHandler.EXTRA_DIRECTION) ?: return
        val delta = when (direction) {
            NoteGestureHandler.DIRECTION_LEFT -> +1
            NoteGestureHandler.DIRECTION_RIGHT -> -1
            else -> 0
        }
        if (delta != 0) navigateNote(context, appWidgetId, delta)
    }

    private fun handleOpenNote(context: Context, appWidgetId: Int) {
        val prefs = context.getSharedPreferences("obsidian_widget_prefs", Context.MODE_PRIVATE)
        val repository = NoteRepository(context)
        val currentPage = prefs.getInt("current_page_$appWidgetId", 0)
        val note = repository.getNoteAt(currentPage) ?: return
        val vaultName = prefs.getString("obsidian_vault_name", "") ?: return

        ObsidianLauncher.openNote(context, vaultName, note.obsidianUri)
    }

    private fun handleToggleCompact(context: Context, appWidgetId: Int) {
        val prefs = context.getSharedPreferences("obsidian_widget_prefs", Context.MODE_PRIVATE)
        val isCompact = prefs.getBoolean("is_compact_$appWidgetId", false)
        prefs.edit().putBoolean("is_compact_$appWidgetId", !isCompact).apply()

        val appWidgetManager = AppWidgetManager.getInstance(context)
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }

    private fun handleShowOverlay(context: Context, appWidgetId: Int) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val rv = RemoteViews(context.packageName, R.layout.widget_layout)
        rv.setViewVisibility(R.id.action_overlay, View.VISIBLE)
        appWidgetManager.partiallyUpdateAppWidget(appWidgetId, rv)

        // Schedule auto-dismiss via AlarmManager
        val dismissIntent = Intent(context, NoteWidgetProvider::class.java).apply {
            action = NoteGestureHandler.ACTION_HIDE_OVERLAY
            putExtra(NoteGestureHandler.EXTRA_WIDGET_ID, appWidgetId)
        }
        val pi = PendingIntent.getBroadcast(
            context, appWidgetId * 100 + 99, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.set(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + OVERLAY_DISMISS_DELAY,
            pi
        )
    }

    private fun handleHideOverlay(context: Context, appWidgetId: Int) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val rv = RemoteViews(context.packageName, R.layout.widget_layout)
        rv.setViewVisibility(R.id.action_overlay, View.GONE)
        appWidgetManager.partiallyUpdateAppWidget(appWidgetId, rv)
    }

    private fun handleAddNote(context: Context, appWidgetId: Int) {
        val intent = Intent(context, WidgetConfigActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra("mode", "add_note")
        }
        context.startActivity(intent)
    }

    private fun handleRemoveNote(context: Context, appWidgetId: Int) {
        val prefs = context.getSharedPreferences("obsidian_widget_prefs", Context.MODE_PRIVATE)
        val repository = NoteRepository(context)
        val currentPage = prefs.getInt("current_page_$appWidgetId", 0)
        val note = repository.getNoteAt(currentPage) ?: return

        repository.removeNote(note.id)

        val newCount = repository.getNoteCount()
        if (currentPage >= newCount && newCount > 0) {
            prefs.edit().putInt("current_page_$appWidgetId", newCount - 1).apply()
        }

        val appWidgetManager = AppWidgetManager.getInstance(context)
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }

    private fun handleOpenSettings(context: Context, appWidgetId: Int) {
        val intent = Intent(context, WidgetConfigActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra("mode", "settings")
        }
        context.startActivity(intent)
    }

    private fun handleItemClick(context: Context, intent: Intent, appWidgetId: Int) {
        val actionType = intent.getStringExtra("action_type") ?: return

        val prefs = context.getSharedPreferences("obsidian_widget_prefs", Context.MODE_PRIVATE)
        val repository = NoteRepository(context)
        
        if (actionType == "open_note") {
            val noteId = intent.getStringExtra("note_id") ?: return
            val note = repository.getNote(noteId) ?: return
            val vaultName = prefs.getString("obsidian_vault_name", "") ?: return
            ObsidianLauncher.openNote(context, vaultName, note.obsidianUri)
             
        } else if (actionType == "open_link") {
            val linkUrl = intent.getStringExtra("link_url") ?: return
            
            if (linkUrl.startsWith("wiki:")) {
                val pageName = linkUrl.substringAfter("wiki:")
                val vaultName = prefs.getString("obsidian_vault_name", "") ?: return
                val uri = "obsidian://open?vault=${Uri.encode(vaultName)}&file=${Uri.encode(pageName)}"
                val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                viewIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(viewIntent)
            } else {
                try {
                    val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(linkUrl))
                    viewIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(viewIntent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences("obsidian_widget_prefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        for (id in appWidgetIds) {
            editor.remove("current_page_$id")
            editor.remove("is_compact_$id")
        }
        editor.apply()
    }
}

private const val ACTION_ADD_NOTE = "com.obsidianwidget.ACTION_ADD_NOTE"
private const val ACTION_REMOVE_NOTE = "com.obsidianwidget.ACTION_REMOVE_NOTE"
private const val ACTION_REORDER = "com.obsidianwidget.ACTION_REORDER"
private const val ACTION_OPEN_SETTINGS = "com.obsidianwidget.ACTION_OPEN_SETTINGS"
private const val ACTION_ITEM_CLICK = "com.obsidianwidget.ACTION_ITEM_CLICK"
private const val ACTION_PREV_NOTE = "com.obsidianwidget.ACTION_PREV_NOTE"
private const val ACTION_NEXT_NOTE = "com.obsidianwidget.ACTION_NEXT_NOTE"
