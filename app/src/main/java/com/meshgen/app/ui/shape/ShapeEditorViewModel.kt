package com.meshgen.app.ui.shape

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.meshgen.app.ui.viewer.MeshWorkbench
import com.meshgen.core.dsl.Quality
import com.meshgen.core.dsl.ShapeDoc
import com.meshgen.core.dsl.ShapeEngine
import com.meshgen.core.dsl.ShapeError
import com.meshgen.core.dsl.Templates
import com.meshgen.core.sdf.MeshingCancelled
import kotlinx.coroutines.CancellationException
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

data class EditorState(
    val doc: ShapeDoc? = null,
    val quality: Quality = Quality.STANDARD,
    /** Quality of the mesh currently shown, or null while nothing is shown. */
    val shownQuality: Quality? = null,
    val generating: Boolean = false,
    val progress: Float = 0f,
    val lastMillis: Long? = null,
    val issues: List<String> = emptyList(),
    val showRecipe: Boolean = false,
)

/**
 * Template editor: parameter changes re-mesh quickly at Draft quality, then refine to the chosen quality
 * once the sliders stop moving. Only the newest request runs; older ones are cancelled.
 */
class ShapeEditorViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val templateId: String = handle.get<String>("template") ?: Templates.IDS.first()
    private val original: ShapeDoc = Templates.load(templateId)

    val workbench = MeshWorkbench(app, viewModelScope, original.name)
    private val _state = MutableStateFlow(EditorState(doc = original))
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private var job: Job? = null
    private var firstMesh = true

    init { regenerate(immediate = true) }

    fun setParam(name: String, value: Double) {
        val doc = _state.value.doc ?: return
        _state.update { it.copy(doc = doc.withValues(mapOf(name to value))) }
        regenerate(immediate = false)
    }

    fun resetParams() {
        _state.update { it.copy(doc = original) }
        regenerate(immediate = true)
    }

    fun setQuality(q: Quality) {
        if (q == _state.value.quality) return
        _state.update { it.copy(quality = q) }
        regenerate(immediate = true)
    }

    fun toggleRecipe() = _state.update { it.copy(showRecipe = !it.showRecipe) }

    private fun regenerate(immediate: Boolean) {
        job?.cancel()
        job = viewModelScope.launch {
            if (!immediate) delay(120) // let the slider settle a little
            val target = _state.value.quality
            val passes = if (target == Quality.DRAFT || immediate && firstMesh) listOf(target) else listOf(Quality.DRAFT, target)
            for ((i, q) in passes.withIndex()) {
                if (i > 0) delay(500) // refine only when the user pauses
                if (!run(q)) break
            }
        }
    }

    /** Generates at [q], falling back to lower qualities on out-of-memory. Returns false to stop further passes. */
    private suspend fun run(q: Quality): Boolean {
        val doc = _state.value.doc ?: return false
        var quality = q
        while (true) {
            _state.update { it.copy(generating = true, progress = 0f) }
            workbench.update { it.copy(busyLabel = "Generating…", busyProgress = null) }
            try {
                val g = withContext(Dispatchers.Default) {
                    ShapeEngine.generate(doc, quality, { p -> _state.update { s -> s.copy(progress = p) } }) { !isActive }
                }
                workbench.show(g.mesh, g.report, g.fixes, reframe = firstMesh)
                firstMesh = false
                _state.update { it.copy(shownQuality = quality, lastMillis = g.millis, issues = emptyList()) }
                if (quality != q) workbench.message("Not enough memory for ${q.label} quality on this phone; showing ${quality.label}.")
                return quality == q
            } catch (e: ShapeError) {
                _state.update { it.copy(issues = e.issues.map { i -> i.toString() }) }
                workbench.update { it.copy(loading = false) }
                return false
            } catch (e: MeshingCancelled) {
                return false
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                val lower = Quality.entries.getOrNull(quality.ordinal - 1)
                if (lower == null) {
                    workbench.update { it.copy(loading = false, error = "Not enough memory to build this shape. Try smaller sizes.") }
                    return false
                }
                quality = lower
            } finally {
                _state.update { it.copy(generating = false) }
                workbench.update { it.copy(busyLabel = null) }
            }
        }
    }
}
