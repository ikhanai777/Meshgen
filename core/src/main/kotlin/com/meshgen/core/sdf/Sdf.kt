package com.meshgen.core.sdf

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Axis-aligned bounding box in mm. */
data class Aabb(
    val minX: Double, val minY: Double, val minZ: Double,
    val maxX: Double, val maxY: Double, val maxZ: Double,
) {
    val isEmpty get() = minX > maxX || minY > maxY || minZ > maxZ
    val sizeX get() = maxX - minX
    val sizeY get() = maxY - minY
    val sizeZ get() = maxZ - minZ

    fun union(o: Aabb) = when {
        isEmpty -> o
        o.isEmpty -> this
        else -> Aabb(min(minX, o.minX), min(minY, o.minY), min(minZ, o.minZ), max(maxX, o.maxX), max(maxY, o.maxY), max(maxZ, o.maxZ))
    }

    fun intersect(o: Aabb) = Aabb(max(minX, o.minX), max(minY, o.minY), max(minZ, o.minZ), min(maxX, o.maxX), min(maxY, o.maxY), min(maxZ, o.maxZ))
    fun expand(r: Double) = Aabb(minX - r, minY - r, minZ - r, maxX + r, maxY + r, maxZ + r)
    fun translate(x: Double, y: Double, z: Double) = Aabb(minX + x, minY + y, minZ + z, maxX + x, maxY + y, maxZ + z)

    /** Distance from a point to the box (0 inside). */
    fun distance(x: Double, y: Double, z: Double): Double {
        val dx = max(max(minX - x, x - maxX), 0.0)
        val dy = max(max(minY - y, y - maxY), 0.0)
        val dz = max(max(minZ - z, z - maxZ), 0.0)
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    companion object {
        val EMPTY = Aabb(1.0, 1.0, 1.0, -1.0, -1.0, -1.0)
    }
}

/**
 * Signed distance field: negative inside, positive outside, in mm.
 * [lipschitz] bounds how fast the value can change per mm (1 for exact distances); the mesher uses it to skip empty space safely.
 */
abstract class Sdf {
    abstract fun d(x: Double, y: Double, z: Double): Double
    abstract val bounds: Aabb
    open val lipschitz: Double get() = 1.0
}

private fun len2(x: Double, y: Double) = sqrt(x * x + y * y)
private fun len3(x: Double, y: Double, z: Double) = sqrt(x * x + y * y + z * z)

/** Combines a 2D cross-section distance with a z-range [0, h] into an exact capped distance. */
private fun capZ(d2: Double, z: Double, h: Double): Double {
    val wz = abs(z - h / 2) - h / 2
    return min(max(d2, wz), 0.0) + len2(max(d2, 0.0), max(wz, 0.0))
}

// ---- Primitives: all rest on the bed (z from 0) and are centred on the Z axis ----

class BoxSdf(val sx: Double, val sy: Double, val sz: Double, val round: Double) : Sdf() {
    override val bounds = Aabb(-sx / 2, -sy / 2, 0.0, sx / 2, sy / 2, sz)
    override fun d(x: Double, y: Double, z: Double): Double {
        val qx = abs(x) - (sx / 2 - round)
        val qy = abs(y) - (sy / 2 - round)
        val qz = abs(z - sz / 2) - (sz / 2 - round)
        return len3(max(qx, 0.0), max(qy, 0.0), max(qz, 0.0)) + min(max(qx, max(qy, qz)), 0.0) - round
    }
}

class SphereSdf(val r: Double) : Sdf() {
    override val bounds = Aabb(-r, -r, 0.0, r, r, 2 * r)
    override fun d(x: Double, y: Double, z: Double) = len3(x, y, z - r) - r
}

class CylinderSdf(val r: Double, val h: Double, val round: Double) : Sdf() {
    override val bounds = Aabb(-r, -r, 0.0, r, r, h)
    override fun d(x: Double, y: Double, z: Double): Double {
        val dx = len2(x, y) - (r - round)
        val dz = abs(z - h / 2) - (h / 2 - round)
        return min(max(dx, dz), 0.0) + len2(max(dx, 0.0), max(dz, 0.0)) - round
    }
}

/** Frustum from radius [r1] at z=0 to [r2] at z=h (exact, after Inigo Quilez). */
class ConeSdf(val r1: Double, val r2: Double, val h: Double) : Sdf() {
    private val rm = max(r1, r2)
    override val bounds = Aabb(-rm, -rm, 0.0, rm, rm, h)
    override fun d(x: Double, y: Double, z: Double): Double {
        val hh = h / 2
        val qx = len2(x, y)
        val qy = z - hh
        val k2x = r2 - r1; val k2y = 2 * hh
        val cax = qx - min(qx, if (qy < 0) r1 else r2)
        val cay = abs(qy) - hh
        val t = (((r2 - qx) * k2x + (hh - qy) * k2y) / (k2x * k2x + k2y * k2y)).coerceIn(0.0, 1.0)
        val cbx = qx - r2 + k2x * t
        val cby = qy - hh + k2y * t
        val s = if (cbx < 0 && cay < 0) -1.0 else 1.0
        return s * sqrt(min(cax * cax + cay * cay, cbx * cbx + cby * cby))
    }
}

class TorusSdf(val major: Double, val minor: Double) : Sdf() {
    private val r = major + minor
    override val bounds = Aabb(-r, -r, 0.0, r, r, 2 * minor)
    override fun d(x: Double, y: Double, z: Double) = len2(len2(x, y) - major, z - minor) - minor
}

/** Vertical capsule of total height [h] (≥ 2r). */
class CapsuleSdf(val r: Double, val h: Double) : Sdf() {
    override val bounds = Aabb(-r, -r, 0.0, r, r, h)
    override fun d(x: Double, y: Double, z: Double): Double {
        val cz = z.coerceIn(r, h - r)
        return len3(x, y, z - cz) - r
    }
}

/** Extruded simple polygon, optionally twisted (degrees over the height) and tapered (top scale factor). */
class ExtrudeSdf(private val px: DoubleArray, private val py: DoubleArray, val h: Double, val twistDeg: Double, val taper: Double) : Sdf() {
    private val n = px.size
    private val rMax = (0 until n).maxOf { len2(px[it], py[it]) }
    private val twistRad = Math.toRadians(twistDeg)

    override val bounds: Aabb = run {
        val s = max(1.0, taper)
        if (twistDeg != 0.0) Aabb(-rMax * s, -rMax * s, 0.0, rMax * s, rMax * s, h)
        else Aabb(px.min() * s, py.min() * s, 0.0, px.max() * s, py.max() * s, h)
    }

    override val lipschitz: Double = run {
        val k = rMax * max(1.0, taper) * (abs(twistRad) + abs(taper - 1)) / h
        sqrt(1 + k * k) * max(1.0, 1 / min(1.0, taper))
    }

    override fun d(x: Double, y: Double, z: Double): Double {
        val u = (z / h).coerceIn(0.0, 1.0)
        val s = 1 + (taper - 1) * u
        val a = -twistRad * u
        val c = cos(a); val sn = sin(a)
        val lx = (x * c - y * sn) / s
        val ly = (x * sn + y * c) / s
        return capZ(polygon(lx, ly) * s, z, h)
    }

    private fun polygon(x: Double, y: Double): Double {
        var d = (x - px[0]) * (x - px[0]) + (y - py[0]) * (y - py[0])
        var sign = 1.0
        var j = n - 1
        for (i in 0 until n) {
            val ex = px[j] - px[i]; val ey = py[j] - py[i]
            val wx = x - px[i]; val wy = y - py[i]
            val t = ((wx * ex + wy * ey) / (ex * ex + ey * ey)).coerceIn(0.0, 1.0)
            val bx = wx - ex * t; val by = wy - ey * t
            d = min(d, bx * bx + by * by)
            val c1 = y >= py[i]; val c2 = y < py[j]; val c3 = ex * wy > ey * wx
            if ((c1 && c2 && c3) || (!c1 && !c2 && !c3)) sign = -sign
            j = i
        }
        return sign * sqrt(d)
    }

    companion object {
        /** Regular polygon with [sides] corners at distance [radius] from the centre. */
        fun regular(sides: Int, radius: Double): Pair<DoubleArray, DoubleArray> {
            val xs = DoubleArray(sides) { radius * cos(2 * Math.PI * it / sides) }
            val ys = DoubleArray(sides) { radius * sin(2 * Math.PI * it / sides) }
            return xs to ys
        }
    }
}

// ---- Booleans ----

/** Union with bounding-box culling: children whose box is farther than the best value so far are skipped. */
class UnionSdf(val children: List<Sdf>) : Sdf() {
    private val boxes = children.map { it.bounds }.toTypedArray()
    private val kids = children.toTypedArray()
    override val bounds = children.fold(Aabb.EMPTY) { a, c -> a.union(c.bounds) }
    override val lipschitz = children.maxOf { it.lipschitz }
    override fun d(x: Double, y: Double, z: Double): Double {
        var best = Double.MAX_VALUE
        for (i in kids.indices) {
            if (best != Double.MAX_VALUE && boxes[i].distance(x, y, z) >= best) continue
            val v = kids[i].d(x, y, z)
            if (v < best) best = v
        }
        return best
    }
}

class SmoothUnionSdf(val children: List<Sdf>, val k: Double) : Sdf() {
    private val kids = children.toTypedArray()
    override val bounds = children.fold(Aabb.EMPTY) { a, c -> a.union(c.bounds) }.expand(k / 4)
    override val lipschitz = children.maxOf { it.lipschitz }
    override fun d(x: Double, y: Double, z: Double): Double {
        var a = kids[0].d(x, y, z)
        for (i in 1 until kids.size) {
            val b = kids[i].d(x, y, z)
            val h = max(k - abs(a - b), 0.0) / k
            a = min(a, b) - h * h * k / 4
        }
        return a
    }
}

class SubtractSdf(val base: Sdf, val cuts: List<Sdf>) : Sdf() {
    private val cutArr = cuts.toTypedArray()
    private val cutBoxes = cuts.map { it.bounds }.toTypedArray()
    override val bounds = base.bounds
    override val lipschitz = max(base.lipschitz, cuts.maxOf { it.lipschitz })
    override fun d(x: Double, y: Double, z: Double): Double {
        var v = base.d(x, y, z)
        for (i in cutArr.indices) {
            // A cut can only matter where it is closer than -v (its value inside is negative).
            if (cutBoxes[i].distance(x, y, z) > -v && cutBoxes[i].distance(x, y, z) > 0) continue
            v = max(v, -cutArr[i].d(x, y, z))
        }
        return v
    }
}

class IntersectSdf(val children: List<Sdf>) : Sdf() {
    private val kids = children.toTypedArray()
    override val bounds = children.drop(1).fold(children[0].bounds) { a, c -> a.intersect(c.bounds) }
    override val lipschitz = children.maxOf { it.lipschitz }
    override fun d(x: Double, y: Double, z: Double): Double {
        var v = -Double.MAX_VALUE
        for (k in kids) v = max(v, k.d(x, y, z))
        return v
    }
}

// ---- Transforms ----

class TranslateSdf(val child: Sdf, val tx: Double, val ty: Double, val tz: Double) : Sdf() {
    override val bounds = child.bounds.translate(tx, ty, tz)
    override val lipschitz get() = child.lipschitz
    override fun d(x: Double, y: Double, z: Double) = child.d(x - tx, y - ty, z - tz)
}

/** Rotation by Euler angles in degrees, applied X then Y then Z, about the origin. */
class RotateSdf(val child: Sdf, rxDeg: Double, ryDeg: Double, rzDeg: Double) : Sdf() {
    private val m: DoubleArray // row-major R = Rz * Ry * Rx

    init {
        val a = Math.toRadians(rxDeg); val b = Math.toRadians(ryDeg); val c = Math.toRadians(rzDeg)
        val ca = cos(a); val sa = sin(a); val cb = cos(b); val sb = sin(b); val cc = cos(c); val sc = sin(c)
        m = doubleArrayOf(
            cc * cb, cc * sb * sa - sc * ca, cc * sb * ca + sc * sa,
            sc * cb, sc * sb * sa + cc * ca, sc * sb * ca - cc * sa,
            -sb, cb * sa, cb * ca,
        )
    }

    override val bounds: Aabb = run {
        val b = child.bounds
        var out = Aabb.EMPTY
        for (i in 0..7) {
            val x = if (i and 1 == 0) b.minX else b.maxX
            val y = if (i and 2 == 0) b.minY else b.maxY
            val z = if (i and 4 == 0) b.minZ else b.maxZ
            val rx = m[0] * x + m[1] * y + m[2] * z
            val ry = m[3] * x + m[4] * y + m[5] * z
            val rz = m[6] * x + m[7] * y + m[8] * z
            out = out.union(Aabb(rx, ry, rz, rx, ry, rz))
        }
        out
    }

    override val lipschitz get() = child.lipschitz

    // Inverse rotation = transpose.
    override fun d(x: Double, y: Double, z: Double) = child.d(
        m[0] * x + m[3] * y + m[6] * z,
        m[1] * x + m[4] * y + m[7] * z,
        m[2] * x + m[5] * y + m[8] * z,
    )
}

class ScaleSdf(val child: Sdf, val sx: Double, val sy: Double, val sz: Double) : Sdf() {
    private val minS = min(sx, min(sy, sz))
    private val maxS = max(sx, max(sy, sz))
    override val bounds = child.bounds.let { Aabb(it.minX * sx, it.minY * sy, it.minZ * sz, it.maxX * sx, it.maxY * sy, it.maxZ * sz) }
    override val lipschitz get() = child.lipschitz * maxS / minS
    override fun d(x: Double, y: Double, z: Double) = child.d(x / sx, y / sy, z / sz) * minS
}

// ---- Modifiers ----

/**
 * Hollows the child, keeping a wall of [t] mm inside its surface.
 * With [openTop], the cavity continues straight up through the top (cups, planters, vases).
 */
class ShellSdf(val child: Sdf, val t: Double, val openTop: Boolean) : Sdf() {
    private val zCut = child.bounds.maxZ - 1.5 * t
    override val bounds = child.bounds
    override val lipschitz get() = child.lipschitz
    override fun d(x: Double, y: Double, z: Double): Double {
        val outer = child.d(x, y, z)
        val inner = if (openTop) child.d(x, y, min(z, zCut)) + t else outer + t
        return max(outer, -inner)
    }
}

/** Grows (positive) or shrinks (negative) the child by [r] mm, rounding its edges. */
class OffsetSdf(val child: Sdf, val r: Double) : Sdf() {
    override val bounds = child.bounds.expand(max(r, 0.0))
    override val lipschitz get() = child.lipschitz
    override fun d(x: Double, y: Double, z: Double) = child.d(x, y, z) - r
}

