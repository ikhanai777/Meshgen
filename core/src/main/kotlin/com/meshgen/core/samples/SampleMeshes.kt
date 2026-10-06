package com.meshgen.core.samples

import com.meshgen.core.mesh.Mesh
import com.meshgen.core.mesh.MeshBuilder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Built-in test meshes. All sizes in mm; every mesh rests on the bed (Z = 0). */
enum class SampleMesh(val title: String, val description: String) {
    CALIBRATION_CUBE("Calibration cube", "20 mm cube. The classic first print."),
    SPHERE("Sphere", "Ø 40 mm, smooth geodesic sphere."),
    RING("Torus ring", "50 mm ring with a 10 mm thick tube."),
    CYLINDER("Cylinder", "Ø 30 × 40 mm solid cylinder."),
    DENSE_SPHERE("Dense sphere", "~82,000 triangles. Use it to try decimation."),
    DAMAGED_BOX("Damaged box", "Deliberately broken: loose triangles, one flipped face and a hole."),
    ;

    fun build(): Mesh = when (this) {
        CALIBRATION_CUBE -> box(20f, 20f, 20f)
        SPHERE -> icosphere(20f, 3)
        RING -> torus(20f, 5f, 72, 24)
        CYLINDER -> cylinder(15f, 40f, 64)
        DENSE_SPHERE -> icosphere(25f, 6)
        DAMAGED_BOX -> damagedBox()
    }.placedOnBed()

    companion object {
        fun box(sx: Float, sy: Float, sz: Float): Mesh {
            val b = MeshBuilder()
            val x = sx / 2; val y = sy / 2; val z = sz / 2
            val v = arrayOf(
                b.vertex(-x, -y, -z), b.vertex(x, -y, -z), b.vertex(x, y, -z), b.vertex(-x, y, -z),
                b.vertex(-x, -y, z), b.vertex(x, -y, z), b.vertex(x, y, z), b.vertex(-x, y, z),
            )
            b.quad(v[0], v[3], v[2], v[1]) // bottom (-Z)
            b.quad(v[4], v[5], v[6], v[7]) // top (+Z)
            b.quad(v[0], v[1], v[5], v[4]) // front (-Y)
            b.quad(v[1], v[2], v[6], v[5]) // right (+X)
            b.quad(v[2], v[3], v[7], v[6]) // back (+Y)
            b.quad(v[3], v[0], v[4], v[7]) // left (-X)
            return b.build()
        }

        fun icosphere(radius: Float, subdivisions: Int): Mesh {
            val t = ((1.0 + sqrt(5.0)) / 2.0)
            val verts = mutableListOf(
                doubleArrayOf(-1.0, t, 0.0), doubleArrayOf(1.0, t, 0.0), doubleArrayOf(-1.0, -t, 0.0), doubleArrayOf(1.0, -t, 0.0),
                doubleArrayOf(0.0, -1.0, t), doubleArrayOf(0.0, 1.0, t), doubleArrayOf(0.0, -1.0, -t), doubleArrayOf(0.0, 1.0, -t),
                doubleArrayOf(t, 0.0, -1.0), doubleArrayOf(t, 0.0, 1.0), doubleArrayOf(-t, 0.0, -1.0), doubleArrayOf(-t, 0.0, 1.0),
            ).map { normalize(it) }.toMutableList()
            var faces = listOf(
                intArrayOf(0, 11, 5), intArrayOf(0, 5, 1), intArrayOf(0, 1, 7), intArrayOf(0, 7, 10), intArrayOf(0, 10, 11),
                intArrayOf(1, 5, 9), intArrayOf(5, 11, 4), intArrayOf(11, 10, 2), intArrayOf(10, 7, 6), intArrayOf(7, 1, 8),
                intArrayOf(3, 9, 4), intArrayOf(3, 4, 2), intArrayOf(3, 2, 6), intArrayOf(3, 6, 8), intArrayOf(3, 8, 9),
                intArrayOf(4, 9, 5), intArrayOf(2, 4, 11), intArrayOf(6, 2, 10), intArrayOf(8, 6, 7), intArrayOf(9, 8, 1),
            )
            repeat(subdivisions) {
                val cache = HashMap<Long, Int>()
                fun mid(a: Int, b: Int): Int {
                    val key = if (a < b) (a.toLong() shl 32) or b.toLong() else (b.toLong() shl 32) or a.toLong()
                    return cache.getOrPut(key) {
                        val pa = verts[a]; val pb = verts[b]
                        verts.add(normalize(doubleArrayOf(pa[0] + pb[0], pa[1] + pb[1], pa[2] + pb[2])))
                        verts.size - 1
                    }
                }
                faces = faces.flatMap { (a, b, c) ->
                    val ab = mid(a, b); val bc = mid(b, c); val ca = mid(c, a)
                    listOf(intArrayOf(a, ab, ca), intArrayOf(b, bc, ab), intArrayOf(c, ca, bc), intArrayOf(ab, bc, ca))
                }
            }
            val b = MeshBuilder()
            verts.forEach { b.vertex(it[0] * radius, it[1] * radius, it[2] * radius) }
            faces.forEach { (a, c, d) -> b.triangle(a, c, d) }
            return b.build()
        }

        /** Ring around the Z axis: [major] = centre-line radius, [minor] = tube radius. */
        fun torus(major: Float, minor: Float, segU: Int, segV: Int): Mesh {
            val b = MeshBuilder()
            for (i in 0 until segU) {
                val u = 2 * PI * i / segU
                for (j in 0 until segV) {
                    val v = 2 * PI * j / segV
                    val r = major + minor * cos(v)
                    b.vertex(r * cos(u), r * sin(u), minor * sin(v))
                }
            }
            fun id(i: Int, j: Int) = (i % segU) * segV + (j % segV)
            for (i in 0 until segU) for (j in 0 until segV) {
                b.quad(id(i, j), id(i + 1, j), id(i + 1, j + 1), id(i, j + 1))
            }
            return b.build()
        }

        fun cylinder(radius: Float, height: Float, segments: Int): Mesh {
            val b = MeshBuilder()
            val bottomCenter = b.vertex(0f, 0f, 0f)
            val topCenter = b.vertex(0f, 0f, height)
            val bottom = IntArray(segments)
            val top = IntArray(segments)
            for (i in 0 until segments) {
                val a = 2 * PI * i / segments
                bottom[i] = b.vertex(radius * cos(a), radius * sin(a), 0.0)
                top[i] = b.vertex(radius * cos(a), radius * sin(a), height.toDouble())
            }
            for (i in 0 until segments) {
                val n = (i + 1) % segments
                b.quad(bottom[i], bottom[n], top[n], top[i])
                b.triangle(topCenter, top[i], top[n])
                b.triangle(bottomCenter, bottom[n], bottom[i])
            }
            return b.build()
        }

        /** A cube as an unindexed "triangle soup" with one inverted and one missing triangle. */
        fun damagedBox(): Mesh {
            val cube = box(25f, 25f, 25f)
            val b = MeshBuilder()
            for (t in 0 until cube.triangleCount) {
                if (t == 3) continue // missing triangle → hole
                val ids = IntArray(3) {
                    val v = cube.indices[t * 3 + it] * 3
                    b.vertex(cube.positions[v], cube.positions[v + 1], cube.positions[v + 2])
                }
                if (t == 7) b.triangle(ids[0], ids[2], ids[1]) else b.triangle(ids[0], ids[1], ids[2])
            }
            return b.build()
        }

        private fun normalize(v: DoubleArray): DoubleArray {
            val l = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
            return doubleArrayOf(v[0] / l, v[1] / l, v[2] / l)
        }
    }
}
