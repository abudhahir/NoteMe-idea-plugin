# Bidirectional Sync Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add bidirectional sync between `index.md` and the filesystem so disk-created files/folders appear in the NoteMe tree and broken links are surfaced with a remove action.

**Architecture:** A `syncFromDisk()` method on `IndexManager` performs a three-pass algorithm (raw line scan → disk walk → missing file detection), writing back to `index.md` and returning a `SyncResult`. The UI wires a Sync toolbar button to this method and updates four composable state variables to drive broken-link and disk-sourced rendering in the tree.

**Tech Stack:** Kotlin 2.1.20, IntelliJ Platform 2025.2, Jewel (`LazyTree`, `IconButton`), Compose for Desktop

**Spec:** `docs/superpowers/specs/2026-04-29-bidirectional-sync-design.md`

---

## File Map

| File | Action | What changes |
|------|--------|-------------|
| `src/main/kotlin/com/cleveloper/notemeideaplugin/IndexManager.kt` | Modify | Add `SyncResult` data class, `headingLevel()` helper, `removeFromIndex()`, `syncFromDisk()` |
| `src/main/kotlin/com/cleveloper/notemeideaplugin/MyToolWindow.kt` | Modify | Add four composable state vars, Sync toolbar button, update tree row renderer |

---

## Chunk 1: IndexManager.kt changes

### Task 1: Add `SyncResult` data class, `headingLevel()` helper, and `removeFromIndex()`

**Files:**
- Modify: `src/main/kotlin/com/cleveloper/notemeideaplugin/IndexManager.kt`

- [ ] **Step 1.1 — Add `SyncResult` data class**

  In `IndexManager.kt`, add `SyncResult` immediately after the `IndexNode` data class (around line 15), before `object IndexManager`:

  ```kotlin
  data class SyncResult(
      val addedHeadings: List<String>,          // heading titles (last path segment) added from disk
      val addedLinks: List<String>,             // note titles added from disk
      val missingFiles: List<Pair<String, String>> // (title, relativePath) in index but missing on disk
  )
  ```

- [ ] **Step 1.2 — Add `headingLevel()` private helper**

  Inside `object IndexManager`, add this private helper after the existing `notifyMutation()` function:

  ```kotlin
  private fun headingLevel(line: String): Int =
      Regex("^(#+)").find(line)?.groupValues?.get(1)?.length ?: 0
  ```

- [ ] **Step 1.3 — Add `removeFromIndex()`**

  Inside `object IndexManager`, add this method after `ensureDirectoryStructure()`:

  ```kotlin
  /**
   * Removes the first line in index.md that exactly matches "- [title](relativePath)".
   * Does not delete any file on disk. Caller must call refreshTree() after this.
   */
  fun removeFromIndex(notesRoot: File, title: String, relativePath: String) {
      val indexFile = getIndexFile(notesRoot)
      val lines = indexFile.readLines().toMutableList()
      val target = "- [$title]($relativePath)"
      val idx = lines.indexOfFirst { it == target }
      if (idx != -1) {
          lines.removeAt(idx)
          indexFile.writeText(lines.joinToString("\n"))
          LocalFileSystem.getInstance().refreshAndFindFileByIoFile(indexFile)
      }
  }
  ```

- [ ] **Step 1.4 — Build to verify compilation**

  ```bash
  ./gradlew build
  ```

  Expected: `BUILD SUCCESSFUL`. Fix any compilation errors before continuing.

- [ ] **Step 1.5 — Commit**

  ```bash
  git add src/main/kotlin/com/cleveloper/notemeideaplugin/IndexManager.kt
  git commit -m "feat: add SyncResult, headingLevel helper, and removeFromIndex to IndexManager"
  ```

---

### Task 2: Add `syncFromDisk()` to `IndexManager`

**Files:**
- Modify: `src/main/kotlin/com/cleveloper/notemeideaplugin/IndexManager.kt`

