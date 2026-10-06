package com.meshgen.app.viewer

import android.opengl.Matrix
import com.meshgen.core.mesh.Bounds
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tan

/** Z-up orbit camera. Mutated on the UI thread, read on the GL thread, so all access is synchronized. */
class OrbitCamera {
    private var yaw = DEFAULT_YAW
    private var pitch = DEFAULT_PITCH
    private var distance = 100f
    private val target = FloatArray(3)
    private var radius = 50f
    private var bounds: Bounds? = null

    @Synchronized
    fun frame(b: Bounds) {
        bounds = b
        radius = max(b.diagonal / 2f, 1f)
        target[0] = b.centerX; target[1] = b.centerY; target[2] = b.centerZ
        yaw = DEFAULT_YAW
        pitch = DEFAULT_PITCH
        distance = radius / sin(Math.toRadians(FOV_DEG / 2.0).toFloat()) * 1.15f
    }

    @Synchronized
    fun reset() { bounds?.let { frame(it) } }

    @Synchronized
    fun orbit(dxPx: Float, dyPx: Float) {
        yaw -= dxPx * 0.35f
        pitch = (pitch + dyPx * 0.35f).coerceIn(-89f, 89f)
    }

    @Synchronized
    fun zoom(scale: Float) {
        distance = (distance / scale).coerceIn(radius * 0.3f, radius * 25f)
    }

    @Synchronized
    fun pan(dxPx: Float, dyPx: Float, viewHeightPx: Int) {
        val perPixel = 2f * distance * tan(Math.toRadians(FOV_DEG / 2.0).toFloat()) / max(viewHeightPx, 1)
        val eye = eye()
        val fx = target[0] - eye[0]; val fy = target[1] - eye[1]; val fz = target[2] - eye[2]
        // right = forward × up(0,0,1); camUp = right × forward
        var rx = fy; var ry = -fx; val rz = 0f
        val rl = kotlin.math.sqrt(rx * rx + ry * ry).coerceAtLeast(1e-6f)
        rx /= rl; ry /= rl
        var ux = ry * fz - rz * fy; var uy = rz * fx - rx * fz; var uz = rx * fy - ry * fx
        val ul = kotlin.math.sqrt(ux * ux + uy * uy + uz * uz).coerceAtLeast(1e-6f)
        ux /= ul; uy /= ul; uz /= ul
        target[0] += (-dxPx * rx + dyPx * ux) * perPixel
        target[1] += (-dxPx * ry + dyPx * uy) * perPixel
        target[2] += (-dxPx * rz + dyPx * uz) * perPixel
    }

    private fun eye(): FloatArray {
        val y = Math.toRadians(yaw.toDouble()); val p = Math.toRadians(pitch.toDouble())
        return floatArrayOf(
            target[0] + (distance * cos(p) * cos(y)).toFloat(),
            target[1] + (distance * cos(p) * sin(y)).toFloat(),
            target[2] + (distance * sin(p)).toFloat(),
        )
    }

    /** Writes view and projection matrices for the given aspect ratio. */
    @Synchronized
    fun matrices(aspect: Float, view: FloatArray, proj: FloatArray) {
        val e = eye()
        Matrix.setLookAtM(view, 0, e[0], e[1], e[2], target[0], target[1], target[2], 0f, 0f, 1f)
        val near = max(distance - radius * 3f, distance * 0.01f)
        val far = distance + radius * 6f
        Matrix.perspectiveM(proj, 0, FOV_DEG, aspect, near, far)
    }

    companion object {
        const val FOV_DEG = 35f
        const val DEFAULT_YAW = -60f
        const val DEFAULT_PITCH = 25f
    }
}
