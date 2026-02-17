package com.obsidianwidget

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class NoteRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("obsidian_widget_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_NOTES = "obsidian_notes_json"
        private const val MAX_NOTES = 20
        private const val MAX_CONTENT_LENGTH = 2000
    }

    fun getAllNotes(): List<NoteModel> {
        val json = prefs.getString(KEY_NOTES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<NoteModel>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Throws(WidgetNoteCapacityException::class)
    fun addNote(note: NoteModel) {
        val notes = getAllNotes().toMutableList()
        if (notes.size >= MAX_NOTES) {
            throw WidgetNoteCapacityException()
        }
        // Ensure content is capped at 2000 chars
        val cappedNote = note.copy(
            content = note.content.take(MAX_CONTENT_LENGTH)
        )
        notes.add(cappedNote)
        saveNotes(notes)
    }

    fun removeNote(id: String) {
        val notes = getAllNotes().toMutableList()
        notes.removeAll { it.id == id }
        saveNotes(notes)
    }

    fun reorderNotes(fromIndex: Int, toIndex: Int) {
        val notes = getAllNotes().toMutableList()
        if (fromIndex < 0 || fromIndex >= notes.size ||
            toIndex < 0 || toIndex >= notes.size) return

        val note = notes.removeAt(fromIndex)
        notes.add(toIndex, note)
        saveNotes(notes)
    }

    fun getNoteAt(index: Int): NoteModel? {
        val notes = getAllNotes()
        return if (index in notes.indices) notes[index] else null
    }

    fun getNote(id: String): NoteModel? {
        return getAllNotes().find { it.id == id }
    }

    fun getNoteCount(): Int = getAllNotes().size

    fun updateNoteContent(id: String, content: String) {
        val notes = getAllNotes().toMutableList()
        val index = notes.indexOfFirst { it.id == id }
        if (index != -1) {
            notes[index] = notes[index].copy(content = content)
            saveNotes(notes)
        }
    }

    fun clearAllNotes() {
        saveNotes(emptyList())
    }

    private fun saveNotes(notes: List<NoteModel>) {
        prefs.edit()
            .putString(KEY_NOTES, gson.toJson(notes))
            .apply()
    }
}
