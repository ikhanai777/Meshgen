package com.meshgen.core

import com.meshgen.core.mesh.Mesh
import com.meshgen.core.mesh.MeshBuilder
import com.meshgen.core.mesh.MeshCleanup
import com.meshgen.core.mesh.MeshReport
import com.meshgen.core.samples.SampleMesh
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshCleanupTest {

    private fun soup(mesh: Mesh, flip: (Int) -> Boolean = { false }): Mesh {
        val b = MeshBuilder()
        for (t in 0 until mesh.triangleCount) {
            val ids = IntArray(3) { val v = mesh.indices[t * 3 + it] * 3; b.vertex(mesh.positions[v], mesh.positions[v + 1], mesh.positions[v + 2]) }
            if (flip(t)) b.triangle(ids[0], ids[2], ids[1]) else b.triangle(ids[0], ids[1], ids[2])
        }
        return b.build()
    }

    @Test
    fun `triangle soup cube is merged into a watertight 8-vertex cube`() {
        val raw = soup(SampleMesh.box(10f, 10f, 10f))
        assertEquals(36, raw.vertexCount)
        assertFalse(MeshReport.of(raw).watertight)
        val result = MeshCleanup.clean(raw)
        assertEquals(8, result.mesh.vertexCount)
        assertEquals(12, result.mesh.triangleCount)
        assertTrue(result.report.watertight)
        assertEquals(1000.0, result.report.volumeMm3, 1e-6)
        assertTrue(result.fixes.any { "Joined 28 duplicate vertices" in it })
    }

    @Test
    fun `inside-out mesh is detected and flipped`() {
        val inverted = soup(SampleMesh.box(10f, 10f, 10f)) { true }
        val merged = MeshCleanup.clean(soup(SampleMesh.box(10f, 10f, 10f))).mesh
        val invertedIndexed = Mesh(merged.positions, IntArray(merged.indices.size) { i -> merged.indices[i - i % 3 + (3 - i % 3) % 3] })
        assertTrue(MeshReport.of(invertedIndexed).insideOut)
        val result = MeshCleanup.clean(inverted)
        assertTrue(result.mesh.signedVolume() > 0)
        assertFalse(result.report.insideOut)
        assertTrue(result.fixes.any { it.startsWith("Flipped 12") })
    }

    @Test
    fun `single flipped face is corrected`() {
        val result = MeshCleanup.clean(soup(SampleMesh.box(10f, 10f, 10f)) { it == 5 })
        assertTrue(result.report.watertight)
        assertEquals(1000.0, result.mesh.signedVolume(), 1e-6)
        assertTrue(result.fixes.any { it.startsWith("Flipped 1 ") })
    }

    @Test
    fun `damaged sample reports exactly one hole in plain words`() {
        val result = MeshCleanup.clean(SampleMesh.DAMAGED_BOX.build())
        assertEquals(1, result.report.holes)
        assertEquals(3, result.report.boundaryEdges)
        assertFalse(result.report.watertight)
        assertTrue(result.mesh.signedVolume() > 0)
        assertTrue(result.report.problems.single().startsWith("Has 1 hole (3 open edges)"))
    }

    @Test
    fun `degenerate and duplicate triangles are removed`() {
        val cube = SampleMesh.box(10f, 10f, 10f)
        val idx = cube.indices.toMutableList()
        idx += listOf(0, 0, 1) // repeated index
        idx += listOf(cube.indices[0], cube.indices[1], cube.indices[2]) // duplicate
        val result = MeshCleanup.clean(Mesh(cube.positions, idx.toIntArray()))
        assertEquals(12, result.mesh.triangleCount)
        assertTrue(result.report.watertight)
        assertTrue(result.fixes.contains("Removed 1 broken (zero-size) triangles."))
        assertTrue(result.fixes.contains("Removed 1 duplicate triangles."))
    }

    @Test
    fun `zero-area collinear triangle is removed`() {
        val b = MeshBuilder()
        val a = b.vertex(0f, 0f, 0f); val c = b.vertex(1f, 0f, 0f); val d = b.vertex(2f, 0f, 0f); val e = b.vertex(0f, 1f, 0f)
        b.triangle(a, c, d) // collinear
        b.triangle(a, c, e)
        val result = MeshCleanup.clean(b.build())
        assertEquals(1, result.mesh.triangleCount)
    }

    @Test
    fun `edge shared by three faces is non-manifold`() {
        val b = MeshBuilder()
        val p = b.vertex(0f, 0f, 0f); val q = b.vertex(0f, 0f, 1f)
        val x = b.vertex(1f, 0f, 0f); val y = b.vertex(0f, 1f, 0f); val z = b.vertex(-1f, -1f, 0f)
        b.triangle(p, q, x); b.triangle(q, p, y); b.triangle(p, q, z)
        val r = MeshReport.of(b.build())
        assertEquals(1, r.nonManifoldEdges)
        assertFalse(r.watertight)
        assertTrue(r.problems.any { "more than two faces" in it })
    }

    @Test
    fun `separate parts are counted`() {
        val a = SampleMesh.box(5f, 5f, 5f)
        val bb = a.translated(20f, 0f, 0f)
        val joined = Mesh(a.positions + bb.positions, a.indices + bb.indices.map { it + a.vertexCount })
        val r = MeshReport.of(joined)
        assertEquals(2, r.parts)
        assertTrue(r.watertight)
        assertEquals(listOf("Made of 2 separate parts."), r.notes)
    }

    @Test
    fun `NaN vertices are dropped`() {
        val cube = SampleMesh.box(10f, 10f, 10f)
        val pos = cube.positions + floatArrayOf(Float.NaN, 0f, 0f)
        val idx = cube.indices + intArrayOf(0, 1, 8)
        val result = MeshCleanup.clean(Mesh(pos, idx))
        assertEquals(12, result.mesh.triangleCount)
        assertTrue(result.report.watertight)
    }
}
