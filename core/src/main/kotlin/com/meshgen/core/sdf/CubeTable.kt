package com.meshgen.core.sdf

/**
 * Marching-cubes case table, generated at start-up instead of typed in by hand.
 *
 * For each of the 256 inside/outside corner patterns, every cube face contributes segments between the
 * edge crossings on that face. Ambiguous faces (diagonal corners inside) always use the same rule —
 * "cut off each inside corner" — and that rule depends only on the face's own corners, so the two cubes
 * sharing a face always agree. Every crossing has exactly two segments, so they form closed loops,
 * oriented to face outward. Loops are fan-triangulated from a vertex whose diagonals stay inside the cube
 * (never along a face, where the neighbouring cube could pick the same diagonal); if no such vertex exists the
 * mesher adds a centre vertex instead. The result is watertight and manifold by construction.
 *
 * Corner c sits at (c & 1, (c >> 1) & 1, (c >> 2) & 1). Inside means value < 0.
 */
internal object CubeTable {
    /** 12 edges as corner pairs; edge e runs along axis [EDGE_AXIS] from corner [EDGE_A] to [EDGE_B]. */
    val EDGE_A = IntArray(12)
    val EDGE_B = IntArray(12)
    val EDGE_AXIS = IntArray(12)

    /** Outward-oriented loops of crossed edges per case. Fan from element 0 unless [CENTER] says otherwise. */
    val LOOPS: Array<Array<IntArray>>

    /** Per case and loop: true when the loop needs a centre vertex (no safe fan start exists). */
    val CENTER: Array<BooleanArray>

    /** Faces (0..5) each edge lies on. */
    private val EDGE_FACES = Array(12) { IntArray(2) }

    private val FACES = arrayOf(
        intArrayOf(0, 1, 3, 2), intArrayOf(4, 5, 7, 6), // z = 0, z = 1
        intArrayOf(0, 1, 5, 4), intArrayOf(2, 3, 7, 6), // y = 0, y = 1
        intArrayOf(0, 2, 6, 4), intArrayOf(1, 3, 7, 5), // x = 0, x = 1
    )

    init {
        var e = 0
        for (a in 0 until 8) for (axis in 0 until 3) {
            if (a and (1 shl axis) == 0) {
                EDGE_A[e] = a; EDGE_B[e] = a or (1 shl axis); EDGE_AXIS[e] = axis; e++
            }
        }
        for (e in 0 until 12) {
            var k = 0
            for ((fi, f) in FACES.withIndex()) {
                val a = f.indexOf(EDGE_A[e]); val b = f.indexOf(EDGE_B[e])
                if (a >= 0 && b >= 0) EDGE_FACES[e][k++] = fi
            }
        }
        val built = Array(256) { build(it) }
        LOOPS = Array(256) { c -> built[c].map { it.first }.toTypedArray() }
        CENTER = Array(256) { c -> built[c].map { it.second }.toBooleanArray() }
    }

    private fun shareFace(a: Int, b: Int) = EDGE_FACES[a].any { it in EDGE_FACES[b] }

    /** Rotates the loop so a fan from its first vertex has no diagonal lying on a cube face; false if impossible. */
    private fun safeFanStart(loop: MutableList<Int>): Boolean {
        val n = loop.size
        if (n == 3) return true
        for (s in 0 until n) {
            val ok = (2 until n - 1).none { k -> shareFace(loop[s], loop[(s + k) % n]) }
            if (ok) { java.util.Collections.rotate(loop, -s); return true }
        }
        return false
    }

    fun edgeOf(a: Int, b: Int): Int {
        for (e in 0 until 12) if ((EDGE_A[e] == a && EDGE_B[e] == b) || (EDGE_A[e] == b && EDGE_B[e] == a)) return e
        error("corners $a,$b are not adjacent")
    }

    private fun corner(c: Int) = doubleArrayOf((c and 1).toDouble(), ((c shr 1) and 1).toDouble(), ((c shr 2) and 1).toDouble())

    private fun build(case: Int): List<Pair<IntArray, Boolean>> {
        fun inside(c: Int) = (case shr c) and 1 == 1
        val link = Array(12) { mutableListOf<Int>() }
        for (f in FACES) {
            val crossing = (0 until 4).filter { inside(f[it]) != inside(f[(it + 1) % 4]) }
            val edges = crossing.map { edgeOf(f[it], f[(it + 1) % 4]) }
            when (crossing.size) {
                0 -> Unit
                2 -> connect(link, edges[0], edges[1])
                4 -> {
                    // Ambiguous face: separate the two inside corners.
                    for (k in 0 until 4) if (inside(f[k])) {
                        connect(link, edgeOf(f[(k + 3) % 4], f[k]), edgeOf(f[k], f[(k + 1) % 4]))
                    }
                }
                else -> error("impossible crossing count")
            }
        }
        val out = mutableListOf<Pair<IntArray, Boolean>>()
        val used = BooleanArray(12)
        for (start in 0 until 12) {
            if (used[start] || link[start].isEmpty()) continue
            check(link[start].size == 2) { "case $case edge $start has ${link[start].size} links" }
            val loop = mutableListOf(start)
            used[start] = true
            var prev = start
            var cur = link[start][0]
            while (cur != start) {
                loop += cur; used[cur] = true
                val next = if (link[cur][0] != prev) link[cur][0] else link[cur][1]
                prev = cur; cur = next
            }
            orientOutward(loop, ::inside)
            val center = !safeFanStart(loop)
            out += loop.toIntArray() to center
        }
        return out
    }

    private fun connect(link: Array<MutableList<Int>>, a: Int, b: Int) { link[a] += b; link[b] += a }

    private fun orientOutward(loop: MutableList<Int>, inside: (Int) -> Boolean) {
        val pts = loop.map { e -> val a = corner(EDGE_A[e]); val b = corner(EDGE_B[e]); DoubleArray(3) { (a[it] + b[it]) / 2 } }
        // Newell normal of the loop.
        val n = DoubleArray(3)
        for (i in pts.indices) {
            val p = pts[i]; val q = pts[(i + 1) % pts.size]
            n[0] += (p[1] - q[1]) * (p[2] + q[2])
            n[1] += (p[2] - q[2]) * (p[0] + q[0])
            n[2] += (p[0] - q[0]) * (p[1] + q[1])
        }
        // Outward direction: from the inside corner to the outside corner of each crossed edge.
        val out = DoubleArray(3)
        for (e in loop) {
            val a = corner(EDGE_A[e]); val b = corner(EDGE_B[e])
            val sign = if (inside(EDGE_A[e])) 1.0 else -1.0
            for (k in 0..2) out[k] += sign * (b[k] - a[k])
        }
        if (n[0] * out[0] + n[1] * out[1] + n[2] * out[2] < 0) loop.reverse()
    }
}
