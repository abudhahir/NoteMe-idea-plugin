# Rich Text Editor - Debugging Guide

**Status**: Build successful ✅ | Debug logging added ✅

## Expected Behavior After Fix

When you click the **Edit** button (pencil icon) in the NoteMe toolbar, it should:
1. Open the selected note in the **IntelliJ editor tab** (top of editor area)
2. Update status bar with: `"Opened: [note-name]"`
3. Show debug logs in IDE console

---

## How to Test

### Step 1: Start the Plugin in Debug Mode

```bash
cd /Users/abu/projects/tooling/NoteMe-idea-plugin
./gradlew runIde
```

This opens a sandboxed IntelliJ IDE with the plugin loaded.

### Step 2: Open NoteMe Tool Window

1. Once IDE loads, go to **View → Tool Windows → NoteMe**
2. Or press `Ctrl+Alt+O`
3. You should see the NoteMe panel on the right with:
   - Toolbar at top (icons for Add, Edit, Search, Delete, etc.)
   - Tree view showing notes

### Step 3: Create a Test Note (if none exist)

1. Click **Add** button (+ icon)
2. Type a note name: `"TestNote"`
3. Select a heading category when prompted
4. The tree should now show `TestNote`

### Step 4: Test the Edit Button

1. **Click on a note** in the tree to select it
2. **Click the Edit button** (pencil icon)
3. **Watch for**:
   - Console output with debug logs
   - IDE status bar update
   - Editor tab opening at top

---

## Debug Output to Check

The IDE console (at bottom of IDE window) should show:

```
[NoteMe] Edit button clicked
[NoteMe] selectedLeaf=TestNote, fallback=null, target=TestNote
[NoteMe] openMarkdownFile: leafName=TestNote, file exists=true, path=/Users/abu/NoteMeNotes/TestNote.md
[NoteMe] File opened: TestNote
```

### If you see this, it's working ✅

---

## Troubleshooting

### Issue 1: "No notes available to open" in status bar

**Debug output:**
```
[NoteMe] selectedLeaf=null, fallback=null, target=null
[NoteMe] No notes available
```

**Cause**: Tree is empty or notes weren't loaded

**Solution**:
1. Create a test note via **Add** button
2. Sync notes via **Sync** button (refresh icon)
3. Close and reopen the tool window

---

### Issue 2: "File not found" in status bar

**Debug output:**
```
[NoteMe] openMarkdownFile: leafName=TestNote, file exists=false, path=/Users/abu/NoteMeNotes/TestNote.md
```

**Cause**: Note file doesn't exist on disk

**Solution**:
1. Manually create: `/Users/abu/NoteMeNotes/TestNote.md`
2. Add content like: `# Test Note\n\nHello world`
3. Try Edit button again

---

### Issue 3: Console shows "[NoteMe] VFS refresh failed"

**Debug output:**
```
[NoteMe] VFS refresh failed for: /Users/abu/NoteMeNotes/TestNote.md
```

**Cause**: IntelliJ VFS cannot access the file

**Solution**:
1. File permissions: `chmod 644 /Users/abu/NoteMeNotes/TestNote.md`
2. Ensure path has no special characters
3. Restart IDE: `./gradlew runIde` again

---

## Console Output Location

### How to view debug logs:

1. **In running IDE**:
   - Click **View → Tool Windows → Run**
   - Or press `Ctrl+Alt+R`
   - Look for console tab at bottom
   - Scroll to see `[NoteMe]` prefixed logs

2. **In terminal** (while IDE is running):
   - Watch tail of build output
   - Debug logs print to stdout

---

## Quick Test Commands

### View logs in real-time:
```bash
cd /Users/abu/projects/tooling/NoteMe-idea-plugin
./gradlew runIde 2>&1 | grep "\[NoteMe\]"
```

### Verify note files exist:
```bash
ls -la ~/NoteMeNotes/*.md
```

### Check file permissions:
```bash
stat ~/NoteMeNotes/TestNote.md
```

---

## Expected Result

After clicking Edit on a valid note:
- ✅ Editor tab appears at top of IDE
- ✅ Markdown content visible
- ✅ Status shows: "Opened: TestNote"
- ✅ Console shows successful debug logs

---

## Next Steps If Still Not Working

1. **Clear caches**:
   ```bash
   rm -rf build/ .gradle/
   ./gradlew build -x test
   ```

2. **Check IDE Event Log**:
   - Help → Show Log in Explorer
   - Search for "NoteMe" or "FileEditorManager"

3. **Verify IndexManager is working**:
   - Check that tree shows notes correctly
   - Try double-clicking a note (should also open)

4. **Test file I/O directly**:
   - Create `/Users/abu/NoteMeNotes/Manual.md`
   - Manually add entry to index.md
   - Try Edit button on manual entry

---

## Code Locations

| Component | File | Line |
|-----------|------|------|
| Edit button click handler | MyToolWindow.kt | 698 |
| openMarkdownFile function | MyToolWindow.kt | 249 |
| firstNoteFromTree helper | MyToolWindow.kt | ~219 |
| IndexManager lookup | IndexManager.kt | 331 |

---

## Summary

The Edit button now:
1. ✅ Logs all steps to console
2. ✅ Tries multiple fallback strategies to find a note
3. ✅ Provides clear error messages
4. ✅ Opens files via IntelliJ FileEditorManager

Use the debug output above to identify exactly where the flow is breaking.


