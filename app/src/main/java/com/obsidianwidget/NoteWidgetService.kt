package com.obsidianwidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.ContextCompat

class NoteWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return NoteContentFactory(applicationContext, intent)
    }
}

/**
 * Serves segments of the CURRENT note as ListView items.
 * Segments can be:
 * - Text blocks (paragraphs, headers, lists, code blocks)
 * - Interactive Checkboxes (lines starting with "- [ ]" or "- [x]")
 */
class NoteContentFactory(
    private val context: Context,
    private val intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private val appWidgetId: Int = intent.getIntExtra(
        AppWidgetManager.EXTRA_APPWIDGET_ID,
        AppWidgetManager.INVALID_APPWIDGET_ID
    )

    // Sealed class to represent different types of rows
    sealed class WidgetItem {
        data class Text(val content: CharSequence) : WidgetItem()
        data class Checkbox(
            val text: CharSequence,
            val isChecked: Boolean,
            val originalLine: String,
            val lineIndex: Int
        ) : WidgetItem()
    }

    private var items: List<WidgetItem> = emptyList()
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
            items = emptyList()
            currentNoteId = ""
            return
        }

        val safeIndex = currentPage.coerceIn(0, notes.size - 1)
        val note = notes[safeIndex]
        currentNoteId = note.id

        // Render setup
        val colorPrimary = ContextCompat.getColor(context, R.color.app_accent)
        val colorTextMain = ContextCompat.getColor(context, R.color.app_text_primary)
        val colorTextMuted = ContextCompat.getColor(context, R.color.app_text_muted)
        val colorCardBg = ContextCompat.getColor(context, R.color.app_surface)

        val renderer = MarkdownRenderer(context)
        val options = MarkdownRenderer.RenderOptions(
            baseTextColor = colorTextMain,
            accentColor = colorPrimary,
            mutedTextColor = colorTextMuted,
            backgroundColor = colorCardBg,
            maxLines = 2000,
            truncateLength = 50000
        )

        val newItems = mutableListOf<WidgetItem>()
        val textBuffer = StringBuilder()

        // Split content by lines to identify checkboxes
        val lines = note.content.lines()

        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trimStart()
            
            // Check usage of regex for checkbox detection: "- [ ] " or "- [x] "
            // We use simple string checks for performance
            val isUnchecked = trimmed.startsWith("- [ ]")
            val isChecked = trimmed.startsWith("- [x]")

            if (isUnchecked || isChecked) {
                // If we have accumulated text, render and add it first
                if (textBuffer.isNotEmpty()) {
                    // Remove trailing newline
                    if (textBuffer.endsWith("\n")) textBuffer.setLength(textBuffer.length - 1)
                    
                    val renderedText = renderer.render(textBuffer.toString(), options)
                    if (renderedText.isNotEmpty()) {
                        newItems.add(WidgetItem.Text(renderedText))
                    }
                    textBuffer.clear()
                }

                // Add the checkbox item
                val checkboxContent = line.substringAfter("]").trim() // Strip "- [ ]"
                // Render the checkbox text with formatting support
                val renderedLabel = renderer.renderLine(checkboxContent, options)
                
                newItems.add(WidgetItem.Checkbox(
                    text = renderedLabel,
                    isChecked = isChecked,
                    originalLine = line, // Keep original line for identification/updating
                    lineIndex = index
                ))
            } else {
                // Regular line, append to buffer
                textBuffer.append(line).append("\n")
            }
        }

        // Add remaining text buffer
        if (textBuffer.isNotEmpty()) {
            if (textBuffer.endsWith("\n")) textBuffer.setLength(textBuffer.length - 1)
            val renderedText = renderer.render(textBuffer.toString(), options)
            if (renderedText.isNotEmpty()) {
                newItems.add(WidgetItem.Text(renderedText))
            }
        }

        items = newItems
    }

    override fun onDestroy() {
        items = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewTypeCount(): Int = 2

    override fun getViewAt(position: Int): RemoteViews {
        if (position >= items.size) return RemoteViews(context.packageName, R.layout.item_widget_text)

        val item = items[position]
        return when (item) {
            is WidgetItem.Text -> {
                val rv = RemoteViews(context.packageName, R.layout.item_widget_text)
                rv.setTextViewText(R.id.item_text, item.content)
                
                // Clicking text opens the note in Obsidian
                val fillInIntent = Intent().apply {
                    putExtra("action_type", "open_note")
                    putExtra("note_id", currentNoteId)
                }
                rv.setOnClickFillInIntent(R.id.widget_item_container, fillInIntent)
                rv
            }
            is WidgetItem.Checkbox -> {
                val rv = RemoteViews(context.packageName, R.layout.item_widget_checkbox)
                rv.setTextViewText(R.id.tv_checkbox_text, item.text)
                
                // Set icon based on state
                val iconRes = if (item.isChecked) R.drawable.ic_check_box else R.drawable.ic_check_box_outline_blank
                val tintColor = if (item.isChecked) R.color.app_text_muted else R.color.app_text_secondary
                
                rv.setImageViewResource(R.id.iv_checkbox, iconRes)
                rv.setInt(R.id.iv_checkbox, "setColorFilter", ContextCompat.getColor(context, tintColor))
                
                // Strike-through if checked? Maybe let the user decide. Usually checked = strike.
                // The MarkdownRenderer.renderLine handles formatting, but strikethrough logic belongs there.
                // For simplicity, let's trust renderLine, but maybe add opacity for checked items?
                if (item.isChecked) {
                    rv.setInt(R.id.tv_checkbox_text, "setAlpha", 150) // dim it
                } else {
                    rv.setInt(R.id.tv_checkbox_text, "setAlpha", 255)
                }

                // Click on CHECKBOX icon toggles state
                val toggleIntent = Intent().apply {
                    putExtra("action_type", "toggle_checkbox")
                    putExtra("note_id", currentNoteId)
                    putExtra("line_index", item.lineIndex)
                    putExtra("is_checked", item.isChecked)
                }
                rv.setOnClickFillInIntent(R.id.iv_checkbox, toggleIntent)
                
                // Click on TEXT opens note (or toggles? Obsidian toggles on text click too usually. Let's stick to open note for text for now to avoid accidental toggles when scrolling)
                val openIntent = Intent().apply {
                    putExtra("action_type", "open_note")
                    putExtra("note_id", currentNoteId)
                }
                rv.setOnClickFillInIntent(R.id.tv_checkbox_text, openIntent)
                
                rv
            }
        }
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false
}
