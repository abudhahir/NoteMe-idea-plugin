package com.cleveloper.notemeideaplugin

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.Constraints
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class NoteMeStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val actionManager = ActionManager.getInstance()
        val editorTitleGroup = actionManager.getAction("EditorTitleActions") as? DefaultActionGroup
        if (editorTitleGroup != null) {
            val myGroup = actionManager.getAction("NoteMe.EditorTitleGroup")
            if (myGroup != null) {
                editorTitleGroup.add(myGroup, Constraints.LAST)
            }
        }
    }
}
