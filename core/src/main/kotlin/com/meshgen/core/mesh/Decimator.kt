package com.meshgen.core.mesh

import com.meshgen.core.util.IntList
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Quadric-error edge-collapse simplification (Garland & Heckbert).
 * Keeps closed meshes closed: collapses that would break manifoldness (link condition),
 * flip a face, or move an open boundary are rejected.
 */
object Decimator {

    fun interface Progress { fun update(fraction: Float) }

    fun decimate(mesh: Mesh, targetTriangles: Int, progress: Progress? = null, isCancelled: () -> Boolean = { false }): Mesh {
        if (targetTriangles >= mesh.triangleCount) return mesh
        return Collapser(mesh).run(maxOf(targetTriangles, 4), progress, isCancelled)
    }

    private class Candidate(val cost: Double, val u: Int, val v: Int, val verU: Int, val verV: Int, val x: Double, val y: Double, val z: Double)

    private class Collapser(mesh: Mesh) {
        val nV = mesh.vertexCount
        val nF = mesh.triangleCount
        val pos = DoubleArray(nV * 3) { mesh.positions[it].toDouble() }
        val faces = mesh.indices.copyOf()
        val faceAlive = BooleanArray(nF) { true }
        val vertFaces = Array(nV) { IntList(6) }
        val q = DoubleArray(nV * 10)
        val version = IntArray(nV)
        val locked = BooleanArray(nV)
        var liveFaces = nF
        val heap = PriorityQueue<Candidate>(compareBy { it.cost })
        val sum = DoubleArray(10)
        val markA = IntArray(nV)
        var stamp = 0
        val n0 = DoubleArray(3)
        val n1 = DoubleArray(3)

