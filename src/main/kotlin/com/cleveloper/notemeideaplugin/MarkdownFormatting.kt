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
        return if (selection.length > 0) {
            val start = selection.min
            val end = selection.max
            val selectedText = text.substring(start, end)
            val newText = text.substring(0, start) + "**$selectedText**" + text.substring(end)
            val newCursor = end + 4
            newText to newCursor
        } else {
            val cursor = selection.start
            val newText = text.substring(0, cursor) + "****" + text.substring(cursor)
            newText to (cursor + 2)
        }
    }

    /**
     * Applies italic formatting around selected text or at cursor position.
     * If text is selected, wraps it with *. Otherwise inserts * *
     */
    fun insertItalic(text: String, selection: TextRange): Pair<String, Int> {
        return if (selection.length > 0) {
            val start = selection.min
            val end = selection.max
            val selectedText = text.substring(start, end)
            val newText = text.substring(0, start) + "*$selectedText*" + text.substring(end)
            val newCursor = end + 2
            newText to newCursor
        } else {
            val cursor = selection.start
            val newText = text.substring(0, cursor) + "**" + text.substring(cursor)
            newText to (cursor + 1)
        }
    }

    /**
     * Inserts a heading at the current line.
     * Moves to start of line, adds heading marker
     */
    fun insertHeading(text: String, selection: TextRange, level: Int): Pair<String, Int> {
        val cursor = selection.start
        val lineStart = text.lastIndexOf('\n', cursor - 1) + 1
        val marker = "#".repeat(level) + " "
        val newText = text.substring(0, lineStart) + marker + text.substring(lineStart)
        return newText to (cursor + marker.length)
    }

    /**
     * Inserts an unordered list item at current line
     */
    fun insertBulletList(text: String, selection: TextRange): Pair<String, Int> {
        val cursor = selection.start
        val lineStart = text.lastIndexOf('\n', cursor - 1) + 1
        val marker = "- "
        val newText = text.substring(0, lineStart) + marker + text.substring(lineStart)
        return newText to (cursor + marker.length)
    }

    /**
     * Inserts an ordered list item at current line
     */
    fun insertOrderedList(text: String, selection: TextRange): Pair<String, Int> {
        val cursor = selection.start
        val lineStart = text.lastIndexOf('\n', cursor - 1) + 1
        val marker = "1. "
        val newText = text.substring(0, lineStart) + marker + text.substring(lineStart)
        return newText to (cursor + marker.length)
    }

    /**
     * Inserts inline code formatting
     */
    fun insertCode(text: String, selection: TextRange): Pair<String, Int> {
        return if (selection.length > 0) {
            val start = selection.min
            val end = selection.max
            val selectedText = text.substring(start, end)
            val newText = text.substring(0, start) + "`$selectedText`" + text.substring(end)
            val newCursor = end + 2
            newText to newCursor
        } else {
            val cursor = selection.start
            val newText = text.substring(0, cursor) + "``" + text.substring(cursor)
            newText to (cursor + 1)
        }
    }

    /**
     * Inserts a code block
     */
    fun insertCodeBlock(text: String, selection: TextRange): Pair<String, Int> {
        val cursor = selection.start
        val lineStart = text.lastIndexOf('\n', cursor - 1) + 1
        val afterLineEnd = text.indexOf('\n', cursor)
            .let { if (it == -1) text.length else it }

        val codeBlockStart = "```kotlin\n"
        val codeBlockEnd = "\n```\n"

        val newText = text.substring(0, lineStart) +
                      codeBlockStart +
                      text.substring(lineStart, afterLineEnd) +
                      codeBlockEnd +
                      text.substring(afterLineEnd)

        return newText to (lineStart + codeBlockStart.length)
    }

    /**
     * Inserts a link template [text](url)
     */
    fun insertLink(text: String, selection: TextRange): Pair<String, Int> {
        val cursor = selection.start
        if (selection.length > 0) {
            val start = selection.min
            val end = selection.max
            val selectedText = text.substring(start, end)
            val newText = text.substring(0, start) + "[$selectedText](url)" + text.substring(end)
            return newText to (end + 6) // cursor at 'url'
        } else {
            val newText = text.substring(0, cursor) + "[Link text](url)" + text.substring(cursor)
            return newText to (cursor + 11) // cursor at 'url'
        }
    }
}

