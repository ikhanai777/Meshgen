package com.meshgen.app.llm

import android.app.ActivityManager
import android.content.Context
import com.meshgen.core.llm.GenerationCancelled
import com.meshgen.core.llm.TextGenerator
import java.io.File
import java.security.MessageDigest

/**
 * llama.cpp running on the phone's CPU. Keeps one model loaded; the shared instruction prefix is cached in
 * memory and on disk, so only the first request after loading pays for reading the instructions.
 */
class LlamaEngine(private val context: Context, private val models: ModelManager) : TextGenerator {
    /** What the engine is doing, for progress display. */
    fun interface Listener { fun onProgress(phase: Phase, done: Int, total: Int) }
    enum class Phase { LOADING, READING, WRITING }

    @Volatile var listener: Listener? = null
    private var handle = 0L
    private var loaded: ModelSpec? = null

    val loadedModel: ModelSpec? get() = loaded

    /** Null when the model can be started; otherwise a reason in plain words. */
    fun memoryProblem(spec: ModelSpec): String? {
        if (loaded?.id == spec.id) return null
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
        val needed = spec.sizeBytes + spec.runtimeOverheadBytes
        return if (mi.availMem < needed * 0.8) {
            "Not enough free memory for ${spec.name} right now (needs about ${ModelManager.gb(needed)}, " +
                "${ModelManager.gb(mi.availMem)} free). Close other apps or use a smaller model."
        } else null
    }

    @Synchronized
    private fun ensure(spec: ModelSpec) {
        if (loaded?.id == spec.id && handle != 0L) return
        release()
        memoryProblem(spec)?.let { throw IllegalStateException(it) }
        listener?.onProgress(Phase.LOADING, 0, 1)
        LlamaNative.ensureLoaded(context.applicationInfo.nativeLibraryDir)
        val threads = Runtime.getRuntime().availableProcessors().let { if (it >= 8) 4 else maxOf(2, it / 2) }
        handle = LlamaNative.load(models.file(spec).absolutePath, spec.contextTokens, threads)
        loaded = spec
    }

    @Synchronized
    fun release() {
        if (handle != 0L) LlamaNative.free(handle)
        handle = 0L
        loaded = null
    }

    @Synchronized
    override fun generate(
        prefix: String,
        suffix: String,
        grammar: String?,
        maxTokens: Int,
        temperature: Float,
        onToken: (String) -> Unit,
        isCancelled: () -> Boolean,
    ): String {
        val spec = models.readyModel() ?: throw IllegalStateException("No AI model is downloaded yet.")
        ensure(spec)
        val cache = File(context.cacheDir, "llm").apply { mkdirs() }.resolve("${spec.id}-${sha(prefix).take(16)}.state")
        val bytes = LlamaNative.generate(
            handle, prefix.toByteArray(), suffix.toByteArray(), grammar, maxTokens, temperature, 1234,
            cache.absolutePath,
        ) { phase, done, total ->
            if (phase == 1) onToken("")
            listener?.onProgress(if (phase == 0) Phase.READING else Phase.WRITING, done, total)
            !isCancelled()
        } ?: if (isCancelled()) throw GenerationCancelled() else throw IllegalStateException("The model stopped unexpectedly. It may be out of memory; try again or use the smaller model.")
        return String(bytes, Charsets.UTF_8)
    }

    private fun sha(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
