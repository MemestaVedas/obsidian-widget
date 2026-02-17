package com.obsidianwidget

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.FileObserver
import android.widget.RemoteViews
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ObsidianThemeEngine(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("obsidian_widget_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private var fileObserver: FileObserver? = null

    companion object {
        private const val KEY_THEME_TOKENS = "theme_tokens_json"
        private const val KEY_VAULT_PATH = "obsidian_vault_path"

        private val CSS_VAR_REGEX = Regex("""--([\w-]+)\s*:\s*([^;]+);""")

        private val VARIABLE_MAP = mapOf(
            "background-primary" to "bgPrimary",
            "background-secondary" to "bgSecondary",
            "background-modifier-border" to "borderColor",
            "text-normal" to "textNormal",
            "text-muted" to "textMuted",
            "text-faint" to "textFaint",
            "interactive-accent" to "accent",
            "interactive-accent-hover" to "accentHover",
            "text-on-accent" to "textOnAccent",
            "font-text-size" to "baseFontSize",
            "font-interface" to "fontInterface",
            "font-text" to "fontText"
        )
    }

    fun getThemeTokens(): ThemeTokens {
        val json = prefs.getString(KEY_THEME_TOKENS, null) ?: return ThemeTokens.default()
        return try {
            gson.fromJson(json, ThemeTokens::class.java)
        } catch (e: Exception) {
            ThemeTokens.default()
        }
    }

    suspend fun parseAndCacheTheme(): ThemeTokens = withContext(Dispatchers.IO) {
        val vaultPath = prefs.getString(KEY_VAULT_PATH, null)
            ?: return@withContext ThemeTokens.default()

        val appearanceFile = File(vaultPath, ".obsidian/appearance.json")
        if (!appearanceFile.exists()) return@withContext ThemeTokens.default()

        try {
            val appearanceJson = appearanceFile.readText()
            val appearance = gson.fromJson(appearanceJson, AppearanceConfig::class.java)

            val colorScheme = appearance.colorScheme ?: "dark"
            val accentColor = appearance.accentColor
            val themeName = appearance.cssTheme

            var tokens = ThemeTokens.default()

            // Apply accent color override from appearance.json
            if (!accentColor.isNullOrBlank()) {
                try {
                    val parsedAccent = Color.parseColor(accentColor)
                    tokens = tokens.copy(accent = parsedAccent)
                } catch (_: IllegalArgumentException) { }
            }

            // Parse theme.css if a custom theme is active
            if (!themeName.isNullOrBlank()) {
                val themeFile = File(vaultPath, ".obsidian/themes/$themeName/theme.css")
                if (themeFile.exists()) {
                    tokens = parseThemeCss(themeFile, colorScheme, tokens)
                }
            }

            // Clamp font size
            tokens = tokens.copy(
                baseFontSize = tokens.baseFontSize.coerceIn(11, 16)
            )

            // Persist to SharedPreferences
            prefs.edit()
                .putString(KEY_THEME_TOKENS, gson.toJson(tokens))
                .apply()

            tokens
        } catch (e: Exception) {
            ThemeTokens.default()
        }
    }

    private fun parseThemeCss(
        themeFile: File,
        colorScheme: String,
        baseTokens: ThemeTokens
    ): ThemeTokens {
        val css = themeFile.readText()
        val targetBlocks = mutableListOf<String>()

        // Extract :root block
        extractCssBlock(css, ":root")?.let { targetBlocks.add(it) }

        // Extract scheme-specific block
        val schemeSelector = if (colorScheme == "light") ".theme-light" else ".theme-dark"
        extractCssBlock(css, schemeSelector)?.let { targetBlocks.add(it) }

        if (targetBlocks.isEmpty()) return baseTokens

        val resolvedVars = mutableMapOf<String, String>()
        for (block in targetBlocks) {
            for (match in CSS_VAR_REGEX.findAll(block)) {
                val varName = match.groupValues[1]
                val varValue = match.groupValues[2].trim()
                if (varName in VARIABLE_MAP) {
                    resolvedVars[varName] = varValue
                }
            }
        }

        var tokens = baseTokens

        resolvedVars["background-primary"]?.let { tokens = tokens.copy(bgPrimary = parseColorSafe(it, tokens.bgPrimary)) }
        resolvedVars["background-secondary"]?.let { tokens = tokens.copy(bgSecondary = parseColorSafe(it, tokens.bgSecondary)) }
        resolvedVars["background-modifier-border"]?.let { tokens = tokens.copy(borderColor = parseColorSafe(it, tokens.borderColor)) }
        resolvedVars["text-normal"]?.let { tokens = tokens.copy(textNormal = parseColorSafe(it, tokens.textNormal)) }
        resolvedVars["text-muted"]?.let { tokens = tokens.copy(textMuted = parseColorSafe(it, tokens.textMuted)) }
        resolvedVars["text-faint"]?.let { tokens = tokens.copy(textFaint = parseColorSafe(it, tokens.textFaint)) }
        resolvedVars["interactive-accent"]?.let { tokens = tokens.copy(accent = parseColorSafe(it, tokens.accent)) }
        resolvedVars["interactive-accent-hover"]?.let { tokens = tokens.copy(accentHover = parseColorSafe(it, tokens.accentHover)) }
        resolvedVars["text-on-accent"]?.let { tokens = tokens.copy(textOnAccent = parseColorSafe(it, tokens.textOnAccent)) }
        resolvedVars["font-text"]?.let { tokens = tokens.copy(fontText = it.trim('"', '\'')) }
        resolvedVars["font-interface"]?.let { tokens = tokens.copy(fontInterface = it.trim('"', '\'')) }
        resolvedVars["font-text-size"]?.let {
            val size = it.replace("px", "").replace("sp", "").trim().toIntOrNull()
            if (size != null) tokens = tokens.copy(baseFontSize = size)
        }

        return tokens
    }

    private fun extractCssBlock(css: String, selector: String): String? {
        val escapedSelector = Regex.escape(selector)
        val blockRegex = Regex("""$escapedSelector\s*\{([^}]*)}""")
        return blockRegex.find(css)?.groupValues?.get(1)
    }

    private fun parseColorSafe(colorStr: String, fallback: Int): Int {
        return try {
            val trimmed = colorStr.trim()
            when {
                trimmed.startsWith("#") -> Color.parseColor(trimmed)
                trimmed.startsWith("rgb") -> parseRgbColor(trimmed) ?: fallback
                trimmed.startsWith("hsl") -> fallback // HSL not supported, use fallback
                else -> Color.parseColor(trimmed)
            }
        } catch (_: Exception) {
            fallback
        }
    }

    private fun parseRgbColor(rgb: String): Int? {
        val regex = Regex("""rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)""")
        val match = regex.find(rgb) ?: return null
        val r = match.groupValues[1].toIntOrNull() ?: return null
        val g = match.groupValues[2].toIntOrNull() ?: return null
        val b = match.groupValues[3].toIntOrNull() ?: return null
        return Color.rgb(r, g, b)
    }

    fun applyToRemoteViews(rv: RemoteViews, tokens: ThemeTokens) {
        rv.setInt(R.id.widget_root, "setBackgroundColor", tokens.bgPrimary)
        rv.setInt(R.id.action_overlay, "setBackgroundColor",
            Color.argb(180, Color.red(tokens.bgSecondary), Color.green(tokens.bgSecondary), Color.blue(tokens.bgSecondary)))
    }

    fun startWatchingAppearance(onThemeChanged: () -> Unit) {
        val vaultPath = prefs.getString(KEY_VAULT_PATH, null) ?: return
        val obsidianDir = File(vaultPath, ".obsidian")
        if (!obsidianDir.exists()) return

        stopWatching()

        fileObserver = object : FileObserver(obsidianDir.path, MODIFY or CREATE) {
            override fun onEvent(event: Int, path: String?) {
                if (path == "appearance.json") {
                    onThemeChanged()
                }
            }
        }
        fileObserver?.startWatching()
    }

    fun stopWatching() {
        fileObserver?.stopWatching()
        fileObserver = null
    }

    private data class AppearanceConfig(
        val colorScheme: String? = null,
        val accentColor: String? = null,
        val cssTheme: String? = null
    )
}
