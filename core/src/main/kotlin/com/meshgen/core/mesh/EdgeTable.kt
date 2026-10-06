package com.meshgen.core.mesh

import com.meshgen.core.util.LongIntMap
import com.meshgen.core.util.edgeKey

/** Undirected edges of a triangle list, with how many faces use each and which ones (first two). */
internal class EdgeTable(indices: IntArray) {
    val edgeCount: Int
    val v0: IntArray
    val v1: IntArray
    val faceA: IntArray
    val faceB: IntArray
    val uses: IntArray
    private val map: LongIntMap

    init {
        val maxEdges = indices.size
        v0 = IntArray(maxEdges); v1 = IntArray(maxEdges)
        faceA = IntArray(maxEdges) { -1 }; faceB = IntArray(maxEdges) { -1 }
        uses = IntArray(maxEdges)
        map = LongIntMap(maxEdges)
        var n = 0
        for (f in 0 until indices.size / 3) {
            for (k in 0..2) {
                val a = indices[f * 3 + k]
                val b = indices[f * 3 + (k + 1) % 3]
                val key = edgeKey(a, b)
                var e = map.putIfAbsent(key, n)
                if (e == -1) { e = n++; v0[e] = minOf(a, b); v1[e] = maxOf(a, b) }
                when (uses[e]) {
                    0 -> faceA[e] = f
                    1 -> faceB[e] = f
                }
                uses[e]++
            }
        }
        edgeCount = n
    }

    fun edgeOf(a: Int, b: Int): Int = map.get(edgeKey(a, b))
}

/** Topology and size facts about a mesh, plus problems described in plain words. */
data class MeshReport(
    val triangleCount: Int,
    val vertexCount: Int,
    val bounds: Bounds,
    val volumeMm3: Double,
    val surfaceAreaMm2: Double,
    val boundaryEdges: Int,
    val holes: Int,
    val nonManifoldEdges: Int,
    val parts: Int,
    val insideOut: Boolean,
) {
    val watertight: Boolean get() = triangleCount > 0 && boundaryEdges == 0 && nonManifoldEdges == 0

    /** Problems a user should know about before printing. Empty when the mesh is print-ready. */
    val problems: List<String>
        get() = buildList {
            if (triangleCount == 0) add("The model is empty.")
            if (holes > 0) add(
                "Has ${plural(holes, "hole")} ($boundaryEdges open edges), so it is not watertight. " +
                    "A slicer may refuse it or fill the gap unpredictably.",
            )
            if (nonManifoldEdges > 0) add(
                "${plural(nonManifoldEdges, "edge")} ${if (nonManifoldEdges == 1) "is" else "are"} shared by more than two faces. " +
                    "Slicers can misread these spots.",
            )
            if (insideOut) add("The surface faces inward (inside-out). Most slicers will show it as empty.")
        }

    val notes: List<String>
        get() = buildList { if (parts > 1) add("Made of $parts separate parts.") }

    companion object {
        fun of(mesh: Mesh): MeshReport {
            val edges = EdgeTable(mesh.indices)
            var boundary = 0
            var nonManifold = 0
            for (e in 0 until edges.edgeCount) {
                when {
                    edges.uses[e] == 1 -> boundary++
                    edges.uses[e] > 2 -> nonManifold++
                }
            }
            val volume = mesh.signedVolume()
            return MeshReport(
                triangleCount = mesh.triangleCount,
                vertexCount = mesh.vertexCount,
                bounds = mesh.bounds(),
                volumeMm3 = kotlin.math.abs(volume),
                surfaceAreaMm2 = mesh.surfaceArea(),
                boundaryEdges = boundary,
                holes = countBoundaryLoops(edges, mesh.vertexCount),
                nonManifoldEdges = nonManifold,
                parts = countParts(edges, mesh.vertexCount),
                insideOut = boundary == 0 && nonManifold == 0 && volume < 0,
            )
        }

        private fun countBoundaryLoops(edges: EdgeTable, vertexCount: Int): Int {
            val uf = UnionFind(vertexCount)
            val onBoundary = BooleanArray(vertexCount)
            for (e in 0 until edges.edgeCount) if (edges.uses[e] == 1) {
                uf.union(edges.v0[e], edges.v1[e]); onBoundary[edges.v0[e]] = true; onBoundary[edges.v1[e]] = true
            }
            return (0 until vertexCount).count { onBoundary[it] && uf.find(it) == it }
        }

        private fun countParts(edges: EdgeTable, vertexCount: Int): Int {
            val uf = UnionFind(vertexCount)
            val used = BooleanArray(vertexCount)
            for (e in 0 until edges.edgeCount) {
                uf.union(edges.v0[e], edges.v1[e]); used[edges.v0[e]] = true; used[edges.v1[e]] = true
            }
            return (0 until vertexCount).count { used[it] && uf.find(it) == it }
        }

        private fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"
    }
}

internal class UnionFind(n: Int) {
    private val parent = IntArray(n) { it }
    fun find(x: Int): Int {
        var r = x
        while (parent[r] != r) r = parent[r]
        var c = x
        while (parent[c] != r) { val next = parent[c]; parent[c] = r; c = next }
        return r
    }
    fun union(a: Int, b: Int) { val ra = find(a); val rb = find(b); if (ra != rb) parent[ra] = rb }
}
