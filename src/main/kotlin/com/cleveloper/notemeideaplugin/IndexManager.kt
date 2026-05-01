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
     * Bidirectional sync: walks the disk, adds new directories as headings and
     * new .md files as links in index.md, and detects links whose files are missing.
     *
     * Does NOT call notifyMutation(). The caller must coordinate state updates
     * and call refreshTree() after storing the returned SyncResult.
     *
     * Link insertion position: new links are inserted after the last DIRECT link
     * under the parent heading (before any sub-headings), tracked via lastDirectLinkLine.
     * New headings are inserted at the end of the parent heading's full block.
     */
    fun syncFromDisk(notesRoot: File): SyncResult {
        val indexFile = getIndexFile(notesRoot)
        val lines = indexFile.readLines().toMutableList()

        // ── Local data structure ─────────────────────────────────────────────────
        data class HeadingTriple(var lineIndex: Int, val title: String, val dirPath: String)

        // ── Mutable tracking maps ────────────────────────────────────────────────
        val headingTriples = mutableListOf<HeadingTriple>()
        val lastDirectLinkLine = mutableMapOf<Int, Int>()

        // ── Local helpers ────────────────────────────────────────────────────────

        fun blockEnd(triple: HeadingTriple): Int {
            val myLevel = headingLevel(lines[triple.lineIndex])
            for (t in headingTriples) {
                if (t.lineIndex > triple.lineIndex && headingLevel(lines[t.lineIndex]) <= myLevel) {
                    return t.lineIndex - 1
                }
            }
            return lines.size - 1
        }

        fun insertAt(at: Int, content: String) {
            lines.add(at, content)
            headingTriples.forEach { if (it.lineIndex >= at) it.lineIndex++ }
            val copy = lastDirectLinkLine.toMap()
            lastDirectLinkLine.clear()
            copy.forEach { (k, v) ->
                lastDirectLinkLine[if (k >= at) k + 1 else k] = if (v >= at) v + 1 else v
            }
        }

        fun toRelPath(file: File): String =
            file.relativeTo(notesRoot).path.replace(File.separatorChar, '/')

        // ── Pass 1: raw line scan of index.md ────────────────────────────────────
        val knownDirPaths = mutableSetOf<String>()
        val knownRelPaths = mutableSetOf<String>()
        val originalLinks = mutableListOf<Pair<String, String>>()
        val stack = mutableListOf<HeadingTriple>()
        var currentHeadingLineIndex = -1

        for ((idx, line) in lines.withIndex()) {
            val hm = Regex("^(#+)\\s+(.*)$").find(line)
            if (hm != null) {
                val level = hm.groupValues[1].length
                val title = hm.groupValues[2].trim()
                while (stack.isNotEmpty() && headingLevel(lines[stack.last().lineIndex]) >= level) {
                    stack.removeAt(stack.size - 1)
                }
                val parentPath = stack.lastOrNull()?.dirPath ?: ""
                val dirPath = if (parentPath.isEmpty()) title else "$parentPath/$title"
                val triple = HeadingTriple(idx, title, dirPath)
                headingTriples.add(triple)
                knownDirPaths.add(dirPath)
                lastDirectLinkLine[idx] = idx
                currentHeadingLineIndex = idx
                stack.add(triple)
            } else {
                val lm = Regex("^- \\[(.*)\\]\\((.*)\\)$").find(line)
                if (lm != null) {
                    knownRelPaths.add(lm.groupValues[2])
                    originalLinks.add(lm.groupValues[1] to lm.groupValues[2])
                    if (currentHeadingLineIndex != -1) {
                        lastDirectLinkLine[currentHeadingLineIndex] = idx
                    }
                }
            }
        }

        // ── Pass 2: disk walk ─────────────────────────────────────────────────────
        val addedHeadings = mutableListOf<String>()
        val addedLinks = mutableListOf<String>()

        fun insertHeading(relDir: String, title: String) {
            val level = relDir.count { it == '/' } + 1
            val prefix = "#".repeat(level)
            val parentDirPath = relDir.substringBeforeLast("/", "")
            val at = if (parentDirPath.isEmpty()) {
                val lastTopLevel = headingTriples.lastOrNull { !it.dirPath.contains('/') }
                if (lastTopLevel != null) blockEnd(lastTopLevel) + 1 else lines.size
            } else {
                val parentTriple = headingTriples.firstOrNull { it.dirPath == parentDirPath }
                if (parentTriple != null) blockEnd(parentTriple) + 1 else lines.size
            }
            insertAt(at, "$prefix $title")
            val newTriple = HeadingTriple(at, title, relDir)
            val pos = headingTriples.indexOfFirst { it.lineIndex > at }
            if (pos == -1) headingTriples.add(newTriple) else headingTriples.add(pos, newTriple)
            lastDirectLinkLine[at] = at
            knownDirPaths.add(relDir)
            addedHeadings.add(title)
        }

        fun ensureOthers() {
            if ("Others" !in knownDirPaths) insertHeading("Others", "Others")
        }

        fun insertLink(noteTitle: String, relPath: String, parentDirPath: String) {
            val parentTriple = if (parentDirPath.isEmpty()) {
                ensureOthers()
                headingTriples.first { it.dirPath == "Others" }
            } else {
                headingTriples.firstOrNull { it.dirPath == parentDirPath } ?: return
            }
            val at = (lastDirectLinkLine[parentTriple.lineIndex] ?: parentTriple.lineIndex) + 1
            insertAt(at, "- [$noteTitle]($relPath)")
            lastDirectLinkLine[parentTriple.lineIndex] = at
            knownRelPaths.add(relPath)
            addedLinks.add(noteTitle)
        }

        fun walkDir(dir: File, depth: Int) {
            if (depth > 10) return
            val children = dir.listFiles()
                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
                ?: return
            for (child in children) {
                if (child.isDirectory) {
                    val relDir = toRelPath(child)
                    if (relDir !in knownDirPaths) insertHeading(relDir, child.name)
                    walkDir(child, depth + 1)
                } else if (child.isFile && child.extension == "md" && child.name != INDEX_FILE_NAME) {
                    val relPath = toRelPath(child)
                    if (relPath !in knownRelPaths) {
                        val parentDirPath = relPath.substringBeforeLast("/", "")
                        insertLink(child.nameWithoutExtension, relPath, parentDirPath)
                    }
                }
            }
        }

        walkDir(notesRoot, 0)

        // ── Pass 3: detect missing files ──────────────────────────────────────────
        val missingFiles = originalLinks.filter { (_, relPath) ->
            !File(notesRoot, relPath).exists()
        }

        // ── Write & return ────────────────────────────────────────────────────────
        indexFile.writeText(lines.joinToString("\n"))
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(indexFile)

        return SyncResult(addedHeadings, addedLinks, missingFiles)
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