- [ ] **Step 2.1 — Add `syncFromDisk()` method**

  Inside `object IndexManager`, add this method after `removeFromIndex()`. Read carefully — this is the core algorithm:

  ```kotlin
  /**
   * Bidirectional sync: walks the disk, adds new directories as headings and
   * new .md files as links in index.md, and detects links whose files are missing.
   *
   * Does NOT call notifyMutation(). The caller must coordinate state updates
   * and call refreshTree() after storing the returned SyncResult.
   *
   * Link insertion position: new links are inserted after the last DIRECT link
   * under the parent heading (before any sub-headings), tracked via lastDirectLinkLine.
   * New headings are inserted at the end of the parent heading's full block.
   */
  fun syncFromDisk(notesRoot: File): SyncResult {
      val indexFile = getIndexFile(notesRoot)
      val lines = indexFile.readLines().toMutableList()

      // ── Local data structure ─────────────────────────────────────────────────
      // var lineIndex so it can be shifted when we insert lines
      data class HeadingTriple(var lineIndex: Int, val title: String, val dirPath: String)

      // ── Mutable tracking maps (declared early so insertAt can update them) ───
      val headingTriples = mutableListOf<HeadingTriple>()
      // headingLineIndex → last direct link line index under that heading
      // Initialized to the heading's own line; updated when a link is found or inserted.
      val lastDirectLinkLine = mutableMapOf<Int, Int>()

      // ── Local helpers ────────────────────────────────────────────────────────

      // Returns the index of the last line in `triple`'s full block
      // (just before next sibling/ancestor heading, or end of file).
      // Used for heading insertion only.
      fun blockEnd(triple: HeadingTriple): Int {
          val myLevel = headingLevel(lines[triple.lineIndex])
          for (t in headingTriples) {
              if (t.lineIndex > triple.lineIndex && headingLevel(lines[t.lineIndex]) <= myLevel) {
                  return t.lineIndex - 1
              }
          }
          return lines.size - 1
      }

      // Inserts `content` at position `at`, then shifts all tracked indices >= at.
      fun insertAt(at: Int, content: String) {
          lines.add(at, content)
          // Shift heading triple lineIndexes
          headingTriples.forEach { if (it.lineIndex >= at) it.lineIndex++ }
          // Shift lastDirectLinkLine keys and values
          val copy = lastDirectLinkLine.toMap()
          lastDirectLinkLine.clear()
          copy.forEach { (k, v) ->
              lastDirectLinkLine[if (k >= at) k + 1 else k] = if (v >= at) v + 1 else v
          }
      }

      // Converts an absolute File to a notesRoot-relative forward-slash path.
      fun toRelPath(file: File): String =
          file.relativeTo(notesRoot).path.replace(File.separatorChar, '/')

      // ── Pass 1: raw line scan of index.md ────────────────────────────────────
      val knownDirPaths = mutableSetOf<String>()
      val knownRelPaths = mutableSetOf<String>()
      val originalLinks = mutableListOf<Pair<String, String>>() // for Pass 3
      val stack = mutableListOf<HeadingTriple>()
      var currentHeadingLineIndex = -1

      for ((idx, line) in lines.withIndex()) {
          val hm = Regex("^(#+)\\s+(.*)$").find(line)
          if (hm != null) {
              val level = hm.groupValues[1].length
              val title = hm.groupValues[2].trim()
              while (stack.isNotEmpty() && headingLevel(lines[stack.last().lineIndex]) >= level) {
                  stack.removeAt(stack.size - 1)
              }
              val parentPath = stack.lastOrNull()?.dirPath ?: ""
              val dirPath = if (parentPath.isEmpty()) title else "$parentPath/$title"
              val triple = HeadingTriple(idx, title, dirPath)
              headingTriples.add(triple)
              knownDirPaths.add(dirPath)
              lastDirectLinkLine[idx] = idx  // initialise to heading's own line
              currentHeadingLineIndex = idx
              stack.add(triple)
          } else {
              val lm = Regex("^- \\[(.*)\\]\\((.*)\\)$").find(line)
              if (lm != null) {
                  knownRelPaths.add(lm.groupValues[2])
                  originalLinks.add(lm.groupValues[1] to lm.groupValues[2])
                  if (currentHeadingLineIndex != -1) {
                      lastDirectLinkLine[currentHeadingLineIndex] = idx
                  }
              }
          }
      }

      // ── Pass 2: disk walk ─────────────────────────────────────────────────────
      val addedHeadings = mutableListOf<String>()
      val addedLinks = mutableListOf<String>()

      // Inserts a new heading for `relDir`. Uses blockEnd() so headings always
      // appear at the end of the parent's block (after existing sub-headings).
      fun insertHeading(relDir: String, title: String) {
          val level = relDir.count { it == '/' } + 1
          val prefix = "#".repeat(level)
          val parentDirPath = relDir.substringBeforeLast("/", "")

          val at = if (parentDirPath.isEmpty()) {
              val lastTopLevel = headingTriples.lastOrNull { !it.dirPath.contains('/') }
              if (lastTopLevel != null) blockEnd(lastTopLevel) + 1 else lines.size
          } else {
              val parentTriple = headingTriples.firstOrNull { it.dirPath == parentDirPath }
              if (parentTriple != null) blockEnd(parentTriple) + 1 else lines.size
          }

          insertAt(at, "$prefix $title")  // also shifts lastDirectLinkLine
          val newTriple = HeadingTriple(at, title, relDir)
          val pos = headingTriples.indexOfFirst { it.lineIndex > at }
          if (pos == -1) headingTriples.add(newTriple) else headingTriples.add(pos, newTriple)
          lastDirectLinkLine[at] = at  // new heading, no links yet
          knownDirPaths.add(relDir)
          addedHeadings.add(title)
      }

      fun ensureOthers() {
          if ("Others" !in knownDirPaths) insertHeading("Others", "Others")
      }

      // Inserts a new link after the last DIRECT link under the parent heading
      // (before any sub-headings), using lastDirectLinkLine for the position.
      fun insertLink(noteTitle: String, relPath: String, parentDirPath: String) {
          val parentTriple = if (parentDirPath.isEmpty()) {
              ensureOthers()
              headingTriples.first { it.dirPath == "Others" }
          } else {
              headingTriples.firstOrNull { it.dirPath == parentDirPath } ?: return
          }
          val at = (lastDirectLinkLine[parentTriple.lineIndex] ?: parentTriple.lineIndex) + 1
          insertAt(at, "- [$noteTitle]($relPath)")  // also shifts lastDirectLinkLine
          // Update this heading's lastDirectLinkLine to the newly inserted link
          lastDirectLinkLine[parentTriple.lineIndex] = at
          knownRelPaths.add(relPath)
          addedLinks.add(noteTitle)
      }

      fun walkDir(dir: File, depth: Int) {
          if (depth > 10) return
          // Dirs first so parent headings exist before their children's files are inserted
          val children = dir.listFiles()
              ?.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
              ?: return
          for (child in children) {
              if (child.isDirectory) {
                  val relDir = toRelPath(child)
                  if (relDir !in knownDirPaths) insertHeading(relDir, child.name)
                  walkDir(child, depth + 1)
              } else if (child.isFile && child.extension == "md" && child.name != INDEX_FILE_NAME) {
                  val relPath = toRelPath(child)
                  if (relPath !in knownRelPaths) {
                      val parentDirPath = relPath.substringBeforeLast("/", "")
                      insertLink(child.nameWithoutExtension, relPath, parentDirPath)
                  }
              }
          }
      }

      walkDir(notesRoot, 0)

      // ── Pass 3: detect missing files ──────────────────────────────────────────
      val missingFiles = originalLinks.filter { (_, relPath) ->
          !File(notesRoot, relPath).exists()
      }

      // ── Write & return ────────────────────────────────────────────────────────
      indexFile.writeText(lines.joinToString("\n"))
      LocalFileSystem.getInstance().refreshAndFindFileByIoFile(indexFile)
      ensureDirectoryStructure(notesRoot)

      return SyncResult(addedHeadings, addedLinks, missingFiles)
  }
  ```

