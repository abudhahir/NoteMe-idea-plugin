package com.cleveloper.notemeideaplugin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.key.*
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.Orientation
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RichTextEditor(
    selectedNote: String?,
    notesRoot: File,
    onClose: () -> Unit = {}
) {
    if (selectedNote == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Rich editor is open. Select a note from the tree to start editing.")
        }
        return
    }

    // Find the file for the selected note
    val noteFile = IndexManager.getFilePathForNote(notesRoot, selectedNote)

    if (noteFile == null || !noteFile.exists()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Note file not found")
        }
        return
    }

    // State management
    var content by remember(selectedNote) {
        mutableStateOf(noteFile.readText())
    }
    var selection by remember { mutableStateOf(TextRange(0)) }
    val coroutineScope = rememberCoroutineScope()
    val autoSaveManager = remember(selectedNote) {
        AutoSaveManager(coroutineScope).apply {
            reset(content)
        }
    }

    // Auto-save on content change
    LaunchedEffect(content) {
        autoSaveManager.scheduleSave(noteFile, content)
    }

    // Get theme colors
    val popupBackground = remember {
        val rgb = javax.swing.UIManager.getColor("Popup.background")?.rgb
            ?: javax.swing.UIManager.getColor("Panel.background")?.rgb
            ?: 0xFFFFFFFF.toInt()
        androidx.compose.ui.graphics.Color(rgb)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        // Formatting Toolbar
        FormattingToolbar(
            onBold = {
                val (newContent, newCursor) = MarkdownFormatting.insertBold(content, selection)
                content = newContent
                selection = TextRange(newCursor)
            },
            onItalic = {
                val (newContent, newCursor) = MarkdownFormatting.insertItalic(content, selection)
                content = newContent
                selection = TextRange(newCursor)
            },
            onHeading1 = {
                val (newContent, newCursor) = MarkdownFormatting.insertHeading(content, selection, 1)
                content = newContent
                selection = TextRange(newCursor)
            },
            onHeading2 = {
                val (newContent, newCursor) = MarkdownFormatting.insertHeading(content, selection, 2)
                content = newContent
                selection = TextRange(newCursor)
            },
            onHeading3 = {
                val (newContent, newCursor) = MarkdownFormatting.insertHeading(content, selection, 3)
                content = newContent
                selection = TextRange(newCursor)
            },
            onBulletList = {
                val (newContent, newCursor) = MarkdownFormatting.insertBulletList(content, selection)
                content = newContent
                selection = TextRange(newCursor)
            },
            onOrderedList = {
                val (newContent, newCursor) = MarkdownFormatting.insertOrderedList(content, selection)
                content = newContent
                selection = TextRange(newCursor)
            },
            onCode = {
                val (newContent, newCursor) = MarkdownFormatting.insertCode(content, selection)
                content = newContent
                selection = TextRange(newCursor)
            },
            onCodeBlock = {
                val (newContent, newCursor) = MarkdownFormatting.insertCodeBlock(content, selection)
                content = newContent
                selection = TextRange(newCursor)
            },
            onLink = {
                val (newContent, newCursor) = MarkdownFormatting.insertLink(content, selection)
                content = newContent
                selection = TextRange(newCursor)
            },
            hasUnsavedChanges = autoSaveManager.hasUnsavedChanges(content),
            onManualSave = {
                autoSaveManager.saveSynchronously(noteFile, content)
            },
            popupBackground = popupBackground
        )

        Divider(orientation = Orientation.Horizontal)

        // Title bar with note name
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Editing: $selectedNote",
                modifier = Modifier.weight(1f),
                style = TextStyle(fontSize = 12.sp)
            )
            if (autoSaveManager.hasUnsavedChanges(content)) {
                Text("●", style = TextStyle(fontSize = 10.sp))
            }
        }

        Divider(orientation = Orientation.Horizontal)

        // Text Editor
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .border(1.dp, JewelTheme.globalColors.borders.normal)
                .padding(8.dp)
        ) {
            BasicTextField(
                value = content,
                onValueChange = { newContent ->
                    content = newContent
                },
                modifier = Modifier
                    .fillMaxSize()
                    .onKeyEvent { keyEvent ->
                        when {
                            keyEvent.key == Key.S && keyEvent.isCtrlPressed && keyEvent.type == KeyEventType.KeyDown -> {
                                autoSaveManager.saveSynchronously(noteFile, content)
                                true
                            }
                            keyEvent.key == Key.B && keyEvent.isCtrlPressed && keyEvent.type == KeyEventType.KeyDown -> {
                                val (newContent, newCursor) = MarkdownFormatting.insertBold(content, selection)
                                content = newContent
                                selection = TextRange(newCursor)
                                true
                            }
                            keyEvent.key == Key.I && keyEvent.isCtrlPressed && keyEvent.type == KeyEventType.KeyDown -> {
                                val (newContent, newCursor) = MarkdownFormatting.insertItalic(content, selection)
                                content = newContent
                                selection = TextRange(newCursor)
                                true
                            }
                            else -> false
                        }
                    },
                singleLine = false,
                cursorBrush = SolidColor(JewelTheme.globalColors.outlines.focused),
                textStyle = TextStyle(color = JewelTheme.contentColor)
            )
        }
    }
}

