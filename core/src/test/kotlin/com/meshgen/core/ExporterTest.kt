package com.meshgen.core

import com.meshgen.core.export.ExportFormat
import com.meshgen.core.export.GlbExporter
import com.meshgen.core.export.MeshExporter
import com.meshgen.core.export.ObjExporter
import com.meshgen.core.export.StlExporter
import com.meshgen.core.export.ThreeMfExporter
import com.meshgen.core.mesh.Mesh
import com.meshgen.core.mesh.MeshCleanup
import com.meshgen.core.mesh.MeshReport
import com.meshgen.core.samples.SampleMesh
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.sqrt

class ExporterTest {
    private val cube = SampleMesh.CALIBRATION_CUBE.build()

    private fun bytes(write: (ByteArrayOutputStream) -> Unit) = ByteArrayOutputStream().also(write).toByteArray()

    @Test
    fun `binary STL has correct size, count and unit normals and round-trips`() {
        val data = bytes { StlExporter.write(cube, it) }
        assertEquals(84 + 50 * 12, data.size)
        assertFalse(String(data, 0, 5, Charsets.US_ASCII) == "solid")
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(12, buf.getInt(80))

        // Parse back as a triangle soup and repair: must equal the original cube.
        val pos = FloatArray(12 * 9)
        for (t in 0 until 12) {
            val o = 84 + t * 50
            val nx = buf.getFloat(o); val ny = buf.getFloat(o + 4); val nz = buf.getFloat(o + 8)
            assertEquals(1f, sqrt(nx * nx + ny * ny + nz * nz), 1e-5f)
            for (k in 0 until 9) pos[t * 9 + k] = buf.getFloat(o + 12 + k * 4)
        }
        val back = MeshCleanup.clean(Mesh(pos, IntArray(36) { it })).report
        assertTrue(back.watertight)
        assertEquals(8000.0, back.volumeMm3, 1e-3)
    }

    @Test
    fun `OBJ lists every vertex and 1-based face`() {
        val text = String(bytes { ObjExporter.write(cube, it, "my cube") })
        val lines = text.lines()
        assertEquals(8, lines.count { it.startsWith("v ") })
        val faces = lines.filter { it.startsWith("f ") }
        assertEquals(12, faces.size)
        val ids = faces.flatMap { it.removePrefix("f ").split(' ').map(String::toInt) }
        assertEquals(1, ids.min()); assertEquals(8, ids.max())
        assertTrue(lines.contains("o my_cube"))
        assertFalse("no scientific notation", lines.filter { it.startsWith("v ") }.any { 'E' in it })
        // Round trip
        val pos = lines.filter { it.startsWith("v ") }.flatMap { it.removePrefix("v ").split(' ').map(String::toFloat) }.toFloatArray()
        val r = MeshReport.of(Mesh(pos, ids.map { it - 1 }.toIntArray()))
        assertTrue(r.watertight); assertEquals(8000.0, r.volumeMm3, 1e-3)
    }

    @Test
    fun `GLB has valid header, aligned chunks and metre Y-up positions`() {
        val data = bytes { GlbExporter.write(cube, it, "cube \"quoted\"") }
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x46546C67, buf.getInt(0))
        assertEquals(2, buf.getInt(4))
        assertEquals(data.size, buf.getInt(8))
        val jsonLen = buf.getInt(12)
        assertEquals(0x4E4F534A, buf.getInt(16))
        assertEquals(0, jsonLen % 4)
        val json = String(data, 20, jsonLen, Charsets.UTF_8)
        assertTrue(json.contains("\"count\":8,\"type\":\"VEC3\""))
        assertTrue(json.contains("\"count\":36,\"type\":\"SCALAR\""))
        assertTrue(json.contains("cube \\\"quoted\\\""))
        val binOffset = 20 + jsonLen
        val binLen = buf.getInt(binOffset)
        assertEquals(0x004E4942, buf.getInt(binOffset + 4))
        assertEquals(data.size, binOffset + 8 + binLen)
        // Max Y in glTF (= max Z in mm) must be 0.02 m for a 20 mm cube resting on the bed.
        var maxY = -1f
        for (v in 0 until 8) maxY = maxOf(maxY, buf.getFloat(binOffset + 8 + v * 12 + 4))
        assertEquals(0.02f, maxY, 1e-6f)
        assertTrue(json.contains("\"max\":[0.01,0.02,0.01]"))
    }

    @Test
    fun `3MF zip contains a millimetre model with all vertices and triangles`() {
        val data = bytes { ThreeMfExporter.write(cube, it, "Cube & <friends>") }
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(data)).use { z ->
            while (true) { val e = z.nextEntry ?: break; entries[e.name] = z.readBytes() }
        }
        assertEquals(setOf("[Content_Types].xml", "_rels/.rels", "3D/3dmodel.model"), entries.keys)
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(ByteArrayInputStream(entries["3D/3dmodel.model"]))
        assertEquals("millimeter", doc.documentElement.getAttribute("unit"))
        assertEquals(8, doc.getElementsByTagName("vertex").length)
        assertEquals(12, doc.getElementsByTagName("triangle").length)
        assertEquals("Cube & <friends>", (doc.getElementsByTagName("object").item(0) as org.w3c.dom.Element).getAttribute("name"))
    }

    @Test
    fun `every format writes every sample without error`() {
        val dir = File("build/sample-exports").apply { mkdirs() }
        for (s in SampleMesh.entries) {
            val mesh = MeshCleanup.clean(s.build()).mesh
            for (f in ExportFormat.entries) {
                File(dir, "${s.name.lowercase()}.${f.extension}").outputStream().use { MeshExporter.write(mesh, f, it, s.title) }
            }
        }
        assertTrue(File(dir, "calibration_cube.stl").length() == 84L + 50 * 12)
    }
}
