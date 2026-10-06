package com.meshgen.app.viewer

import com.meshgen.core.mesh.Bounds
import com.meshgen.core.mesh.Mesh
import com.meshgen.core.mesh.MeshReport

/** GPU-ready mesh data. Built off the main thread; uploaded on the GL thread. */
class ViewerMesh(
    val positions: FloatArray,
    val indices: IntArray,
    val edges: IntArray,
    val bounds: Bounds,
    /** Closed, outward-facing meshes hide back faces; open ones show them (tinted) so gaps are visible. */
    val closed: Boolean,
) {
    companion object {
        fun from(mesh: Mesh, report: MeshReport) =
            ViewerMesh(mesh.positions, mesh.indices, mesh.uniqueEdges(), mesh.bounds(), report.watertight && !report.insideOut)
    }
}

enum class LightingPreset(val label: String) {
    STUDIO("Studio"),
    CLAY("Clay"),
    CONTRAST("Contrast"),
}