- [ ] **Step 2.2 — Build to verify compilation**

  ```bash
  ./gradlew build
  ```

  Expected: `BUILD SUCCESSFUL`. Common pitfalls:
  - `continue` inside a lambda won't compile — use `if/else` guards instead (code above already does this)
  - Local `fun` inside `fun syncFromDisk` can reference enclosing mutable state — this is valid Kotlin
  - `data class HeadingTriple` with `var lineIndex` must be `var` (not `val`) so `insertAt` can mutate it in place
  - `lastDirectLinkLine` keys shift on every `insertAt` call — this is handled by the map-copy-and-rebuild pattern in `insertAt`

- [ ] **Step 2.3 — Commit**

  ```bash
  git add src/main/kotlin/com/cleveloper/notemeideaplugin/IndexManager.kt
  git commit -m "feat: add syncFromDisk() bidirectional sync to IndexManager"
  ```

---

## Chunk 2: MyToolWindow.kt changes

### Task 3: Add composable state and Sync toolbar button

**Files:**
- Modify: `src/main/kotlin/com/cleveloper/notemeideaplugin/MyToolWindow.kt`

- [ ] **Step 3.1 — Add four composable state variables**

  In `MyToolWindowContent`, find the block of existing `var` state declarations (around line 101, after `var searchQuery by remember { mutableStateOf("") }`). Add these four new state variables directly after `searchQuery`:

  ```kotlin
  // Sync state — populated by the Sync button; cleared and repopulated on each sync
  var missingFilePairs by remember { mutableStateOf(listOf<Pair<String, String>>()) }
  var missingFileTitles by remember { mutableStateOf(setOf<String>()) }
  var diskSourcedHeadings by remember { mutableStateOf(setOf<String>()) }
  var diskSourcedNotes by remember { mutableStateOf(setOf<String>()) }
  ```

