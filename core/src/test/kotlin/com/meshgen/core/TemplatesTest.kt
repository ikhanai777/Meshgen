package com.meshgen.core

import com.meshgen.core.dsl.Quality
import com.meshgen.core.dsl.ShapeDsl
import com.meshgen.core.dsl.ShapeEngine
import com.meshgen.core.dsl.Templates
import com.meshgen.core.export.ExportFormat
import com.meshgen.core.export.MeshExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TemplatesTest {

    @Test
    fun `there are at least 15 templates`() {
        assertTrue(Templates.IDS.size >= 15)
        assertEquals(Templates.IDS.size, Templates.IDS.toSet().size)
    }

    @Test
    fun `every template meshes watertight at default, minimum and maximum parameters`() {
        val failures = mutableListOf<String>()
        for (id in Templates.IDS) {
            val doc = Templates.load(id)
            assertTrue("$id needs a prompt", !doc.prompt.isNullOrBlank())
            assertTrue("$id needs a category", !doc.category.isNullOrBlank())
            val variants = mapOf(
                "default" to doc,
                "min" to doc.withValues(doc.params.associate { it.name to it.min }),
                "max" to doc.withValues(doc.params.associate { it.name to it.max }),
            )
            for ((label, d) in variants) {
                val compiled = ShapeDsl.compile(d)
                if (compiled.sdf == null) { failures += "$id/$label: ${compiled.issues}"; continue }
                val g = ShapeEngine.generate(d, Quality.DRAFT)
                if (!g.report.watertight) failures += "$id/$label: not watertight ${g.report.problems}"
                if (g.report.triangleCount < 100) failures += "$id/$label: only ${g.report.triangleCount} triangles"
                if (g.report.bounds.minZ != 0f) failures += "$id/$label: not on the bed"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `planter default has the documented size and a drainage hole`() {
        val g = ShapeEngine.generate(Templates.load("planter"), Quality.STANDARD)
        assertTrue(g.report.watertight)
        assertEquals(1, g.report.parts)
        assertEquals(122f, g.report.bounds.sizeX, 2f) // 120 mm + rolled rim
        assertEquals(110f, g.report.bounds.sizeZ, 1.5f)
        // Genus: a hole through the bottom makes the pot a torus-like surface (Euler characteristic 0).
        val r = g.report
        assertEquals(0, r.vertexCount - r.triangleCount * 3 / 2 + r.triangleCount)
    }

    @Test
    fun `write template exports for inspection`() {
        val dir = File("build/template-exports").apply { mkdirs() }
        for (id in Templates.IDS) {
            val g = ShapeEngine.generate(Templates.load(id), Quality.STANDARD)
            File(dir, "$id.stl").outputStream().buffered().use { MeshExporter.write(g.mesh, ExportFormat.STL, it, id) }
            File(dir, "$id.obj").outputStream().buffered().use { MeshExporter.write(g.mesh, ExportFormat.OBJ, it, id) }
            println("$id: ${g.report.triangleCount} tris, ${g.millis} ms, grid ${g.stats.gridX}x${g.stats.gridY}x${g.stats.gridZ}, evals ${g.stats.evaluations}")
        }
    }
}
