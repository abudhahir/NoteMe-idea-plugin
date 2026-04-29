package com.cleveloper.notemeideaplugin

import java.io.File
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class IndexNode(
    val title: String,
    val level: Int,
    val children: MutableList<IndexNode> = mutableListOf(),
    val links: MutableList<Pair<String, String>> = mutableListOf(), // title to relative path from notesRoot
    val dirPath: String = "" // relative dir path from notesRoot (e.g. "Knowledge Base/Java")
)

data class SyncResult(
    val addedHeadings: List<String>,          // heading titles (last path segment) added from disk
    val addedLinks: List<String>,             // note titles added from disk
    val missingFiles: List<Pair<String, String>> // (title, relativePath) in index but missing on disk
)

object IndexManager {
    private const val INDEX_FILE_NAME = "index.md"

    private val _mutationCount = MutableStateFlow(0)
    val mutationCount: StateFlow<Int> = _mutationCount.asStateFlow()

    private fun notifyMutation() {
        _mutationCount.value++
    }

    private fun headingLevel(line: String): Int =
        Regex("^(#+)").find(line)?.groupValues?.get(1)?.length ?: 0
    private val DEFAULT_CONTENT = """
        # Knowledge Base
        ## Java
        ## Components
        # Tools
        # How to
        # Tutorials
        # Others
    """.trimIndent().trimStart('\n')

    fun getIndexFile(notesRoot: File): File {
        val indexFile = File(notesRoot, INDEX_FILE_NAME)
        if (!indexFile.exists()) {
            if (!notesRoot.exists()) {
                notesRoot.mkdirs()
            }
            indexFile.writeText(DEFAULT_CONTENT)

            // Create welcome note under Others/ subdirectory
            val welcomeTitle = "Welcome"
            val othersDir = File(notesRoot, "Others")
            if (!othersDir.exists()) othersDir.mkdirs()
            val welcomeFile = File(othersDir, "Welcome.md")
            welcomeFile.writeText(
                """
                # $welcomeTitle

                Welcome to **NoteMe**!

                This plugin helps you manage your notes directly within IntelliJ IDEA.

                ### Features
                - **Markdown Support**: Write notes using Markdown.
                - **Tree View**: Organize notes by topics (headings).
                - **Global Shortcuts**:
                  - `Ctrl+Alt+N`: Create a new note.
                  - `Ctrl+Alt+O`: Open NoteMe tool window.
                - **Context Menu**: Right-click on notes to rename, delete, or open in file system.

                ### Getting Started
                Click the **Add** icon in the tool window to create your first note!
                """.trimIndent()
            )
            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(welcomeFile)
            addNoteToIndex(notesRoot, welcomeTitle, "Others")
        }
        // Ensure the directory structure on disk matches the heading hierarchy in index.md
        ensureDirectoryStructure(notesRoot)
        return indexFile
    }

    /**
     * Creates a directory on disk for every heading node defined in index.md.
     * This keeps the filesystem structure in sync with the index hierarchy without
     * waiting for the first note to be added under a heading.
     */
    fun ensureDirectoryStructure(notesRoot: File) {
        val indexFile = File(notesRoot, INDEX_FILE_NAME)
        if (!indexFile.exists()) return
        val lines = indexFile.readLines()
        val stack = mutableListOf<IndexNode>()
        for (line in lines) {
            val headingMatch = Regex("^(#+)\\s+(.*)$").find(line) ?: continue
            val level = headingMatch.groupValues[1].length
            val title = headingMatch.groupValues[2].trim()
            while (stack.isNotEmpty() && stack.last().level >= level) stack.removeAt(stack.size - 1)
            val parentPath = stack.lastOrNull()?.dirPath ?: ""
            val dirPath = if (parentPath.isEmpty()) title else "$parentPath/$title"
            val newNode = IndexNode(title, level, dirPath = dirPath)
            stack.add(newNode)
            File(notesRoot, dirPath).mkdirs()
        }
    }

    /**
     * Removes the first line in index.md that exactly matches "- [title](relativePath)".
     * Does not delete any file on disk. Caller must call refreshTree() after this.
     */
    fun removeFromIndex(notesRoot: File, title: String, relativePath: String) {
        val indexFile = getIndexFile(notesRoot)
        val lines = indexFile.readLines().toMutableList()
        val target = "- [$title]($relativePath)"
        val idx = lines.indexOfFirst { it == target }
        if (idx != -1) {
            lines.removeAt(idx)
            indexFile.writeText(lines.joinToString("\n"))
            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(indexFile)
        }
    }