@Composable
private fun FormattingToolbar(
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onHeading1: () -> Unit,
    onHeading2: () -> Unit,
    onHeading3: () -> Unit,
    onBulletList: () -> Unit,
    onOrderedList: () -> Unit,
    onCode: () -> Unit,
    onCodeBlock: () -> Unit,
    onLink: () -> Unit,
    hasUnsavedChanges: Boolean,
    onManualSave: () -> Unit,
    popupBackground: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Bold
        FormattingButton(
            label = "B",
            tooltip = "Bold (Ctrl+B)",
            onClick = onBold,
            popupBackground = popupBackground
        )

        // Italic
        FormattingButton(
            label = "I",
            tooltip = "Italic (Ctrl+I)",
            onClick = onItalic,
            popupBackground = popupBackground
        )

        // Separator
        Divider(orientation = Orientation.Vertical, modifier = Modifier.height(20.dp))

        // Headings
        FormattingButton(
            label = "H1",
            tooltip = "Heading 1",
            onClick = onHeading1,
            popupBackground = popupBackground
        )

        FormattingButton(
            label = "H2",
            tooltip = "Heading 2",
            onClick = onHeading2,
            popupBackground = popupBackground
        )

        FormattingButton(
            label = "H3",
            tooltip = "Heading 3",
            onClick = onHeading3,
            popupBackground = popupBackground
        )

        // Separator
        Divider(orientation = Orientation.Vertical, modifier = Modifier.height(20.dp))

        // Lists
        FormattingButton(
            label = "•",
            tooltip = "Bullet List",
            onClick = onBulletList,
            popupBackground = popupBackground
        )

        FormattingButton(
            label = "1.",
            tooltip = "Ordered List",
            onClick = onOrderedList,
            popupBackground = popupBackground
        )

        // Separator
        Divider(orientation = Orientation.Vertical, modifier = Modifier.height(20.dp))

        // Code
        FormattingButton(
            label = "`",
            tooltip = "Inline Code",
            onClick = onCode,
            popupBackground = popupBackground
        )

        FormattingButton(
            label = "{}",
            tooltip = "Code Block",
            onClick = onCodeBlock,
            popupBackground = popupBackground
        )

        // Link
        FormattingButton(
            label = "🔗",
            tooltip = "Link",
            onClick = onLink,
            popupBackground = popupBackground
        )

        Spacer(modifier = Modifier.weight(1f))

        // Save status and manual save button
        if (hasUnsavedChanges) {
            FormattingButton(
                label = "Save",
                tooltip = "Save (Ctrl+S)",
                onClick = onManualSave,
                popupBackground = popupBackground
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun FormattingButton(
    label: String,
    tooltip: String,
    onClick: () -> Unit,
    popupBackground: androidx.compose.ui.graphics.Color
) {
    TooltipArea(
        tooltip = {
            Box(
                modifier = Modifier
                    .background(popupBackground)
                    .border(1.dp, JewelTheme.globalColors.borders.normal)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(tooltip)
            }
        }
    ) {
        IconButton(onClick = onClick) {
            Text(label)
        }
    }
}

