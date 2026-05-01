# NoteMe — ChromaDB Vector Search Design

**Date:** 2026-05-01
**Status:** Draft
**Scope:** File-based ChromaDB vector indexing and semantic search for NoteMe notes

---

## Overview

Add a semantic search capability to NoteMe using ChromaDB as a local, file-based vector database. Users can query their notes using natural language and get ranked results based on meaning, not just keyword matching. The feature is accessed via a popup query window in the NoteMe tool window.

---

## Goals

- Index all `.md` notes from the configured notes root into a local ChromaDB instance
- Provide a query popup where users type natural language questions
- Return semantically relevant note excerpts ranked by similarity
- Support re-indexing on demand (manual trigger via sync button or settings)
- Persist the ChromaDB data on disk so indexing doesn't repeat on every IDE restart
- Respect the `chromaDbEnabled` and `reindexOnSync` settings flags

---

## Non-Goals

- Cloud-hosted vector DB or external API calls for embeddings (local only in v1)
- Real-time indexing on file save (manual re-index only in v1)
- Multi-modal search (images, PDFs — text-only in v1)
- RAG / LLM-powered answer generation (search returns note excerpts, not generated answers)

---

## Architecture

### Component Diagram

```
┌──────────────────────────────────────────────────┐
│                  MyToolWindow                     │
│  ┌─────────┐  ┌──────────┐  ┌─────────────────┐ │
│  │ Toolbar  │  │ LazyTree │  │   Status Bar    │ │
│  │ [Query]  │  │          │  │                 │ │
│  └────┬─────┘  └──────────┘  └─────────────────┘ │
│       │                                           │
│  ┌────▼──────────────────────────────────────┐   │
│  │          QueryPopup (new)                  │   │
│  │  ┌─────────────┐  ┌────────────────────┐  │   │
│  │  │ Query Input  │  │ Results List       │  │   │
│  │  └──────┬──────┘  │ - Note title       │  │   │
│  │         │         │ - Excerpt           │  │   │
│  │         ▼         │ - Similarity score  │  │   │
│  │  ┌──────────────┐ └────────────────────┘  │   │
│  │  │ VectorSearch │                          │   │
│  │  │   Manager    │                          │   │
│  │  └──────┬───────┘                          │   │
│  └─────────┼──────────────────────────────────┘   │
└────────────┼──────────────────────────────────────┘
             │
    ┌────────▼────────┐
    │  ChromaDB       │
    │  (file-based)   │
    │  ~/NoteMeNotes/ │
    │  .chromadb/     │
    └─────────────────┘
```

### Key Components

#### 1. VectorSearchManager (new singleton)

Responsibilities:
- Initialize and manage the ChromaDB client (file-based, stored in `<notesRoot>/.chromadb/`)
- Index notes: read each `.md` file, chunk content, generate embeddings, store in ChromaDB
- Query: accept a natural language string, return ranked results with note title, excerpt, and score
- Re-index: clear and rebuild the entire index
- Incremental updates: track file modification times to only re-index changed files

```kotlin
object VectorSearchManager {
    fun initialize(notesRoot: File)
    fun indexAllNotes(notesRoot: File): IndexResult
    fun query(queryText: String, maxResults: Int = 10): List<SearchResult>
    fun reindex(notesRoot: File): IndexResult
    fun isIndexed(): Boolean
    fun getIndexStats(): IndexStats
}

data class SearchResult(
    val noteTitle: String,
    val notePath: String,       // relative path from notesRoot
    val excerpt: String,        // matched chunk text
    val score: Float,           // similarity score 0.0-1.0
    val chunkIndex: Int         // which chunk of the note matched
)

data class IndexResult(
    val totalNotes: Int,
    val totalChunks: Int,
    val indexedCount: Int,
    val skippedCount: Int,      // already up-to-date
    val errorCount: Int
)

data class IndexStats(
    val noteCount: Int,
    val chunkCount: Int,
    val lastIndexedAt: Long     // epoch millis
)
```

