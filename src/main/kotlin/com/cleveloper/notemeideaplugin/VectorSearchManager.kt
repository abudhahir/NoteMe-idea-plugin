package com.cleveloper.notemeideaplugin

import dev.langchain4j.data.document.Metadata
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.store.embedding.EmbeddingSearchRequest
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore
import java.io.File
import java.nio.file.Path

data class SearchResult(
    val noteTitle: String,
    val notePath: String,
    val excerpt: String,
    val score: Float,
    val headingPath: String,
    val chunkIndex: Int
)

data class IndexResult(
    val totalNotes: Int,
    val totalChunks: Int,
    val indexedCount: Int,
    val skippedCount: Int,
    val errorCount: Int
)

data class IndexStats(
    val noteCount: Int,
    val chunkCount: Int,
    val lastIndexedAt: Long
)

object VectorSearchManager {

    private var store: InMemoryEmbeddingStore<TextSegment>? = null
    private var currentRoot: File? = null
    private var lastIndexedAt: Long = 0L
    private var indexedNoteCount: Int = 0
    private var indexedChunkCount: Int = 0
    private val fileTimestamps = mutableMapOf<String, Long>()

    private fun getStoreDir(notesRoot: File): File {
        return File(notesRoot, ".chromadb").also { it.mkdirs() }
    }

    private fun getStorePath(notesRoot: File): Path {
        return getStoreDir(notesRoot).resolve("embedding-store.json").toPath()
    }

    fun initialize(notesRoot: File) {
        if (currentRoot == notesRoot && store != null) return

        currentRoot = notesRoot
        val storePath = getStorePath(notesRoot)

        store = if (storePath.toFile().exists()) {
            try {
                InMemoryEmbeddingStore.fromFile(storePath)
            } catch (e: Exception) {
                InMemoryEmbeddingStore()
            }
        } else {
            InMemoryEmbeddingStore()
        }
    }

    fun indexAllNotes(
        notesRoot: File,
        onProgress: ((indexed: Int, total: Int) -> Unit)? = null
    ): IndexResult {
        initialize(notesRoot)

        val mdFiles = notesRoot.walkTopDown()
            .filter { it.isFile && it.extension == "md" && !it.absolutePath.contains("/.chromadb/") }
            .toList()

        var indexedCount = 0
        var skippedCount = 0
        var errorCount = 0
        var totalChunks = 0

        for ((index, file) in mdFiles.withIndex()) {
            try {
                val relativePath = file.relativeTo(notesRoot).path
                val lastModified = file.lastModified()

                // Skip if file hasn't changed since last index
                if (fileTimestamps[relativePath] == lastModified) {
                    skippedCount++
                    onProgress?.invoke(index + 1, mdFiles.size)
                    continue
                }

                val content = file.readText()
                val chunks = MarkdownChunker.chunk(content)

                if (chunks.isEmpty()) {
                    skippedCount++
                    onProgress?.invoke(index + 1, mdFiles.size)
                    continue
                }

                val chunkTexts = chunks.map { it.text }
                val embeddings = EmbeddingService.embedBatchToLangChain(chunkTexts)

                for ((chunkIdx, chunk) in chunks.withIndex()) {
                    val segment = TextSegment.from(
                        chunk.text,
                        Metadata.from("noteTitle", file.nameWithoutExtension)
                            .put("notePath", relativePath)
                            .put("headingPath", chunk.headingPath)
                            .put("chunkIndex", chunkIdx.toString())
                            .put("lastModified", lastModified.toString())
                    )
                    val id = "${relativePath}_chunk_${chunkIdx}"
                    store!!.add(id, embeddings[chunkIdx], segment)
                }

                fileTimestamps[relativePath] = lastModified
                indexedCount++
                totalChunks += chunks.size
                onProgress?.invoke(index + 1, mdFiles.size)
            } catch (e: Exception) {
                errorCount++
                onProgress?.invoke(index + 1, mdFiles.size)
            }
        }

        indexedNoteCount = mdFiles.size - errorCount
        indexedChunkCount += totalChunks
        lastIndexedAt = System.currentTimeMillis()

        persistStore(notesRoot)

        return IndexResult(
            totalNotes = mdFiles.size,
            totalChunks = totalChunks,
            indexedCount = indexedCount,
            skippedCount = skippedCount,
            errorCount = errorCount
        )
    }

    fun query(queryText: String, maxResults: Int = 10): List<SearchResult> {
        val currentStore = store ?: return emptyList()
        if (queryText.isBlank()) return emptyList()

        val queryEmbedding = EmbeddingService.embedToLangChain(queryText)

        val request = EmbeddingSearchRequest.builder()
            .queryEmbedding(queryEmbedding)
            .maxResults(maxResults)
            .minScore(0.3)
            .build()

        val result = currentStore.search(request)

        return result.matches().map { match ->
            val meta = match.embedded().metadata()
            SearchResult(
                noteTitle = meta.getString("noteTitle") ?: "Unknown",
                notePath = meta.getString("notePath") ?: "",
                excerpt = match.embedded().text(),
                score = match.score().toFloat(),
                headingPath = meta.getString("headingPath") ?: "",
                chunkIndex = meta.getString("chunkIndex")?.toIntOrNull() ?: 0
            )
        }
    }

    fun reindex(
        notesRoot: File,
        onProgress: ((indexed: Int, total: Int) -> Unit)? = null
    ): IndexResult {
        store = InMemoryEmbeddingStore()
        fileTimestamps.clear()
        indexedChunkCount = 0
        return indexAllNotes(notesRoot, onProgress)
    }

    fun isIndexed(): Boolean {
        return store != null && lastIndexedAt > 0
    }

    fun getIndexStats(): IndexStats {
        return IndexStats(
            noteCount = indexedNoteCount,
            chunkCount = indexedChunkCount,
            lastIndexedAt = lastIndexedAt
        )
    }

    private fun persistStore(notesRoot: File) {
        try {
            val storePath = getStorePath(notesRoot)
            store?.serializeToFile(storePath)
        } catch (e: Exception) {
            // Log but don't fail — the in-memory store is still usable
        }
    }
}
