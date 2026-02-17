package com.obsidianwidget

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Parser for Obsidian .obsidian folder settings and themes
 */
class ObsidianSettingsParser(private val context: Context) {

    data class ObsidianTheme(
        val baseColor: String = "#1e1e1e",
        val accentColor: String = "#7c3aed",
        val textColor: String = "#dcdcdc",
        val textMuted: String = "#999999",
        val fontFamily: String = "Inter",
        val cssSnippets: List<String> = emptyList()
    )

    data class ObsidianSettings(
        val theme: ObsidianTheme,
        val vaultName: String,
        val configFolder: String = ".obsidian"
    )

    /**
     * Parse Obsidian settings from the .obsidian folder
     */
    suspend fun parseSettings(vaultUri: Uri): ObsidianSettings? = withContext(Dispatchers.IO) {
        try {
            val vaultDoc = DocumentFile.fromTreeUri(context, vaultUri) ?: return@withContext null
            val obsidianFolder = vaultDoc.listFiles().find { it.name == ".obsidian" && it.isDirectory }
                ?: return@withContext null

            val theme = parseThemeSettings(obsidianFolder)
            val vaultName = vaultDoc.name ?: "Vault"

            ObsidianSettings(
                theme = theme,
                vaultName = vaultName
            )
        } catch (e: Exception) {
            Log.e("ObsidianSettingsParser", "Failed to parse settings", e)
            null
        }
    }

    private fun parseThemeSettings(obsidianFolder: DocumentFile): ObsidianTheme {
        var baseColor = "#1e1e1e"
        var accentColor = "#7c3aed"
        var textColor = "#dcdcdc"
        var textMuted = "#999999"
        var fontFamily = "Inter"
        val cssSnippets = mutableListOf<String>()

        // Parse appearance.json
        val appearanceFile = obsidianFolder.listFiles().find { it.name == "appearance.json" }
        if (appearanceFile != null) {
            try {
                context.contentResolver.openInputStream(appearanceFile.uri)?.use { stream ->
                    val json = JSONObject(stream.bufferedReader().readText())

                    // Get theme mode (dark/light)
                    val themeMode = json.optString("theme", "obsidian")

                    // Get accent color
                    accentColor = json.optString("accentColor", accentColor)

                    // Get base color scheme
                    val base = json.optString("base", "dark")
                    if (base == "light") {
                        baseColor = "#ffffff"
                        textColor = "#2e2e2e"
                        textMuted = "#666666"
                    }

                    // Get CSS snippets enabled
                    val snippets = json.optJSONArray("cssSnippets")
                    if (snippets != null) {
                        for (i in 0 until snippets.length()) {
                            cssSnippets.add(snippets.getString(i))
                        }
                    }

                    Log.d("ObsidianSettingsParser", "Parsed appearance: theme=$themeMode, accent=$accentColor")
                }
            } catch (e: Exception) {
                Log.e("ObsidianSettingsParser", "Failed to parse appearance.json", e)
            }
        }

        // Load CSS snippets if enabled
        val snippetsFolder = obsidianFolder.listFiles().find { it.name == "snippets" && it.isDirectory }
        if (snippetsFolder != null && cssSnippets.isNotEmpty()) {
            for (snippetName in cssSnippets) {
                val snippetFile = snippetsFolder.listFiles().find { it.name == "$snippetName.css" }
                if (snippetFile != null) {
                    try {
                        context.contentResolver.openInputStream(snippetFile.uri)?.use { stream ->
                            val css = stream.bufferedReader().readText()
                            // Extract colors from CSS if present
                            extractColorsFromCss(css)?.let { (base, accent, text) ->
                                baseColor = base
                                accentColor = accent
                                textColor = text
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ObsidianSettingsParser", "Failed to load snippet $snippetName", e)
                    }
                }
            }
        }

        // Check for community theme
        val themesFolder = obsidianFolder.listFiles().find { it.name == "themes" && it.isDirectory }
        if (themesFolder != null) {
            val activeTheme = themesFolder.listFiles().firstOrNull { it.isDirectory }
            if (activeTheme != null) {
                Log.d("ObsidianSettingsParser", "Found community theme: ${activeTheme.name}")
                // Could load theme.css here for more accurate colors
            }
        }

        return ObsidianTheme(
            baseColor = baseColor,
            accentColor = accentColor,
            textColor = textColor,
            textMuted = textMuted,
            fontFamily = fontFamily,
            cssSnippets = cssSnippets
        )
    }

    /**
     * Extract color values from CSS content
     */
    private fun extractColorsFromCss(css: String): Triple<String, String, String>? {
        // Simple regex to find color values - this is basic and could be improved
        val colorRegex = Regex("#(?:[0-9a-fA-F]{3}){1,2}")
        val colorList = mutableListOf<String>()
        colorRegex.findAll(css).forEach { colorList.add(it.value) }
        val colors = colorList.distinct()

        if (colors.size >= 3) {
            // Heuristic: first dark color is background, first bright is text, others are accents
            return Triple(colors[0], colors[1], colors[2])
        }
        return null
    }

    /**
     * Get the Obsidian URI for opening a note in the Obsidian app
     */
    fun getObsidianUri(vaultName: String, notePath: String): String {
        // Obsidian URI format: obsidian://open?vault=VaultName&file=path/to/note
        val encodedVault = Uri.encode(vaultName)
        val encodedFile = Uri.encode(notePath.removeSuffix(".md"))
        return "obsidian://open?vault=$encodedVault&file=$encodedFile"
    }
}
