package com.meshgen.app.ui.viewer

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.meshgen.app.files.MeshFiles
import com.meshgen.app.viewer.LightingPreset
import com.meshgen.app.viewer.ViewerMesh
import com.meshgen.core.export.ExportFormat
import com.meshgen.core.mesh.Decimator
import com.meshgen.core.mesh.Mesh
import com.meshgen.core.mesh.MeshCleanup
import com.meshgen.core.mesh.MeshReport
import com.meshgen.core.samples.SampleMesh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ViewerUiState(
    val title: String = "",
    val loading: Boolean = true,
    val viewerMesh: ViewerMesh? = null,
    val reframe: Boolean = true,
    val report: MeshReport? = null,
    val fixes: List<String> = emptyList(),
    val originalTriangles: Int = 0,
    val decimated: Boolean = false,
    val decimateFraction: Float = 0.5f,
    val busyLabel: String? = null,
    val busyProgress: Float? = null,
    val wireframe: Boolean = false,
    val lighting: LightingPreset = LightingPreset.STUDIO,
    val format: ExportFormat = ExportFormat.STL,
    val resetViewCount: Int = 0,
    val error: String? = null,
)

sealed interface ViewerEvent {
    data class Share(val intent: Intent) : ViewerEvent
    data class Message(val text: String) : ViewerEvent
}

class ViewerViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val sample = SampleMesh.entries.firstOrNull { it.name == handle.get<String>("sample") } ?: SampleMesh.CALIBRATION_CUBE

    private val _state = MutableStateFlow(ViewerUiState(title = sample.title))
    val state: StateFlow<ViewerUiState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<ViewerEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ViewerEvent> = _events.asSharedFlow()

    private var original: Mesh? = null
    private var current: Mesh? = null
    private var job: Job? = null

    init { load() }

    private fun load() {
        viewModelScope.launch {
            try {
                val (cleaned, viewer) = withContext(Dispatchers.Default) {
                    val c = MeshCleanup.clean(sample.build())
                    c to ViewerMesh.from(c.mesh, c.report)
                }
                original = cleaned.mesh
                current = cleaned.mesh
                _state.update {
                    it.copy(
                        loading = false, viewerMesh = viewer, reframe = true, report = cleaned.report,
                        fixes = cleaned.fixes, originalTriangles = cleaned.mesh.triangleCount,
                    )
                }
            } catch (e: OutOfMemoryError) {
                _state.update { it.copy(loading = false, error = "Not enough memory to load this mesh on this phone.") }
            }
        }
    }

    fun setWireframe(on: Boolean) = _state.update { it.copy(wireframe = on) }
    fun setLighting(p: LightingPreset) = _state.update { it.copy(lighting = p) }
    fun setFormat(f: ExportFormat) = _state.update { it.copy(format = f) }
    fun resetView() = _state.update { it.copy(resetViewCount = it.resetViewCount + 1) }
    fun setDecimateFraction(f: Float) = _state.update { it.copy(decimateFraction = f) }
    fun onRenderError(message: String) = _state.update { it.copy(error = message) }

    fun decimate() {
        val source = original ?: return
        val target = (source.triangleCount * _state.value.decimateFraction).toInt().coerceAtLeast(4)
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(busyLabel = "Simplifying…", busyProgress = 0f) }
            try {
                val (mesh, viewer, report) = withContext(Dispatchers.Default) {
                    val out = Decimator.decimate(source, target, { p -> _state.update { it.copy(busyProgress = p) } }) { !isActive }
                    ensureActive()
                    val report = MeshReport.of(out)
                    Triple(out, ViewerMesh.from(out, report), report)
                }
                current = mesh
                _state.update {
                    it.copy(viewerMesh = viewer, reframe = false, report = report, decimated = true, busyLabel = null, busyProgress = null)
                }
                if (mesh.triangleCount > target * 1.05) {
                    _events.tryEmit(ViewerEvent.Message("Stopped at ${mesh.triangleCount} triangles to keep the shape intact."))
                }
            } catch (e: OutOfMemoryError) {
                _state.update { it.copy(busyLabel = null, busyProgress = null) }
                _events.tryEmit(ViewerEvent.Message("Not enough memory to simplify this mesh."))
            } finally {
                _state.update { it.copy(busyLabel = null, busyProgress = null) }
            }
        }
    }

    fun restoreOriginal() {
        val mesh = original ?: return
        job?.cancel()
        viewModelScope.launch {
            val (viewer, report) = withContext(Dispatchers.Default) {
                val r = MeshReport.of(mesh)
                ViewerMesh.from(mesh, r) to r
            }
            current = mesh
            _state.update { it.copy(viewerMesh = viewer, reframe = false, report = report, decimated = false) }
        }
    }

    fun share() = export(save = false)
    fun saveToDownloads() = export(save = true)

    private fun export(save: Boolean) {
        val mesh = current ?: return
        val format = _state.value.format
        val name = _state.value.title
        viewModelScope.launch {
            _state.update { it.copy(busyLabel = "Writing ${format.label}…", busyProgress = null) }
            try {
                val ctx = getApplication<Application>()
                if (save) {
                    val path = withContext(Dispatchers.IO) { MeshFiles.saveToDownloads(ctx, mesh, format, name) }
                    _events.emit(ViewerEvent.Message("Saved to $path"))
                } else {
                    val intent = withContext(Dispatchers.IO) { MeshFiles.shareIntent(ctx, mesh, format, name) }
                    _events.emit(ViewerEvent.Share(intent))
                }
            } catch (e: OutOfMemoryError) {
                _events.emit(ViewerEvent.Message("Not enough memory to export. Try simplifying the mesh first."))
            } catch (e: Exception) {
                _events.emit(ViewerEvent.Message("Export failed: ${e.message ?: e.javaClass.simpleName}"))
            } finally {
                _state.update { it.copy(busyLabel = null) }
            }
        }
    }
}
