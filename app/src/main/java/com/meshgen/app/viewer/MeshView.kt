package com.meshgen.app.viewer

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay

/**
 * Touch-driven 3D viewport. One finger orbits, two fingers pan and pinch to zoom, double-tap resets.
 */
@SuppressLint("ViewConstructor")
class MeshView(context: Context, onError: (String) -> Unit) : GLSurfaceView(context) {

    private val camera = OrbitCamera()
    private val renderer = MeshRenderer(context.assets, camera) { msg -> post { onError(msg) } }
    private var current: ViewerMesh? = null
    private var framed = false

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(MultisampleConfigChooser())
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun setMesh(mesh: ViewerMesh?, reframe: Boolean) {
        if (mesh === current) return
        current = mesh
        // Always frame the first mesh this view shows (e.g. after rotation recreates the view).
        if (mesh != null && (reframe || !framed)) { camera.frame(mesh.bounds); framed = true }
        renderer.setMesh(mesh)
        requestRender()
    }

    fun setWireframe(on: Boolean) {
        if (renderer.wireframe != on) { renderer.wireframe = on; requestRender() }
    }

    fun setLighting(preset: LightingPreset) {
        if (renderer.lighting != preset) { renderer.lighting = preset; requestRender() }
    }

    fun resetView() { camera.reset(); requestRender() }

    private var lastResetCount = 0

    /** Resets the camera when [count] increases (driven by UI state). */
    fun resetViewIfRequested(count: Int) {
        if (count != lastResetCount) { lastResetCount = count; resetView() }
    }

    // --- Touch ---------------------------------------------------------------------------

    private var lastX = 0f
    private var lastY = 0f
    private var lastPointers = 0

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            camera.zoom(d.scaleFactor)
            requestRender()
            return true
        }
    })

    private val tapDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean { resetView(); return true }
    })

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        scaleDetector.onTouchEvent(e)
        tapDetector.onTouchEvent(e)

        // Centroid of all active pointers.
        var cx = 0f; var cy = 0f; var n = 0
        val skip = if (e.actionMasked == MotionEvent.ACTION_POINTER_UP) e.actionIndex else -1
        for (i in 0 until e.pointerCount) if (i != skip) { cx += e.getX(i); cy += e.getY(i); n++ }
        if (n == 0) return true
        cx /= n; cy /= n

        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                lastX = cx; lastY = cy; lastPointers = n
            }
            MotionEvent.ACTION_MOVE -> {
                if (n != lastPointers) { lastX = cx; lastY = cy; lastPointers = n; return true }
                val dx = cx - lastX; val dy = cy - lastY
                lastX = cx; lastY = cy
                if (n == 1) camera.orbit(dx, dy) else camera.pan(dx, dy, height)
                requestRender()
            }
        }
        return true
    }

    /** Prefers 4× MSAA for smooth edges; falls back to no multisampling. */
    private class MultisampleConfigChooser : EGLConfigChooser {
        override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
            val es3 = 0x40 // EGL_OPENGL_ES3_BIT_KHR
            val attempts = listOf(
                intArrayOf(EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_DEPTH_SIZE, 24,
                    EGL10.EGL_RENDERABLE_TYPE, es3, EGL10.EGL_SAMPLE_BUFFERS, 1, EGL10.EGL_SAMPLES, 4, EGL10.EGL_NONE),
                intArrayOf(EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_DEPTH_SIZE, 16,
                    EGL10.EGL_RENDERABLE_TYPE, es3, EGL10.EGL_SAMPLE_BUFFERS, 1, EGL10.EGL_SAMPLES, 4, EGL10.EGL_NONE),
                intArrayOf(EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_DEPTH_SIZE, 16,
                    EGL10.EGL_RENDERABLE_TYPE, es3, EGL10.EGL_NONE),
            )
            for (attrs in attempts) {
                val num = IntArray(1)
                val configs = arrayOfNulls<EGLConfig>(1)
                if (egl.eglChooseConfig(display, attrs, configs, 1, num) && num[0] > 0 && configs[0] != null) return configs[0]!!
            }
            throw IllegalStateException("No OpenGL ES 3 configuration available")
        }
    }
}
