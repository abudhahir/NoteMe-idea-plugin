package com.cleveloper.notemeideaplugin

import com.intellij.openapi.extensions.ExtensionPointName
import java.util.Locale

data class MarkdownCommandContext(
    val text: String,
    val queryStart: Int,
    val caretOffset: Int,
    val selectionStart: Int = caretOffset,
    val selectionEnd: Int = caretOffset,
    val isMarkdown: Boolean = true,
    val isWritable: Boolean = true
) {
    val selectionRange: IntRange
        get() = minOf(selectionStart, selectionEnd)..maxOf(selectionStart, selectionEnd)

    val hasSelection: Boolean
        get() = selectionStart != selectionEnd
}

data class MarkdownTextEdit(
    val start: Int,
    val end: Int,
    val replacement: String
)

data class MarkdownTransformationResult(
    val edits: List<MarkdownTextEdit>,
    val caretOffset: Int,
    val selectionStart: Int = caretOffset,
    val selectionEnd: Int = caretOffset
) {
    init {
        require(edits.all { it.start >= 0 && it.end >= it.start }) {
            "Transformation edits must use non-negative, ordered ranges"
        }
    }
}

data class MarkdownCommandDescriptor(
    val id: String,
    val name: String,
    val description: String,
    val aliases: List<String> = emptyList(),
    val isAvailable: (MarkdownCommandContext) -> Boolean = { true },
    val transform: (MarkdownCommandContext) -> MarkdownTransformationResult
) {
    val normalizedNames: List<String>
        get() = (listOf(name) + aliases).map { it.lowercase(Locale.ROOT) }
}

interface MarkdownCommandProvider {
    val id: String
    fun commands(context: MarkdownCommandContext): List<MarkdownCommandDescriptor>

    companion object {
        val EP_NAME: ExtensionPointName<MarkdownCommandProvider> =
            ExtensionPointName.create("com.cleveloper.notemeideaplugin.markdownCommandProvider")
    }
}

fun MarkdownTransformationResult.applyTo(text: String): String {
    var result = text
    edits.sortedByDescending { it.start }.forEach { edit ->
        require(edit.end <= result.length) {
            "Transformation edit ${edit.start}..${edit.end} exceeds text length ${result.length}"
        }
        result = result.replaceRange(edit.start, edit.end, edit.replacement)
    }
    return result
}
