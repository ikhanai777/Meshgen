package com.meshgen.core.mesh

import com.meshgen.core.util.IntList
import com.meshgen.core.util.LongIntMap
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

class CleanupResult(
    val mesh: Mesh,
    /** What was repaired, in plain words. */
    val fixes: List<String>,
    val report: MeshReport,
)

/**
 * Repairs common mesh defects: duplicate vertices, broken (zero-size) and duplicate triangles,
 * inconsistent or inside-out face orientation. Holes and non-manifold edges are reported, not repaired.
 */
object MeshCleanup {

    fun clean(input: Mesh): CleanupResult {
        val fixes = mutableListOf<String>()

        // 1. Drop triangles that touch non-finite coordinates.
        var mesh = dropNonFinite(input).also { (m, removed) ->
            if (removed > 0) fixes += "Removed $removed triangles with invalid coordinates."
        }.first

        // 2. Merge vertices that sit on (almost) the same spot.
        val diag = mesh.bounds().diagonal
        val eps = max(diag * 1e-6f, 1e-7f)
        val before = mesh.vertexCount
        mesh = mergeVertices(mesh, eps)
        val merged = before - mesh.vertexCount
        if (merged > 0) fixes += "Joined $merged duplicate vertices."

        // 3. Remove degenerate and duplicate triangles.
        val (noDegenerate, degenerate) = removeDegenerate(mesh, eps)
        if (degenerate > 0) fixes += "Removed $degenerate broken (zero-size) triangles."
        val (noDuplicates, duplicates) = removeDuplicateFaces(noDegenerate)
        if (duplicates > 0) fixes += "Removed $duplicates duplicate triangles."
        mesh = compact(noDuplicates)

        // 4. Make face orientation consistent and outward.
        val flipped = orient(mesh)
        if (flipped > 0) fixes += "Flipped $flipped triangles that faced the wrong way."

        return CleanupResult(mesh, fixes, MeshReport.of(mesh))
    }

    private fun dropNonFinite(mesh: Mesh): Pair<Mesh, Int> {
        val p = mesh.positions
        val bad = BooleanArray(mesh.vertexCount) { v -> !(p[v * 3].isFinite() && p[v * 3 + 1].isFinite() && p[v * 3 + 2].isFinite()) }
        if (bad.none { it }) return mesh to 0
        val out = IntList(mesh.indices.size)
        var removed = 0
        for (t in 0 until mesh.triangleCount) {
            val a = mesh.indices[t * 3]; val b = mesh.indices[t * 3 + 1]; val c = mesh.indices[t * 3 + 2]
            if (bad[a] || bad[b] || bad[c]) { removed++; continue }
            out.add(a); out.add(b); out.add(c)
        }
        val zeroed = p.copyOf().also { for (i in it.indices) if (!it[i].isFinite()) it[i] = 0f }
        return compact(Mesh(zeroed, out.toIntArray())) to removed
    }

