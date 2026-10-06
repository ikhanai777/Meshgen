package com.meshgen.app.ui.viewer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.meshgen.core.mesh.MeshCleanup
import com.meshgen.core.samples.SampleMesh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Viewer for the built-in sample meshes. */
class ViewerViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val sample = SampleMesh.entries.firstOrNull { it.name == handle.get<String>("sample") } ?: SampleMesh.CALIBRATION_CUBE
    val workbench = MeshWorkbench(app, viewModelScope, sample.title)

    init {
        viewModelScope.launch {
            try {
                val cleaned = withContext(Dispatchers.Default) { MeshCleanup.clean(sample.build()) }
                workbench.show(cleaned.mesh, cleaned.report, cleaned.fixes, reframe = true)
            } catch (e: OutOfMemoryError) {
                workbench.update { it.copy(loading = false, error = "Not enough memory to load this mesh on this phone.") }
            }
        }
    }
}
