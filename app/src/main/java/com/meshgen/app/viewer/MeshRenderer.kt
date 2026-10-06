package com.meshgen.app.viewer

import android.content.res.AssetManager
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.ceil
import kotlin.math.max

/** OpenGL ES 3.0 renderer: flat-shaded mesh, optional wireframe overlay, print-bed grid. */
class MeshRenderer(
    private val assets: AssetManager,
    val camera: OrbitCamera,
    private val onError: (String) -> Unit,
) : GLSurfaceView.Renderer {

    @Volatile var wireframe = false
    @Volatile var lighting = LightingPreset.STUDIO

    @Volatile private var mesh: ViewerMesh? = null
    @Volatile private var dirty = false

    private var meshProgram = 0
    private var lineProgram = 0
    private val buffers = IntArray(4) // positions, triangles, edges, grid
    private var triCount = 0
    private var edgeCount = 0
    private var gridVerts = 0
    private var cull = false
    private var width = 1
    private var height = 1
    private val view = FloatArray(16)
    private val proj = FloatArray(16)
    private val mvp = FloatArray(16)

    fun setMesh(m: ViewerMesh?) {
        mesh = m
        dirty = true
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        meshProgram = program("mesh")
        lineProgram = program("line")
        GLES30.glGenBuffers(4, buffers, 0)
        dirty = true // context may be new: re-upload
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = max(w, 1); height = max(h, 1)
        GLES30.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        if (dirty) upload()
        GLES30.glClearColor(0.043f, 0.047f, 0.055f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        if (triCount == 0) return

        camera.matrices(width.toFloat() / height, view, proj)
        Matrix.multiplyMM(mvp, 0, proj, 0, view, 0)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)

        // Bed grid
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false) // grid never hides the model
        drawLines(buffers[3], gridVerts, null, floatArrayOf(0.36f, 0.40f, 0.46f, 0.35f))
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)

        // Shaded mesh, pushed back slightly so wireframe lines win the depth test.
        GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL)
        GLES30.glPolygonOffset(1.5f, 2f)
        if (cull) GLES30.glEnable(GLES30.GL_CULL_FACE) else GLES30.glDisable(GLES30.GL_CULL_FACE)
        drawMesh()
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)

        if (wireframe && edgeCount > 0) {
            GLES30.glEnable(GLES30.GL_BLEND)
            drawLines(buffers[0], edgeCount, buffers[2], floatArrayOf(0.36f, 0.88f, 0.90f, 0.55f))
            GLES30.glDisable(GLES30.GL_BLEND)
        }
    }

    private fun drawMesh() {
        val p = meshProgram
        GLES30.glUseProgram(p)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(p, "uMvp"), 1, false, mvp, 0)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(p, "uView"), 1, false, view, 0)
        applyLighting(p, lighting)
        val loc = GLES30.glGetAttribLocation(p, "aPos")
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffers[0])
        GLES30.glEnableVertexAttribArray(loc)
        GLES30.glVertexAttribPointer(loc, 3, GLES30.GL_FLOAT, false, 12, 0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, buffers[1])
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, triCount * 3, GLES30.GL_UNSIGNED_INT, 0)
        GLES30.glDisableVertexAttribArray(loc)
    }

    /** Draws GL_LINES from [vbo]; indexed by [ibo] when given, else [count] vertices. */
    private fun drawLines(vbo: Int, count: Int, ibo: Int?, color: FloatArray) {
        if (count == 0) return
        val p = lineProgram
        GLES30.glUseProgram(p)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(p, "uMvp"), 1, false, mvp, 0)
        GLES30.glUniform4fv(GLES30.glGetUniformLocation(p, "uColor"), 1, color, 0)
        val loc = GLES30.glGetAttribLocation(p, "aPos")
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glEnableVertexAttribArray(loc)
        GLES30.glVertexAttribPointer(loc, 3, GLES30.GL_FLOAT, false, 12, 0)
        if (ibo != null) {
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo)
            GLES30.glDrawElements(GLES30.GL_LINES, count * 2, GLES30.GL_UNSIGNED_INT, 0)
        } else {
            GLES30.glDrawArrays(GLES30.GL_LINES, 0, count)
        }
        GLES30.glDisableVertexAttribArray(loc)
    }

    private fun applyLighting(p: Int, preset: LightingPreset) {
        fun u3(name: String, x: Float, y: Float, z: Float) = GLES30.glUniform3f(GLES30.glGetUniformLocation(p, name), x, y, z)
        fun dir(name: String, x: Float, y: Float, z: Float) {
            val l = kotlin.math.sqrt(x * x + y * y + z * z); u3(name, x / l, y / l, z / l)
        }
        fun u1(name: String, v: Float) = GLES30.glUniform1f(GLES30.glGetUniformLocation(p, name), v)
        // Directions are in view space, so lights follow the camera like a photo studio.
        when (preset) {
            LightingPreset.STUDIO -> {
                dir("uKeyDir", 0.5f, 0.7f, 0.6f); u3("uKeyColor", 0.85f, 0.85f, 0.88f)
                dir("uFillDir", -0.7f, -0.1f, 0.5f); u3("uFillColor", 0.22f, 0.26f, 0.30f)
                u3("uAmbient", 0.10f, 0.11f, 0.12f); u3("uBase", 0.62f, 0.64f, 0.68f)
                u1("uSpec", 0.25f); u1("uRim", 0.10f)
            }
            LightingPreset.CLAY -> {
                dir("uKeyDir", 0.2f, 0.5f, 1f); u3("uKeyColor", 0.70f, 0.68f, 0.65f)
                dir("uFillDir", -0.3f, -0.4f, 0.8f); u3("uFillColor", 0.30f, 0.30f, 0.32f)
                u3("uAmbient", 0.22f, 0.22f, 0.22f); u3("uBase", 0.72f, 0.66f, 0.60f)
                u1("uSpec", 0.03f); u1("uRim", 0.0f)
            }
            LightingPreset.CONTRAST -> {
                dir("uKeyDir", 0.8f, 0.6f, 0.3f); u3("uKeyColor", 1.0f, 1.0f, 1.0f)
                dir("uFillDir", -0.8f, 0.2f, 0.2f); u3("uFillColor", 0.05f, 0.07f, 0.09f)
                u3("uAmbient", 0.03f, 0.03f, 0.04f); u3("uBase", 0.55f, 0.57f, 0.60f)
                u1("uSpec", 0.6f); u1("uRim", 0.30f)
            }
        }
        u3("uBack", 0.85f, 0.45f, 0.20f)
    }

    private fun upload() {
        dirty = false
        val m = mesh
        if (m == null) { triCount = 0; edgeCount = 0; gridVerts = 0; return }
        try {
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffers[0])
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, m.positions.size * 4, floats(m.positions), GLES30.GL_STATIC_DRAW)
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, buffers[1])
            GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, m.indices.size * 4, ints(m.indices), GLES30.GL_STATIC_DRAW)
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, buffers[2])
            GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, m.edges.size * 4, ints(m.edges), GLES30.GL_STATIC_DRAW)
            val grid = gridLines(m)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffers[3])
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, grid.size * 4, floats(grid), GLES30.GL_STATIC_DRAW)
            if (GLES30.glGetError() == GLES30.GL_OUT_OF_MEMORY) {
                triCount = 0; edgeCount = 0; gridVerts = 0
                onError("Not enough graphics memory to display this mesh. Try decimating it.")
                return
            }
            triCount = m.indices.size / 3
            cull = m.closed
            edgeCount = m.edges.size / 2
            gridVerts = grid.size / 3
        } catch (e: OutOfMemoryError) {
            triCount = 0; edgeCount = 0; gridVerts = 0
            onError("Not enough memory to display this mesh. Try decimating it.")
        }
    }

    /** 10 mm grid on the bed (Z = bottom of the mesh), sized to the model. */
    private fun gridLines(m: ViewerMesh): FloatArray {
        val b = m.bounds
        val size = max(max(b.sizeX, b.sizeY), 10f)
        val step = when {
            size > 400f -> 50f
            size > 120f -> 20f
            size < 25f -> 5f
            else -> 10f
        }
        val half = ceil(size * 0.9f / step) * step
        val cx = (b.centerX / step).toInt() * step
        val cy = (b.centerY / step).toInt() * step
        val z = b.minZ
        val n = (2 * half / step).toInt() + 1
        val out = FloatArray(n * 2 * 2 * 3)
        var i = 0
        for (k in 0 until n) {
            val o = -half + k * step
            out[i++] = cx + o; out[i++] = cy - half; out[i++] = z
            out[i++] = cx + o; out[i++] = cy + half; out[i++] = z
            out[i++] = cx - half; out[i++] = cy + o; out[i++] = z
            out[i++] = cx + half; out[i++] = cy + o; out[i++] = z
        }
        return out
    }

    private fun program(name: String): Int {
        val vs = shader(GLES30.GL_VERTEX_SHADER, read("shaders/$name.vert"))
        val fs = shader(GLES30.GL_FRAGMENT_SHADER, read("shaders/$name.frag"))
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, vs)
        GLES30.glAttachShader(p, fs)
        GLES30.glLinkProgram(p)
        val ok = IntArray(1)
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) onError("Viewer shader failed to link: ${GLES30.glGetProgramInfoLog(p)}")
        return p
    }

    private fun shader(type: Int, src: String): Int {
        val s = GLES30.glCreateShader(type)
        GLES30.glShaderSource(s, src)
        GLES30.glCompileShader(s)
        val ok = IntArray(1)
        GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) onError("Viewer shader failed to compile: ${GLES30.glGetShaderInfoLog(s)}")
        return s
    }

    private fun read(path: String) = assets.open(path).bufferedReader().use { it.readText() }

    private fun floats(a: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(a); position(0) }

    private fun ints(a: IntArray): IntBuffer =
        ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).asIntBuffer().apply { put(a); position(0) }
}
