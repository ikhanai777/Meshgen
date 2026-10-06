package com.meshgen.core

import com.meshgen.core.dsl.Expr
import com.meshgen.core.dsl.ExprException
import com.meshgen.core.dsl.Quality
import com.meshgen.core.dsl.ShapeDsl
import com.meshgen.core.dsl.ShapeEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class ShapeDslTest {

    private fun recipe(shape: String, params: String = "{}") = """{"version":1,"name":"t","params":$params,"shape":$shape}"""

    private fun issues(shape: String, params: String = "{}") = ShapeDsl.parse(recipe(shape, params)).issues.map { it.toString() }

    @Test
    fun `expressions evaluate with parameters and functions`() {
        val v = mapOf("d" to 80.0, "wall" to 3.0)
        assertEquals(37.0, Expr.eval("d/2 - wall", v), 1e-9)
        assertEquals(-5.0, Expr.eval("-(2 + 3)", v), 1e-9)
        assertEquals(8.0, Expr.eval("2^3", v), 1e-9)
        assertEquals(14.0, Expr.eval("2 + 3 * 4", v), 1e-9)
        assertEquals(1.0, Expr.eval("sin(90)", v), 1e-9)
        assertEquals(PI, Expr.eval("pi", v), 1e-12)
        assertEquals(3.0, Expr.eval("clamp(5, 1, 3)", v), 1e-9)
        assertEquals(1.5e2, Expr.eval("1.5e2", v), 1e-9)
    }

    @Test
    fun `expression errors are explained`() {
        fun err(s: String) = try { Expr.eval(s, mapOf("h" to 1.0)); "" } catch (e: ExprException) { e.message!! }
        assertTrue(err("height*2").contains("unknown parameter 'height'. Defined parameters: h"))
        assertTrue(err("h/0").contains("division by zero"))
        assertTrue(err("foo(1)").contains("unknown function 'foo'"))
        assertTrue(err("(h+1").contains("missing ')'"))
        assertTrue(err("h h").contains("unexpected 'h'"))
    }

    @Test
    fun `valid recipe parses and compiles`() {
        val p = ShapeDsl.parse(recipe("""{"type":"cylinder","radius":"d/2","height":50}""", """{"d":{"value":40,"min":10,"max":100}}"""))
        assertTrue(p.issues.toString(), p.issues.isEmpty())
        val g = ShapeEngine.generate(p.doc!!, Quality.DRAFT)
        assertTrue(g.report.watertight)
        assertEquals(40f, g.report.bounds.sizeX, 1.0f)
        assertEquals(50f, g.report.bounds.sizeZ, 1.0f)
        assertEquals(PI * 400 * 50, g.report.volumeMm3, PI * 400 * 50 * 0.03)
    }

    @Test
    fun `errors carry paths and allowed values`() {
        assertTrue(issues("""{"type":"cube","size":[1,2,3]}""").single().startsWith("shape.type: unknown type \"cube\". Use one of: box,"))
        assertEquals(listOf("shape.radious: unknown field for \"sphere\". Allowed: radius", "shape.radius: missing").sorted(),
            issues("""{"type":"sphere","radious":5}""").sorted())
        assertEquals(listOf("shape.children[1].height: must be > 0 (got -2)"),
            issues("""{"type":"union","children":[{"type":"sphere","radius":5},{"type":"cylinder","radius":3,"height":-2}]}"""))
        assertTrue(issues("""{"type":"translate","offset":[0,0,1],"children":[{"type":"sphere","radius":1}]}""")
            .any { it.contains("takes a single \"child\"") })
        assertTrue(issues("""{"type":"box","size":[10,10,"h*2"]}""").single().contains("unknown parameter 'h'"))
        assertTrue(ShapeDsl.parse("not json").issues.single().message.startsWith("not valid JSON"))
        assertTrue(ShapeDsl.parse("""{"name":"x"}""").issues.single().toString().startsWith("shape: missing"))
    }

    @Test
    fun `several errors are reported together`() {
        val i = issues("""{"type":"union","children":[{"type":"sphere"},{"type":"box","size":[1,2]},{"type":"torus","major_radius":5,"minor_radius":6}]}""")
        assertEquals(3, i.size)
    }

    @Test
    fun `shorthand params and markdown fences are accepted`() {
        val text = "```json\n" + recipe("""{"type":"sphere","radius":"r"}""", """{"r":10}""") + "\n```"
        val p = ShapeDsl.parse(text)
        assertNotNull(p.issues.toString(), p.doc)
        val r = p.doc!!.params.single()
        assertEquals(10.0, r.value, 0.0)
        assertTrue(r.min < 10 && r.max > 10)
    }

    @Test
    fun `self-intersecting outline is rejected`() {
        val i = issues("""{"type":"extrude","height":5,"points":[[0,0],[10,10],[10,0],[5,12]]}""")
        assertTrue(i.single().contains("crosses itself"))
    }

    @Test
    fun `too large and empty results are rejected`() {
        assertTrue(issues("""{"type":"box","size":[2000,10,10]}""").single().contains("larger than 1000 mm"))
        assertTrue(issues("""{"type":"intersect","children":[{"type":"sphere","radius":5},{"type":"translate","offset":[100,0,0],"child":{"type":"sphere","radius":5}}]}""")
            .single().contains("empty"))
    }

    @Test
    fun `arrays are bounded`() {
        assertTrue(issues("""{"type":"linear_array","count":500,"spacing":[1,0,0],"child":{"type":"sphere","radius":1}}""")
            .single().contains("must be ≤ 200"))
        // 150 x 150 nested copies would be 22,500 parts
        val nested = """{"type":"linear_array","count":150,"spacing":[1,0,0],"child":{"type":"linear_array","count":150,"spacing":[0,1,0],"child":{"type":"sphere","radius":1}}}"""
        assertTrue(issues(nested).any { it.contains("too many parts") })
    }

    @Test
    fun `transforms place shapes where expected`() {
        val doc = ShapeDsl.parse(recipe(
            """{"type":"union","children":[{"type":"box","size":[10,10,10]},
               {"type":"translate","offset":[30,0,0],"child":{"type":"rotate","angles":[0,90,0],"child":{"type":"cylinder","radius":5,"height":20}}}]}""",
        )).doc!!
        val b = ShapeDsl.compile(doc).sdf!!.bounds
        assertEquals(-5.0, b.minX, 1e-9); assertEquals(50.0, b.maxX, 1e-9) // cylinder along +X from 30 to 50
        assertEquals(-5.0, b.minZ, 1e-9); assertEquals(10.0, b.maxZ, 1e-9)
    }

    @Test
    fun `parameter values are clamped and round-trip through JSON`() {
        val doc = ShapeDsl.parse(recipe("""{"type":"sphere","radius":"r"}""", """{"r":{"value":10,"min":5,"max":20}}""")).doc!!
        val changed = doc.withValues(mapOf("r" to 99.0))
        assertEquals(20.0, changed.values["r"]!!, 0.0)
        val again = ShapeDsl.parse(changed.toJson()).doc!!
        assertEquals(20.0, again.values["r"]!!, 0.0)
        assertNull(ShapeDsl.parse(changed.toJson().replace("\"sphere\"", "\"blob\"")).doc)
    }
}
