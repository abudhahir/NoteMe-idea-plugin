# NoteMe — ChromaDB Vector Search Implementation Plan

**Date:** 2026-05-01
**Status:** Draft
**Design Doc:** [chromadb-vector-search-design.md](../specs/2026-05-01-chromadb-vector-search-design.md)

---

## Summary

Implement semantic search over NoteMe notes using a local vector store. Users type natural language queries in a popup and get ranked note excerpts. Gated by the existing `chromaDbEnabled` setting.

---

## Phases

### Phase 1 — Vector Store Foundation

**Goal:** Set up the vector store, chunking, and embedding pipeline. No UI yet.

#### Tasks

- [ ] **1.1 Evaluate and select JVM vector store library**
  - Research candidates: LangChain4j, Lucene vector search, Chroma Java client
  - Criteria: JVM-native (no Python process), file-based persistence, similarity search API, minimal footprint
  - Produce a short comparison table with recommendation
  - Files: `docs/superpowers/specs/2026-05-01-vector-store-evaluation.md`

- [ ] **1.2 Evaluate and select embedding approach**
  - Research: ONNX Runtime + all-MiniLM-L6-v2, DJL (Deep Java Library), TF-IDF fallback
  - Criteria: works offline, plugin size impact, embedding quality, JVM compatibility
  - Produce a short comparison table with recommendation
  - Files: `docs/superpowers/specs/2026-05-01-embedding-evaluation.md`

- [ ] **1.3 Add dependencies to build.gradle.kts**
  - Add chosen vector store and embedding libraries
  - Verify build succeeds with IntelliJ platform plugin
  - Verify plugin size is acceptable (<50MB added)
  - Files: `build.gradle.kts`

- [ ] **1.4 Implement MarkdownChunker**
  - Split `.md` content by heading boundaries
  - Handle large sections (>500 tokens) by splitting at paragraph breaks
  - Return list of `Chunk(text, headingPath, chunkIndex)`
  - Unit tests with sample markdown
  - Files: `src/main/kotlin/.../MarkdownChunker.kt`

