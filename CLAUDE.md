# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

NoteMe is an IntelliJ IDEA plugin that provides a note management tool window inside the IDE. Notes are stored as Markdown files in `~/NoteMeNotes/` and organized via a structured `index.md` file. The UI is built with Compose for Desktop via JetBrains Jewel.

## Build & Run Commands

```bash
# Build the plugin
./gradlew build

# Run the plugin in a sandboxed IDE instance
./gradlew runIde

# Run tests
./gradlew test

# Verify plugin compatibility
./gradlew verifyPlugin

# Publish to JetBrains Marketplace
./gradlew publishPlugin
```

The `.run/` directory contains pre-configured IntelliJ Run/Debug configurations for these tasks.

## Architecture

### Core Components

- **`IndexManager.kt`** — Singleton that owns all `index.md` operations. It parses the heading/link structure into `IndexNode` trees, creates the default index and welcome note on first run, and handles add/rename/delete mutations. All note metadata lives in `~/NoteMeNotes/index.md`; note content lives in `.md` files alongside it.

- **`MyToolWindow.kt`** — The main UI. `MyToolWindowFactory` registers the Compose tab. `MyToolWindowContent` is a single large `@Composable` that manages all tool window state: tree display, search, add-note flow, context menu, and file open. It reads from `IndexManager` and calls it to mutate state, then refreshes the tree.

- **`MyActions.kt`** — Two `AnAction` subclasses registered in `plugin.xml`:
  - `CreateNoteAction` (Ctrl+Alt+N): Shows an input dialog, creates the `.md` file, prompts for a target heading, and updates the index.
  - `OpenNoteAction` (Ctrl+Alt+O): Opens the NoteMe tool window.

- **`NoteMeStartupActivity.kt`** — `ProjectActivity` that runs on project open to inject the NoteMe action group into the editor title bar.

### Data Flow

```
index.md (~/NoteMeNotes/index.md)
    └── IndexManager.parseIndex() → List<IndexNode>
        └── MyToolWindowContent.getTreeFromIndex() → List<Any> (Pair tree)
            └── buildJewelTree() → Tree<String> (Jewel LazyTree)
```

The `index.md` uses standard Markdown heading hierarchy (`#`, `##`) as folders/topics, and `- [NoteName](NoteName.md)` links as leaf notes.

### Key Dependencies

- **IntelliJ Platform 2025.2.4** (build `252.25557+`)
- **Jewel** — JetBrains Compose UI library for IDE-native look and feel (`LazyTree`, `IconButton`, `TooltipArea`, etc.)
- **Bundled plugin**: `org.intellij.plugins.markdown` (required dependency)
- **JVM target**: Java 21 / Kotlin 2.1.20

## Plugin Registration (`plugin.xml`)

- Tool window ID: `MyToolWindow`
- Action group: `NoteMe.EditorToolbar` — added to editor context bar, popup menu, nav bar, main toolbar, Markdown toolbars, and tab popup menu
- Startup activity: `NoteMeStartupActivity`

## Notes Storage Convention

- All notes stored at `~/NoteMeNotes/`
- `index.md` is the single source of truth for tree structure
- Notes are flat files (no subdirectory nesting) despite the heading hierarchy in `index.md`
- On first run, a default `index.md` with standard headings and a `Welcome.md` note are auto-created

## Requirements & Roadmap

See `docs/requirements.md` for the phased feature roadmap (Phases 1–3), covering search, index-driven tree, right-click menus, and keyboard shortcuts.
