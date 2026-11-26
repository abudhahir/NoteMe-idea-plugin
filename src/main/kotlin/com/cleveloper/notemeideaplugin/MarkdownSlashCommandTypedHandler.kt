package com.cleveloper.notemeideaplugin

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

class MarkdownSlashCommandTypedHandler : TypedHandlerDelegate() {
    override fun charTyped(char: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (char == '/' &&
            MarkdownSlashCommandContextAnalyzer.analyze(editor, file, requireNewSlash = true) != null
        ) {
            AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
        }
        return Result.CONTINUE
    }
}
