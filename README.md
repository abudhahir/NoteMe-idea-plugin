# NoteMe — IntelliJ IDEA Plugin

A note management plugin that lives inside your IDE. Create, organise, and search Markdown notes without leaving IntelliJ. Notes are stored as plain Markdown files on disk and organised through a structured `index.md` file, keeping them readable and portable outside the IDE.

---

## Features

### Note Tree
- Displays all notes as a hierarchical tree, driven by the heading structure in `~/NoteMeNotes/index.md`
- Folder nodes correspond to Markdown headings (`#`, `##`); leaf nodes are individual note files
- Hover over any node to see its full path on disk as a tooltip
- **Broken-link indicator** — notes whose files are missing on disk appear dimmed; hover to reveal a remove button that cleans the dangling entry from the index
- **Disk-sourced indicator** — notes discovered via Sync from Disk show a small upload icon badge so you can distinguish them from manually created notes

### Toolbar Actions

The tool window toolbar (top row) provides quick access to every major action:

| Icon | Action | Description |
|---|---|---|
| **+** (Add) | Add Note | Enter a note name inline, pick a topic heading, and the note is created and opened |
| **Magnifier** (Search) | Search Notes | Opens a text filter bar — matches note titles and full file content |
| **Trash** (Delete) | Delete Selected | Deletes the currently selected note with a confirmation dialog |
| **Binoculars** (Find) | Semantic Search | Opens the ChromaDB semantic search popup (requires ChromaDB enabled in settings) |
| **List Files** | Open Index | Opens `index.md` (the landing page / source of truth) directly in the editor |
| **Sync** | Sync Notes | Presents a dialog to choose Sync from Disk or Sync from Index |
| **Gear** (Settings) | Open Settings | Jumps directly to **Settings > Tools > NoteMe** |

### Add Note (Tool Window)
- Click the **+** icon in the tool window toolbar to enter a note name inline
- A topic picker popup lists all headings from `index.md` — click a heading to place the note there
- The note file is created inside the matching subdirectory and opened immediately in the editor
- The tree refreshes automatically after creation

### Search
- Click the **Search** icon in the toolbar to open the search bar
- Filters the tree by matching against both the note name and the full note file content
- Press `Escape` or click **Cancel** to clear search and restore the full tree

### Semantic Search (ChromaDB)
- Enable in **Settings > Tools > NoteMe** by checking "Enable file-based ChromaDB indexing"
- Click the **Semantic Search** icon in the toolbar to open the query popup
- Type a natural language question and press `Enter` — results are ranked by semantic similarity
- Each result shows the note title, heading path, excerpt, and similarity score
- Click a result to open the note in the editor
- Re-index button in the popup footer rebuilds the search index
- Index is persisted to `<notesRoot>/.chromadb/embedding-store.json` and survives IDE restarts
- Powered by LangChain4j with ONNX all-MiniLM-L6-v2 embeddings — fully offline, no API keys needed

### Sync
- Click the **Sync** icon for bidirectional sync options:
  - **Sync from Disk** — scans notes folder and adds new files/folders into `index.md`
  - **Sync from Index** — reads `index.md` and creates missing folders on disk
- When "Re-index notes on sync" is enabled in settings, Sync from Disk also rebuilds the semantic search index

### Settings
- **Settings > Tools > NoteMe** or click the **gear icon** in the toolbar
- **Notes source** — choose between **Local** (folder on disk) or **Git** (clone a remote repository)
- **Notes root directory** — configurable path for local source (default: `~/NoteMeNotes/`)
- **Git repository URL** — when Git source is selected, enter the remote URL; the plugin clones the repo using IntelliJ's built-in Git integration and sets the clone directory as the notes root
- **Enable file-based ChromaDB indexing** — gates the semantic search feature
- **Re-index notes on sync** — automatically rebuilds search index after Sync from Disk

### Git Repository Source
- In settings, select the **Git** radio button and enter a repository URL (e.g. `https://github.com/user/notes.git`)
- On apply, the plugin prompts you to choose a parent directory, then clones the repository using IntelliJ's Git4Idea integration with a progress dialog
- If the target directory already exists, you can choose to use it as-is without re-cloning
- The cloned directory becomes the notes root — all notes are read from and written to it
- Switching back to **Local** restores the standard folder-based workflow

### Right-Click Context Menu
Right-click any note node in the tree for quick actions:

| Action | Description |
|---|---|
| Rename | Renames the note file on disk and updates the link in `index.md` |
| Delete | Deletes the file with a confirmation dialog and removes its index entry |
| Copy Path | Copies the absolute file path to the clipboard |
| Open in File System | Reveals the file or folder in Finder / Explorer |

### Global Keyboard Shortcuts

| Shortcut | Action |
|---|---|
| `Ctrl+Alt+N` | Create a new note from anywhere in the IDE |
| `Ctrl+Alt+O` | Open the NoteMe tool window |

Both actions are also accessible from the editor context bar, editor right-click menu, nav bar toolbar, main toolbar, and Markdown editor toolbars.

### Source Capture When Creating Notes via Shortcut

When `Ctrl+Alt+N` is pressed while an editor file is open, the created note automatically includes a source context block at the bottom:

