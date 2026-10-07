package com.meshgen.core

import com.meshgen.core.dsl.Templates
import com.meshgen.core.llm.ChatFormat
import com.meshgen.core.llm.ChatMessage
import com.meshgen.core.llm.DslGrammar
import com.meshgen.core.llm.GenerationCancelled
import com.meshgen.core.llm.PlanGrammar
import com.meshgen.core.llm.PlanPrompt
import com.meshgen.core.llm.ShapeAgent
import com.meshgen.core.llm.ShapePrompt
import com.meshgen.core.llm.StatedSizes
import com.meshgen.core.llm.TextGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ShapeAgentTest {

    /** Replays canned replies and records what it was asked. */
    private class Scripted(vararg val replies: String) : TextGenerator {
        val suffixes = mutableListOf<String>()
        val prefixes = mutableSetOf<String>()
        override fun generate(prefix: String, suffix: String, grammar: String?, maxTokens: Int, temperature: Float, onToken: (String) -> Unit, isCancelled: () -> Boolean): String {
            prefixes += prefix
            suffixes += suffix
            val r = replies[suffixes.size - 1]
            onToken(r)
            return r
        }
    }

    private val good = """{"version":1,"name":"Cup","units":"mm","params":{"h":{"value":80,"min":20,"max":200}},
        "shape":{"type":"shell","thickness":2,"open_top":true,"child":{"type":"cylinder","radius":35,"height":"h"}}}"""
    private val custom = """{"template":"custom"}"""

    @Test
    fun `template path fills parameters from the request`() {
        val llm = Scripted("""{"template":"hex_pen_holder","name":"Hex pen holder","params":{"height":100,"wall":3,"sides":6}}""")
        val r = ShapeAgent(llm).create("a 10cm hexagonal pen holder with 3mm walls")
        assertEquals(ShapeAgent.Source.TEMPLATE, r.source)
        assertEquals("hex_pen_holder", r.templateId)
        assertEquals("Hex pen holder", r.doc!!.name)
        assertEquals(100.0, r.doc!!.values["height"]!!, 0.0)
        assertEquals(3.0, r.doc!!.values["wall"]!!, 0.0)
        assertTrue(llm.suffixes[0].contains("Request: a 10cm hexagonal pen holder with 3mm walls"))
        assertTrue(r.notes.isEmpty())
    }

    @Test
    fun `stated sizes are read with units and dimension groups`() {
        assertEquals(listOf(100.0, 3.0), StatedSizes.millimetres("a 10cm hexagonal pen holder with 3mm walls"))
        assertEquals(listOf(60.0, 40.0, 30.0, 2.0), StatedSizes.millimetres("a box 60 x 40 x 30 mm with 2 mm walls"))
        assertEquals(listOf(50.8), StatedSizes.millimetres("a 2 inch cube"))
        assertEquals(listOf(150.0), StatedSizes.millimetres("planter 15 cm wide for 3 plants"))
        assertEquals(listOf(4.5), StatedSizes.millimetres("holes of 4,5 mm"))
        assertTrue(StatedSizes.millimetres("a hook for 5 keys").isEmpty())
    }

    @Test
    fun `invented sizes go back to defaults`() {
        val llm = Scripted("""{"template":"hex_pen_holder","name":"Pen holder","params":{"width":200,"height":100,"wall":3,"sides":6}}""")
        val r = ShapeAgent(llm).create("a 10cm hexagonal pen holder with 3mm walls")
        assertEquals("width was never stated", 80.0, r.doc!!.values["width"]!!, 0.0)
        assertEquals(100.0, r.doc!!.values["height"]!!, 0.0)
        assertEquals(6.0, r.doc!!.values["sides"]!!, 0.0)
        assertEquals(1, llm.suffixes.size)
    }

    @Test
    fun `an unused stated size triggers one follow-up question`() {
        val llm = Scripted(
            """{"template":"knob","name":"Knob","params":{"shaft":6}}""",
            """{"template":"knob","name":"Knob","params":{"shaft":6,"diameter":40}}""",
        )
        val r = ShapeAgent(llm).create("a knob for a 6 mm shaft, 40 mm wide")
        assertEquals(40.0, r.doc!!.values["diameter"]!!, 0.0)
        assertTrue(llm.suffixes[1].contains("You did not use these sizes from the request: 40 mm."))
    }

    @Test
    fun `out-of-range values are clamped and explained`() {
        val r = ShapeAgent(Scripted("""{"template":"planter","name":"Huge planter","params":{"height":900}}""")).create("a 90 cm planter")
        assertEquals(250.0, r.doc!!.values["height"]!!, 0.0)
        assertEquals(listOf("Height 900 mm is outside this design's range, so it was set to 250 mm."), r.notes)
    }

    @Test
    fun `custom path accepts a valid recipe`() {
        val llm = Scripted(custom, good)
        val r = ShapeAgent(llm).create("a cup")
        assertEquals(ShapeAgent.Source.CUSTOM, r.source)
        assertNotNull(r.doc)
        assertEquals(1, r.attempts)
        assertTrue(llm.suffixes[1].endsWith("<|im_start|>assistant\n<think>\n\n</think>\n\n"))
        assertEquals("plan and recipe prompts use two cached prefixes", 2, llm.prefixes.size)
    }

    @Test
    fun `custom path feeds problems back and retries`() {
        val bad = good.replace("\"radius\":35", "\"radius\":-5")
        val llm = Scripted(custom, bad, good)
        val events = mutableListOf<ShapeAgent.Event>()
        val r = ShapeAgent(llm).create("a cup", { events += it })
        assertNotNull(r.doc)
        assertEquals(2, r.attempts)
        val retry = llm.suffixes[2]
        assertTrue(retry.contains("shape.child.radius: must be > 0 (got -5)"))
        assertTrue(retry.contains(bad))
        assertTrue(events.any { it is ShapeAgent.Event.Rejected })
    }

    @Test
    fun `custom path gives up after three retries with the last problems`() {
        val bad = """{"version":1,"name":"x","units":"mm","params":{},"shape":{"type":"sphere"}}"""
        val r = ShapeAgent(Scripted(custom, bad, bad, bad, bad)).create("a ball")
        assertNull(r.doc)
        assertEquals(4, r.attempts)
        assertEquals(listOf("shape.radius: missing"), r.problems)
    }

    @Test
    fun `rejects shapes that are too thin to print`() {
        val thin = """{"version":1,"name":"x","units":"mm","params":{},"shape":{"type":"box","size":[20,20,0.5]}}"""
        assertTrue(ShapeAgent(Scripted()).check(thin).single().contains("thinner than 0.8 mm"))
    }

    @Test
    fun `edit changes parameters when possible`() {
        val llm = Scripted("""{"template":"current","params":{"height":150}}""")
        val r = ShapeAgent(llm).edit(Templates.load("planter"), "make it taller")
        assertEquals(ShapeAgent.Source.PARAMS, r.source)
        assertEquals(150.0, r.doc!!.values["height"]!!, 0.0)
        assertTrue(llm.suffixes[0].contains("Current design \"Planter\": top_diameter=120 mm"))
        assertTrue(llm.suffixes[0].contains("Change: make it taller"))
    }

    @Test
    fun `edit rewrites the recipe when parameters are not enough`() {
        val llm = Scripted(custom, good)
        val r = ShapeAgent(llm).edit(Templates.load("planter"), "add two handles")
        assertEquals(ShapeAgent.Source.CUSTOM, r.source)
        assertTrue(llm.suffixes[1].contains("Current recipe: {\"version\":1,\"name\":\"Planter\""))
    }

    @Test
    fun `cancellation stops before generating`() {
        try {
            ShapeAgent(Scripted(good)).create("a cup", isCancelled = { true })
            throw AssertionError("expected cancellation")
        } catch (_: GenerationCancelled) {
        }
    }

    @Test
    fun `plan prompt lists every template and the grammar allows each`() {
        for (id in Templates.IDS) {
            assertTrue(id, PlanPrompt.SYSTEM.contains("\n$id: "))
            assertTrue(id, PlanGrammar.CREATE.contains("\\\"$id\\\","))
        }
        val prefix = ChatFormat.CHATML_NO_THINK.prefix(ShapePrompt.SYSTEM)
        val full = ChatFormat.CHATML_NO_THINK.render(listOf(ChatMessage(ChatMessage.Role.SYSTEM, ShapePrompt.SYSTEM), ShapePrompt.create("x")))
        assertTrue(full.startsWith(prefix))
    }

    @Test
    fun `every template conforms to the recipe grammar after normalising`() {
        val problems = Templates.IDS.flatMap { id ->
            val shape = Templates.load(id).toJsonElement()["shape"]!!
            DslGrammar.conformance(DslGrammar.normalize(shape), id)
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `write grammars and prompts for the evaluation harness`() {
        val dir = File("build/llm").apply { mkdirs() }
        File(dir, "grammar.gbnf").writeText(DslGrammar.GBNF)
        File(dir, "system.txt").writeText(ShapePrompt.SYSTEM)
        File(dir, "plan_grammar.gbnf").writeText(PlanGrammar.CREATE)
        File(dir, "plan_system.txt").writeText(PlanPrompt.SYSTEM)
        File(dir, "edit_grammar_planter.gbnf").writeText(PlanGrammar.edit(Templates.load("planter")))
        assertTrue(PlanPrompt.SYSTEM.length > 1000)
    }
}
