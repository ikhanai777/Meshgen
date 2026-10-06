package com.meshgen.core

import com.meshgen.core.dsl.Quality
import com.meshgen.core.dsl.ShapeEngine
import com.meshgen.core.llm.ShapeAgent
import com.meshgen.core.llm.TextGenerator
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Locale

/**
 * Real-model evaluation (not part of CI). Start tools/llm_eval/server.py with a model, then run:
 *   MESHGEN_LLM_URL=http://127.0.0.1:8765 ./gradlew :core:test --tests '*LlmEval*'
 * Writes build/llm/eval-<label>.md and an STL per successful prompt.
 */
class LlmEval {
    private class HttpGenerator(val url: String) : TextGenerator {
        val client: HttpClient = HttpClient.newHttpClient()
        var lastSeconds = 0.0
        var lastPromptTokens = 0
        override fun generate(prefix: String, suffix: String, grammar: String?, maxTokens: Int, temperature: Float, onToken: (String) -> Unit, isCancelled: () -> Boolean): String {
            val body = buildJsonObject {
                put("prefix", prefix); put("suffix", suffix); put("grammar", grammar)
                put("max_tokens", maxTokens); put("temperature", temperature)
            }.toString()
            val res = client.send(HttpRequest.newBuilder(URI(url)).POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString())
            val json = Json.parseToJsonElement(res.body()).jsonObject
            lastSeconds = json["seconds"]!!.jsonPrimitive.content.toDouble()
            lastPromptTokens = json["prompt_tokens"]!!.jsonPrimitive.int
            repeat(json["tokens"]!!.jsonPrimitive.int) { onToken("") }
            return (json["text"] as JsonPrimitive).content
        }
    }

    private val prompts = listOf(
        "a 10cm hexagonal pen holder with 3mm walls",
        "a round planter 15 cm wide with a drainage hole",
        "a simple coaster with a raised edge",
        "a box 60 x 40 x 30 mm with 2 mm walls and no lid",
        "a wall hook for keys",
        "a ring 20 mm in diameter and 3 mm thick",
        "a square vase 20 cm tall with a twist",
        "a cylindrical spacer 10 mm tall, 20 mm across, with an 8 mm hole",
        "a small bowl",
        "a phone stand for a thick phone",
        "a knob for a 6 mm shaft, 40 mm wide",
        "a ball 30 mm in diameter",
        "a cube with a hole through it",
        "a pencil cup shaped like a star",
    )

    private val edits = listOf(
        0 to "make it taller, 15 cm",
        1 to "thicker walls please",
        3 to "make it twice as long",
    )

    @Test
    fun evaluate() {
        val url = System.getenv("MESHGEN_LLM_URL")
        assumeTrue("set MESHGEN_LLM_URL to run", url != null)
        val label = System.getenv("MESHGEN_LLM_LABEL") ?: "model"
        val llm = HttpGenerator(url!!)
        val agent = ShapeAgent(llm)
        val dir = File("build/llm/eval-$label").apply { mkdirs() }
        val report = StringBuilder("# Shape agent evaluation: $label\n\n| # | request | result | path | attempts | size (mm) | time (s) | name / problem |\n|---|---|---|---|---|---|---|---|\n")
        var ok = 0
        val docs = mutableMapOf<Int, com.meshgen.core.dsl.ShapeDoc>()
        fun record(i: String, p: String, r: ShapeAgent.Result, secs: Double) {
            File(dir, "$i.txt").writeText("REQUEST: $p\n\n" + r.raw.joinToString("\n\n----- next -----\n\n") + "\n\nNOTES: ${r.notes}\nPROBLEMS: ${r.problems}\n")
            val path = r.source?.name?.lowercase() + (r.templateId?.let { ":$it" } ?: "")
            if (r.doc != null) {
                ok++
                val g = ShapeEngine.generate(r.doc!!, Quality.STANDARD)
                File(dir, "$i.stl").outputStream().buffered().use { com.meshgen.core.export.StlExporter.write(g.mesh, it) }
                File(dir, "$i.obj").outputStream().buffered().use { com.meshgen.core.export.ObjExporter.write(g.mesh, it) }
                val b = g.report.bounds
                val vals = r.doc!!.params.joinToString(" ") { "${it.name}=${PlanPromptFmt(it.value)}" }
                report.append(String.format(Locale.US, "| %s | %s | ok%s | %s | %d | %.0f×%.0f×%.0f | %.0f | %s — %s |\n", i, p,
                    if (g.report.watertight) "" else " (NOT watertight)", path, r.attempts, b.sizeX, b.sizeY, b.sizeZ, secs, r.doc!!.name, vals))
            } else {
                report.append(String.format(Locale.US, "| %s | %s | **failed** | %s | %d | – | %.0f | %s |\n", i, p, path, r.attempts, secs, r.problems.firstOrNull() ?: ""))
            }
            println(report.lines().last { it.isNotBlank() })
        }
        for ((i, p) in prompts.withIndex()) {
            val start = System.nanoTime()
            val r = agent.create(p)
            r.doc?.let { docs[i] = it }
            record("$i", p, r, (System.nanoTime() - start) / 1e9)
        }
        for ((k, change) in edits) {
            val base = docs[k] ?: continue
            val start = System.nanoTime()
            val r = agent.edit(base, change)
            record("edit$k", "[${base.name}] $change", r, (System.nanoTime() - start) / 1e9)
        }
        report.append("\n**$ok / ${prompts.size + edits.size} requests produced a watertight printable mesh.**\n")
        File("build/llm/eval-$label.md").writeText(report.toString())
        println(report)
    }

    @Suppress("FunctionName")
    private fun PlanPromptFmt(v: Double) = if (v == Math.rint(v)) v.toLong().toString() else String.format(Locale.US, "%.2f", v)
}