- [ ] **Step 3.2 — Add Sync toolbar button**

  In the toolbar `Row`, find the existing "Open Index" `TooltipArea` block. Add a new `TooltipArea` block immediately **after** the closing `}` of the "Open Index" block and **before** the "Add note" block:

  ```kotlin
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
  ```

  > **Note:** If `AllIconsKeys.Actions.Refresh` does not resolve, use `AllIconsKeys.Actions.Redo` or `AllIconsKeys.General.Reset` as a fallback.

- [ ] **Step 3.3 — Build to verify compilation**

  ```bash
  ./gradlew build
  ```

  Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3.4 — Commit**

  ```bash
  git add src/main/kotlin/com/cleveloper/notemeideaplugin/MyToolWindow.kt
  git commit -m "feat: add sync state vars and Sync toolbar button to MyToolWindow"
  ```

---

### Task 4: Update tree row renderer — broken links and disk-sourced icons

**Files:**
- Modify: `src/main/kotlin/com/cleveloper/notemeideaplugin/MyToolWindow.kt`

- [ ] **Step 4.1 — Update the `LazyTree` item block**

  Find the `LazyTree(...) { element ->` block. Replace everything inside the `{ element ->` lambda (from `val nodePath` through to the closing `}` of the `TooltipArea`) with the following. This preserves all existing behaviour and adds broken-link and disk-sourced rendering:

  ```kotlin
  { element ->
      val nodePath = remember(element.data) {
          if (element is Tree.Element.Node<*>) {
              val dirPath = IndexManager.getHeadingDirPath(notesRoot, element.data)
              if (dirPath != null) File(notesRoot, dirPath).absolutePath else notesRoot.absolutePath
          } else {
              IndexManager.getFilePathForNote(notesRoot, element.data)?.absolutePath
                  ?: findFileForElement(element.data, notesRoot)?.absolutePath
                  ?: "Location unknown"
          }
      }

      // Sync-derived rendering state
      val isMissing = element.data in missingFileTitles
      val isDiskSourced = when (element) {
          is Tree.Element.Node<*> -> element.data in diskSourcedHeadings
          is Tree.Element.Leaf<*> -> element.data in diskSourcedNotes
          else -> false
      }
      var rowHovered by remember(element.data) { mutableStateOf(false) }
      var elementPosition by remember(element.data) { mutableStateOf(IntOffset.Zero) }

      TooltipArea(
          tooltip = {
              Box(modifier = Modifier
                  .background(popupBackground)
                  .border(1.dp, JewelTheme.globalColors.borders.normal)
                  .padding(horizontal = 8.dp, vertical = 4.dp)
              ) {
                  Text(if (isMissing) "$nodePath (file missing)" else nodePath)
              }
          },
          modifier = Modifier.fillMaxWidth()
              .onGloballyPositioned { coordinates ->
                  elementPosition = coordinates.positionInWindow().round()
              }
              .onPointerEvent(PointerEventType.Enter) { rowHovered = true }
              .onPointerEvent(PointerEventType.Exit) { rowHovered = false }
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
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.padding(vertical = 2.dp, horizontal = 4.dp).fillMaxWidth()
          ) {
              Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.weight(1f, fill = false)
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
                  Text(
                      text = element.data,
                      color = if (isMissing) JewelTheme.contentColor.copy(alpha = 0.4f)
                              else JewelTheme.contentColor
                  )
                  if (isDiskSourced) {
                      Spacer(modifier = Modifier.width(4.dp))
                      Icon(
                          key = AllIconsKeys.Actions.Upload,
                          contentDescription = "From disk",
                          modifier = Modifier.size(16.dp)
                      )
                  }
              }
              if (isMissing && rowHovered) {
                  IconButton(
                      onClick = {
                          val pair = missingFilePairs.firstOrNull { it.first == element.data }
                          if (pair != null) {
                              coroutineScope.launch(Dispatchers.IO) {
                                  IndexManager.removeFromIndex(notesRoot, pair.first, pair.second)
                                  withContext(Dispatchers.Main) {
                                      val remaining = missingFilePairs - pair
                                      missingFilePairs = remaining
                                      missingFileTitles = remaining.map { it.first }.toSet()
                                      refreshTree()
                                  }
                              }
                          }
                      },
                      modifier = Modifier.size(16.dp)
                  ) {
                      Icon(
                          key = AllIconsKeys.Actions.Cancel,
                          contentDescription = "Remove broken link",
                          modifier = Modifier.size(12.dp)
                      )
                  }
              }
          }
      }
  }
  ```

