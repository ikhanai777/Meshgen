package com.meshgen.app.llm

/** Thin JNI surface over llama.cpp (see src/main/cpp/meshllm.cpp). */
object LlamaNative {
    fun interface Progress {
        /** phase 0 = reading the prompt, 1 = writing the answer. Return false to cancel. */
        fun onProgress(phase: Int, done: Int, total: Int): Boolean
    }

    @Volatile private var initialized = false

    @Synchronized
    fun ensureLoaded(nativeLibDir: String) {
        if (initialized) return
        System.loadLibrary("meshllm")
        initBackends(nativeLibDir)
        initialized = true
    }

    @JvmStatic external fun initBackends(libDir: String)
    @JvmStatic external fun load(path: String, nCtx: Int, nThreads: Int): Long
    @JvmStatic external fun free(handle: Long)
    @JvmStatic external fun generate(
        handle: Long,
        prefix: ByteArray,
        suffix: ByteArray,
        grammar: String?,
        maxTokens: Int,
        temperature: Float,
        seed: Int,
        cachePath: String?,
        callback: Progress,
    ): ByteArray?
}
