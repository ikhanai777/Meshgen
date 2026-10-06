package com.meshgen.core

import com.meshgen.core.mesh.Decimator
import com.meshgen.core.mesh.MeshReport
import com.meshgen.core.samples.SampleMesh
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DecimatorTest {

    @Test
    fun `dense sphere decimates to target and stays watertight`() {
        val mesh = SampleMesh.DENSE_SPHERE.build()
        val before = MeshReport.of(mesh)
        val out = Decimator.decimate(mesh, 5000)
        val after = MeshReport.of(out)
        assertTrue("got ${after.triangleCount}", after.triangleCount in 4000..5000)
        assertTrue(after.problems.toString(), after.watertight)
        assertEquals(1, after.parts)
        assertEquals(before.volumeMm3, after.volumeMm3, before.volumeMm3 * 0.02)
        assertEquals(before.bounds.sizeZ, after.bounds.sizeZ, before.bounds.sizeZ * 0.03f)
    }

    @Test
    fun `torus keeps its hole and stays watertight`() {
        val mesh = SampleMesh.RING.build()
        val out = Decimator.decimate(mesh, mesh.triangleCount / 4)
        val r = MeshReport.of(out)
        assertTrue(r.watertight)
        assertEquals(1, r.parts)
        // Euler characteristic of a torus is 0: V - E + F = 0, with E = 3F/2 for a closed mesh.
        assertEquals(0, r.vertexCount - r.triangleCount * 3 / 2 + r.triangleCount)
    }

    @Test
    fun `cube cannot collapse below a valid solid`() {
        val out = Decimator.decimate(SampleMesh.CALIBRATION_CUBE.build(), 4)
        val r = MeshReport.of(out)
        assertTrue(r.watertight)
        assertTrue(r.triangleCount >= 4)
    }

    @Test
    fun `target above current count returns the same mesh`() {
        val mesh = SampleMesh.SPHERE.build()
        assertTrue(Decimator.decimate(mesh, mesh.triangleCount + 1) === mesh)
    }
}
