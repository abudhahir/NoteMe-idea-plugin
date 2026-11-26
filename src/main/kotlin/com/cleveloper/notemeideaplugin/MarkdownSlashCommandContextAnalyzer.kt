package com.cleveloper.notemeideaplugin

import com.intellij.lang.Language
import com.intellij.lang.PsiBuilder
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

data class MarkdownSlashQuery(
    val startOffset: Int,
    val endOffset: Int,
    val query: String
)

object MarkdownSlashCommandContextAnalyzer {
    fun analyze(
        editor: Editor,
        psiFile: PsiFile?,
        requireNewSlash: Boolean = false
    ): MarkdownSlashQuery? {
        val document = editor.document
        val caret = editor.caretModel.offset
        if (!editor.isInsertMode || !document.isWritable || caret !in 0..document.textLength) return null
        if (!isMarkdown(psiFile)) return null
        if (requireNewSlash && (caret == 0 || document.getText(com.intellij.openapi.util.TextRange(caret - 1, caret)) != "/")) {
            return null
        }

        val slash = document.text.lastIndexOf('/', (caret - 1).coerceAtLeast(0))
        if (slash < 0 || isSuppressed(document, slash, caret)) return null
        if (slash > 0 && document.charsSequence[slash - 1] == '\\') return null

        val query = document.text.substring(slash + 1, caret)
        if (!query.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == ' ' }) return null
        if (query.contains(' ') && !query.trimEnd().endsWith(" ")) return null
        return MarkdownSlashQuery(slash, caret, query)
    }

    fun isMarkdown(psiFile: PsiFile?): Boolean {
        val language = psiFile?.language ?: return false
        return language.id.equals("Markdown", ignoreCase = true) ||
            language.baseLanguage?.id.equals("Markdown", ignoreCase = true)
    }

    private fun isSuppressed(document: Document, slash: Int, caret: Int): Boolean {
        val text = document.text
        val lineStart = text.lastIndexOf('\n', (slash - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val before = text.substring(lineStart, slash)
        val line = text.substring(lineStart, text.indexOf('\n', caret).let { if (it < 0) text.length else it })

        if (before.endsWith("http:") || before.endsWith("https:") || before.endsWith("file:") ||
            before.endsWith("mailto:") || before.endsWith("www.")
        ) return true
        if (before.any { it == ':' } && (before.contains("://") || before.lastOrNull()?.isLetterOrDigit() == true)) return true
        if (before.endsWith("[") || (before.contains("](") && !before.endsWith("]("))) return true
        if (before.contains("/") && before.none { it.isWhitespace() }) return true
        if (before.trimStart().startsWith("```") || isInsideFence(text, lineStart)) return true
        if (isInsideInlineCode(line, slash - lineStart)) return true
        if (slash > lineStart && text[slash - 1] == '\\') return true
        if (slash > lineStart && text[slash - 1].isLetterOrDigit() && before.contains(".")) return true
        return false
    }

    private fun isInsideFence(text: String, lineStart: Int): Boolean {
        var fenced = false
        var position = 0
        while (position < lineStart) {
            val next = text.indexOf('\n', position).let { if (it < 0) lineStart else it }
            if (text.substring(position, next).trimStart().startsWith("```")) fenced = !fenced
            if (next >= lineStart) break
            position = next + 1
        }
        return fenced
    }

    private fun isInsideInlineCode(line: String, offset: Int): Boolean {
        var backticks = 0
        line.take(offset).forEach { if (it == '`') backticks++ }
        return backticks % 2 == 1
    }
}