#### 2. QueryPopup (new Compose UI)

A modal popup triggered from a toolbar button:
- Text input field for the query
- Results list showing matched notes with excerpts
- Click on a result opens the note in the editor
- Escape or clicking outside dismisses the popup
- Status indicator (indexing progress, result count)

#### 3. Embedding Strategy

**Option A — Local embeddings (recommended for v1):**
- Use a lightweight Java/Kotlin sentence embedding library
- Candidates: ONNX Runtime with a small model (e.g., all-MiniLM-L6-v2), or TF-IDF as fallback
- Pros: No external dependencies, works offline, no API costs
- Cons: Larger plugin size if bundling a model

**Option B — External embedding API:**
- Call OpenAI, Anthropic, or local Ollama for embeddings
- Pros: Better quality embeddings
- Cons: Requires API key configuration, network dependency
- Could be added as an option in v2

**Recommendation:** Start with Option A using a small ONNX model. The `chromaDbEnabled` setting already exists to gate this feature.

#### 4. Chunking Strategy

Notes are split into chunks for granular search:
- Split by markdown headings (##, ###, etc.) as primary boundaries
- Within a heading section, split at paragraph boundaries if section > 500 tokens
- Each chunk stores metadata: `noteTitle`, `notePath`, `headingPath`, `chunkIndex`
- Overlap: 50 tokens between chunks for context continuity

```
Note: "Kotlin Tutorial.md"
├── Chunk 0: "# Kotlin Tutorial\n\nKotlin is a modern..." (heading: root)
├── Chunk 1: "## Variables\n\nKotlin uses val and var..." (heading: Variables)
├── Chunk 2: "## Functions\n\nFunctions are declared..." (heading: Functions)
└── Chunk 3: "### Extension Functions\n\nKotlin allows..." (heading: Functions > Extension Functions)
```

---

## Data Storage

### ChromaDB Location

```
<notesRoot>/
├── .chromadb/           # ChromaDB persistent storage (gitignored)
│   ├── chroma.sqlite3   # metadata and embeddings
│   └── ...
├── index.md
├── Knowledge Base/
│   └── ...
└── Tutorials/
    └── ...
```

- Stored inside the notes root under `.chromadb/`
- Hidden directory (dot-prefix) so it doesn't appear in the NoteMe tree
- The `scanDir` function already filters out directories starting with `.`
- If root directory changes, ChromaDB is re-initialized from the new root

### Metadata per Document

| Field | Type | Description |
|-------|------|-------------|
| `id` | String | `<relativePath>_chunk_<index>` |
| `noteTitle` | String | Note filename without extension |
| `notePath` | String | Relative path from notes root |
| `headingPath` | String | Heading hierarchy (e.g., "Functions > Extension Functions") |
| `chunkIndex` | Int | Position of chunk in the note |
| `lastModified` | Long | File modification timestamp (epoch ms) |

---

## UI Design

### Query Button (Toolbar)

- New icon in the toolbar: magnifying glass with sparkle (semantic search indicator)
- Position: after the existing search icon
- Tooltip: "Semantic search (ChromaDB)"
- Disabled when `chromaDbEnabled` is false (greyed out with tooltip explaining)

### Query Popup

```
┌─────────────────────────────────────────────┐
│  Semantic Search                        [X] │
├─────────────────────────────────────────────┤
│  ┌─────────────────────────────────────┐    │
│  │ What is the syntax for Kotlin...    │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  3 results (0.2s)                           │
│                                             │
│  ┌─────────────────────────────────────┐    │
│  │ 📄 Kotlin Tutorial         [0.92]  │    │
│  │ ## Variables                        │    │
│  │ Kotlin uses val for immutable and   │    │
│  │ var for mutable variables...        │    │
│  └─────────────────────────────────────┘    │
│  ┌─────────────────────────────────────┐    │
│  │ 📄 Language Comparison     [0.78]  │    │
│  │ ## Kotlin vs Java                   │    │
│  │ Kotlin provides more concise        │    │
│  │ syntax compared to Java...          │    │
│  └─────────────────────────────────────┘    │
│  ┌─────────────────────────────────────┐    │
│  │ 📄 Quick Reference         [0.65]  │    │
│  │ ## Syntax                           │    │
│  │ Basic Kotlin syntax includes...     │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  Status: Index: 47 notes, 312 chunks       │
└─────────────────────────────────────────────┘
```

### Interactions

| Action | Behavior |
|--------|----------|
| Type query + Enter | Execute search, show results |
| Click result | Open note in editor, dismiss popup |
| Escape | Dismiss popup |
| Click outside | Dismiss popup |
| Empty query | Show index stats |

---

## Settings Integration

Existing settings in `NoteMeSettings`:

| Setting | Type | Default | Purpose |
|---------|------|---------|---------|
| `chromaDbEnabled` | Boolean | false | Gates the entire vector search feature |
| `reindexOnSync` | Boolean | false | When true, re-indexes ChromaDB during "Sync from Disk" |

### Behavior When Disabled

- Query toolbar button is greyed out
- Tooltip shows: "Enable ChromaDB in Settings to use semantic search"
- No indexing occurs
- `.chromadb/` directory is not created

### Behavior When Enabled

- First enable: prompt user to build index (can be slow for large note collections)
- Query button becomes active
- Status bar shows indexing progress during index builds
- "Sync from Disk" optionally re-indexes if `reindexOnSync` is true

---

## ChromaDB Integration

### Library Choice

ChromaDB provides a Python client. For JVM integration, options are:

**Option A — Chroma Java Client (recommended):**
- Use `chromadb-java-client` library
- Connects to ChromaDB running as a local process or embedded
- Maven: `io.chromadb:chromadb-java-client`

**Option B — Embedded via Python subprocess:**
- Bundle a Python script that runs ChromaDB operations
- Plugin calls it via `ProcessBuilder`
- More complex but gives full ChromaDB API access

**Option C — Pure JVM vector store:**
- Use a JVM-native vector store (e.g., LangChain4j in-memory, Lucene with vector search)
- No external process needed
- Simpler deployment

**Recommendation:** Evaluate Option C first (pure JVM) for simplicity. If embedding quality or features are insufficient, move to Option A.

### Collection Schema

```
Collection: "noteme_notes"
- Documents: chunk text content
- Embeddings: vector representation of each chunk
- Metadata: noteTitle, notePath, headingPath, chunkIndex, lastModified
- IDs: "<relativePath>_chunk_<index>"
```

---

## Error Handling

| Scenario | Handling |
|----------|----------|
| ChromaDB initialization fails | Show error in status bar, disable query button, log error |
| Embedding generation fails for a note | Skip note, increment error count, continue indexing |
| Query returns no results | Show "No matching notes found" in popup |
| Notes root changes | Re-initialize ChromaDB from new root |
| Corrupted index | Offer re-index option via status bar message |
| Large note collection (>1000 notes) | Show progress bar during indexing |

---

## Performance Considerations

- **Indexing**: Run on IO dispatcher, show progress in status bar
- **Querying**: Should complete in <500ms for typical collections (<500 notes)
- **Memory**: ChromaDB file-based mode keeps data on disk, minimal memory overhead
- **Startup**: Do NOT auto-index on startup. Index only on explicit user action
- **Incremental indexing**: Compare `lastModified` timestamps to skip unchanged files

---

## Security

- All data stays local (no external API calls in v1)
- ChromaDB data stored in user's notes directory
- No credentials or API keys needed for local-only mode
- `.chromadb/` should be added to `.gitignore` if notes are version-controlled

---

## Future Extensions (out of scope for v1)

- Real-time indexing on file save via file watcher
- External embedding API support (OpenAI, Ollama)
- RAG: feed search results to an LLM for answer generation
- Cross-note link suggestions based on semantic similarity
- "Similar notes" sidebar panel
- Multi-collection support (separate indexes per project)
