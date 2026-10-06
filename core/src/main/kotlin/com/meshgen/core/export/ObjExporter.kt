package com.meshgen.core.export

import com.meshgen.core.mesh.Mesh
import java.io.OutputStream

/** Wavefront OBJ (positions + faces). Units are millimetres. */
object ObjExporter {
    fun write(mesh: Mesh, out: OutputStream, name: String = "MeshGen") {
        val w = out.bufferedWriter(Charsets.UTF_8)
        w.write("# Exported by MeshGen. Units: millimetres, Z up.\n")
        w.write("# ${mesh.vertexCount} vertices, ${mesh.triangleCount} triangles\n")
        w.write("o ${name.replace(Regex("\\s+"), "_")}\n")
        val p = mesh.positions
        val sb = StringBuilder(64)
        for (v in 0 until mesh.vertexCount) {
            sb.setLength(0)
            sb.append("v ").append(fmt(p[v * 3])).append(' ').append(fmt(p[v * 3 + 1])).append(' ').append(fmt(p[v * 3 + 2])).append('\n')
            w.write(sb.toString())
        }
        val idx = mesh.indices
        for (t in 0 until mesh.triangleCount) {
            sb.setLength(0)
            sb.append("f ").append(idx[t * 3] + 1).append(' ').append(idx[t * 3 + 1] + 1).append(' ').append(idx[t * 3 + 2] + 1).append('\n')
            w.write(sb.toString())
        }
        w.flush()
    }
}

/** Plain decimal formatting (no exponent), up to 6 decimals. */
internal fun fmt(f: Float): String {
    if (f == 0f) return "0"
    val s = java.math.BigDecimal(f.toDouble()).setScale(6, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    return if (s == "-0") "0" else s
}
