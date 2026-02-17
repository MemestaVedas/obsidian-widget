package com.obsidianwidget

import android.graphics.Color

/**
 * Resolved theme tokens used to style the Obsidian widget.
 *
 * Default values mirror the Obsidian **dark** theme so the widget looks
 * consistent even when no custom theme has been applied.
 *
 * @property bgPrimary     Primary background color.
 * @property bgSecondary   Secondary / surface background color.
 * @property borderColor   Border and divider color.
 * @property textNormal    Default body-text color.
 * @property textMuted     De-emphasised text color (e.g. timestamps).
 * @property textFaint     Very subtle text color (e.g. placeholders).
 * @property accent        Accent / brand color used for interactive elements.
 * @property accentHover   Accent color variant for hover / pressed states.
 * @property textOnAccent  Text color rendered on top of accent backgrounds.
 * @property fontText      Font family name for body text (`"default"` = system font).
 * @property fontInterface Font family name for UI chrome (`"default"` = system font).
 * @property baseFontSize  Base font size in **sp**, clamped to the range 11–16.
 */
data class ThemeTokens(
    val bgPrimary: Int = Color.parseColor("#1e1e1e"),
    val bgSecondary: Int = Color.parseColor("#252525"),
    val borderColor: Int = Color.parseColor("#3d3d3d"),
    val textNormal: Int = Color.parseColor("#dcddde"),
    val textMuted: Int = Color.parseColor("#999999"),
    val textFaint: Int = Color.parseColor("#666666"),
    val accent: Int = Color.parseColor("#7C3AED"),
    val accentHover: Int = Color.parseColor("#6D28D9"),
    val textOnAccent: Int = Color.parseColor("#FFFFFF"),
    val fontText: String = "default",
    val fontInterface: String = "default",
    val baseFontSize: Int = 13
) {
    companion object {
        /** Returns a [ThemeTokens] instance with all Obsidian dark-theme defaults. */
        fun default(): ThemeTokens = ThemeTokens()
    }
}
