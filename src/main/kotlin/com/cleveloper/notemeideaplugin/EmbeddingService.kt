package com.cleveloper.notemeideaplugin

import dev.langchain4j.data.embedding.Embedding
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel

object EmbeddingService {

    private val model: EmbeddingModel by lazy {
        val originalCL = Thread.currentThread().contextClassLoader
        try {
            Thread.currentThread().contextClassLoader = AllMiniLmL6V2EmbeddingModel::class.java.classLoader
            AllMiniLmL6V2EmbeddingModel()
        } finally {
            Thread.currentThread().contextClassLoader = originalCL
        }
    }

    fun embed(text: String): FloatArray {
        val response = model.embed(text)
        return response.content().vector()
    }

    fun embedBatch(texts: List<String>): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()
        val segments = texts.map { TextSegment.from(it) }
        val response = model.embedAll(segments)
        return response.content().map { it.vector() }
    }

    fun embedToLangChain(text: String): Embedding {
        return model.embed(text).content()
    }

    fun embedBatchToLangChain(texts: List<String>): List<Embedding> {
        if (texts.isEmpty()) return emptyList()
        val segments = texts.map { TextSegment.from(it) }
        return model.embedAll(segments).content()
    }
}