    fun parseIndex(notesRoot: File): List<IndexNode> {
        val indexFile = getIndexFile(notesRoot)
        val lines = indexFile.readLines()
        val rootNodes = mutableListOf<IndexNode>()
        val stack = mutableListOf<IndexNode>()

        for (line in lines) {
            val headingMatch = Regex("^(#+)\\s+(.*)$").find(line)
            if (headingMatch != null) {
                val level = headingMatch.groupValues[1].length
                val title = headingMatch.groupValues[2].trim()

                while (stack.isNotEmpty() && stack.last().level >= level) {
                    stack.removeAt(stack.size - 1)
                }

                val parentPath = stack.lastOrNull()?.dirPath ?: ""
                val dirPath = if (parentPath.isEmpty()) title else "$parentPath/$title"
                val newNode = IndexNode(title, level, dirPath = dirPath)

                if (stack.isEmpty()) {
                    rootNodes.add(newNode)
                } else {
                    stack.last().children.add(newNode)
                }
                stack.add(newNode)
            } else {
                val linkMatch = Regex("^- \\[(.*)\\]\\((.*)\\)$").find(line)
                if (linkMatch != null && stack.isNotEmpty()) {
                    val linkTitle = linkMatch.groupValues[1]
                    val linkPath = linkMatch.groupValues[2]
                    stack.last().links.add(linkTitle to linkPath)
                }
            }
        }
        return rootNodes
    }

    fun getAllHeadings(notesRoot: File): List<String> {
        val indexFile = getIndexFile(notesRoot)
        return indexFile.readLines()
            .filter { it.startsWith("#") }
            .map { it.replace(Regex("^#+\\s+"), "").trim() }
    }

    /** Returns the relative directory path for a heading (e.g. "Knowledge Base/Java"). */
    fun getHeadingDirPath(notesRoot: File, heading: String): String? =
        findHeadingNode(parseIndex(notesRoot), heading)?.dirPath

    /** Resolves the absolute File for a note by looking up its relative path in the index. */
    fun getFilePathForNote(notesRoot: File, noteTitle: String): File? {
        fun searchNodes(nodes: List<IndexNode>): File? {
            for (node in nodes) {
                for ((title, relPath) in node.links) {
                    if (title == noteTitle) return File(notesRoot, relPath)
                }
                val found = searchNodes(node.children)
                if (found != null) return found
            }
            return null
        }
        return searchNodes(parseIndex(notesRoot))
    }

    private fun findHeadingNode(nodes: List<IndexNode>, heading: String): IndexNode? {
        for (node in nodes) {
            if (node.title == heading) return node
            val found = findHeadingNode(node.children, heading)
            if (found != null) return found
        }
        return null
    }

    /**
     * Creates the note file inside the subdirectory matching [heading]'s path,
     * then adds the relative-path link to index.md.
     * Returns the created/existing File, or null if the heading was not found.
     */
    fun addNoteToIndex(notesRoot: File, noteName: String, heading: String): File? {
        val nodes = parseIndex(notesRoot)
        val headingNode = findHeadingNode(nodes, heading) ?: return null

        val noteDir = File(notesRoot, headingNode.dirPath)
        if (!noteDir.exists()) noteDir.mkdirs()

        val fileName = if (noteName.endsWith(".md")) noteName else "$noteName.md"
        val noteFile = File(noteDir, fileName)
        if (!noteFile.exists()) {
            noteFile.writeText("# $noteName\n\nNew note content.")
            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(noteFile)
        }

        val relativePath = "${headingNode.dirPath}/$fileName"

        val indexFile = getIndexFile(notesRoot)
        val lines = indexFile.readLines().toMutableList()

        var headingIndex = -1
        for (i in lines.indices) {
            if (lines[i].replace(Regex("^#+\\s+"), "").trim() == heading) {
                headingIndex = i
                break
            }
        }

        if (headingIndex != -1) {
            var insertIndex = headingIndex + 1
            while (insertIndex < lines.size && !lines[insertIndex].startsWith("#")) {
                insertIndex++
            }
            lines.add(insertIndex, "- [$noteName]($relativePath)")
        } else {
            lines.add("- [$noteName]($relativePath)")
        }

        indexFile.writeText(lines.joinToString("\n"))
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(indexFile)

        notifyMutation()
        return noteFile
    }
}
