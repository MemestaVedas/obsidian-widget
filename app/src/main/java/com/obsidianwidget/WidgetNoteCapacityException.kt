package com.obsidianwidget

/**
 * Thrown when an attempt is made to add more notes to a single widget
 * instance than the allowed maximum (20 notes).
 *
 * This hard cap exists to keep widget storage and rendering performant
 * on the Android home screen.
 *
 * @param message Human-readable description of the error.
 */
class WidgetNoteCapacityException(
    message: String = "Maximum of 20 notes per widget instance exceeded"
) : Exception(message)
