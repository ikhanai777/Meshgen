package com.meshgen.core

import com.meshgen.core.mesh.MeshCleanup
import com.meshgen.core.mesh.MeshReport
import com.meshgen.core.sdf.BoxSdf
import com.meshgen.core.sdf.CubeTable
import com.meshgen.core.sdf.CylinderSdf
import com.meshgen.core.sdf.Sdf
import com.meshgen.core.sdf.SdfMesher
import com.meshgen.core.sdf.ShellSdf
import com.meshgen.core.sdf.SmoothUnionSdf
import com.meshgen.core.sdf.SphereSdf
import com.meshgen.core.sdf.SubtractSdf
import com.meshgen.core.sdf.TorusSdf
import com.meshgen.core.sdf.TranslateSdf
import com.meshgen.core.sdf.UnionSdf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.random.Random

class SdfMesherTest {

    private fun check(sdf: Sdf, res: Int = 64): MeshReport {
        val raw = SdfMesher.mesh(sdf, res).mesh
        val rawReport = MeshReport.of(raw)
        assertTrue("raw mesh not watertight: ${rawReport.problems}", rawReport.watertight)
        val clean = MeshCleanup.clean(raw)
        assertTrue("cleanup had to fix: ${clean.fixes}", clean.fixes.none { it.startsWith("Flipped") })
        return clean.report
    }

    @Test
    fun `every case table loop is closed and uses each crossing edge once`() {
        for (case in 0 until 256) {
            val edges = CubeTable.LOOPS[case].flatMap { it.toList() }
            assertEquals("case $case uses an edge twice", edges.size, edges.toSet().size)
            val crossing = (0 until 12).filter { e ->
                ((case shr CubeTable.EDGE_A[e]) and 1) != ((case shr CubeTable.EDGE_B[e]) and 1)
            }.toSet()
            assertEquals("case $case", crossing, edges.toSet())
        }
        assertTrue(CubeTable.LOOPS[0].isEmpty()); assertTrue(CubeTable.LOOPS[255].isEmpty())
    }

    @Test
    fun `sphere is watertight with correct volume and size`() {
        val r = check(SphereSdf(20.0), 80)
        assertEquals(4.0 / 3 * PI * 8000, r.volumeMm3, 4.0 / 3 * PI * 8000 * 0.01)
        assertEquals(40f, r.bounds.sizeZ, 0.3f)
        assertEquals(0f, r.bounds.minZ, 0.2f)
        assertEquals(1, r.parts)
    }

    @Test
    fun `box volume within one percent`() {
        val r = check(BoxSdf(30.0, 20.0, 10.0, 0.0), 120)
        assertEquals(6000.0, r.volumeMm3, 60.0)
    }

    @Test
    fun `torus has genus one`() {
        val r = check(TorusSdf(20.0, 6.0), 80)
        assertEquals(0, r.vertexCount - r.triangleCount * 3 / 2 + r.triangleCount)
        assertEquals(2 * PI * PI * 20 * 36, r.volumeMm3, 2 * PI * PI * 20 * 36 * 0.015)
    }

    @Test
    fun `open-top shell cup has the expected material volume`() {
        // Cylinder R=30, H=60, wall 3 → material = outer - cavity (cavity R=27, from z=3 to the top).
        val r = check(ShellSdf(CylinderSdf(30.0, 60.0, 0.0), 3.0, openTop = true), 140)
        val expected = PI * 30 * 30 * 60 - PI * 27 * 27 * 57
        assertEquals(expected, r.volumeMm3, expected * 0.03)
    }

    @Test
    fun `random blobs are always watertight`() {
        val rnd = Random(42)
        repeat(12) {
            val blobs = (0 until 6).map {
                TranslateSdf(SphereSdf(3.0 + rnd.nextDouble() * 6), rnd.nextDouble() * 20 - 10, rnd.nextDouble() * 20 - 10, rnd.nextDouble() * 10)
            }
            val holes = (0 until 3).map {
                TranslateSdf(SphereSdf(2.0 + rnd.nextDouble() * 4), rnd.nextDouble() * 20 - 10, rnd.nextDouble() * 20 - 10, rnd.nextDouble() * 10)
            }
            val shape = SubtractSdf(if (it % 2 == 0) UnionSdf(blobs) else SmoothUnionSdf(blobs, 4.0), holes)
            check(shape, 24 + it * 5)
        }
        // Thin, twisted walls create many ambiguous faces.
        repeat(6) {
            val wavy = object : Sdf() {
                override val bounds = com.meshgen.core.sdf.Aabb(-20.0, -20.0, -20.0, 20.0, 20.0, 20.0)
                override val lipschitz = 4.0
                override fun d(x: Double, y: Double, z: Double) =
                    maxOf(kotlin.math.sqrt(x * x + y * y + z * z) - 15, kotlin.math.sin(x * 0.9 + it) * kotlin.math.cos(y * 0.8) + kotlin.math.sin(z * 1.1) * 0.7)
            }
            check(wavy, 30 + it * 7)
        }
    }

    @Test
    fun `cancellation stops meshing`() {
        try {
            SdfMesher.mesh(SphereSdf(10.0), 64, isCancelled = { true })
            throw AssertionError("expected cancellation")
        } catch (_: com.meshgen.core.sdf.MeshingCancelled) {
        }
    }
}