- [ ] **1.5 Implement EmbeddingService**
  - Wrapper around the chosen embedding library
  - `fun embed(text: String): FloatArray`
  - `fun embedBatch(texts: List<String>): List<FloatArray>`
  - Lazy initialization (don't load model until first use)
  - Files: `src/main/kotlin/.../EmbeddingService.kt`

- [ ] **1.6 Implement VectorSearchManager**
  - Initialize vector store at `<notesRoot>/.chromadb/`
  - `indexAllNotes(notesRoot)`: walk disk, chunk, embed, upsert
  - `query(queryText, maxResults)`: embed query, similarity search, return `SearchResult` list
  - `reindex(notesRoot)`: clear collection, re-index all
  - `getIndexStats()`: note count, chunk count, last indexed timestamp
  - Incremental indexing: skip files where `lastModified` hasn't changed
  - Files: `src/main/kotlin/.../VectorSearchManager.kt`

**Exit criteria:** Can programmatically index notes and query them from a test. No UI required.

---

### Phase 2 — Query Popup UI

**Goal:** Build the Compose popup for entering queries and viewing results.

#### Tasks

- [ ] **2.1 Add semantic search toolbar button**
  - Add icon after existing search button (left of settings)
  - Tooltip: "Semantic search (ChromaDB)"
  - Disabled with explanatory tooltip when `chromaDbEnabled` is false
  - Clicking opens the query popup
  - Files: `MyToolWindow.kt`

- [ ] **2.2 Implement QueryPopup composable**
  - Modal popup with:
    - Title bar: "Semantic Search" + close button
    - Text input field (single line, Enter to search)
    - Results list: scrollable, each result shows note title, heading, excerpt, score
    - Footer: result count, query time, index stats
  - Escape or click-outside dismisses
  - Files: `MyToolWindow.kt` (or extract to `QueryPopup.kt` if large)

- [ ] **2.3 Wire query execution**
  - On Enter: run query on IO dispatcher
  - Show "Searching..." in popup during query
  - Display results with similarity scores
  - Update status bar: "Search: N results for 'query...'"
  - Files: `MyToolWindow.kt`

- [ ] **2.4 Wire result click to open note**
  - Click on a result opens the note file in the editor
  - Dismiss popup after opening
  - Files: `MyToolWindow.kt`

**Exit criteria:** User can click semantic search button, type a query, see results, click to open a note.

---

### Phase 3 — Indexing Triggers and Settings Integration

**Goal:** Connect indexing to user actions and settings.

#### Tasks

- [ ] **3.1 First-enable indexing prompt**
  - When `chromaDbEnabled` is toggled from false to true in settings:
    - Show dialog: "Build search index now? This may take a moment for large collections."
    - Yes: run `indexAllNotes` on IO thread with status bar progress
    - No: defer until user clicks re-index or sync
  - Files: `NoteMeSettingsConfigurable.kt`, `MyToolWindow.kt`

- [ ] **3.2 Re-index button in query popup**
  - Add a small re-index icon/button in the query popup footer
  - Clicking runs `reindex(notesRoot)` on IO thread
  - Status bar: "Re-indexing... N/M notes" then "Re-indexed N notes, M chunks"
  - Files: `MyToolWindow.kt`

- [ ] **3.3 Sync integration**
  - When `reindexOnSync` is true and user clicks "Sync from Disk":
    - After sync completes, run `indexAllNotes` (incremental)
    - Status bar: "Synced from disk — re-indexing..." then "Re-indexed"
  - Files: `MyToolWindow.kt`

- [ ] **3.4 Settings change handling**
  - When root directory changes: re-initialize vector store from new root
  - When `chromaDbEnabled` toggled off: disable query button, don't delete `.chromadb/`
  - When `chromaDbEnabled` toggled on with existing index: just enable query button
  - Files: `MyToolWindow.kt`, `VectorSearchManager.kt`

**Exit criteria:** Indexing is triggered appropriately from settings, sync, and manual re-index. Settings changes are reflected immediately.

---

### Phase 4 — Polish and Edge Cases

**Goal:** Handle errors, performance, and UX refinements.

#### Tasks

- [ ] **4.1 Progress reporting**
  - During indexing, update status bar with progress: "Indexing: 15/47 notes..."
  - For large collections, show a non-blocking progress indicator
  - Files: `MyToolWindow.kt`, `VectorSearchManager.kt`

- [ ] **4.2 Error handling**
  - ChromaDB init failure: log, show in status bar, disable query
  - Embedding failure for individual notes: skip, report count
  - Corrupted index: detect and offer re-index
  - Files: `VectorSearchManager.kt`, `MyToolWindow.kt`

- [ ] **4.3 Excerpt highlighting**
  - In search results, bold or color the portion most relevant to the query
  - Files: `MyToolWindow.kt`

- [ ] **4.4 Empty state and onboarding**
  - ChromaDB enabled but no index: show "No index. Click to build."
  - Index empty (no notes): show "No notes to search"
  - Query has no results: show "No matching notes found"
  - Files: `MyToolWindow.kt`

- [ ] **4.5 Performance testing**
  - Test with 10, 100, 500, 1000 notes
  - Measure indexing time, query latency, memory usage
  - Document results and set acceptable thresholds
  - Files: `docs/superpowers/specs/2026-05-01-vector-search-benchmarks.md`

**Exit criteria:** Feature is robust, handles all error states, and performs acceptably.

---

## File Inventory

### New Files

| File | Purpose |
|------|---------|
| `MarkdownChunker.kt` | Split markdown into heading-based chunks |
| `EmbeddingService.kt` | Generate vector embeddings from text |
| `VectorSearchManager.kt` | Manage vector store: index, query, re-index |
| `QueryPopup.kt` (optional) | Compose UI for query popup (may stay in MyToolWindow.kt) |

### Modified Files

| File | Changes |
|------|---------|
| `build.gradle.kts` | Add vector store + embedding dependencies |
| `MyToolWindow.kt` | Add semantic search button, query popup, indexing triggers |
| `NoteMeSettingsConfigurable.kt` | First-enable indexing prompt |

---

## Dependencies to Evaluate

### Vector Store Options

| Library | Type | Persistence | JVM Native | Notes |
|---------|------|-------------|------------|-------|
| LangChain4j InMemory | In-memory | Manual save/load | Yes | Simple but no built-in persistence |
| Apache Lucene 9.x | Disk-based | Yes | Yes | Mature, vector search via HNSW |
| Chroma Java Client | Client-server | Yes | Client only | Requires ChromaDB server process |
| Qdrant Java Client | Client-server | Yes | Client only | Requires Qdrant server process |

### Embedding Options

| Library | Model | Size | JVM Native | Quality |
|---------|-------|------|------------|---------|
| ONNX Runtime + MiniLM | all-MiniLM-L6-v2 | ~23MB | Yes | Good |
| DJL (Deep Java Library) | Various | Varies | Yes | Good |
| TF-IDF (custom) | N/A | ~0MB | Yes | Basic |

---

## Risk Assessment

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| Plugin size too large with bundled model | High | Medium | Use smallest viable model, lazy-load |
| Embedding quality insufficient | Medium | Low | Start with proven model (MiniLM) |
| Vector store incompatible with IntelliJ classloader | High | Medium | Test early in Phase 1, have fallback options |
| Indexing too slow for large collections | Medium | Low | Incremental indexing, background processing |
| Memory usage too high during embedding | Medium | Low | Batch processing, single-note-at-a-time mode |

---

## Implementation Order

```
Phase 1.1 + 1.2 (evaluations — parallel)
        │
  Phase 1.3 (add dependencies)
        │
        ├── Phase 1.4 (MarkdownChunker — parallel)
        ├── Phase 1.5 (EmbeddingService — parallel)
        │
        └──► Phase 1.6 (VectorSearchManager)
                │
          Phase 2 (Query Popup UI)
                │
          Phase 3 (Indexing Triggers)
                │
          Phase 4 (Polish)
```

---

## Open Questions

1. **Which vector store library works best within IntelliJ's plugin classloader?** — Needs Phase 1.1 evaluation
2. **What is the acceptable plugin size increase?** — Suggested threshold: <50MB
3. **Should we support external embedding APIs in v1 or defer to v2?** — Recommendation: defer
4. **Should `.chromadb/` location be configurable or always under notes root?** — Recommendation: always under notes root
5. **Should deleted notes be automatically removed from the index?** — Recommendation: yes during re-index, no automatic removal otherwise
