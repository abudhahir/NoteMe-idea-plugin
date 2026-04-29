package com.cleveloper.notemeideaplugin

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.TooltipArea
import androidx.compose.ui.text.font.FontWeight
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import org.jetbrains.jewel.foundation.lazy.rememberSelectableLazyListState
import org.jetbrains.jewel.bridge.addComposeTab
import org.jetbrains.jewel.foundation.lazy.tree.buildTree
import org.jetbrains.jewel.foundation.lazy.tree.rememberTreeState
import org.jetbrains.jewel.foundation.lazy.tree.Tree
import org.jetbrains.jewel.ui.component.LazyTree
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.ui.text.TextStyle
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Divider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.foundation.ExperimentalFoundationApi
import org.jetbrains.jewel.ui.Orientation
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.LaunchedEffect
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.round
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ide.CopyPasteManager
import java.awt.datatransfer.StringSelection
import java.awt.Desktop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState

class MyToolWindowFactory : ToolWindowFactory {
    override fun shouldBeAvailable(project: Project) = true

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.addComposeTab("NoteMe", focusOnClickInside = true) {
            MyToolWindowContent(project)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun MyToolWindowContent(project: Project) {
    val notesRoot = File(System.getProperty("user.home"), "NoteMeNotes")
    IndexManager.getIndexFile(notesRoot)

    fun getTreeFromIndex(): List<Any> {
        val rootNodes = IndexManager.parseIndex(notesRoot)
        fun nodeToTree(node: IndexNode): Any {
            val children = node.children.map { nodeToTree(it) }.toMutableList<Any>()
            children.addAll(node.links.map { it.first }) // links are (title, relativePath) pairs
            return if (children.isEmpty()) {
                node.title
            } else {
                node.title to children
            }
        }
        return rootNodes.map { nodeToTree(it) }
    }

    var treeData by remember { mutableStateOf(getTreeFromIndex()) }
    val mutationCount by IndexManager.mutationCount.collectAsState()
    var selectedElement by remember { mutableStateOf<Tree.Element<String>?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    // Sync state — populated by the Sync button; cleared and repopulated on each sync
    var missingFilePairs by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var missingFileTitles by remember { mutableStateOf(setOf<String>()) }
    var diskSourcedHeadings by remember { mutableStateOf(setOf<String>()) }
    var diskSourcedNotes by remember { mutableStateOf(setOf<String>()) }
    val coroutineScope = rememberCoroutineScope()
    val popupBackground = remember {
        val rgb = javax.swing.UIManager.getColor("Popup.background")?.rgb
            ?: javax.swing.UIManager.getColor("Panel.background")?.rgb
            ?: 0xFFFFFFFF.toInt()
        Color(rgb)
    }

    fun refreshTree() {
        coroutineScope.launch(Dispatchers.IO) {
            val newData = getTreeFromIndex()
            withContext(Dispatchers.Main) {
                treeData = newData
            }
        }
    }

    // Refresh the tree whenever IndexManager reports a mutation (e.g., note created via shortcut)
    LaunchedEffect(mutationCount) {
        if (mutationCount > 0) {
            val newData = withContext(Dispatchers.IO) { getTreeFromIndex() }
            treeData = newData
        }
    }

    // Helper to build Jewel Tree from our structured data
    fun buildJewelTree(data: List<Any>): Tree<String> = buildTree {
        data.forEach { item ->
            when (item) {
                is String -> addLeaf(item)
                is Pair<*, *> -> {
                    val (name, children) = item as Pair<String, List<Any>>
                    addNode(name) {
                        children.forEach { child ->
                            when (child) {
                                is String -> addLeaf(child)
                                is Pair<*, *> -> {
                                    val (cName, cChildren) = child as Pair<String, List<Any>>
                                    addNode(cName) {
                                        cChildren.forEach { leaf -> addLeaf(leaf.toString()) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val filteredTreeData = remember(treeData, searchQuery) {
        if (searchQuery.isEmpty()) {
            buildJewelTree(treeData)
        } else {
            buildTree {
                fun filterNodes(data: List<Any>) {
                    data.forEach { item ->
                        val name = if (item is Pair<*, *>) item.first.toString() else item.toString()
                        val isLeaf = item !is Pair<*, *>

                        val matchesName = name.contains(searchQuery, ignoreCase = true)
                        val matchesContent = if (isLeaf) {
                            val resolvedFile = IndexManager.getFilePathForNote(notesRoot, name)
                            resolvedFile != null && resolvedFile.exists() && resolvedFile.readText().contains(searchQuery, ignoreCase = true)
                        } else false

                        if (matchesName || matchesContent) {
                            addLeaf(name)
                        }
                        
                        if (item is Pair<*, *>) {
                            filterNodes(item.second as List<Any>)
                        }
                    }
                }
                filterNodes(treeData)
            }
        }
    }

    val listState = rememberLazyListState()
    val selectionState = rememberSelectableLazyListState()
    val treeState = rememberTreeState(listState, selectionState)
    val deleteEnabled = selectedElement != null
    var isSearching by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    var isAdding by remember { mutableStateOf(false) }
    var newNodeName by remember { mutableStateOf("") }
    var showHeadingPicker by remember { mutableStateOf(false) }
    val addFocusRequester = remember { FocusRequester() }

    fun findFileForElement(data: String, dir: File): File? {
        val items = dir.listFiles() ?: return null
        for (item in items) {
            if (item.name == data) return item
            if (item.name == "$data.md") return item
            if (item.isDirectory) {
                val found = findFileForElement(data, item)
                if (found != null) return found
            }
        }
        return null
    }

    fun openMarkdownFile(leafName: String) {
        // Prefer the index-resolved path; fall back to filesystem scan if not yet indexed
        val file = IndexManager.getFilePathForNote(notesRoot, leafName)
            ?: findFileForElement(leafName, notesRoot)
            ?: return
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
        if (virtualFile != null) {
            FileEditorManager.getInstance(project).openFile(virtualFile, true)
        }
    }

    fun addNote() {
        if (newNodeName.isNotBlank()) {
            showHeadingPicker = true
        }
    }

    fun finalizeAddNote(heading: String) {
        val noteFile = IndexManager.addNoteToIndex(notesRoot, newNodeName, heading)
        if (noteFile == null) {
            Messages.showErrorDialog(project, "Heading '$heading' not found in index.", "Error")
            return
        }
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(noteFile)
        if (virtualFile != null) {
            FileEditorManager.getInstance(project).openFile(virtualFile, true)
        }
        refreshTree()
        isAdding = false
        newNodeName = ""
        showHeadingPicker = false
    }

    var contextMenuVisible by remember { mutableStateOf(false) }
    var contextMenuOffset by remember { mutableStateOf(IntOffset.Zero) }
    var contextMenuTarget by remember { mutableStateOf<String?>(null) }

    fun renameElement(oldName: String, newName: String) {
        val file = findFileForElement(oldName, notesRoot) ?: return
        val newFile = File(file.parentFile, if (file.isDirectory) newName else if (newName.endsWith(".md")) newName else "$newName.md")
        if (file.renameTo(newFile)) {
            // Update index.md to reflect the name change
            val indexFile = IndexManager.getIndexFile(notesRoot)
            val content = indexFile.readText()
            val oldLink = "[$oldName]"
            val newLink = "[$newName]"
            val oldFileLink = "($oldName.md)"
            val newFileLink = "($newName.md)"
            
            val escapedOldName = Regex.escape(oldName)
            val headingRegex = Regex("^(#+)\\s+${escapedOldName}$", RegexOption.MULTILINE)
            val updatedContent = content
                .replace(headingRegex) { matchResult -> "${matchResult.groupValues[1]} $newName" }
                .replace(oldLink, newLink)
                .replace(oldFileLink, newFileLink)
            
            indexFile.writeText(updatedContent)
            refreshTree()
        }
    }

    fun copyElement(name: String) {
        val file = findFileForElement(name, notesRoot) ?: return
        CopyPasteManager.getInstance().setContents(StringSelection(file.absolutePath))
    }

    fun openInFileSystem(name: String) {
        // Resolve as note file, then as heading directory, then recursive search, then root
        val resolved = IndexManager.getFilePathForNote(notesRoot, name)
            ?: run {
                val dirPath = IndexManager.getHeadingDirPath(notesRoot, name)
                if (dirPath != null) File(notesRoot, dirPath) else null
            }
            ?: findFileForElement(name, notesRoot)
            ?: notesRoot

        // Walk up to the nearest existing ancestor if the resolved path doesn't exist yet
        val target = generateSequence(resolved) { it.parentFile }.firstOrNull { it.exists() } ?: notesRoot

        val os = System.getProperty("os.name").lowercase()
        when {
            os.contains("mac") -> Runtime.getRuntime().exec(arrayOf("open", "-R", target.absolutePath))
            os.contains("win") -> Runtime.getRuntime().exec(arrayOf("explorer", "/select,${target.absolutePath}"))
            else -> if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(
                if (target.isDirectory) target else target.parentFile
            )
        }
    }

    fun deleteElement(name: String) {
        val file = findFileForElement(name, notesRoot)
        file?.deleteRecursively()
        
        // Update index.md
        val indexFile = IndexManager.getIndexFile(notesRoot)
        val lines = indexFile.readLines().toMutableList()
        val oldLinkPart = "[$name]"
        
        val updatedLines = lines.filter { line ->
            !line.contains(oldLinkPart) && !line.replace(Regex("^#+\\s+"), "").trim().equals(name, ignoreCase = false)
        }
        
        indexFile.writeText(updatedLines.joinToString("\n"))
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(indexFile)
        refreshTree()
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // Left Side: Tree (Now taking full width or staying as a sidebar)
        Column(modifier = Modifier.fillMaxSize()) {
            if (isSearching) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(popupBackground)
                            .border(1.dp, JewelTheme.globalColors.borders.normal)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester)
                                .onKeyEvent {
                                    if (it.type == KeyEventType.KeyDown && it.key == Key.Enter) {
                                        isSearching = false
                                        true
                                    } else if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) {
                                        isSearching = false
                                        searchQuery = ""
                                        true
                                    } else {
                                        false
                                    }
                                },
                            textStyle = TextStyle(color = JewelTheme.contentColor),
                            cursorBrush = SolidColor(JewelTheme.contentColor),
                            singleLine = true,
                            decorationBox = { inner ->
                                if (searchQuery.isEmpty()) {
                                    Text("Search notes...", color = JewelTheme.contentColor.copy(alpha = 0.5f))
                                }
                                inner()
                            }
                        )
                    }
                    TooltipArea(
                        tooltip = {
                            Box(modifier = Modifier
                                .background(popupBackground)
                                .border(1.dp, JewelTheme.globalColors.borders.normal)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Cancel search")
                            }
                        }
                    ) {
                        IconButton(onClick = { 
                            isSearching = false
                            searchQuery = ""
                        }) {
                            Icon(AllIconsKeys.Actions.Cancel, contentDescription = "Cancel")
                        }
                    }
                    LaunchedEffect(Unit) {
                        searchFocusRequester.requestFocus()
                    }
                }
            }

            if (isAdding) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(popupBackground)
                            .border(1.dp, JewelTheme.globalColors.borders.normal)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        BasicTextField(
                            value = newNodeName,
                            onValueChange = { newNodeName = it },
                            modifier = Modifier.fillMaxWidth().focusRequester(addFocusRequester)
                                .onKeyEvent {
                                    if (it.type == KeyEventType.KeyDown && it.key == Key.Enter) {
                                        addNote()
                                        true
                                    } else if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) {
                                        isAdding = false
                                        newNodeName = ""
                                        true
                                    } else {
                                        false
                                    }
                                },
                            textStyle = TextStyle(color = JewelTheme.contentColor),
                            cursorBrush = SolidColor(JewelTheme.contentColor),
                            singleLine = true,
                            decorationBox = { inner ->
                                if (newNodeName.isEmpty()) {
                                    Text("Note name...", color = JewelTheme.contentColor.copy(alpha = 0.5f))
                                }
                                inner()
                            }
                        )
                    }
                    TooltipArea(
                        tooltip = {
                            Box(modifier = Modifier
                                .background(popupBackground)
                                .border(1.dp, JewelTheme.globalColors.borders.normal)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Confirm")
                            }
                        }
                    ) {
                        IconButton(onClick = { addNote() }) {
                            Icon(AllIconsKeys.Actions.Checked, contentDescription = "Confirm")
                        }
                    }
                    TooltipArea(
                        tooltip = {
                            Box(modifier = Modifier
                                .background(popupBackground)
                                .border(1.dp, JewelTheme.globalColors.borders.normal)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Cancel")
                            }
                        }
                    ) {
                        IconButton(onClick = { 
                            isAdding = false
                            newNodeName = ""
                        }) {
                            Icon(AllIconsKeys.Actions.Cancel, contentDescription = "Cancel")
                        }
                    }
                    LaunchedEffect(Unit) {
                        addFocusRequester.requestFocus()
                    }
                }

                if (showHeadingPicker) {
                    Popup(alignment = Alignment.TopStart, onDismissRequest = { showHeadingPicker = false }) {
                        Box(
                            modifier = Modifier
                                .width(200.dp)
                                .background(popupBackground)
                                .border(1.dp, JewelTheme.globalColors.borders.normal)
                                .padding(vertical = 4.dp)
                        ) {
                            Column {
                                Text("Select the target topic", 
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    style = TextStyle(fontWeight = FontWeight.Bold)
                                )
                                Divider(orientation = Orientation.Horizontal)
                                LazyColumn {
                                    items(IndexManager.getAllHeadings(notesRoot)) { heading ->
                                        var headingHovered by remember { mutableStateOf(false) }
                                        Text(
                                            text = heading,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(if (headingHovered) JewelTheme.globalColors.outlines.focused.copy(alpha = 0.15f) else Color.Transparent)
                                                .onPointerEvent(PointerEventType.Enter) { headingHovered = true }
                                                .onPointerEvent(PointerEventType.Exit) { headingHovered = false }
                                                .clickable { finalizeAddNote(heading) }
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TooltipArea(
                    tooltip = {
                        Box(modifier = Modifier
                                .background(popupBackground)
                                .border(1.dp, JewelTheme.globalColors.borders.normal)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                            Text("Open index (landing page)")
                        }
                    }
                ) {
                    IconButton(onClick = {
                        val indexFile = IndexManager.getIndexFile(notesRoot)
                        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(indexFile)
                        if (virtualFile != null) {
                            FileEditorManager.getInstance(project).openFile(virtualFile, true)
                        }
                    }, enabled = true) {
                        Icon(AllIconsKeys.Actions.ListFiles, contentDescription = "Open Index")
                    }
                }
                TooltipArea(
                    tooltip = {
                        Box(modifier = Modifier
                            .background(popupBackground)
                            .border(1.dp, JewelTheme.globalColors.borders.normal)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Sync from disk")
                        }
                    }
                ) {
                    IconButton(onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            val result = IndexManager.syncFromDisk(notesRoot)
                            withContext(Dispatchers.Main) {
                                missingFilePairs = result.missingFiles
                                missingFileTitles = result.missingFiles.map { it.first }.toSet()
                                diskSourcedHeadings = result.addedHeadings.toSet()
                                diskSourcedNotes = result.addedLinks.toSet()
                                refreshTree()
                            }
                        }
                    }, enabled = true) {
                        Icon(AllIconsKeys.Actions.Refresh, contentDescription = "Sync from disk")
                    }
                }
                TooltipArea(
                    tooltip = {
                        Box(modifier = Modifier
                                .background(popupBackground)
                                .border(1.dp, JewelTheme.globalColors.borders.normal)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                            Text("Add note")
                        }
                    }
                ) {
                    IconButton(onClick = { isAdding = !isAdding }, enabled = true) {
                        Icon(AllIconsKeys.General.Add, contentDescription = "Add")
                    }
                }
                TooltipArea(
                    tooltip = {
                        Box(modifier = Modifier
                                .background(popupBackground)
                                .border(1.dp, JewelTheme.globalColors.borders.normal)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                            Text("Search notes")
                        }
                    }
                ) {
                    IconButton(onClick = { isSearching = !isSearching }, enabled = true) {
                        Icon(AllIconsKeys.Actions.Search, contentDescription = "Search")
                    }
                }
                TooltipArea(
                    tooltip = {
                        Box(modifier = Modifier
                                .background(popupBackground)
                                .border(1.dp, JewelTheme.globalColors.borders.normal)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                            Text("Delete selected")
                        }
                    }
                ) {
                    IconButton(onClick = {
                        val element = selectedElement
                        if (element != null) {
                            val dataToRemove = element.data
                            deleteElement(dataToRemove)
                            selectedElement = null
                        }
                    }, enabled = deleteEnabled) {
                        Icon(AllIconsKeys.General.Delete, contentDescription = "Delete")
                    }
                }
            }

            Divider(orientation = Orientation.Horizontal)

            LazyTree(
                tree = filteredTreeData,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                onElementClick = {
                    selectedElement = it
                    if (it is Tree.Element.Leaf) {
                        openMarkdownFile(it.data)
                    }
                },
                treeState = treeState,
                onElementDoubleClick = {},
                onSelectionChange = {
                    val last = it.lastOrNull()
                    selectedElement = last
                },
                interactionSource = remember { MutableInteractionSource() }
            ) { element ->
                val nodePath = remember(element.data) {
                    if (element is Tree.Element.Node<*>) {
                        // Heading node → show the directory path (may not exist yet if no notes added)
                        val dirPath = IndexManager.getHeadingDirPath(notesRoot, element.data)
                        if (dirPath != null) File(notesRoot, dirPath).absolutePath else notesRoot.absolutePath
                    } else {
                        // Leaf note → resolve via index first, then fallback to recursive search
                        IndexManager.getFilePathForNote(notesRoot, element.data)?.absolutePath
                            ?: findFileForElement(element.data, notesRoot)?.absolutePath
                            ?: "Location unknown"
                    }
                }

                var elementPosition by remember(element.data) { mutableStateOf(IntOffset.Zero) }

                TooltipArea(
                    tooltip = {
                        Box(modifier = Modifier
                                .background(popupBackground)
                                .border(1.dp, JewelTheme.globalColors.borders.normal)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                            Text(nodePath)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            elementPosition = coordinates.positionInWindow().round()
                        }
                        .onPointerEvent(PointerEventType.Release) {
                            if (it.button == PointerButton.Secondary || it.buttons.isSecondaryPressed) {
                                contextMenuTarget = element.data
                                val position = it.changes.first().position
                                contextMenuOffset = IntOffset(
                                    (elementPosition.x + position.x).toInt(),
                                    (elementPosition.y + position.y).toInt()
                                )
                                contextMenuVisible = true
                            }
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp, horizontal = 4.dp).fillMaxWidth()
                    ) {
                        val iconKey = if (element is Tree.Element.Node<*>) {
                            AllIconsKeys.Nodes.Folder
                        } else {
                            AllIconsKeys.FileTypes.Text
                        }
                        Icon(
                            key = iconKey,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(element.data)
                    }
                }
            }

            if (contextMenuVisible && contextMenuTarget != null) {
                val target = contextMenuTarget!!
                Popup(
                    offset = contextMenuOffset,
                    onDismissRequest = { contextMenuVisible = false },
                    properties = PopupProperties(focusable = true)
                ) {
                    Box(
                        modifier = Modifier
                            .background(popupBackground)
                            .border(1.dp, JewelTheme.globalColors.borders.normal)
                            .padding(vertical = 4.dp)
                    ) {
                        Column {
                            ContextMenuItem("Rename") {
                                contextMenuVisible = false
                                val newName = Messages.showInputDialog(project, "Enter new name", "Rename", null, target, null)
                                if (newName != null && newName != target) {
                                    renameElement(target, newName)
                                }
                            }
                            ContextMenuItem("Delete") {
                                contextMenuVisible = false
                                if (Messages.showYesNoDialog(project, "Delete $target?", "Delete", null) == Messages.YES) {
                                    deleteElement(target)
                                }
                            }
                            ContextMenuItem("Copy Path") {
                                contextMenuVisible = false
                                copyElement(target)
                            }
                            ContextMenuItem("Open in File System") {
                                contextMenuVisible = false
                                openInFileSystem(target)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ContextMenuItem(label: String, onClick: () -> Unit) {
    var hovered by remember { mutableStateOf(false) }
    Text(
        text = label,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (hovered) JewelTheme.globalColors.outlines.focused.copy(alpha = 0.15f) else Color.Transparent)
            .hoverable(interactionSource = remember { MutableInteractionSource() }, enabled = true)
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}
