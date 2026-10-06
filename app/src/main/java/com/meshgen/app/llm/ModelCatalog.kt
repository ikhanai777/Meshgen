package com.meshgen.app.llm

/** Downloadable language models. Details and license checks: MODELS.md. */
data class ModelSpec(
    val id: String,
    val name: String,
    val tagline: String,
    val url: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val license: String,
    /** Usable RAM (as Android reports it) below which the model is not offered as safe. */
    val minRamGb: Double,
    val contextTokens: Int,
    /** Extra working memory beyond the file (KV cache, buffers), used for the free-memory check. */
    val runtimeOverheadBytes: Long,
)

object ModelCatalog {
    val QWEN3_1_7B = ModelSpec(
        id = "qwen3-1.7b-q4km",
        name = "Qwen3 1.7B",
        tagline = "Fast. Recommended for most phones.",
        url = "https://huggingface.co/unsloth/Qwen3-1.7B-GGUF/resolve/d7f544eead698dbd1f15126ef60b45a1e1933222/Qwen3-1.7B-Q4_K_M.gguf",
        fileName = "Qwen3-1.7B-Q4_K_M.gguf",
        sizeBytes = 1_107_409_472L,
        sha256 = "b139949c5bd74937ad8ed8c8cf3d9ffb1e99c866c823204dc42c0d91fa181897",
        license = "Apache 2.0",
        minRamGb = 5.0,
        contextTokens = 4096,
        runtimeOverheadBytes = 650L shl 20,
    )

    val QWEN3_4B = ModelSpec(
        id = "qwen3-4b-q4km",
        name = "Qwen3 4B",
        tagline = "Smarter at custom shapes and edits. Slower; needs 8 GB+ RAM.",
        url = "https://huggingface.co/Qwen/Qwen3-4B-GGUF/resolve/bc640142c66e1fdd12af0bd68f40445458f3869b/Qwen3-4B-Q4_K_M.gguf",
        fileName = "Qwen3-4B-Q4_K_M.gguf",
        sizeBytes = 2_497_280_256L,
        sha256 = "7485fe6f11af29433bc51cab58009521f205840f5b4ae3a32fa7f92e8534fdf5",
        license = "Apache 2.0",
        minRamGb = 7.0,
        contextTokens = 4096,
        runtimeOverheadBytes = 800L shl 20,
    )

    val ALL = listOf(QWEN3_1_7B, QWEN3_4B)
    fun byId(id: String?) = ALL.firstOrNull { it.id == id }
}
