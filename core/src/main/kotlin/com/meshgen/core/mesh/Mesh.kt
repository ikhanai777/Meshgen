package com.meshgen.core.mesh

import com.meshgen.core.util.IntList
import com.meshgen.core.util.LongIntMap
import com.meshgen.core.util.edgeKey
import kotlin.math.sqrt

/**
 * Indexed triangle mesh. Units are millimetres, Z is up (3D-printing convention).
 * Triangles are counter-clockwise when seen from outside.
 */
class Mesh(val positions: FloatArray, val indices: IntArray) {
    init {
        require(positions.size % 3 == 0) { "positions must be xyz triples" }
        require(indices.size % 3 == 0) { "indices must be triangles" }
    }

    val vertexCount: Int get() = positions.size / 3
    val triangleCount: Int get() = indices.size / 3

    fun bounds(): Bounds {
        if (vertexCount == 0) return Bounds(0f, 0f, 0f, 0f, 0f, 0f)
        var minX = Float.POSITIVE_INFINITY; var minY = minX; var minZ = minX
        var maxX = Float.NEGATIVE_INFINITY; var maxY = maxX; var maxZ = maxX
        for (i in 0 until vertexCount) {
            val x = positions[i * 3]; val y = positions[i * 3 + 1]; val z = positions[i * 3 + 2]
            if (x < minX) minX = x; if (x > maxX) maxX = x
            if (y < minY) minY = y; if (y > maxY) maxY = y
            if (z < minZ) minZ = z; if (z > maxZ) maxZ = z
        }
        return Bounds(minX, minY, minZ, maxX, maxY, maxZ)
    }

    /** Signed volume in mm³ (positive when faces point outward). */
    fun signedVolume(): Double {
        var v = 0.0
        for (t in 0 until triangleCount) v += tetVolume(t)
        return v
    }

    internal fun tetVolume(t: Int): Double {
        val a = indices[t * 3] * 3; val b = indices[t * 3 + 1] * 3; val c = indices[t * 3 + 2] * 3
        val p = positions
        val ax = p[a].toDouble(); val ay = p[a + 1].toDouble(); val az = p[a + 2].toDouble()
        val bx = p[b].toDouble(); val by = p[b + 1].toDouble(); val bz = p[b + 2].toDouble()
        val cx = p[c].toDouble(); val cy = p[c + 1].toDouble(); val cz = p[c + 2].toDouble()
        return (ax * (by * cz - bz * cy) - ay * (bx * cz - bz * cx) + az * (bx * cy - by * cx)) / 6.0
    }

    fun surfaceArea(): Double {
        var area = 0.0
        val n = DoubleArray(3)
        for (t in 0 until triangleCount) {
            faceNormal(t, n)
            area += sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]) / 2.0
        }
        return area
    }

    /** Unnormalized face normal (length = 2 × area) written into [out]. */
    fun faceNormal(t: Int, out: DoubleArray) {
        val a = indices[t * 3] * 3; val b = indices[t * 3 + 1] * 3; val c = indices[t * 3 + 2] * 3
        val p = positions
        val ux = (p[b] - p[a]).toDouble(); val uy = (p[b + 1] - p[a + 1]).toDouble(); val uz = (p[b + 2] - p[a + 2]).toDouble()
        val vx = (p[c] - p[a]).toDouble(); val vy = (p[c + 1] - p[a + 1]).toDouble(); val vz = (p[c + 2] - p[a + 2]).toDouble()
        out[0] = uy * vz - uz * vy
        out[1] = uz * vx - ux * vz
        out[2] = ux * vy - uy * vx
    }

    /** Each undirected edge once, as consecutive vertex index pairs (for wireframe drawing). */
    fun uniqueEdges(): IntArray {
        val seen = LongIntMap(indices.size)
        val out = IntList(indices.size)
        for (t in 0 until triangleCount) {
            for (k in 0..2) {
                val a = indices[t * 3 + k]
                val b = indices[t * 3 + (k + 1) % 3]
                if (seen.putIfAbsent(edgeKey(a, b), 1) == -1) { out.add(a); out.add(b) }
            }
        }
        return out.toIntArray()
    }

    fun translated(dx: Float, dy: Float, dz: Float): Mesh {
        val p = positions.copyOf()
        for (i in 0 until vertexCount) { p[i * 3] += dx; p[i * 3 + 1] += dy; p[i * 3 + 2] += dz }
        return Mesh(p, indices.copyOf())
    }

    /** Centres the mesh on X/Y and rests it on Z = 0 (the print bed). */
    fun placedOnBed(): Mesh {
        val b = bounds()
        return translated(-b.centerX, -b.centerY, -b.minZ)
    }
}

data class Bounds(
    val minX: Float, val minY: Float, val minZ: Float,
    val maxX: Float, val maxY: Float, val maxZ: Float,
) {
    val sizeX get() = maxX - minX
    val sizeY get() = maxY - minY
    val sizeZ get() = maxZ - minZ
    val centerX get() = (minX + maxX) / 2f
    val centerY get() = (minY + maxY) / 2f
    val centerZ get() = (minZ + maxZ) / 2f
    val diagonal get() = sqrt(sizeX * sizeX + sizeY * sizeY + sizeZ * sizeZ)
}

/** Builds indexed meshes incrementally. */
class MeshBuilder {
    private var pos = FloatArray(256)
    private var posSize = 0
    private val idx = IntList(256)

    val vertexCount get() = posSize / 3

    fun vertex(x: Float, y: Float, z: Float): Int {
        if (posSize + 3 > pos.size) pos = pos.copyOf(pos.size * 2)
        pos[posSize++] = x; pos[posSize++] = y; pos[posSize++] = z
        return posSize / 3 - 1
    }

    fun vertex(x: Double, y: Double, z: Double) = vertex(x.toFloat(), y.toFloat(), z.toFloat())

    fun triangle(a: Int, b: Int, c: Int) { idx.add(a); idx.add(b); idx.add(c) }

    /** Quad a-b-c-d in counter-clockwise order. */
    fun quad(a: Int, b: Int, c: Int, d: Int) { triangle(a, b, c); triangle(a, c, d) }

    fun build() = Mesh(pos.copyOf(posSize), idx.toIntArray())
}
