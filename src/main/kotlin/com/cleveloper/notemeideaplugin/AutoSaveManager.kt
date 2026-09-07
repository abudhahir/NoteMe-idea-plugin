package com.cleveloper.notemeideaplugin

import kotlinx.coroutines.*
import java.io.File

/**
 * Manages debounced auto-save for edited note content.
 * Waits for user to stop typing for 500ms before saving to disk.
 */
class AutoSaveManager(private val coroutineScope: CoroutineScope) {

    private var saveJob: Job? = null
    private val SAVE_DELAY_MS = 500L

    private var lastSavedContent: String = ""
    var isSaving = false
        private set

    /**
     * Schedules a save operation.
     * Cancels previous pending save and starts a new one with debounce delay.
     */
    fun scheduleSave(file: File, content: String, onSaveComplete: () -> Unit = {}) {
        // Cancel previous save job if it's pending
        saveJob?.cancel()

        saveJob = coroutineScope.launch {
            delay(SAVE_DELAY_MS)

            // Only save if content actually changed
            if (content != lastSavedContent) {
                isSaving = true
                try {
                    withContext(Dispatchers.IO) {
                        file.writeText(content)
                    }
                    lastSavedContent = content
                    onSaveComplete()
                } finally {
                    isSaving = false
                }
            }
        }
    }

    /**
     * Immediately saves content without debounce delay.
     * Used for manual save (Ctrl+S) or when editor closes.
     */
    fun saveSynchronously(file: File, content: String) {
        saveJob?.cancel()

        if (content != lastSavedContent) {
            try {
                file.writeText(content)
                lastSavedContent = content
            } catch (e: Exception) {
                // Log error in production
                e.printStackTrace()
            }
        }
    }

    /**
     * Resets the saved content tracker (e.g., when opening a new file)
     */
    fun reset(content: String = "") {
        saveJob?.cancel()
        lastSavedContent = content
        isSaving = false
    }

    /**
     * Returns true if there are unsaved changes
     */
    fun hasUnsavedChanges(currentContent: String): Boolean {
        return currentContent != lastSavedContent
    }
}

