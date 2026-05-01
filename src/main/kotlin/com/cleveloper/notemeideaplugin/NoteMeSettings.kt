package com.cleveloper.notemeideaplugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

@State(
    name = "com.cleveloper.notemeideaplugin.NoteMeSettings",
    storages = [Storage("NoteMeSettings.xml")]
)
class NoteMeSettings : PersistentStateComponent<NoteMeSettings.State> {

    data class State(
        var notesRootPath: String = File(System.getProperty("user.home"), "NoteMeNotes").absolutePath,
        var chromaDbEnabled: Boolean = false,
        var reindexOnSync: Boolean = false
    )

    private var myState = State()

    private val _settingsVersion = MutableStateFlow(0)
    val settingsVersion: StateFlow<Int> = _settingsVersion.asStateFlow()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    val notesRoot: File get() = File(myState.notesRootPath)

    fun notifySettingsChanged() {
        _settingsVersion.value++
    }

    companion object {
        fun getInstance(): NoteMeSettings =
            ApplicationManager.getApplication().getService(NoteMeSettings::class.java)
    }
}
