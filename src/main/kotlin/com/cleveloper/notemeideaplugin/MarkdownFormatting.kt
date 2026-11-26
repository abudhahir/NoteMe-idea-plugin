package com.cleveloper.notemeideaplugin

import androidx.compose.ui.text.TextRange

/**
 * Markdown formatting utilities for inserting syntax markers at cursor/selection.
 */
object MarkdownFormatting {

    /**
     * Applies bold formatting around selected text or at cursor position.
     * If text is selected, wraps it with **. Otherwise inserts ** **
     */
    fun insertBold(text: String, selection: TextRange): Pair<String, Int> {
        return applyInline(text, selection, "**")
    }

    /**
     * Applies italic formatting around selected text or at cursor position.
     * If text is selected, wraps it with *. Otherwise inserts * *
     */
    fun insertItalic(text: String, selection: TextRange): Pair<String, Int> {
        return applyInline(text, selection, "*")
    }

    /**
     * Inserts a heading at the current line.
     * Moves to start of line, adds heading marker
     */
    fun insertHeading(text: String, selection: TextRange, level: Int): Pair<String, Int> {
        val result = MarkdownTransformations.linePrefix(
            MarkdownCommandContext(text, selection.start, selection.start, selection.start),
            "#".repeat(level) + " "
        )
        return result.applyTo(text) to result.caretOffset
    }

    /**
     * Inserts an unordered list item at current line
     */
    fun insertBulletList(text: String, selection: TextRange): Pair<String, Int> {
        val result = MarkdownTransformations.linePrefix(
            MarkdownCommandContext(text, selection.start, selection.start, selection.start),
            "- "
        )
        return result.applyTo(text) to result.caretOffset
    }

    /**
     * Inserts an ordered list item at current line
     */
    fun insertOrderedList(text: String, selection: TextRange): Pair<String, Int> {
        val result = MarkdownTransformations.linePrefix(
            MarkdownCommandContext(text, selection.start, selection.start, selection.start),
            "1. "
        )
        return result.applyTo(text) to result.caretOffset
    }

    /**
     * Inserts inline code formatting
     */
    fun insertCode(text: String, selection: TextRange): Pair<String, Int> {
        return applyInline(text, selection, "`")
    }

    /**
     * Inserts a code block
     */
    fun insertCodeBlock(text: String, selection: TextRange): Pair<String, Int> {
        val result = MarkdownTransformations.codeBlock(
            MarkdownCommandContext(text, selection.start, selection.start, selection.start)
        )
        return result.applyTo(text) to result.caretOffset
    }

    /**
     * Inserts a link template [text](url)
     */
    fun insertLink(text: String, selection: TextRange): Pair<String, Int> {
        val result = MarkdownTransformations.link(
            MarkdownCommandContext(
                text = text,
                queryStart = selection.start,
                caretOffset = selection.start,
                selectionStart = selection.start,
                selectionEnd = selection.end
            )
        )
        return result.applyTo(text) to result.caretOffset
    }

    private fun applyInline(text: String, selection: TextRange, delimiter: String): Pair<String, Int> {
        val result = MarkdownTransformations.paired(
            MarkdownCommandContext(
                text = text,
                queryStart = selection.start,
                caretOffset = selection.start,
                selectionStart = selection.start,
                selectionEnd = selection.end
            ),
            delimiter
        )
        return result.applyTo(text) to result.caretOffset
    }
}
