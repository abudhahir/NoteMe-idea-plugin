package com.cleveloper.notemeideaplugin

import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessExtension
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

class NoteFileWritingAccessExtension : NonProjectFileWritingAccessExtension {
    private val notesRoot = File(System.getProperty("user.home"), "NoteMeNotes").canonicalPath

    override fun isWritable(file: VirtualFile): Boolean {
        return File(file.path).canonicalPath.startsWith(notesRoot)
    }
}
