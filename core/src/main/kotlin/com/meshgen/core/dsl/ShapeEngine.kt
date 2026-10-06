package com.meshgen.core.dsl

import com.meshgen.core.mesh.Mesh
import com.meshgen.core.mesh.MeshCleanup
import com.meshgen.core.mesh.MeshReport
import com.meshgen.core.sdf.SdfMesher

enum class Quality(val label: String, val cells: Int) {
    DRAFT("Draft", 64),
    STANDARD("Standard", 128),
    FINE("Fine", 200),
}

class ShapeError(val issues: List<DslIssue>) : Exception(issues.joinToString("\n"))

/** Recipe → SDF → marching cubes → bed placement → cleanup and report. */
object ShapeEngine {
    class Generated(val mesh: Mesh, val report: MeshReport, val fixes: List<String>, val stats: SdfMesher.Stats, val millis: Long)

    fun generate(
        doc: ShapeDoc,
        quality: Quality,
        progress: SdfMesher.Progress? = null,
        isCancelled: () -> Boolean = { false },
    ): Generated {
        val start = System.nanoTime()
        val compiled = ShapeDsl.compile(doc)
        val sdf = compiled.sdf ?: throw ShapeError(compiled.issues)
        val result = SdfMesher.mesh(sdf, quality.cells, progress, isCancelled)
        val cleaned = MeshCleanup.clean(result.mesh.placedOnBed())
        return Generated(cleaned.mesh, cleaned.report, cleaned.fixes, result.stats, (System.nanoTime() - start) / 1_000_000)
    }
}
