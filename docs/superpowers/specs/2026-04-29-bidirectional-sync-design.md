# NoteMe — Bidirectional Sync Design

**Date:** 2026-04-29
**Status:** Approved (v5 — post spec-review round 4)
**Scope:** Bidirectional sync between `index.md` and the filesystem in the NoteMe IntelliJ IDEA plugin

---

## Overview

The plugin tree is currently driven solely by `index.md`. This design adds the reverse direction: files and folders created on disk (outside the plugin) are picked up and reflected in `index.md` and the tree on demand, via a manual Sync action.

---

## Goals

- Directory structure on disk mirrors the heading hierarchy in `index.md` (index → disk, via existing `ensureDirectoryStructure`)
- Files added to disk outside the plugin appear in `index.md` and the tree after a manual sync (disk → index)
- Broken links (in `index.md` but file missing on disk) are surfaced visually with a user-action to remove them
- Items discovered from disk are visually distinguished from items created via the plugin

---

## Non-Goals

- Real-time/automatic file watching (out of scope; manual sync only)
- Renaming or moving files during sync (additive only)
- Syncing non-`.md` files
- Persisting `missingFiles` / `diskSourced` UI state across IDE restarts (deliberate; state reflects last sync only)

---

## Data Model

```kotlin
data class SyncResult(
    val addedHeadings: List<String>,                    // heading TITLES (last path segment) added from disk
    val addedLinks: List<String>,                       // note titles added from disk
    val missingFiles: List<Pair<String, String>>        // (title, relativePath) in index but no file on disk;
                                                        // preserves insertion order (order of occurrence in index.md)
)
```

**Note on `addedHeadings`:** Stores the heading title (the last path segment, e.g., `"Research"` from `"Knowledge Base/Research"`).

---

## Algorithm — `IndexManager.syncFromDisk(notesRoot): SyncResult`

`syncFromDisk` does **not** call `notifyMutation()`. The caller (UI) is responsible for coordinating state update and tree refresh after the call returns.

### Pass 1 — Raw line scan of `index.md`

**Do not call `parseIndex()`** — `IndexNode` carries no line index information. Instead, perform a direct line scan (same pattern used by `ensureDirectoryStructure`):

```
headingTriples: List<Triple<Int, String, String>>       // (lineIndex, title, dirPath)
knownDirPaths: Set<String>                              // all dirPath values
knownRelPaths: MutableSet<String>                       // all link relativePath values
lastLinkLineForHeading: MutableMap<Int, Int>            // headingLineIndex → last link/child line index
allLinks: MutableList<Pair<String, String>>             // (title, relPath) in order of occurrence
```

Scan `index.md` line by line:
- Heading line → push `Triple(lineIndex, title, dirPath)` onto `headingTriples`; add `dirPath` to `knownDirPaths`; init `lastLinkLineForHeading[lineIndex] = lineIndex`
- Link line → update `lastLinkLineForHeading[currentHeadingLineIndex] = lineIndex`; add `relPath` to `knownRelPaths`; append `(title, relPath)` to `allLinks`

### Pass 2 — Walk disk recursively

- Recurse through `notesRoot` depth-first, `maxDepth = 10` guard
- Skip `index.md` itself

**For each directory:**
- Compute `relDir` (forward-slash, no trailing slash, case-sensitive)
- If `relDir ∉ knownDirPaths`:
  - Determine nesting level from `/` count in `relDir`
  - Insert after the last line of the parent heading's block (last link or last child-heading line tracked in `lastLinkLineForHeading`); top-level headings appended after the last line of all existing top-level blocks
  - Update in-memory lines list and `headingTriples`, `knownDirPaths`, `lastLinkLineForHeading` for subsequent insertions in the same pass
  - Record last segment of `relDir` in `addedHeadings`

**For each `.md` file:**
- Compute `relPath` (forward-slash)
- If `relPath ∈ knownRelPaths` → skip (idempotent)
- Derive `parentDirPath = relPath.substringBeforeLast("/", "")`
- Find matching heading: search `headingTriples` for entry where `dirPath == parentDirPath` (exact, case-sensitive; disambiguates non-unique heading titles)
- If found: insert `- [title](relPath)` at `lastLinkLineForHeading[headingLineIndex] + 1`; update `lastLinkLineForHeading` and `knownRelPaths`; record title in `addedLinks`
- If not found and `parentDirPath == ""`: add under `# Others` (create heading if absent)

### Pass 3 — Detect missing files

- For every `(title, relPath)` in `allLinks` (preserved insertion order):
  - If `File(notesRoot, relPath).exists()` is false → add `(title, relPath)` to `missingFiles`

### Write & return

- Write updated in-memory lines list to `index.md`
- Call `ensureDirectoryStructure(notesRoot)` to materialise new headings as directories
- Return `SyncResult` — **do not** call `notifyMutation()`

---

## New IndexManager Methods

| Method | Signature | Responsibility |
|--------|-----------|---------------|
| `syncFromDisk` | `(notesRoot: File): SyncResult` | Runs all three passes, writes `index.md`, calls `ensureDirectoryStructure`, returns result |
| `removeFromIndex` | `(notesRoot: File, title: String, relativePath: String)` | Removes the first line exactly matching `- [title](relativePath)` from `index.md`; does not touch disk; caller must call `refreshTree()` after |
| `ensureDirectoryStructure` | `(notesRoot: File)` | (existing) Creates dirs for all heading nodes; also called at end of `syncFromDisk` |

**`removeFromIndex` algorithm:** Read `index.md` lines; find and remove the first line exactly equal to `"- [$title]($relativePath)"`; write back. Caller is responsible for calling `refreshTree()` afterward.

---

## UI Changes — `MyToolWindow.kt`

