package com.cleveloper.notemeideaplugin

import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessExtension
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

class NoteFileWritingAccessExtension : NonProjectFileWritingAccessExtension {
    override fun isWritable(file: VirtualFile): Boolean {
        val notesRoot = NoteMeSettings.getInstance().notesRoot.canonicalPath
        return File(file.path).canonicalPath.startsWith(notesRoot)
    }
}
