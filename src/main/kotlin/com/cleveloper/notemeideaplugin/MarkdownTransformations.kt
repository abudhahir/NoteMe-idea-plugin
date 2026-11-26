package com.cleveloper.notemeideaplugin

object MarkdownTransformations {
    fun paired(context: MarkdownCommandContext, delimiter: String): MarkdownTransformationResult {
        if (context.hasSelection) {
            val start = minOf(context.selectionStart, context.selectionEnd)
            val end = maxOf(context.selectionStart, context.selectionEnd)
            return MarkdownTransformationResult(
                edits = listOf(
                    MarkdownTextEdit(start, start, delimiter),
                    MarkdownTextEdit(end, end, delimiter)
                ),
                caretOffset = end + delimiter.length * 2
            )
        }

        val cursor = context.queryStart
        return MarkdownTransformationResult(
            edits = listOf(MarkdownTextEdit(context.queryStart, context.caretOffset, delimiter + delimiter)),
            caretOffset = cursor + delimiter.length
        )
    }

    fun linePrefix(context: MarkdownCommandContext, prefix: String): MarkdownTransformationResult {
        val lineStart = context.text.lastIndexOf('\n', (context.queryStart - 1).coerceAtLeast(0))
            .let { if (it < 0) 0 else it + 1 }
        val lineEnd = context.text.indexOf('\n', context.caretOffset)
            .let { if (it < 0) context.text.length else it }
        val line = context.text.substring(lineStart, lineEnd)
        val normalized = normalizePrefix(line, prefix)
        val queryLength = context.caretOffset - context.queryStart
        val edits = buildList {
            if (context.queryStart == lineStart &&
                normalized.replacementStart == 0 &&
                normalized.replacementEnd == 0
            ) {
                add(MarkdownTextEdit(lineStart, context.caretOffset, normalized.replacement))
            } else {
                add(
                    MarkdownTextEdit(
                        lineStart + normalized.replacementStart,
                        lineStart + normalized.replacementEnd,
                        normalized.replacement
                    )
                )
                add(MarkdownTextEdit(context.queryStart, context.caretOffset, ""))
            }
        }
        val prefixDelta = normalized.replacement.length -
            (normalized.replacementEnd - normalized.replacementStart)
        val caret = (context.caretOffset - queryLength + prefixDelta).coerceAtLeast(lineStart)
        return MarkdownTransformationResult(edits, caret)
    }

    fun link(context: MarkdownCommandContext): MarkdownTransformationResult {
        val template = "[Link text](url)"
        val urlOffset = template.indexOf("url")
        return MarkdownTransformationResult(
            edits = listOf(MarkdownTextEdit(context.queryStart, context.caretOffset, template)),
            caretOffset = context.queryStart + urlOffset,
            selectionStart = context.queryStart + urlOffset,
            selectionEnd = context.queryStart + urlOffset + 3
        )
    }

    fun codeBlock(context: MarkdownCommandContext): MarkdownTransformationResult {
        val lineStart = context.text.lastIndexOf('\n', (context.queryStart - 1).coerceAtLeast(0))
            .let { if (it < 0) 0 else it + 1 }
        val lineEnd = context.text.indexOf('\n', context.caretOffset)
            .let { if (it < 0) context.text.length else it }
        val block = "```\n\n```"
        return MarkdownTransformationResult(
            edits = listOf(
                MarkdownTextEdit(lineStart, lineEnd, block)
            ),
            caretOffset = lineStart + 4
        )
    }

    fun horizontalRule(context: MarkdownCommandContext): MarkdownTransformationResult {
        val lineStart = context.text.lastIndexOf('\n', (context.queryStart - 1).coerceAtLeast(0))
            .let { if (it < 0) 0 else it + 1 }
        return MarkdownTransformationResult(
            edits = listOf(
                MarkdownTextEdit(lineStart, context.caretOffset, "---")
            ),
            caretOffset = lineStart + 3
        )
    }

    private data class PrefixNormalization(
        val replacementStart: Int,
        val replacementEnd: Int,
        val replacement: String
    )

    private fun normalizePrefix(line: String, prefix: String): PrefixNormalization {
        val leadingWhitespace = line.takeWhile { it == ' ' || it == '\t' }
        val content = line.substring(leadingWhitespace.length)
        val existing = when {
            content.matches(Regex("""#{1,6}\s+.*""")) -> Regex("""^#{1,6}\s+""").find(content)!!.value
            content.matches(Regex("""[-*+]\s+\[[ xX]\]\s+.*""")) ->
                Regex("""^[-*+]\s+\[[ xX]\]\s+""").find(content)!!.value
            content.matches(Regex("""[-*+]\s+.*""")) -> Regex("""^[-*+]\s+""").find(content)!!.value
            content.matches(Regex("""\d+[.)]\s+.*""")) -> Regex("""^\d+[.)]\s+""").find(content)!!.value
            content.matches(Regex("""\[[ xX]\]\s+.*""")) -> Regex("""^\[[ xX]\]\s+""").find(content)!!.value
            content.matches(Regex("""(?:>\s*)+.*""")) -> Regex("""^>\s*""").find(content)!!.value
            else -> ""
        }
        return PrefixNormalization(
            replacementStart = leadingWhitespace.length,
            replacementEnd = leadingWhitespace.length + existing.length,
            replacement = prefix
        )
    }
}
