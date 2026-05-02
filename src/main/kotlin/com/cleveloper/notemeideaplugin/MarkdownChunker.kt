package com.cleveloper.notemeideaplugin

data class Chunk(
    val text: String,
    val headingPath: String,
    val chunkIndex: Int
)

object MarkdownChunker {

    private const val MAX_CHUNK_TOKENS = 500
    private const val APPROX_CHARS_PER_TOKEN = 4

    fun chunk(markdown: String): List<Chunk> {
        val sections = splitByHeadings(markdown)
        val chunks = mutableListOf<Chunk>()
        var chunkIndex = 0

        for ((headingPath, sectionText) in sections) {
            val subChunks = splitLargeSection(sectionText)
            for (sub in subChunks) {
                if (sub.isNotBlank()) {
                    chunks.add(Chunk(text = sub.trim(), headingPath = headingPath, chunkIndex = chunkIndex))
                    chunkIndex++
                }
            }
        }

        return chunks
    }

    private fun splitByHeadings(markdown: String): List<Pair<String, String>> {
        val lines = markdown.lines()
        val sections = mutableListOf<Pair<String, String>>()
        val headingStack = mutableListOf<String>()
        val currentLines = mutableListOf<String>()
        var currentHeadingPath = ""

        for (line in lines) {
            val headingMatch = Regex("^(#{1,6})\\s+(.+)$").find(line)
            if (headingMatch != null) {
                // Flush previous section
                if (currentLines.isNotEmpty()) {
                    sections.add(currentHeadingPath to currentLines.joinToString("\n"))
                    currentLines.clear()
                }

                val level = headingMatch.groupValues[1].length
                val title = headingMatch.groupValues[2].trim()

                // Update heading stack to current level
                while (headingStack.size >= level) {
                    headingStack.removeAt(headingStack.size - 1)
                }
                headingStack.add(title)
                currentHeadingPath = headingStack.joinToString(" > ")
                currentLines.add(line)
            } else {
                currentLines.add(line)
            }
        }

        // Flush final section
        if (currentLines.isNotEmpty()) {
            sections.add(currentHeadingPath to currentLines.joinToString("\n"))
        }

        return sections
    }

    private fun splitLargeSection(text: String): List<String> {
        val maxChars = MAX_CHUNK_TOKENS * APPROX_CHARS_PER_TOKEN
        if (text.length <= maxChars) {
            return listOf(text)
        }

        // Split at paragraph boundaries (double newline)
        val paragraphs = text.split(Regex("\n\\s*\n"))
        val result = mutableListOf<String>()
        val current = StringBuilder()

        for (paragraph in paragraphs) {
            if (current.length + paragraph.length > maxChars && current.isNotEmpty()) {
                result.add(current.toString())
                current.clear()
            }
            if (current.isNotEmpty()) {
                current.append("\n\n")
            }
            current.append(paragraph)
        }

        if (current.isNotEmpty()) {
            result.add(current.toString())
        }

        return result
    }
}
