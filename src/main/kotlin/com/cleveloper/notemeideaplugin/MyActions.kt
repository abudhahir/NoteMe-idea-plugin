package com.cleveloper.notemeideaplugin

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.editor.Editor
import com.intellij.ide.bookmark.BookmarksManager
import com.intellij.ide.bookmark.BookmarkType
import com.intellij.ide.bookmark.providers.LineBookmarkProvider
import java.io.File

class CreateNoteAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: ProjectManager.getInstance().openProjects.firstOrNull() ?: return
        val notesRoot = File(System.getProperty("user.home"), "NoteMeNotes")
        val noteName = Messages.showInputDialog(project, "Enter note name", "New Note", null) ?: return

        val editor = e.getData(CommonDataKeys.EDITOR)
        val currentFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val bookmarkInfo = buildSourceContext(project, currentFile, editor)

        val headings = IndexManager.getAllHeadings(notesRoot)
        val selectedHeading = Messages.showEditableChooseDialog(
            "Select target topic",
            "Target Topic",
            null,
            headings.toTypedArray(),
            headings.firstOrNull() ?: "",
            null
        ) ?: return

        val noteFile = IndexManager.addNoteToIndex(notesRoot, noteName, selectedHeading)
        if (noteFile == null) {
            Messages.showErrorDialog(project, "Heading '$selectedHeading' not found in index.", "Error")
            return
        }

        // Append bookmark info to the created file if triggered from an editor context
        if (bookmarkInfo.isNotEmpty()) {
            noteFile.writeText(noteFile.readText().trimEnd() + bookmarkInfo + "\n")
            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(noteFile)
        }

        // Open the tool window to show the new note
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("NoteMe")
        toolWindow?.show()
    }

    /**
     * Builds a Markdown section capturing the source context (file, line, selected text).
     * If triggered from an editor with a selection, also creates an IntelliJ line bookmark
     * at the selection start so the user can navigate back from the Bookmarks panel.
     */
    private fun buildSourceContext(project: Project, file: VirtualFile?, editor: Editor?): String {
        file ?: return ""

        val sb = StringBuilder("\n\n---\n\n")

        if (editor != null) {
            val selectionModel = editor.selectionModel
            val document = editor.document
            val startLine = document.getLineNumber(selectionModel.selectionStart) + 1 // 1-based
            val endLine = document.getLineNumber(selectionModel.selectionEnd) + 1
            val selectedText = selectionModel.selectedText

            val lineRef = if (selectedText.isNullOrEmpty() || startLine == endLine) "L$startLine" else "L$startLine-L$endLine"
            // file.url produces a proper file:// URI that IntelliJ's Markdown preview can open
            sb.appendLine("**Source:** [${file.name}:$lineRef](${file.url})")

            if (!selectedText.isNullOrBlank()) {
                sb.appendLine()
                sb.appendLine("```")
                sb.appendLine(selectedText.trimEnd())
                sb.appendLine("```")
            }

            // Create an IntelliJ line bookmark at the selection start.
            // Note: Markdown cannot link to a specific line natively — the bookmark in the
            // IntelliJ Bookmarks panel (View > Tool Windows > Bookmarks) is the navigation target.
            val bookmarkCreated = tryCreateLineBookmark(project, file, startLine - 1)
            if (bookmarkCreated) {
                sb.appendLine()
                sb.appendLine("*IntelliJ bookmark added at $lineRef — navigate via the Bookmarks panel*")
            }
        } else {
            sb.appendLine("**Source:** [${file.name}](${file.url})")
        }

        return sb.toString()
    }

    /** Creates a line bookmark at [zeroBasedLine] in [file]. Returns true on success. */
    private fun tryCreateLineBookmark(project: Project, file: VirtualFile, zeroBasedLine: Int): Boolean {
        return try {
            val manager = BookmarksManager.getInstance(project) ?: return false
            val provider = LineBookmarkProvider.Util.find(project) ?: return false
            val bookmark = provider.createBookmark(mapOf(
                "url" to file.url,
                "line" to zeroBasedLine.toString()
            )) ?: return false
            manager.add(bookmark, BookmarkType.DEFAULT)
            true
        } catch (_: Exception) {
            false
        }
    }
}

class OpenNoteAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("NoteMe")
        toolWindow?.show()
    }
}