### New composable state

```kotlin
// Source of truth for broken-link removal; preserves insertion order from SyncResult
var missingFilePairs by remember { mutableStateOf(listOf<Pair<String, String>>()) }
// Derived from missingFilePairs for fast O(1) tree row lookup
var missingFileTitles by remember { mutableStateOf(setOf<String>()) }
// Titles of headings discovered from disk — matched against Tree.Element.Node only
var diskSourcedHeadings by remember { mutableStateOf(setOf<String>()) }
// Titles of notes discovered from disk — matched against Tree.Element.Leaf only
var diskSourcedNotes by remember { mutableStateOf(setOf<String>()) }
```

### Toolbar (new Sync button)

- Icon: `AllIconsKeys.Actions.Refresh`, tooltip: `"Sync from disk"`
- Placed between "Open Index" and "Add note" buttons
- On click (on `Dispatchers.IO`, state updates on `Dispatchers.Main`):
  1. Call `IndexManager.syncFromDisk(notesRoot)` → `result`
  2. `missingFilePairs = result.missingFiles`  ← preserves insertion order
  3. `missingFileTitles = result.missingFiles.map { it.first }.toSet()`
  4. `diskSourcedHeadings = result.addedHeadings.toSet()`
  5. `diskSourcedNotes = result.addedLinks.toSet()`
  6. Call `refreshTree()` — asynchronous; newly synced items appear only after IO/Main round-trip. Single-frame transient state (flags set before treeData updates) is acceptable.

### Tree row renderer — broken links

```kotlin
val isMissing = element.data in missingFileTitles
var rowHovered by remember(element.data) { mutableStateOf(false) }
```

- Row wrapped with `onPointerEvent` Enter/Exit to set `rowHovered`
- Label `Text` rendered at `alpha = 0.4f` when `isMissing`
- When `isMissing && rowHovered`: trailing `×` `IconButton` (`AllIconsKeys.Actions.Cancel`) at row end

**`×` click:**

`missingFilePairs` preserves insertion order (Pass 3 appends in `index.md` line order). `firstOrNull` is therefore deterministic: when two missing notes share a title, the one appearing first in `index.md` is always removed first. Iterative clicks remove them in order.

```kotlin
val pair = missingFilePairs.firstOrNull { it.first == element.data }
if (pair != null) {
    IndexManager.removeFromIndex(notesRoot, pair.first, pair.second)
    val remaining = missingFilePairs - pair
    missingFilePairs = remaining
    missingFileTitles = remaining.map { it.first }.toSet()
    refreshTree()   // required after removeFromIndex
}
```

### Tree row renderer — disk-sourced items

Separate sets prevent cross-type false positives (a note title matching a heading title, or vice versa):

```kotlin
val isDiskSourced = when (element) {
    is Tree.Element.Node -> element.data in diskSourcedHeadings
    is Tree.Element.Leaf -> element.data in diskSourcedNotes
    else -> false
}
```

If `isDiskSourced`: append `Icon(AllIconsKeys.Actions.Upload, contentDescription = "From disk", modifier = Modifier.size(16.dp))` after the label text.

---

## Known Limitations

| Scenario | Behaviour |
|----------|-----------|
| `.md` file directly in `notesRoot` | Added under `# Others` (created if absent) |
| Empty directory on disk | Added as heading with no links |
| `index.md` during walk | Skipped |
| Symlink cycle | Walk stops at `maxDepth = 10` |
| Special characters in filename | Display title = raw name; relPath normalized to forward-slash |
| Same file in multiple disk locations | Idempotency check on `relPath` prevents duplicate links |
| Sync run multiple times | Idempotent — known `relPath` values skipped |
| Two missing notes, same title, different paths | Both greyed out; `×` removes first (by `index.md` order) then second; iterative |
| Two missing notes, identical title AND identical path | Second entry permanently irremovable via UI; requires manual `index.md` edit. Known limitation. |
| Non-missing note with same title as a missing note | Also greyed out (false positive). Known limitation; acceptable given rarity. |
| Two disk-discovered headings sharing a last segment (e.g. `KB/Research` and `Tools/Research`) | Both show Upload icon if both were newly added. Known limitation — the icon is informational only. |
| Disk directory same name as existing heading at different nesting level | `dirPath` exact match prevents collision during insertion |
| New heading insertion position | After last line of parent heading's block; top-level after last top-level block |
| Link insertion under non-unique heading title | `headingTriples` `dirPath` match disambiguates; `lastLinkLineForHeading` targets correct line |
| New headings round-trip to disk as dirs | `ensureDirectoryStructure` called after `index.md` written |
| Sync state not persisted across IDE restarts | Deliberate non-goal |
| Brief async gap between state update and treeData refresh | Acceptable single-frame transient |

---

## State Management

All sync-derived state lives in `MyToolWindowContent` composable:

- `missingFilePairs` — insertion-ordered source of truth for `removeFromIndex` calls
- `missingFileTitles` — derived from `missingFilePairs`; recomputed on each sync and removal
- `diskSourcedHeadings` / `diskSourcedNotes` — cleared and repopulated on each sync; used with type-guarded tree rendering

---

## Sync is Additive Only

`syncFromDisk` **never** deletes headings or links from `index.md`. Removing broken links is an explicit user action via the `×` button, which calls `removeFromIndex` (caller must follow with `refreshTree()`).

---

## Files Affected

| File | Change |
|------|--------|
| `IndexManager.kt` | Add `SyncResult` data class; add `syncFromDisk()` and `removeFromIndex()` methods |
| `MyToolWindow.kt` | Add Sync toolbar button; add `missingFilePairs`, `missingFileTitles`, `diskSourcedHeadings`, `diskSourcedNotes` state; update tree row renderer |
