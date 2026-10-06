package com.meshgen.core.export

import com.meshgen.core.mesh.Mesh
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** Binary STL. Units are millimetres (the de-facto convention slicers assume). */
object StlExporter {
    fun write(mesh: Mesh, out: OutputStream, name: String = "MeshGen") {
        val header = ByteArray(80)
        val text = "MeshGen binary STL, units mm: $name".toByteArray(Charsets.US_ASCII)
        // Must not start with "solid", or some readers mistake binary for ASCII STL.
        System.arraycopy(text, 0, header, 0, minOf(text.size, 80))
        out.write(header)
        val buf = ByteBuffer.allocate(50 * 1024).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(mesh.triangleCount)
        val n = DoubleArray(3)
        val p = mesh.positions
        for (t in 0 until mesh.triangleCount) {
            if (buf.remaining() < 50) { out.write(buf.array(), 0, buf.position()); buf.clear() }
            mesh.faceNormal(t, n)
            val len = sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2])
            if (len > 0) { buf.putFloat((n[0] / len).toFloat()); buf.putFloat((n[1] / len).toFloat()); buf.putFloat((n[2] / len).toFloat()) }
            else { buf.putFloat(0f); buf.putFloat(0f); buf.putFloat(0f) }
            for (k in 0..2) {
                val v = mesh.indices[t * 3 + k] * 3
                buf.putFloat(p[v]); buf.putFloat(p[v + 1]); buf.putFloat(p[v + 2])
            }
            buf.putShort(0)
        }
        out.write(buf.array(), 0, buf.position())
        out.flush()
    }
}
