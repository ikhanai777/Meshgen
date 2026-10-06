package com.meshgen.core

import com.meshgen.core.mesh.MeshCleanup
import com.meshgen.core.mesh.MeshReport
import com.meshgen.core.samples.SampleMesh
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class SampleMeshTest {

    @Test
    fun `all clean samples are watertight, outward-facing and on the bed`() {
        for (s in SampleMesh.entries - SampleMesh.DAMAGED_BOX) {
            val mesh = s.build()
            val r = MeshReport.of(mesh)
            assertTrue("$s watertight", r.watertight)
            assertFalse("$s inside-out", r.insideOut)
            assertEquals("$s parts", 1, r.parts)
            assertTrue("$s problems: ${r.problems}", r.problems.isEmpty())
            assertEquals("$s rests on bed", 0f, r.bounds.minZ, 1e-4f)
            assertTrue("$s needs no repair: ${MeshCleanup.clean(mesh).fixes}", MeshCleanup.clean(mesh).fixes.isEmpty())
        }
    }

    @Test
    fun `sample volumes match geometry`() {
        assertEquals(8000.0, MeshReport.of(SampleMesh.CALIBRATION_CUBE.build()).volumeMm3, 1e-3)
        val sphere = 4.0 / 3.0 * PI * 20.0 * 20.0 * 20.0
        assertEquals(sphere, MeshReport.of(SampleMesh.SPHERE.build()).volumeMm3, sphere * 0.03)
        val torus = 2 * PI * PI * 20.0 * 5.0 * 5.0
        assertEquals(torus, MeshReport.of(SampleMesh.RING.build()).volumeMm3, torus * 0.02)
        val cyl = PI * 15.0 * 15.0 * 40.0
        assertEquals(cyl, MeshReport.of(SampleMesh.CYLINDER.build()).volumeMm3, cyl * 0.01)
    }

    @Test
    fun `cube dimensions are 20 mm`() {
        val b = MeshReport.of(SampleMesh.CALIBRATION_CUBE.build()).bounds
        assertEquals(20f, b.sizeX, 1e-4f); assertEquals(20f, b.sizeY, 1e-4f); assertEquals(20f, b.sizeZ, 1e-4f)
    }
}
