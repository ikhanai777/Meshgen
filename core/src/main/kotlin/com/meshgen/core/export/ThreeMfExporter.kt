package com.meshgen.core.export

import com.meshgen.core.mesh.Mesh
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 3MF core specification: a zip with one mesh object, units in millimetres. */
object ThreeMfExporter {
    fun write(mesh: Mesh, out: OutputStream, name: String = "MeshGen") {
        val zip = ZipOutputStream(out)
        zip.putNextEntry(ZipEntry("[Content_Types].xml"))
        zip.write(
            """<?xml version="1.0" encoding="UTF-8"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="model" ContentType="application/vnd.ms-package.3dmanufacturing-3dmodel+xml"/>
</Types>
""".toByteArray(Charsets.UTF_8),
        )
        zip.closeEntry()
        zip.putNextEntry(ZipEntry("_rels/.rels"))
        zip.write(
            """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Target="/3D/3dmodel.model" Id="rel0" Type="http://schemas.microsoft.com/3dmanufacturing/2013/01/3dmodel"/>
</Relationships>
""".toByteArray(Charsets.UTF_8),
        )
        zip.closeEntry()

        zip.putNextEntry(ZipEntry("3D/3dmodel.model"))
        val w = zip.bufferedWriter(Charsets.UTF_8)
        val title = name.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        w.write("<model unit=\"millimeter\" xml:lang=\"en-US\" xmlns=\"http://schemas.microsoft.com/3dmanufacturing/core/2015/02\">\n")
        w.write(" <metadata name=\"Application\">MeshGen</metadata>\n")
        w.write(" <metadata name=\"Title\">$title</metadata>\n")
        w.write(" <resources>\n  <object id=\"1\" type=\"model\" name=\"$title\">\n   <mesh>\n    <vertices>\n")
        val p = mesh.positions
        val sb = StringBuilder(96)
        for (v in 0 until mesh.vertexCount) {
            sb.setLength(0)
            sb.append("     <vertex x=\"").append(fmt(p[v * 3])).append("\" y=\"").append(fmt(p[v * 3 + 1]))
                .append("\" z=\"").append(fmt(p[v * 3 + 2])).append("\"/>\n")
            w.write(sb.toString())
        }
        w.write("    </vertices>\n    <triangles>\n")
        val idx = mesh.indices
        for (t in 0 until mesh.triangleCount) {
            sb.setLength(0)
            sb.append("     <triangle v1=\"").append(idx[t * 3]).append("\" v2=\"").append(idx[t * 3 + 1])
                .append("\" v3=\"").append(idx[t * 3 + 2]).append("\"/>\n")
            w.write(sb.toString())
        }
        w.write("    </triangles>\n   </mesh>\n  </object>\n </resources>\n <build>\n  <item objectid=\"1\"/>\n </build>\n</model>\n")
        w.flush()
        zip.closeEntry()
        zip.finish()
        out.flush()
    }
}
