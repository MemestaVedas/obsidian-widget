package com.obsidianwidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.ContextCompat

class NoteWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return NoteContentFactory(applicationContext, intent)
    }
}

/**
 * Serves paragraphs of the CURRENT note as ListView items.
 * The current note is determined by "current_page" in SharedPreferences.
 */
class NoteContentFactory(
    private val context: Context,
    private val intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private val appWidgetId: Int = intent.getIntExtra(
        AppWidgetManager.EXTRA_APPWIDGET_ID,
        AppWidgetManager.INVALID_APPWIDGET_ID
    )

    private var paragraphs: List<CharSequence> = emptyList()
    private var currentNoteId: String = ""

    override fun onCreate() {
        loadData()
    }

    override fun onDataSetChanged() {
        loadData()
    }

    private fun loadData() {
        val prefs = context.getSharedPreferences("obsidian_widget_prefs", Context.MODE_PRIVATE)
        val currentPage = prefs.getInt("current_page_$appWidgetId", 0)

        val repository = NoteRepository(context)
        val notes = repository.getAllNotes()
        if (notes.isEmpty()) {
            paragraphs = emptyList()
            currentNoteId = ""
            return
        }

        val safeIndex = currentPage.coerceIn(0, notes.size - 1)
        val note = notes[safeIndex]
        currentNoteId = note.id

        // Render the full content using MarkdownRenderer
        val colorPrimary = ContextCompat.getColor(context, R.color.max_primary)
        val colorTextMain = ContextCompat.getColor(context, R.color.max_text_main)
        val colorTextMuted = ContextCompat.getColor(context, R.color.max_text_muted)
        val colorCardBg = ContextCompat.getColor(context, R.color.max_obsidian_card)

        val renderer = MarkdownRenderer(context)
        val options = MarkdownRenderer.RenderOptions(
            baseTextColor = colorTextMain,
            accentColor = colorPrimary,
            mutedTextColor = colorTextMuted,
            backgroundColor = colorCardBg
        )

        val rendered = renderer.render(note.content, options)

        // Split into paragraphs by double newline for scrollable chunks
        // If no double newlines, split by single newline
        val rawText = rendered.toString()
        val splitPoints = mutableListOf<Int>()

        // Find paragraph break positions (double newline)
        var i = 0
        while (i < rawText.length - 1) {
            if (rawText[i] == '\n' && rawText[i + 1] == '\n') {
                splitPoints.add(i)
                i += 2
            } else {
                i++
            }
        }

        if (splitPoints.isEmpty()) {
            // No paragraph breaks, just show as one item
            paragraphs = listOf(rendered)
        } else {
            val result = mutableListOf<CharSequence>()
            var lastEnd = 0
            for (splitPoint in splitPoints) {
                if (splitPoint > lastEnd) {
                    val chunk = rendered.subSequence(lastEnd, splitPoint)
                    if (chunk.toString().isNotBlank()) {
                        result.add(chunk)
                    }
                }
                lastEnd = splitPoint + 2 // skip the two newlines
            }
            // Add remaining
            if (lastEnd < rendered.length) {
                val chunk = rendered.subSequence(lastEnd, rendered.length)
                if (chunk.toString().isNotBlank()) {
                    result.add(chunk)
                }
            }
            paragraphs = if (result.isEmpty()) listOf(rendered) else result
        }
    }

    override fun onDestroy() {
        paragraphs = emptyList()
    }

    override fun getCount(): Int = paragraphs.size

    override fun getViewAt(position: Int): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.item_widget_text)

        if (position < paragraphs.size) {
            rv.setTextViewText(R.id.item_text, paragraphs[position])
        }

        // Fill-in intent for clicking any paragraph -> opens the note in Obsidian
        val fillInIntent = Intent().apply {
            putExtra("action_type", "open_note")
            putExtra("note_id", currentNoteId)
        }
        rv.setOnClickFillInIntent(R.id.widget_item_container, fillInIntent)

        return rv
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false
}
