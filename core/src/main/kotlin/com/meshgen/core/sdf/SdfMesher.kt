package com.meshgen.core.sdf

import com.meshgen.core.mesh.Mesh
import com.meshgen.core.util.IntList
import java.util.stream.IntStream
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

class MeshingCancelled : Exception("Meshing cancelled")

/**
 * Turns an SDF into a closed triangle mesh with marching cubes.
 *
 * Works one z-slab at a time (memory grows with the cross-section, not the volume). A coarse pre-pass
 * finds regions provably far from the surface, using the field's Lipschitz bound; only points near the
 * surface are evaluated at full resolution. Plane evaluation is spread over all CPU cores.
 */
object SdfMesher {

    fun interface Progress { fun update(fraction: Float) }

    class Stats(val gridX: Int, val gridY: Int, val gridZ: Int, val cellSize: Double, val evaluations: Long)

    class Result(val mesh: Mesh, val stats: Stats)

    /** [resolution] = number of cells along the longest side of the shape. */
    fun mesh(sdf: Sdf, resolution: Int, progress: Progress? = null, isCancelled: () -> Boolean = { false }): Result {
        val b = sdf.bounds
        require(!b.isEmpty) { "The shape is empty." }
        val longest = max(b.sizeX, max(b.sizeY, b.sizeZ))
        require(longest > 0) { "The shape has no size." }
        val cell = longest / resolution
        val pad = 2 * cell
        val ox = b.minX - pad; val oy = b.minY - pad; val oz = b.minZ - pad
        val nx = ceil((b.sizeX + 2 * pad) / cell).toInt()
        val ny = ceil((b.sizeY + 2 * pad) / cell).toInt()
        val nz = ceil((b.sizeZ + 2 * pad) / cell).toInt()
        val px = nx + 1; val py = ny + 1 // points per row / column
        val lip = max(sdf.lipschitz, 1.0)

        // Coarse grid: every C cells.
        val c = 4
        val cnx = nx / c + 2; val cny = ny / c + 2; val cnz = nz / c + 2
        val coarse = FloatArray(cnx * cny * cnz)
        IntStream.range(0, cnz).parallel().forEach { k ->
            for (j in 0 until cny) for (i in 0 until cnx) {
                coarse[(k * cny + j) * cnx + i] = sdf.d(ox + i * c * cell, oy + j * c * cell, oz + k * c * cell).toFloat()
            }
        }
        var evaluations = coarse.size.toLong()
        if (isCancelled()) throw MeshingCancelled()

        val margin = cell * 1.01

        fun evalPlane(k: Int, out: FloatArray): Long {
            val z = oz + k * cell
            val ck = (k.toDouble() / c).roundToInt().coerceIn(0, cnz - 1)
            val dzc = (k - ck * c) * cell
            val counts = LongArray(py)
            IntStream.range(0, py).parallel().forEach { j ->
                val y = oy + j * cell
                val cj = (j.toDouble() / c).roundToInt().coerceIn(0, cny - 1)
                val dyc = (j - cj * c) * cell
                var n = 0L
                for (i in 0 until px) {
                    val ci = (i.toDouble() / c).roundToInt().coerceIn(0, cnx - 1)
                    val dxc = (i - ci * c) * cell
                    val cv = coarse[(ck * cny + cj) * cnx + ci]
                    val dist = sqrt(dxc * dxc + dyc * dyc + dzc * dzc)
                    // Far from the surface: the coarse sample has the right sign, and this point can't touch a crossing edge.
                    out[j * px + i] = if (abs(cv) > lip * (dist + margin)) cv
                    else { n++; sdf.d(ox + i * cell, y, z).toFloat() }
                }
                counts[j] = n
            }
            return counts.sum()
        }

        // Vertex ids for edges on the bottom/top planes (x- and y-directed) and the vertical edges of the slab.
        var xBot = IntArray(nx * py); var yBot = IntArray(px * ny)
        var xTop = IntArray(nx * py); var yTop = IntArray(px * ny)
        val zEdge = IntArray(px * py)
        var bottom = FloatArray(px * py)
        var top = FloatArray(px * py)
        evaluations += evalPlane(0, bottom)
        xBot.fill(-1); yBot.fill(-1)

        var pos = FloatArray(1 shl 16)
        var vCount = 0
        val tris = IntList(1 shl 16)

        fun addVertex(x: Double, y: Double, z: Double): Int {
            if ((vCount + 1) * 3 > pos.size) pos = pos.copyOf(pos.size * 2)
            pos[vCount * 3] = x.toFloat(); pos[vCount * 3 + 1] = y.toFloat(); pos[vCount * 3 + 2] = z.toFloat()
            return vCount++
        }

        fun crossing(va: Float, vb: Float): Double = (va / (va - vb)).toDouble().coerceIn(0.01, 0.99)

        val cornerVal = FloatArray(8)

        for (k in 1..nz) {
            if (isCancelled()) throw MeshingCancelled()
            evaluations += evalPlane(k, top)
            xTop.fill(-1); yTop.fill(-1); zEdge.fill(-1)
            val zb = oz + (k - 1) * cell

            for (j in 0 until ny) for (i in 0 until nx) {
                val i00 = j * px + i
                cornerVal[0] = bottom[i00]; cornerVal[1] = bottom[i00 + 1]
                cornerVal[2] = bottom[i00 + px]; cornerVal[3] = bottom[i00 + px + 1]
                cornerVal[4] = top[i00]; cornerVal[5] = top[i00 + 1]
                cornerVal[6] = top[i00 + px]; cornerVal[7] = top[i00 + px + 1]
                var case = 0
                for (q in 0 until 8) if (cornerVal[q] < 0f) case = case or (1 shl q)
                if (case == 0 || case == 255) continue
                val loops = CubeTable.LOOPS[case]
                val centers = CubeTable.CENTER[case]
                for (l in loops.indices) {
                    val loop = loops[l]
                    val ids = IntArray(loop.size)
                    for (t in loop.indices) {
                        val e = loop[t]
                        val a = CubeTable.EDGE_A[e]
                        val ax = a and 1; val ay = (a shr 1) and 1; val az = (a shr 2) and 1
                        val gi = i + ax; val gj = j + ay
                        val cache: IntArray; val idx: Int
                        when (CubeTable.EDGE_AXIS[e]) {
                            0 -> { cache = if (az == 0) xBot else xTop; idx = gj * nx + gi }
                            1 -> { cache = if (az == 0) yBot else yTop; idx = gj * px + gi }
                            else -> { cache = zEdge; idx = gj * px + gi }
                        }
                        var v = cache[idx]
                        if (v == -1) {
                            val bEnd = CubeTable.EDGE_B[e]
                            val s = crossing(cornerVal[a], cornerVal[bEnd])
                            val x0 = ox + gi * cell; val y0 = oy + gj * cell; val z0 = zb + az * cell
                            v = when (CubeTable.EDGE_AXIS[e]) {
                                0 -> addVertex(x0 + s * cell, y0, z0)
                                1 -> addVertex(x0, y0 + s * cell, z0)
                                else -> addVertex(x0, y0, z0 + s * cell)
                            }
                            cache[idx] = v
                        }
                        ids[t] = v
                    }
                    if (centers[l]) {
                        var cx = 0.0; var cy = 0.0; var cz = 0.0
                        for (v in ids) { cx += pos[v * 3]; cy += pos[v * 3 + 1]; cz += pos[v * 3 + 2] }
                        val c = addVertex(cx / ids.size, cy / ids.size, cz / ids.size)
                        for (t in ids.indices) { tris.add(c); tris.add(ids[t]); tris.add(ids[(t + 1) % ids.size]) }
                    } else {
                        for (t in 1 until ids.size - 1) { tris.add(ids[0]); tris.add(ids[t]); tris.add(ids[t + 1]) }
                    }
                }
            }
            // Top plane becomes the next slab's bottom.
            val tv = bottom; bottom = top; top = tv
            val tx = xBot; xBot = xTop; xTop = tx
            val ty = yBot; yBot = yTop; yTop = ty
            progress?.update(k.toFloat() / nz)
        }
        val mesh = Mesh(pos.copyOf(vCount * 3), tris.toIntArray())
        return Result(mesh, Stats(nx, ny, nz, cell, evaluations))
    }
}