        init {
            for (f in 0 until nF) for (k in 0..2) vertFaces[faces[f * 3 + k]].add(f)
            val n = DoubleArray(3)
            for (f in 0 until nF) {
                faceNormalTo(f, n)
                val len = sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2])
                if (len == 0.0) continue
                val a = n[0] / len; val b = n[1] / len; val c = n[2] / len
                val p0 = faces[f * 3] * 3
                val d = -(a * pos[p0] + b * pos[p0 + 1] + c * pos[p0 + 2])
                val w = len / 2.0 // area weight
                for (k in 0..2) addPlane(faces[f * 3 + k], a, b, c, d, w)
            }
            val edges = EdgeTable(faces)
            for (e in 0 until edges.edgeCount) {
                if (edges.uses[e] != 2) { locked[edges.v0[e]] = true; locked[edges.v1[e]] = true }
            }
            for (e in 0 until edges.edgeCount) push(edges.v0[e], edges.v1[e])
        }

        fun addPlane(v: Int, a: Double, b: Double, c: Double, d: Double, w: Double) {
            val o = v * 10
            q[o] += w * a * a; q[o + 1] += w * a * b; q[o + 2] += w * a * c; q[o + 3] += w * a * d
            q[o + 4] += w * b * b; q[o + 5] += w * b * c; q[o + 6] += w * b * d
            q[o + 7] += w * c * c; q[o + 8] += w * c * d; q[o + 9] += w * d * d
        }

        fun error(s: DoubleArray, x: Double, y: Double, z: Double): Double =
            s[0] * x * x + 2 * s[1] * x * y + 2 * s[2] * x * z + 2 * s[3] * x +
                s[4] * y * y + 2 * s[5] * y * z + 2 * s[6] * y +
                s[7] * z * z + 2 * s[8] * z + s[9]

        fun push(u: Int, v: Int) {
            if (locked[u] || locked[v]) return
            for (i in 0 until 10) sum[i] = q[u * 10 + i] + q[v * 10 + i]
            val s = sum
            // Solve A x = -b for the optimal position.
            val a00 = s[0]; val a01 = s[1]; val a02 = s[2]; val a11 = s[4]; val a12 = s[5]; val a22 = s[7]
            val b0 = -s[3]; val b1 = -s[6]; val b2 = -s[8]
            val det = a00 * (a11 * a22 - a12 * a12) - a01 * (a01 * a22 - a12 * a02) + a02 * (a01 * a12 - a11 * a02)
            val ux = pos[u * 3]; val uy = pos[u * 3 + 1]; val uz = pos[u * 3 + 2]
            val vx = pos[v * 3]; val vy = pos[v * 3 + 1]; val vz = pos[v * 3 + 2]
            val mx = (ux + vx) / 2; val my = (uy + vy) / 2; val mz = (uz + vz) / 2
            val edgeLen = sqrt((ux - vx) * (ux - vx) + (uy - vy) * (uy - vy) + (uz - vz) * (uz - vz))
            var bx = mx; var by = my; var bz = mz
            var best = Double.MAX_VALUE
            if (abs(det) > 1e-12) {
                val x = (b0 * (a11 * a22 - a12 * a12) - a01 * (b1 * a22 - a12 * b2) + a02 * (b1 * a12 - a11 * b2)) / det
                val y = (a00 * (b1 * a22 - a12 * b2) - b0 * (a01 * a22 - a12 * a02) + a02 * (a01 * b2 - b1 * a02)) / det
                val z = (a00 * (a11 * b2 - b1 * a12) - a01 * (a01 * b2 - b1 * a02) + b0 * (a01 * a12 - a11 * a02)) / det
                val dist = sqrt((x - mx) * (x - mx) + (y - my) * (y - my) + (z - mz) * (z - mz))
                if (dist <= edgeLen * 2) { bx = x; by = y; bz = z; best = error(s, x, y, z) }
            }
            for ((x, y, z) in arrayOf(doubleArrayOf(ux, uy, uz), doubleArrayOf(vx, vy, vz), doubleArrayOf(mx, my, mz))) {
                val e = error(s, x, y, z)
                if (e < best) { best = e; bx = x; by = y; bz = z }
            }
            heap.add(Candidate(maxOf(best, 0.0), u, v, version[u], version[v], bx, by, bz))
        }

        fun faceNormalTo(f: Int, out: DoubleArray, sub: Int = -1, sx: Double = 0.0, sy: Double = 0.0, sz: Double = 0.0) {
            val ia = faces[f * 3]; val ib = faces[f * 3 + 1]; val ic = faces[f * 3 + 2]
            fun cx(i: Int) = if (i == sub) sx else pos[i * 3]
            fun cy(i: Int) = if (i == sub) sy else pos[i * 3 + 1]
            fun cz(i: Int) = if (i == sub) sz else pos[i * 3 + 2]
            val ux = cx(ib) - cx(ia); val uy = cy(ib) - cy(ia); val uz = cz(ib) - cz(ia)
            val vx = cx(ic) - cx(ia); val vy = cy(ic) - cy(ia); val vz = cz(ic) - cz(ia)
            out[0] = uy * vz - uz * vy; out[1] = uz * vx - ux * vz; out[2] = ux * vy - uy * vx
        }

        fun has(f: Int, v: Int) = faces[f * 3] == v || faces[f * 3 + 1] == v || faces[f * 3 + 2] == v

        fun pruneDead(v: Int) {
            val list = vertFaces[v]
            var i = 0
            while (i < list.size) { val f = list[i]; if (!faceAlive[f] || !has(f, v)) list.removeAt(i) else i++ }
        }

        /** Link condition: u and v must share exactly the two vertices opposite their shared edge. */
        fun linkOk(u: Int, v: Int): Boolean {
            stamp++
            val lu = vertFaces[u]
            for (i in 0 until lu.size) { val f = lu[i]; for (k in 0..2) markA[faces[f * 3 + k]] = stamp }
            var shared = 0
            var sharedFaces = 0
            val lv = vertFaces[v]
            stamp++
            val seenStamp = stamp
            for (i in 0 until lv.size) {
                val f = lv[i]
                if (has(f, u)) sharedFaces++
                for (k in 0..2) {
                    val w = faces[f * 3 + k]
                    if (w == u || w == v) continue
                    if (markA[w] == seenStamp - 1) { markA[w] = seenStamp; shared++ }
                }
            }
            return sharedFaces == 2 && shared == 2
        }

        fun flipsAny(moving: Int, other: Int, x: Double, y: Double, z: Double): Boolean {
            val list = vertFaces[moving]
            for (i in 0 until list.size) {
                val f = list[i]
                if (has(f, other)) continue // removed by the collapse
                faceNormalTo(f, n0)
                faceNormalTo(f, n1, moving, x, y, z)
                val l1 = sqrt(n1[0] * n1[0] + n1[1] * n1[1] + n1[2] * n1[2])
                val l0 = sqrt(n0[0] * n0[0] + n0[1] * n0[1] + n0[2] * n0[2])
                if (l1 < 1e-12 * (1 + l0)) return true
                if (n0[0] * n1[0] + n0[1] * n1[1] + n0[2] * n1[2] < 0.2 * l0 * l1) return true
            }
            return false
        }

        fun run(target: Int, progress: Progress?, isCancelled: () -> Boolean): Mesh {
            val toRemove = (nF - target).toFloat()
            var steps = 0
            while (liveFaces > target && heap.isNotEmpty()) {
                val c = heap.poll()
                val u = c.u; val v = c.v
                if (version[u] != c.verU || version[v] != c.verV) continue
                pruneDead(u); pruneDead(v)
                if (vertFaces[u].size == 0 || vertFaces[v].size == 0) continue
                if (!linkOk(u, v)) continue
                if (flipsAny(u, v, c.x, c.y, c.z) || flipsAny(v, u, c.x, c.y, c.z)) continue

                // Collapse v into u.
                val lv = vertFaces[v]
                for (i in 0 until lv.size) {
                    val f = lv[i]
                    if (!faceAlive[f]) continue
                    if (has(f, u)) { faceAlive[f] = false; liveFaces--; continue }
                    for (k in 0..2) if (faces[f * 3 + k] == v) faces[f * 3 + k] = u
                    vertFaces[u].add(f)
                }
                lv.clear()
                pruneDead(u)
                pos[u * 3] = c.x; pos[u * 3 + 1] = c.y; pos[u * 3 + 2] = c.z
                for (i in 0 until 10) q[u * 10 + i] += q[v * 10 + i]
                version[u]++; version[v]++

                // Re-queue edges around u.
                stamp++
                val lu = vertFaces[u]
                for (i in 0 until lu.size) {
                    val f = lu[i]
                    for (k in 0..2) {
                        val w = faces[f * 3 + k]
                        if (w != u && markA[w] != stamp) { markA[w] = stamp; push(u, w) }
                    }
                }
                if (++steps % 512 == 0) {
                    if (isCancelled()) break
                    progress?.update(((nF - liveFaces) / toRemove).coerceIn(0f, 1f))
                }
            }
            progress?.update(1f)
            val out = IntList(liveFaces * 3)
            for (f in 0 until nF) if (faceAlive[f]) { out.add(faces[f * 3]); out.add(faces[f * 3 + 1]); out.add(faces[f * 3 + 2]) }
            val positions = FloatArray(nV * 3) { pos[it].toFloat() }
            return MeshCleanup.compact(Mesh(positions, out.toIntArray()))
        }
    }
}
