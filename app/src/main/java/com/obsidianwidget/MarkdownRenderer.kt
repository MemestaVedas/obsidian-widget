package com.obsidianwidget

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.*
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.regex.Pattern

/**
 * Markdown renderer that converts Obsidian markdown to styled Spannable text
 * Optimized for widget display (simplified, limited formatting)
 */
class MarkdownRenderer(private val context: Context) {

    data class RenderOptions(
        val baseTextColor: Int,
        val accentColor: Int,
        val mutedTextColor: Int,
        val backgroundColor: Int,
        val maxLines: Int = 50,
        val truncateLength: Int = 2000
    )

    /**
     * Render markdown content to styled Spannable text
     */
    fun render(content: String, options: RenderOptions): SpannableStringBuilder {
        val builder = SpannableStringBuilder()
        val lines = content.lines().take(options.maxLines)

        var inCodeBlock = false
        var inQuote = false

        for ((index, line) in lines.withIndex()) {
            when {
                line.startsWith("```") -> {
                    inCodeBlock = !inCodeBlock
                    if (!inCodeBlock) {
                        builder.append("\n")
                    }
                }
                inCodeBlock -> {
                    // Code block content - use monospace and muted color
                    val start = builder.length
                    builder.append(line)
                    builder.setSpan(
                        TypefaceSpan("monospace"),
                        start, builder.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    builder.setSpan(
                        ForegroundColorSpan(options.mutedTextColor),
                        start, builder.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    builder.append("\n")
                }
                line.startsWith("> ") -> {
                    // Blockquote
                    inQuote = true
                    val start = builder.length
                    val quoteText = line.substring(2)
                    builder.append("▍ $quoteText") // Left bar for quote
                    builder.setSpan(
                        ForegroundColorSpan(options.mutedTextColor),
                        start, builder.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    builder.setSpan(
                        StyleSpan(Typeface.ITALIC),
                        start, builder.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    builder.append("\n")
                }
                line.startsWith("# ") -> {
                    // H1
                    val start = builder.length
                    builder.append(line.substring(2))
                    builder.setSpan(
                        StyleSpan(Typeface.BOLD),
                        start, builder.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    builder.setSpan(
                        RelativeSizeSpan(1.5f),
                        start, builder.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    builder.setSpan(
                        ForegroundColorSpan(options.accentColor),
                        start, builder.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    builder.append("\n\n")
                }
                line.startsWith("## ") -> {
                    // H2
                    val start = builder.length
                    builder.append(line.substring(3))
                    builder.setSpan(
                        StyleSpan(Typeface.BOLD),
                        start, builder.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    builder.setSpan(
                        RelativeSizeSpan(1.3f),
                        start, builder.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    builder.append("\n\n")
                }
                line.startsWith("### ") -> {
                    // H3
                    val start = builder.length
                    builder.append(line.substring(4))
                    builder.setSpan(
                        StyleSpan(Typeface.BOLD),
                        start, builder.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    builder.setSpan(
                        RelativeSizeSpan(1.1f),
                        start, builder.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    builder.append("\n\n")
                }
                line.startsWith("- [ ]") -> {
                    val start = builder.length
                    val itemText = line.substring(5).trim()
                    builder.append("☐ ${renderInlineFormatting(itemText, options)}")
                    builder.append("\n")
                }
                line.startsWith("- [x]") -> {
                    val start = builder.length
                    val itemText = line.substring(5).trim()
                    builder.append("☑ ${renderInlineFormatting(itemText, options)}")
                    builder.setSpan(StrikethroughSpan(), start, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    builder.setSpan(ForegroundColorSpan(options.mutedTextColor), start, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    builder.append("\n")
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    // Bullet list
                    val start = builder.length
                    val itemText = line.substring(2)
                    builder.append("• ${renderInlineFormatting(itemText, options)}")
                    builder.append("\n")
                }
                line.startsWith("1. ") || line.matches(Regex("^\\d+\\. ")) -> {
                    // Numbered list
                    val start = builder.length
                    val match = Regex("^(\\d+)\\. (.*)$").find(line)
                    if (match != null) {
                        val (number, text) = match.destructured
                        builder.append("$number. ${renderInlineFormatting(text, options)}")
                    }
                    builder.append("\n")
                }
                line.startsWith("---") || line.startsWith("***") -> {
                    // Horizontal rule
                    builder.append("\n────────────\n")
                }
                line.isBlank() -> {
                    builder.append("\n")
                }
                else -> {
                    // Regular paragraph with inline formatting
                    val start = builder.length
                    builder.append(renderInlineFormatting(line, options))
                    builder.append("\n")
                }
            }
        }

        // Truncate if too long
        if (builder.length > options.truncateLength) {
            builder.delete(options.truncateLength, builder.length)
            builder.append("...")
        }

        return builder
    }

    /**
     * Render a single line of markdown, optimized for ListView items.
     * Prevents adding document-level spacing.
     */
    fun renderLine(line: String, options: RenderOptions): CharSequence {
        val builder = SpannableStringBuilder()
        
        when {
            line.startsWith("# ") -> {
                val start = builder.length
                builder.append(line.substring(2))
                builder.setSpan(StyleSpan(Typeface.BOLD), start, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(RelativeSizeSpan(1.25f), start, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(ForegroundColorSpan(options.accentColor), start, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            line.startsWith("## ") -> {
                val start = builder.length
                builder.append(line.substring(3))
                builder.setSpan(StyleSpan(Typeface.BOLD), start, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(RelativeSizeSpan(1.15f), start, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            line.startsWith("### ") -> {
                val start = builder.length
                builder.append(line.substring(4))
                builder.setSpan(StyleSpan(Typeface.BOLD), start, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(RelativeSizeSpan(1.05f), start, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            else -> {
                builder.append(renderInlineFormatting(line, options))
            }
        }
        return builder
    }

    /**
     * Render inline markdown formatting (bold, italic, links, code, etc.)
     */
    private fun renderInlineFormatting(text: String, options: RenderOptions): SpannableStringBuilder {
        val builder = SpannableStringBuilder(text)

        // Bold: **text** or __text__
        applySimpleSpan(builder, "\\*\\*(.+?)\\*\\*", { StyleSpan(Typeface.BOLD) })
        applySimpleSpan(builder, "__(.+?)__", { StyleSpan(Typeface.BOLD) })

        // Italic: *text* or _text_
        applySimpleSpan(builder, "\\*(.+?)\\*", { StyleSpan(Typeface.ITALIC) })
        applySimpleSpan(builder, "_(.+?)_", { StyleSpan(Typeface.ITALIC) })

        // Strikethrough: ~~text~~
        applySimpleSpan(builder, "~~(.+?)~~", { StrikethroughSpan() })

        // Inline code: `code`
        applySimpleSpan(builder, "`(.+?)`", 
            { TypefaceSpan("monospace") },
            { BackgroundColorSpan(options.backgroundColor) },
            { ForegroundColorSpan(options.accentColor) }
        )

        // Wikilinks: [[Note Name]] -> Note Name (accent color)
        applySpanPattern(builder, "\\[\\[(.+?)\\]\\]",
            { it.split("|").firstOrNull() ?: it },
            { ForegroundColorSpan(options.accentColor) },
            { UnderlineSpan() }
        )

        // External links: [text](url) -> text (underlined, accent color)
        applySpanPattern(builder, "\\[(.+?)\\]\\((.+?)\\)",
            { it },
            { ForegroundColorSpan(options.accentColor) },
            { UnderlineSpan() }
        )

        // Highlight: ==text== -> highlighted background
        applySimpleSpan(builder, "==(.+?)==",
            { BackgroundColorSpan(options.accentColor and 0x40FFFFFF) }
        )

        return builder
    }

    private fun applySimpleSpan(
        builder: SpannableStringBuilder,
        pattern: String,
        vararg spans: () -> Any
    ) {
        applySpanPattern(builder, pattern, { it }, *spans)
    }

    private fun applySpanPattern(
        builder: SpannableStringBuilder,
        pattern: String,
        contentTransformer: (String) -> String,
        vararg spans: () -> Any
    ) {
        val regex = Pattern.compile(pattern)
        var offset = 0

        while (true) {
            val matcher = regex.matcher(builder.toString())
            if (!matcher.find(offset)) break

            val fullMatchStart = matcher.start()
            val fullMatchEnd = matcher.end()
            val content = matcher.group(1) ?: continue
            val transformedContent = contentTransformer(content)

            // Replace full match with transformed content
            builder.replace(fullMatchStart, fullMatchEnd, transformedContent)
            
            // Apply all spans to the new range
            for (spanProvider in spans) {
                builder.setSpan(
                    spanProvider(),
                    fullMatchStart,
                    fullMatchStart + transformedContent.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            // Move offset forward to avoid infinite loop
            offset = fullMatchStart + transformedContent.length
        }
    }

    companion object {
        private val codeMatcher = Pattern.compile("`(.+?)`")
    }
}
