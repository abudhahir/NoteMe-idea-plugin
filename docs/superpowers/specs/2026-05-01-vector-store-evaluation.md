# Vector Store Evaluation

**Date:** 2026-05-01
**Decision:** LangChain4j InMemoryEmbeddingStore

## Candidates

| Library | Type | Persistence | JVM Native | Plugin Size | Complexity |
|---------|------|-------------|------------|-------------|------------|
| LangChain4j InMemoryEmbeddingStore | In-memory | JSON file (serializeToFile/fromFile) | Yes | ~2MB | Low |
| Apache Lucene 9.x HNSW | Disk-based | Yes | Yes | ~10MB | High |
| Chroma Java Client | Client-server | Yes | Client only | ~1MB + server | High (requires external process) |
| Qdrant Java Client | Client-server | Yes | Client only | ~1MB + server | High (requires external process) |

## Decision: LangChain4j InMemoryEmbeddingStore

**Reasons:**
- Fully JVM-native, no external process needed
- Built-in JSON serialization for file-based persistence (`serializeToFile`/`fromFile`)
- Part of LangChain4j core — same library provides the embedding model, so one cohesive API
- Minimal footprint (~2MB JAR)
- Simple API: `add()`, `search(EmbeddingSearchRequest)`, `serializeToFile()`
- Avoids Lucene's complexity for what is essentially a small-scale search (< 1000 notes)
- Uses Gson internally, avoiding Jackson conflicts with IntelliJ Platform

**Trade-offs:**
- Entire store loaded in memory (acceptable for note collections < 10K chunks)
- No built-in HNSW indexing — uses brute-force cosine similarity (fast enough for < 10K vectors)
- Persistence is full JSON dump, not incremental (acceptable for note-sized collections)

**Storage location:** `<notesRoot>/.chromadb/embedding-store.json`