    /** Merges vertices closer than [eps] using a spatial hash; checks the 27 neighbouring cells. */
    internal fun mergeVertices(mesh: Mesh, eps: Float): Mesh {
        val n = mesh.vertexCount
        if (n == 0) return mesh
        val p = mesh.positions
        val b = mesh.bounds()
        val cell = eps * 2f
        val cells = LongIntMap(n)
        val next = IntArray(n) { -1 } // linked list of representatives per cell
        val remap = IntArray(n)
        val outPos = FloatArray(p.size)
        var outCount = 0
        val eps2 = eps * eps

        fun key(ix: Int, iy: Int, iz: Int): Long =
            (ix.toLong() and 0x1FFFFF) or ((iy.toLong() and 0x1FFFFF) shl 21) or ((iz.toLong() and 0x1FFFFF) shl 42)

        for (v in 0 until n) {
            val x = p[v * 3]; val y = p[v * 3 + 1]; val z = p[v * 3 + 2]
            val ix = floor((x - b.minX) / cell).toInt()
            val iy = floor((y - b.minY) / cell).toInt()
            val iz = floor((z - b.minZ) / cell).toInt()
            var found = -1
            search@ for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) {
                var r = cells.get(key(ix + dx, iy + dy, iz + dz))
                while (r != -1) {
                    val ex = outPos[r * 3] - x; val ey = outPos[r * 3 + 1] - y; val ez = outPos[r * 3 + 2] - z
                    if (ex * ex + ey * ey + ez * ez <= eps2) { found = r; break@search }
                    r = next[r]
                }
            }
            if (found == -1) {
                found = outCount++
                outPos[found * 3] = x; outPos[found * 3 + 1] = y; outPos[found * 3 + 2] = z
                val k = key(ix, iy, iz)
                next[found] = cells.get(k)
                cells.put(k, found)
            }
            remap[v] = found
        }
        val idx = IntArray(mesh.indices.size) { remap[mesh.indices[it]] }
        return Mesh(outPos.copyOf(outCount * 3), idx)
    }

    private fun removeDegenerate(mesh: Mesh, eps: Float): Pair<Mesh, Int> {
        val out = IntList(mesh.indices.size)
        val n = DoubleArray(3)
        val minArea2 = (eps.toDouble() * eps) // |cross| threshold
        var removed = 0
        for (t in 0 until mesh.triangleCount) {
            val a = mesh.indices[t * 3]; val b = mesh.indices[t * 3 + 1]; val c = mesh.indices[t * 3 + 2]
            if (a == b || b == c || a == c) { removed++; continue }
            mesh.faceNormal(t, n)
            if (sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]) <= minArea2) { removed++; continue }
            out.add(a); out.add(b); out.add(c)
        }
        return Mesh(mesh.positions, out.toIntArray()) to removed
    }

    private fun removeDuplicateFaces(mesh: Mesh): Pair<Mesh, Int> {
        if (mesh.vertexCount >= (1 shl 21)) return mesh to 0 // key packing limit; skip on huge meshes
        val seen = LongIntMap(mesh.triangleCount)
        val out = IntList(mesh.indices.size)
        var removed = 0
        for (t in 0 until mesh.triangleCount) {
            val a = mesh.indices[t * 3]; val b = mesh.indices[t * 3 + 1]; val c = mesh.indices[t * 3 + 2]
            val lo = minOf(a, b, c); val hi = maxOf(a, b, c); val mid = a + b + c - lo - hi
            val key = lo.toLong() or (mid.toLong() shl 21) or (hi.toLong() shl 42)
            if (seen.putIfAbsent(key, t) != -1) { removed++; continue }
            out.add(a); out.add(b); out.add(c)
        }
        return Mesh(mesh.positions, out.toIntArray()) to removed
    }

    /** Removes vertices no triangle uses. */
    internal fun compact(mesh: Mesh): Mesh {
        val remap = IntArray(mesh.vertexCount) { -1 }
        var count = 0
        val pos = FloatArray(mesh.positions.size)
        val idx = IntArray(mesh.indices.size)
        for (i in mesh.indices.indices) {
            val v = mesh.indices[i]
            if (remap[v] == -1) {
                remap[v] = count
                System.arraycopy(mesh.positions, v * 3, pos, count * 3, 3)
                count++
            }
            idx[i] = remap[v]
        }
        if (count == mesh.vertexCount && idx.contentEquals(mesh.indices)) return mesh
        return Mesh(pos.copyOf(count * 3), idx)
    }

    /**
     * Makes neighbouring triangles agree on orientation (flood fill across manifold edges),
     * then flips any part whose volume is negative so it faces outward. Modifies indices in place.
     * Returns the number of triangles flipped.
     */
    internal fun orient(mesh: Mesh): Int {
        val idx = mesh.indices
        val faces = mesh.triangleCount
        if (faces == 0) return 0
        val edges = EdgeTable(idx)
        val visited = BooleanArray(faces)
        val flippedNow = BooleanArray(faces)
        val queue = IntArray(faces)
        val component = IntList()

        fun hasDirected(f: Int, a: Int, b: Int): Boolean {
            for (k in 0..2) if (idx[f * 3 + k] == a && idx[f * 3 + (k + 1) % 3] == b) return true
            return false
        }

        fun flip(f: Int) {
            val t = idx[f * 3 + 1]; idx[f * 3 + 1] = idx[f * 3 + 2]; idx[f * 3 + 2] = t
            flippedNow[f] = !flippedNow[f]
        }

        for (seed in 0 until faces) {
            if (visited[seed]) continue
            component.clear()
            var head = 0; var tail = 0
            queue[tail++] = seed; visited[seed] = true
            while (head < tail) {
                val f = queue[head++]
                component.add(f)
                for (k in 0..2) {
                    val a = idx[f * 3 + k]; val b = idx[f * 3 + (k + 1) % 3]
                    val e = edges.edgeOf(a, b)
                    if (edges.uses[e] != 2) continue
                    val g = if (edges.faceA[e] == f) edges.faceB[e] else edges.faceA[e]
                    if (visited[g]) continue
                    visited[g] = true
                    if (hasDirected(g, a, b)) flip(g) // neighbour must traverse the shared edge the other way
                    queue[tail++] = g
                }
            }
            // Outward check: signed volume relative to the part's centroid.
            var cx = 0.0; var cy = 0.0; var cz = 0.0; var cnt = 0
            for (i in 0 until component.size) {
                val f = component[i]
                for (k in 0..2) {
                    val v = idx[f * 3 + k] * 3
                    cx += mesh.positions[v]; cy += mesh.positions[v + 1]; cz += mesh.positions[v + 2]; cnt++
                }
            }
            cx /= cnt; cy /= cnt; cz /= cnt
            var vol = 0.0
            val p = mesh.positions
            for (i in 0 until component.size) {
                val f = component[i]
                val a = idx[f * 3] * 3; val b = idx[f * 3 + 1] * 3; val c = idx[f * 3 + 2] * 3
                val ax = p[a] - cx; val ay = p[a + 1] - cy; val az = p[a + 2] - cz
                val bx = p[b] - cx; val by = p[b + 1] - cy; val bz = p[b + 2] - cz
                val qx = p[c] - cx; val qy = p[c + 1] - cy; val qz = p[c + 2] - cz
                vol += ax * (by * qz - bz * qy) - ay * (bx * qz - bz * qx) + az * (bx * qy - by * qx)
            }
            if (vol < 0) for (i in 0 until component.size) flip(component[i])
        }
        return flippedNow.count { it }
    }
}
