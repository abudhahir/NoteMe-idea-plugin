package com.cleveloper.notemeideaplugin

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import java.io.File
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JPanel

class NoteMeSettingsConfigurable : Configurable {

    private var rootPathField: TextFieldWithBrowseButton? = null
    private var localRadio: JBRadioButton? = null
    private var gitRadio: JBRadioButton? = null
    private var gitUrlField: JBTextField? = null
    private var chromaDbCheckBox: JBCheckBox? = null
    private var reindexCheckBox: JBCheckBox? = null
    private var mainPanel: JPanel? = null

    override fun getDisplayName(): String = "NoteMe"

    override fun createComponent(): JComponent {
        localRadio = JBRadioButton("Local")
        gitRadio = JBRadioButton("Git")
        ButtonGroup().apply {
            add(localRadio)
            add(gitRadio)
        }

        rootPathField = TextFieldWithBrowseButton().apply {
            addBrowseFolderListener(
                "Select Notes Root Directory",
                "Choose the root folder where NoteMe stores notes",
                null,
                FileChooserDescriptorFactory.createSingleFolderDescriptor()
            )
        }

        gitUrlField = JBTextField().apply {
            emptyText.text = "https://github.com/user/repo.git"
        }

        chromaDbCheckBox = JBCheckBox("Enable file-based ChromaDB indexing")
        reindexCheckBox = JBCheckBox("Re-index notes on sync")

        // Toggle field enabled state based on radio selection
        val updateFieldStates = {
            val isLocal = localRadio?.isSelected == true
            rootPathField?.isEnabled = isLocal
            gitUrlField?.isEnabled = !isLocal
        }
        localRadio?.addActionListener { updateFieldStates() }
        gitRadio?.addActionListener { updateFieldStates() }

        val radioPanel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 10, 0)).apply {
            add(localRadio)
            add(gitRadio)
        }

        mainPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Notes source:"), radioPanel, 1, false)
            .addLabeledComponent(JBLabel("Notes root directory:"), rootPathField!!, 1, false)
            .addLabeledComponent(JBLabel("Git repository URL:"), gitUrlField!!, 1, false)
            .addComponent(chromaDbCheckBox!!, 10)
            .addComponent(reindexCheckBox!!, 5)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        reset()
        return mainPanel!!
    }

    override fun isModified(): Boolean {
        val settings = NoteMeSettings.getInstance()
        val selectedType = if (localRadio?.isSelected == true) NoteMeSettings.SourceType.LOCAL else NoteMeSettings.SourceType.GIT
        return rootPathField?.text != settings.state.notesRootPath
                || selectedType != settings.state.sourceType
                || gitUrlField?.text != settings.state.gitRepoUrl
                || chromaDbCheckBox?.isSelected != settings.state.chromaDbEnabled
                || reindexCheckBox?.isSelected != settings.state.reindexOnSync
    }

    override fun apply() {
        val settings = NoteMeSettings.getInstance()
        val selectedType = if (localRadio?.isSelected == true) NoteMeSettings.SourceType.LOCAL else NoteMeSettings.SourceType.GIT
        val oldType = settings.state.sourceType

        // Handle LOCAL source type
        if (selectedType == NoteMeSettings.SourceType.LOCAL) {
            val newPath = rootPathField?.text ?: settings.state.notesRootPath
            val oldPath = settings.state.notesRootPath

            if (newPath != oldPath) {
                val choice = Messages.showYesNoDialog(
                    "Load '$newPath' as the NoteMe notes root?\n\n" +
                        "All notes will be read from this directory going forward.",
                    "Change Notes Root Directory",
                    "Load",
                    "Cancel",
                    Messages.getQuestionIcon()
                )
                if (choice == Messages.YES) {
                    settings.state.notesRootPath = newPath
                } else {
                    rootPathField?.text = oldPath
                }
            }
            settings.state.sourceType = NoteMeSettings.SourceType.LOCAL
            settings.state.gitRepoUrl = gitUrlField?.text ?: ""
        }

        // Handle GIT source type
        if (selectedType == NoteMeSettings.SourceType.GIT) {
            val gitUrl = gitUrlField?.text?.trim() ?: ""
            if (gitUrl.isEmpty()) {
                Messages.showErrorDialog("Git repository URL cannot be empty.", "Invalid Git URL")
                return
            }

            val urlChanged = gitUrl != settings.state.gitRepoUrl
            val typeChanged = oldType != NoteMeSettings.SourceType.GIT

            if (urlChanged || typeChanged) {
                // Ask user for clone destination using IntelliJ's file chooser
                val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
                    .withTitle("Select Destination Directory")
                    .withDescription("Choose the parent directory where the git repository will be cloned")
                val chosenFiles = com.intellij.openapi.fileChooser.FileChooser.chooseFiles(descriptor, null, null)
                if (chosenFiles.isEmpty()) return

                val destParent = File(chosenFiles[0].path)
                val repoName = gitUrl.trimEnd('/').substringAfterLast('/').removeSuffix(".git")
                val cloneTarget = File(destParent, repoName)

                if (cloneTarget.exists() && cloneTarget.listFiles()?.isNotEmpty() == true) {
                    val overwrite = Messages.showYesNoDialog(
                        "Directory '${cloneTarget.absolutePath}' already exists.\n\nUse it as the notes root?",
                        "Directory Exists",
                        "Use Existing",
                        "Cancel",
                        Messages.getQuestionIcon()
                    )
                    if (overwrite != Messages.YES) return
                } else {
                    // Clone using IntelliJ's Git integration
                    var cloneSuccess = false
                    var cloneError: String? = null

                    ProgressManager.getInstance().run(object : Task.Modal(
                        ProjectManager.getInstance().defaultProject,
                        "Cloning Git Repository",
                        true
                    ) {
                        override fun run(indicator: ProgressIndicator) {
                            indicator.isIndeterminate = true
                            indicator.text = "Cloning $gitUrl..."

                            val handler = GitLineHandler(
                                ProjectManager.getInstance().defaultProject,
                                destParent,
                                GitCommand.CLONE
                            )
                            handler.setUrl(gitUrl)
                            handler.addParameters("--progress", gitUrl, cloneTarget.absolutePath)

                            val result = Git.getInstance().runCommand(handler)
                            cloneSuccess = result.success()
                            if (!cloneSuccess) {
                                cloneError = result.errorOutputAsJoinedString
                            }
                        }
                    })

                    if (!cloneSuccess) {
                        Messages.showErrorDialog(
                            "Git clone failed:\n${cloneError ?: "Unknown error"}",
                            "Git Clone Error"
                        )
                        return
                    }
                }

                settings.state.notesRootPath = cloneTarget.absolutePath
                rootPathField?.text = cloneTarget.absolutePath
            }

            settings.state.sourceType = NoteMeSettings.SourceType.GIT
            settings.state.gitRepoUrl = gitUrl
        }

        val wasChromaEnabled = settings.state.chromaDbEnabled
        settings.state.chromaDbEnabled = chromaDbCheckBox?.isSelected ?: false
        settings.state.reindexOnSync = reindexCheckBox?.isSelected ?: false

        // First-enable: prompt to build search index
        if (!wasChromaEnabled && settings.state.chromaDbEnabled) {
            val buildNow = Messages.showYesNoDialog(
                "Build search index now?\n\nThis may take a moment for large note collections.",
                "ChromaDB Indexing",
                "Build Now",
                "Later",
                Messages.getQuestionIcon()
            )
            if (buildNow == Messages.YES) {
                val notesRoot = settings.notesRoot
                Thread {
                    VectorSearchManager.indexAllNotes(notesRoot)
                }.start()
            }
        }

        settings.notifySettingsChanged()
    }

    override fun reset() {
        val settings = NoteMeSettings.getInstance()
        rootPathField?.text = settings.state.notesRootPath
        gitUrlField?.text = settings.state.gitRepoUrl
        chromaDbCheckBox?.isSelected = settings.state.chromaDbEnabled
        reindexCheckBox?.isSelected = settings.state.reindexOnSync

        when (settings.state.sourceType) {
            NoteMeSettings.SourceType.LOCAL -> localRadio?.isSelected = true
            NoteMeSettings.SourceType.GIT -> gitRadio?.isSelected = true
        }

        // Update field enabled states
        val isLocal = settings.state.sourceType == NoteMeSettings.SourceType.LOCAL
        rootPathField?.isEnabled = isLocal
        gitUrlField?.isEnabled = !isLocal
    }

    override fun disposeUIResources() {
        rootPathField = null
        localRadio = null
        gitRadio = null
        gitUrlField = null
        chromaDbCheckBox = null
        reindexCheckBox = null
        mainPanel = null
    }
}