**Without a selection** — appends a link to the current file:
```markdown
---

**Source:** [MyService.kt](file:///Users/you/project/src/MyService.kt)
```

**With text selected** — appends the file with line reference, the selected text as a fenced code block, and creates an IntelliJ line bookmark at the selection start:
```markdown
---

**Source:** [MyService.kt:L42-L57](file:///Users/you/project/src/MyService.kt)

```
fun processPayment(order: Order): Result {
    ...
}
```

*IntelliJ bookmark added at L42-L57 — navigate via the Bookmarks panel*
```

The `file://` link opens the source file in the editor. For line-precise navigation back to the exact selection, open **View → Tool Windows → Bookmarks**.

### Status Bar
The bottom of the tool window displays a contextual status message — showing sync results (headings/notes added, missing files detected), search result counts, error messages, and the current notes root path.

### Auto Tree Refresh
The tool window tree updates automatically whenever a note is added via the keyboard shortcut, even if the tool window was already open in the background.

---

## Notes Storage

All notes are stored in `~/NoteMeNotes/` as plain Markdown files.

```
~/NoteMeNotes/
├── index.md                          ← source of truth for tree structure
├── Knowledge Base/
│   ├── Java/
│   │   └── JavaGenerics.md
│   └── Components/
│       └── ButtonDesign.md
├── Tools/
│   └── GradleCheatsheet.md
├── How to/
├── Tutorials/
└── Others/
    └── Welcome.md
```

`index.md` uses standard Markdown headings for topic folders and link entries for notes:

```markdown
# Knowledge Base
## Java
- [JavaGenerics](Knowledge Base/Java/JavaGenerics.md)
## Components
# Tools
- [GradleCheatsheet](Tools/GradleCheatsheet.md)
# How to
# Tutorials
# Others
- [Welcome](Others/Welcome.md)
```

On first run, a default `index.md` with the following headings is created automatically, along with a `Welcome.md` note under **Others**:

- Knowledge Base → Java, Components
- Tools
- How to
- Tutorials
- Others

---

## Usage Guide

### Creating your first note

1. Open any project in IntelliJ IDEA.
2. Open the **NoteMe** tool window from the side panel, or press `Ctrl+Alt+O`.
3. Click the **+** (Add) icon in the toolbar.
4. Type a note name and press `Enter`.
5. In the topic picker popup, click the heading you want the note filed under.
6. The note opens in the editor — start writing.

### Creating a note from code context

1. Open any source file in the editor.
2. Optionally select a block of code you want to reference.
3. Press `Ctrl+Alt+N`.
4. Enter a note name → select a topic.
5. The note is created with your selection captured as a code block and a bookmark added at that line.

### Searching notes

1. Click the **Search** (magnifier) icon in the NoteMe toolbar, or use `Ctrl+Alt+O` to bring up the window first.
2. Type to filter — matches note titles and full content of all note files.
3. Click a result to open the note.

### Renaming or deleting a note

Right-click the note in the tree → choose **Rename** or **Delete**.
Rename updates both the file on disk and the link in `index.md`. Delete removes both.

### Finding notes on disk

Hover over any node to see the full path. Right-click → **Open in File System** to reveal it in Finder / Explorer.

---

## Build & Run

### Prerequisites
- IntelliJ IDEA 2025.1 or later
- JDK 21
- Gradle (wrapper included — no separate install needed)

### Commands

```bash
# Run plugin in a sandboxed IDE instance
./gradlew runIde

# Build the distributable plugin zip
./gradlew build

# Run tests
./gradlew test

# Verify plugin compatibility
./gradlew verifyPlugin
```

The plugin zip is output to `build/distributions/`.

---

## Project Structure

```
src/main/kotlin/com/cleveloper/notemeideaplugin/
├── IndexManager.kt                    Singleton owning all index.md parsing and mutations
├── MyToolWindow.kt                    Jewel-based Compose UI — tree, search, add flow, context menu, semantic search popup
├── MyActions.kt                       CreateNoteAction (Ctrl+Alt+N) and OpenNoteAction (Ctrl+Alt+O)
├── NoteFileWritingAccessExtension.kt  Allows editing note files that live outside the project root
├── NoteMeStartupActivity.kt           Injects the NoteMe action group into the editor title bar on startup
├── NoteMeSettings.kt                  PersistentStateComponent for plugin settings (root dir, ChromaDB flags)
├── NoteMeSettingsConfigurable.kt      Settings UI under Settings > Tools > NoteMe
├── MarkdownChunker.kt                 Splits markdown into heading-based chunks for vector indexing
├── EmbeddingService.kt                ONNX-based sentence embedding (all-MiniLM-L6-v2) via LangChain4j
└── VectorSearchManager.kt             Vector store management — index, query, re-index, persistence
```

### Key Dependencies

| Dependency | Version |
|---|---|
| IntelliJ Platform | 2025.2.4 (`252.25557+`) |
| Jewel (Compose UI) | bundled with platform |
| Kotlin | 2.1.20 |
| JVM target | Java 21 |
| Required bundled plugin | `org.intellij.plugins.markdown` |
| LangChain4j | 1.14.0 |
| LangChain4j ONNX MiniLM | 1.14.0-beta24 |
