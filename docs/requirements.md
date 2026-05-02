# Requirements
## Phase 1
1. add note icon should create a note in the current folder, if no node is selected it should create a note in the root folder
2. add tool tips to all the icons
3. create a welcome note with help information about the plugin, markdown tutorial, mermaid diagram, etc
4. for add note, name field need not be multi-line.
5. search field should search the contents of the notes 

# Phase 2
1. create a default index page
2. add sample headings to the index page. for e.g., 
   - Knowledege Base
      - Java
      - Components
   - Tools
   - How to
   - Tutorials
   - Others
3. read the heading structure of the index page and create a tree view of the notes
4. read the headding structure of he index and ask where does this new note go, add a link below the selected heading

# Phase 3
1. on hover on the heading or note, show the location of the note in the file system
2. add right mouse click menu to the heading and note, which will have options to rename, delete, move, copy, and open in file system
3. add keyboard shortcut 
   - to create a new note from anywhere 
   - new node from anywhere should show the possible to link as a bookmark from where the note is created
   - to open the note from anywhere


# Phase 4 — Settings and Configuration
1. Configurable notes root directory (default: ~/NoteMeNotes/)
2. Settings page under Settings > Tools > NoteMe
3. Confirmation dialog when changing root directory
4. Settings icon in toolbar (right-aligned)
5. Status bar at bottom of NoteMe window showing activity for all operations

# Phase 5 — Semantic Search (ChromaDB Vector Search)
1. Local vector store using LangChain4j InMemoryEmbeddingStore with file persistence
2. ONNX-based sentence embeddings (all-MiniLM-L6-v2) — fully offline
3. Markdown chunking by heading boundaries with large-section paragraph splitting
4. Semantic search toolbar button (disabled when ChromaDB is off)
5. Query popup with text input, ranked results (title, heading, excerpt, score), and stats footer
6. Re-index button in popup footer
7. First-enable indexing prompt when ChromaDB is toggled on in settings
8. Automatic re-indexing after Sync from Disk when reindexOnSync is enabled
9. Incremental indexing — skips unchanged files based on lastModified timestamp
10. Index persisted to notesRoot/.chromadb/embedding-store.json