- [ ] **Step 4.2 — Build to verify compilation**

  ```bash
  ./gradlew build
  ```

  Expected: `BUILD SUCCESSFUL`. Common pitfalls:
  - `Tree.Element.Leaf<*>` and `Tree.Element.Node<*>` require the `<*>` wildcard in `when` branches
  - `Arrangement.SpaceBetween` is in `androidx.compose.foundation.layout` (already imported via `*`)
  - If `AllIconsKeys.Actions.Upload` does not resolve, use `AllIconsKeys.Actions.MoveToButton` or `AllIconsKeys.Nodes.DataSource` as fallback

- [ ] **Step 4.3 — Manual smoke test via `runIde`**

  ```bash
  ./gradlew runIde
  ```

  In the sandboxed IDE:
  1. Open any project. The NoteMe tool window should appear.
  2. Click the **Sync** (Refresh) icon in the toolbar — no crash, tree refreshes.
  3. Drop a `.md` file into `~/NoteMeNotes/Knowledge Base/` from Finder.
  4. Click **Sync** — the new note appears in tree with Upload icon.
  5. Delete that file directly from Finder (not via plugin).
  6. Click **Sync** — the note appears greyed out.
  7. Hover the greyed-out note — `×` button appears on the right.
  8. Click `×` — note disappears from tree, link removed from `index.md`.

- [ ] **Step 4.4 — Commit**

  ```bash
  git add src/main/kotlin/com/cleveloper/notemeideaplugin/MyToolWindow.kt
  git commit -m "feat: update tree renderer with broken-link and disk-sourced indicators"
  ```

---

## Done

All tasks complete. The bidirectional sync feature is implemented:

- `IndexManager.syncFromDisk()` — three-pass algorithm writes index.md from disk state
- `IndexManager.removeFromIndex()` — targeted broken-link removal
- Sync toolbar button — coordinates IO work and composable state updates
- Tree renderer — greyed broken links with `×` hover-remove, Upload icon for disk-sourced items
