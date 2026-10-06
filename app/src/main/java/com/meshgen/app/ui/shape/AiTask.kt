package com.meshgen.app.ui.shape

import com.meshgen.app.MeshGenApp
import com.meshgen.app.llm.LlamaEngine
import com.meshgen.core.llm.GenerationCancelled
import com.meshgen.core.llm.ShapeAgent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the AI is doing right now, in plain words. */
data class AiProgress(val stage: String, val detail: String? = null, val fraction: Float? = null, val elapsedMs: Long = 0)

/** Runs one agent request at a time off the main thread, with progress, elapsed time and cancel. */
class AiTask(private val app: MeshGenApp, private val scope: CoroutineScope) {
    private val _progress = MutableStateFlow<AiProgress?>(null)
    val progress: StateFlow<AiProgress?> = _progress.asStateFlow()
    private var job: Job? = null
    @Volatile private var cancelled = false

    val running: Boolean get() = job?.isActive == true

    fun run(
        work: (ShapeAgent, (ShapeAgent.Event) -> Unit, () -> Boolean) -> ShapeAgent.Result,
        onDone: (ShapeAgent.Result) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (running) return
        cancelled = false
        val start = System.currentTimeMillis()
        _progress.value = AiProgress("Starting…")
        job = scope.launch {
            val ticker = launch { while (isActive) { _progress.update { it?.copy(elapsedMs = System.currentTimeMillis() - start) }; delay(250) } }
            var writing = "Choosing a design…"
            app.llm.listener = LlamaEngine.Listener { phase, done, total ->
                _progress.update {
                    when (phase) {
                        LlamaEngine.Phase.LOADING -> AiProgress("Starting the AI model…", elapsedMs = it?.elapsedMs ?: 0)
                        LlamaEngine.Phase.READING -> AiProgress("Reading instructions (once per model start)", "${done * 100 / maxOf(total, 1)}%", done.toFloat() / maxOf(total, 1), it?.elapsedMs ?: 0)
                        LlamaEngine.Phase.WRITING -> AiProgress(writing, "$done tokens", null, it?.elapsedMs ?: 0)
                    }
                }
            }
            try {
                val result = withContext(Dispatchers.Default) {
                    work(app.agent, { e ->
                        when (e) {
                            ShapeAgent.Event.Planning -> writing = "Choosing a design…"
                            is ShapeAgent.Event.Writing -> writing = if (e.attempt == 1) "Writing a custom recipe…" else "Fixing the recipe (attempt ${e.attempt} of ${e.total})…"
                            is ShapeAgent.Event.Rejected -> _progress.update { it?.copy(stage = "Found a problem, asking the model to fix it", detail = e.problems.firstOrNull()) }
                            is ShapeAgent.Event.Tokens -> Unit
                        }
                    }) { cancelled }
                }
                onDone(result)
            } catch (e: GenerationCancelled) {
                // user cancelled
            } catch (e: OutOfMemoryError) {
                app.llm.release()
                onError("Ran out of memory. Close other apps or switch to the smaller model.")
            } catch (e: Exception) {
                onError(e.message ?: e.javaClass.simpleName)
            } finally {
                app.llm.listener = null
                ticker.cancel()
                _progress.value = null
            }
        }
    }

    fun cancel() { cancelled = true }
}
