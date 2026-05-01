package com.cleveloper.notemeideaplugin

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class NoteMeSettingsConfigurable : Configurable {

    private var rootPathField: TextFieldWithBrowseButton? = null
    private var chromaDbCheckBox: JBCheckBox? = null
    private var reindexCheckBox: JBCheckBox? = null
    private var mainPanel: JPanel? = null

    override fun getDisplayName(): String = "NoteMe"

    override fun createComponent(): JComponent {
        rootPathField = TextFieldWithBrowseButton().apply {
            addBrowseFolderListener(
                "Select Notes Root Directory",
                "Choose the root folder where NoteMe stores notes",
                null,
                FileChooserDescriptorFactory.createSingleFolderDescriptor()
            )
        }
        chromaDbCheckBox = JBCheckBox("Enable file-based ChromaDB indexing")
        reindexCheckBox = JBCheckBox("Re-index notes on sync")

        mainPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Notes root directory:"), rootPathField!!, 1, false)
            .addComponent(chromaDbCheckBox!!, 10)
            .addComponent(reindexCheckBox!!, 5)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        reset()
        return mainPanel!!
    }

    override fun isModified(): Boolean {
        val settings = NoteMeSettings.getInstance()
        return rootPathField?.text != settings.state.notesRootPath
                || chromaDbCheckBox?.isSelected != settings.state.chromaDbEnabled
                || reindexCheckBox?.isSelected != settings.state.reindexOnSync
    }

    override fun apply() {
        val settings = NoteMeSettings.getInstance()
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
                settings.notifySettingsChanged()
            } else {
                rootPathField?.text = oldPath
            }
        }

        settings.state.chromaDbEnabled = chromaDbCheckBox?.isSelected ?: false
        settings.state.reindexOnSync = reindexCheckBox?.isSelected ?: false
    }

    override fun reset() {
        val settings = NoteMeSettings.getInstance()
        rootPathField?.text = settings.state.notesRootPath
        chromaDbCheckBox?.isSelected = settings.state.chromaDbEnabled
        reindexCheckBox?.isSelected = settings.state.reindexOnSync
    }

    override fun disposeUIResources() {
        rootPathField = null
        chromaDbCheckBox = null
        reindexCheckBox = null
        mainPanel = null
    }
}
