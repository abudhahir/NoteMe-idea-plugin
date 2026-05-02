# Embedding Approach Evaluation

**Date:** 2026-05-01
**Decision:** LangChain4j ONNX all-MiniLM-L6-v2

## Candidates

| Library | Model | Size | JVM Native | Quality | Offline |
|---------|-------|------|------------|---------|---------|
| LangChain4j ONNX MiniLM | all-MiniLM-L6-v2 | ~23MB | Yes (ONNX Runtime) | Good (384-dim) | Yes |
| DJL (Deep Java Library) | Various | 20-100MB | Yes | Good | Yes |
| TF-IDF (custom) | N/A | ~0MB | Yes | Basic (no semantics) | Yes |
| External API (OpenAI/Ollama) | Various | 0MB | N/A | Excellent | No |

## Decision: LangChain4j ONNX all-MiniLM-L6-v2

**Reasons:**
- Proven sentence embedding model, widely used for semantic search
- 384-dimensional embeddings — good quality-to-size ratio
- Bundled inside a single JAR (`langchain4j-embeddings-all-minilm-l6-v2`)
- Runs via ONNX Runtime — fast inference, no Python/TensorFlow needed
- Works completely offline — no API keys, no network dependency
- Same LangChain4j ecosystem as the vector store — cohesive API
- Lazy initialization — model loads on first `embed()` call, not at plugin startup

**Trade-offs:**
- Adds ~23MB to plugin size (acceptable, within <50MB threshold)
- ONNX Runtime extracts native libs to temp directory at runtime
- First embedding call has ~1-2s latency for model loading (subsequent calls are fast)

**Maven coordinate:** `dev.langchain4j:langchain4j-embeddings-all-minilm-l6-v2:1.0.0`
