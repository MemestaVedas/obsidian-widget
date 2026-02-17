package com.obsidianwidget

/**
 * Data class representing an Obsidian note stored in the widget.
 *
 * Each note captures a snapshot of the original Obsidian vault note,
 * including a truncated content preview for efficient widget rendering.
 *
 * @property id        Unique identifier for the note (typically derived from the vault path).
 * @property title     Display title of the note.
 * @property content   Truncated content preview (first 2 000 characters of the note body).
 * @property obsidianUri  Deep-link URI used to open the note in the Obsidian app
 *                        (e.g. `obsidian://open?vault=MyVault&file=Note`).
 * @property lastModified Epoch milliseconds of the last modification time.
 */
data class NoteModel(
    val id: String,
    val title: String,
    val content: String,
    val obsidianUri: String,
    val lastModified: Long
)
