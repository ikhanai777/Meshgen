package com.meshgen.core.export

import com.meshgen.core.mesh.Mesh
import java.io.OutputStream

enum class ExportFormat(val extension: String, val mimeType: String, val label: String, val description: String) {
    STL("stl", "model/stl", "STL", "Universal 3D printing format"),
    THREE_MF("3mf", "model/3mf", "3MF", "Modern printing format with units"),
    OBJ("obj", "model/obj", "OBJ", "Widely supported by 3D apps"),
    GLB("glb", "model/gltf-binary", "GLB", "For web, AR and game engines"),
}

object MeshExporter {
    fun write(mesh: Mesh, format: ExportFormat, out: OutputStream, name: String = "MeshGen model") {
        when (format) {
            ExportFormat.STL -> StlExporter.write(mesh, out, name)
            ExportFormat.OBJ -> ObjExporter.write(mesh, out, name)
            ExportFormat.GLB -> GlbExporter.write(mesh, out, name)
            ExportFormat.THREE_MF -> ThreeMfExporter.write(mesh, out, name)
        }
    }
}
