package com.meshgen.core.export

import com.meshgen.core.mesh.Mesh
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * glTF 2.0 binary. glTF uses metres and Y-up, so millimetre Z-up coordinates are converted:
 * (x, y, z) mm → (x, z, −y) / 1000 m. Normals are omitted on purpose: viewers then render flat
 * shading, which is correct for both hard-edged and curved printable parts.
 */
object GlbExporter {
    fun write(mesh: Mesh, out: OutputStream, name: String = "MeshGen") {
        val nV = mesh.vertexCount
        val nI = mesh.indices.size
        val posBytes = nV * 12
        val idxBytes = nI * 4
        val bin = ByteBuffer.allocate(pad4(posBytes + idxBytes)).order(ByteOrder.LITTLE_ENDIAN)
        val min = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
        val max = floatArrayOf(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        val p = mesh.positions
        for (v in 0 until nV) {
            val c = floatArrayOf(p[v * 3] / 1000f, p[v * 3 + 2] / 1000f, -p[v * 3 + 1] / 1000f)
            for (k in 0..2) {
                bin.putFloat(c[k])
                if (c[k] < min[k]) min[k] = c[k]
                if (c[k] > max[k]) max[k] = c[k]
            }
        }
        if (nV == 0) { min.fill(0f); max.fill(0f) }
        for (i in mesh.indices) bin.putInt(i)
        while (bin.hasRemaining()) bin.put(0)

        val safeName = name.replace("\\", "\\\\").replace("\"", "\\\"")
        val json = """
            {"asset":{"version":"2.0","generator":"MeshGen"},
            "scene":0,"scenes":[{"nodes":[0]}],
            "nodes":[{"mesh":0,"name":"$safeName"}],
            "materials":[{"name":"MeshGen grey","pbrMetallicRoughness":{"baseColorFactor":[0.72,0.74,0.77,1.0],"metallicFactor":0.0,"roughnessFactor":0.6}}],
            "meshes":[{"name":"$safeName","primitives":[{"attributes":{"POSITION":0},"indices":1,"material":0,"mode":4}]}],
            "buffers":[{"byteLength":${bin.capacity()}}],
            "bufferViews":[
              {"buffer":0,"byteOffset":0,"byteLength":$posBytes,"target":34962},
              {"buffer":0,"byteOffset":$posBytes,"byteLength":$idxBytes,"target":34963}],
            "accessors":[
              {"bufferView":0,"componentType":5126,"count":$nV,"type":"VEC3","min":[${min.joinToString(",") { it.toString() }}],"max":[${max.joinToString(",") { it.toString() }}]},
              {"bufferView":1,"componentType":5125,"count":$nI,"type":"SCALAR"}]}
        """.trimIndent().replace("\n", "")
        val jsonBytes = json.toByteArray(Charsets.UTF_8)
        val jsonLen = pad4(jsonBytes.size)
        val total = 12 + 8 + jsonLen + 8 + bin.capacity()

        val head = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
        head.putInt(0x46546C67) // "glTF"
        head.putInt(2)
        head.putInt(total)
        head.putInt(jsonLen)
        head.putInt(0x4E4F534A) // "JSON"
        out.write(head.array())
        out.write(jsonBytes)
        repeat(jsonLen - jsonBytes.size) { out.write(' '.code) }
        val binHead = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        binHead.putInt(bin.capacity())
        binHead.putInt(0x004E4942) // "BIN\0"
        out.write(binHead.array())
        out.write(bin.array())
        out.flush()
    }

    private fun pad4(n: Int) = (n + 3) and 3.inv()
}
