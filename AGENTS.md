# AGENTS.md
## Critical guidance for AI agents working on NoteMe—IntelliJ IDEA plugin

### Architecture Overview

**NoteMe** is a plugin that manages Markdown notes inside the IntelliJ IDE. The architecture has four layers:

1. **Data Persistence** — `index.md` + individual `.md` files live in `~/NoteMeNotes/` (configurable)
2. **State Management** — `IndexManager` singleton parses the index tree and emits mutations via StateFlow  
3. **UI Layer** — `MyToolWindowContent` is a single large Compose function using Jewel (IntelliJ's native Compose library)
4. **Plugin Integration** — IntelliJ extension points: tool window, actions, settings, startup activity

**Critical design principle**: The index file (`index.md`) is the single source of truth. Markdown headings (`#`, `##`) define folder structure; links (`- [Title](path/to/file.md)`) define individual notes.

### Build & Developer Workflow

```bash
# Run in sandboxed IDE (opens a separate IDE instance with the plugin loaded)
./gradlew runIde

# Build plugin distributable
./gradlew build  # outputs to build/distributions/

# Run unit tests
./gradlew test

# Verify plugin compatibility with IDE version
./gradlew verifyPlugin
```

**Key build details**:
- Gradle Configuration Cache enabled (`gradle.properties`)
- IntelliJ Platform 2025.2.4 (`252.25557+`)
- Kotlin 2.1.20 + Compose plugin
- JVM target: Java 21
- Bundled dependencies: `org.intellij.plugins.markdown`, `Git4Idea`
- External: `LangChain4j 1.14.0` for embeddings

### Core Components & Patterns

#### **IndexManager** (Singleton)
- **File**: `IndexManager.kt`
- **Role**: Parses `index.md` into a tree of `IndexNode` objects; handles all mutations to the index
- **Key methods**:
  - `parseIndex(notesRoot)` — parses heading hierarchy + links into tree
  - `addNoteToIndex(notesRoot, title, parentHeading)` — adds a note link under a heading
  - `renameNote(notesRoot, oldPath, newPath, newTitle)` — renames file + updates link
  - `deleteFromIndex(notesRoot, title, dirPath)` — removes link from index
  - `syncFromDisk(notesRoot)` — discovers new files/folders, updates index
  - `syncFromIndex(notesRoot)` — ensures directory structure matches index
- **State flow**: Emits `mutationCount` StateFlow when index changes (UI subscription triggers re-render)
- **Conventions**: 
  - Relative paths from `notesRoot` (e.g., `"Knowledge Base/Java/JavaGenerics.md"`)
  - Headings max 2 levels deep (top-level `#`, sub-level `##`)
  - Note files stored flat—no nested directories per note

#### **MyToolWindow** (UI Composition)
- **File**: `MyToolWindow.kt` (~1150 lines)
- **Role**: Entire UI rendered as a single `@Composable` function using Jewel components
- **Key sections**:
  - **Tree rendering**: `LazyTree` from Jewel with right-click context menu (Rename, Delete, Copy Path, Open in File System)
  - **Toolbar**: Search filter, add-note inline input, semantic search, settings, sync
  - **Semantic search popup**: Natural language query → top-k ranked results
  - **Status bar**: Shows sync results, error messages, current notes root
- **State management**: 
  - Subscribes to `IndexManager.mutationCount` to refresh tree
  - Uses Compose `remember` + `MutableState` for UI local state (search filter, selection, etc.)
  - Coroutine scope for async operations (file I/O, vector search)
- **Conventions**: 
  - Icons from `AllIconsKeys` (Jewel standard)
  - Dialog prompts use `Messages.showInputDialog()`, confirmation via `Messages.showOkCancelDialog()`
  - Right-click menu position captured with `onPointerEvent()` + `onGloballyPositioned()`

#### **CreateNoteAction & OpenNoteAction** (AnAction)
- **File**: `MyActions.kt`
- **Role**: Global keyboard shortcuts (`Ctrl+Alt+N`, `Ctrl+Alt+O`)
- **CreateNoteAction** context capture:
  - Captures currently open editor file path via `FileEditorManager.getInstance(project).selectedTextEditor`
  - If text is selected: appends code block + creates bookmark at selection start
  - If no selection: appends file link
  - Source context block format: `**Source:** [FileName.kt:L42-L57](file:///...)`
- **Convention**: Uses `LocalFileSystem.getInstance().refreshAndFindFileByIoFile()` to notify IDE of new files

#### **EmbeddingService & VectorSearchManager** (Semantic Search)
- **Files**: `EmbeddingService.kt`, `VectorSearchManager.kt`, `MarkdownChunker.kt`
- **Role**: Local vector store + offline ONNX embeddings (all-MiniLM-L6-v2) via LangChain4j
- **Chunking strategy**: Markdown chunker splits by heading boundaries; large sections further split by paragraphs
- **Index persistence**: JSON file at `<notesRoot>/.chromadb/embedding-store.json`
- **Incremental indexing**: Tracks `File.lastModified()` to skip unchanged files
- **Convention**: Index rebuilds on Sync from Disk if `reindexOnSync` setting is enabled

#### **NoteMeSettings & NoteMeSettingsConfigurable**
- **Files**: `NoteMeSettings.kt`, `NoteMeSettingsConfigurable.kt`
- **Pattern**: `PersistentStateComponent` singleton for app-level settings persistence
- **Key settings**:
  - `notesRoot` — local directory path or Git clone directory
  - `sourceType` — "Local" vs "Git" (enum)
  - `gitUrl` — remote repository URL
  - `enableChromaDB` — toggles semantic search
  - `reindexOnSync` — auto-rebuild vector index after Sync from Disk
- **UI**: Settings Configurable registered in `plugin.xml` under `Settings > Tools > NoteMe`

### Critical Data Structures

```kotlin
// Core tree node
data class IndexNode(
    val title: String,
    val level: Int,              // heading level (1 or 2)
    val children: MutableList<IndexNode> = mutableListOf(),
    val links: MutableList<Pair<String, String>> = mutableListOf(),  // (title, relativePath)
    val dirPath: String = ""     // e.g., "Knowledge Base/Java"
)

// Sync operation result
data class SyncResult(
    val addedHeadings: List<String>,      // new folder names discovered
    val addedLinks: List<String>,         // new note files discovered
    val missingFiles: List<Pair<String, String>>  // (title, path) in index but not on disk
)
```

### Integration Points & Platform APIs

- **File editing outside project root**: `NoteFileWritingAccessExtension` (nonProjectFileWritingAccessExtension)
- **Startup hook**: `NoteMeStartupActivity` (postStartupActivity) injects action group into editor toolbar on IDE startup
- **Tool window registration**: `MyToolWindowFactory` creates Compose tab with Jewel bridge
- **Markdown dependency check**: Plugin declares hard dependency on `org.intellij.plugins.markdown`
- **Git integration**: Uses `Git4Idea` bundled plugin for clone operations

### Common Developer Tasks

**Adding a toolbar button**:
1. Add Compose `IconButton` in `MyToolWindowContent`
2. Wrap icon with `TooltipArea()` for hover text
3. Coroutine-scoped action: `rememberCoroutineScope().launch { ... }`

**Modifying index structure**:
1. Update `IndexManager` method (parse, add, rename, delete)
2. Fire `notifyMutation()` at end of method
3. UI automatically re-renders via StateFlow subscription

**Adding a new setting**:
1. Add field to `NoteMeSettings` with `@get:OptionTag` annotation
2. Add UI control to `NoteMeSettingsConfigurable`
3. Reference via `NoteMeSettings.getInstance().myNewSetting`

**Changing note storage location**:
- Update `settings.notesRoot` and call `settingsVersion.value++` to trigger UI refresh
- All file I/O uses `File(notesRoot, ...)` relative path pattern

### Testing & Verification

- Unit tests in `src/test/` follow platform test framework (`testFramework(Platform)`)
- Plugin verification: `./gradlew verifyPlugin` checks IDE compatibility
- Manual testing: `./gradlew runIde` launches separate IDE with plugin active
- No integration tests for file system ops—use `LocalFileSystem` mock helpers for isolation

### Anti-Patterns to Avoid

- **Hard paths**: Never use absolute paths; always relative to `notesRoot`
- **Direct file reads**: Use `LocalFileSystem.getInstance()` for VFS refresh notifications
- **Blocking UI**: Always launch coroutines for I/O; use `withContext(Dispatchers.IO)`
- **Skipping index write**: All mutations must trigger `notifyMutation()` so UI subscribers refresh
- **Ignoring settings version**: Always check `settingsVersion` in Compose effect to detect config changes

### Entry Points for New Features

1. **New toolbar action** — add to `MyToolWindowContent` + register in `plugin.xml`
2. **New setting** — add to `NoteMeSettings` + UI in `NoteMeSettingsConfigurable`
3. **New tree mutation** — add method to `IndexManager` + emit `notifyMutation()`
4. **New search capability** — extend `VectorSearchManager` or add new search implementation
5. **New file sync strategy** — extend sync logic in `IndexManager.syncFromDisk()` or `syncFromIndex()`

---

See `README.md` for feature overview and `docs/requirements.md` for phase-based roadmap.

