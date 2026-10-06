package com.meshgen.core.llm

import com.meshgen.core.dsl.Quality
import com.meshgen.core.dsl.ShapeDoc
import com.meshgen.core.dsl.ShapeDsl
import com.meshgen.core.dsl.ShapeEngine
import com.meshgen.core.dsl.ShapeError
import com.meshgen.core.dsl.Templates
import com.meshgen.core.sdf.MeshingCancelled
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/** A language-model runtime. [prefix] is identical across calls of one kind, so implementations should cache it. */
interface TextGenerator {
    fun generate(
        prefix: String,
        suffix: String,
        grammar: String?,
        maxTokens: Int,
        temperature: Float,
        onToken: (String) -> Unit,
        isCancelled: () -> Boolean,
    ): String
}

class GenerationCancelled : Exception("Cancelled")

/**
 * Text → shape. Stage 1 asks the model to choose a template and fill in its parameters (short, reliable).
 * Only if no template fits does stage 2 have it write a full recipe, which is validated, test-meshed and
 * corrected up to [maxRetries] times with the problems fed back in plain words.
 * Edits are tried as parameter changes first and rewritten only when the change needs new parts.
 */
class ShapeAgent(
    private val llm: TextGenerator,
    private val format: ChatFormat = ChatFormat.CHATML_NO_THINK,
    private val maxRetries: Int = 3,
    private val maxTokens: Int = 1400,
) {
    sealed interface Event {
        data object Planning : Event
        data class Writing(val attempt: Int, val total: Int) : Event
        data class Tokens(val count: Int) : Event
        data class Rejected(val attempt: Int, val problems: List<String>) : Event
    }

    enum class Source { TEMPLATE, PARAMS, CUSTOM }

    class Result(
        val doc: ShapeDoc?,
        val source: Source?,
        val templateId: String?,
        val attempts: Int,
        /** Things the user should know (e.g. a value limited to the template's range). */
        val notes: List<String>,
        val problems: List<String>,
        val raw: List<String>,
    )

    fun create(request: String, onEvent: (Event) -> Unit = {}, isCancelled: () -> Boolean = { false }): Result {
        val raws = mutableListOf<String>()
        onEvent(Event.Planning)
        val planText = ask(PlanPrompt.SYSTEM, listOf(PlanPrompt.create(request)), PlanGrammar.CREATE, 200, 0.1f, onEvent, isCancelled)
        raws += planText
        val plan = parse(planText)
        val id = (plan?.get("template") as? JsonPrimitive)?.content
        if (id != null && id != PlanPrompt.CUSTOM && id in Templates.IDS) {
            val template = Templates.load(id)
            val (values, notes) = values(template, plan["params"] as? JsonObject)
            val name = (plan["name"] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() } ?: template.name
            val doc = template.withValues(values).renamed(name)
            val problems = check(doc)
            if (problems.isEmpty()) return Result(doc, Source.TEMPLATE, id, 1, notes, emptyList(), raws)
            // Rare: a combination of values that does not build. Fall through to a custom recipe.
        }
        return writeRecipe(listOf(ShapePrompt.create(request)), raws, onEvent, isCancelled)
    }

    fun edit(current: ShapeDoc, request: String, onEvent: (Event) -> Unit = {}, isCancelled: () -> Boolean = { false }): Result {
        val raws = mutableListOf<String>()
        onEvent(Event.Planning)
        val text = ask(PlanPrompt.SYSTEM, listOf(PlanPrompt.edit(current, request)), PlanGrammar.edit(current), 200, 0.1f, onEvent, isCancelled)
        raws += text
        val plan = parse(text)
        if ((plan?.get("template") as? JsonPrimitive)?.content == PlanPrompt.CURRENT) {
            val (values, notes) = values(current, plan["params"] as? JsonObject)
            val doc = current.withValues(values)
            if (values.isEmpty()) {
                return Result(null, Source.PARAMS, null, 1, emptyList(), listOf("The model did not change anything. Try describing the change differently."), raws)
            }
            val problems = check(doc)
            if (problems.isEmpty()) return Result(doc, Source.PARAMS, null, 1, notes, emptyList(), raws)
        }
        return writeRecipe(listOf(ShapePrompt.edit(ShapePrompt.compact(current.toJsonElement()), request)), raws, onEvent, isCancelled)
    }

    private fun writeRecipe(start: List<ChatMessage>, raws: MutableList<String>, onEvent: (Event) -> Unit, isCancelled: () -> Boolean): Result {
        val conversation = start.toMutableList()
        var problems: List<String> = emptyList()
        val total = maxRetries + 1
        for (attempt in 1..total) {
            onEvent(Event.Writing(attempt, total))
            var count = 0
            val text = ask(
                ShapePrompt.SYSTEM, conversation, DslGrammar.GBNF, maxTokens, if (attempt == 1) 0.2f else 0.6f,
                { if (it is Event.Tokens) count = it.count; onEvent(it) }, isCancelled,
            )
            raws += text
            problems = check(text, count >= maxTokens)
            if (problems.isEmpty()) return Result(ShapeDsl.parse(text).doc, Source.CUSTOM, null, attempt, emptyList(), emptyList(), raws)
            onEvent(Event.Rejected(attempt, problems))
            conversation += ChatMessage(ChatMessage.Role.ASSISTANT, text)
            conversation += ShapePrompt.fix(problems)
        }
        return Result(null, Source.CUSTOM, null, total, emptyList(), problems, raws)
    }

    private fun ask(
        system: String,
        conversation: List<ChatMessage>,
        grammar: String,
        maxTokens: Int,
        temperature: Float,
        onEvent: (Event) -> Unit,
        isCancelled: () -> Boolean,
    ): String {
        if (isCancelled()) throw GenerationCancelled()
        val prefix = format.prefix(system)
        val full = format.render(listOf(ChatMessage(ChatMessage.Role.SYSTEM, system)) + conversation)
        check(full.startsWith(prefix))
        var count = 0
        val text = llm.generate(prefix, full.substring(prefix.length), grammar, maxTokens, temperature, { onEvent(Event.Tokens(++count)) }, isCancelled)
        if (isCancelled()) throw GenerationCancelled()
        return text
    }

    private fun parse(text: String): JsonObject? = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()

    /** Values from the model, clamped to each parameter's range. Notes describe any clamping. */
    private fun values(doc: ShapeDoc, params: JsonObject?): Pair<Map<String, Double>, List<String>> {
        if (params == null) return emptyMap<String, Double>() to emptyList()
        val values = mutableMapOf<String, Double>()
        val notes = mutableListOf<String>()
        for (p in doc.params) {
            val v = (params[p.name] as? JsonPrimitive)?.doubleOrNull ?: continue
            val c = p.clamp(v)
            if (kotlin.math.abs(c - v) > 1e-9 && !(p.integer && kotlin.math.abs(Math.round(v) - v) < 1e-9 && c == Math.round(v).toDouble())) {
                notes += "${p.label} ${PlanPrompt.fmt(v)}${unit(p.unit)} is outside this design's range, so it was set to ${PlanPrompt.fmt(c)}${unit(p.unit)}."
            }
            values[p.name] = c
        }
        return values to notes
    }

    private fun unit(u: String) = if (u.isEmpty() || u == "°") u else " $u"

    private fun check(doc: ShapeDoc): List<String> = check(doc.toJson(pretty = false))

    /** Problems with a candidate recipe, in words the model can act on. Empty = accepted. */
    fun check(text: String, truncated: Boolean = false): List<String> {
        if (truncated) return listOf("The recipe was too long and got cut off. Make a simpler recipe with fewer parts.")
        val parsed = ShapeDsl.parse(text)
        val doc = parsed.doc ?: return parsed.issues.map { it.toString() }
        return try {
            val g = ShapeEngine.generate(doc, Quality.DRAFT)
            val b = g.report.bounds
            buildList {
                if (!g.report.watertight) addAll(g.report.problems)
                if (minOf(b.sizeX, b.sizeY, b.sizeZ) < 0.8f) add("The result is thinner than 0.8 mm in one direction; it cannot be printed.")
            }
        } catch (e: ShapeError) {
            e.issues.map { it.toString() }
        } catch (e: MeshingCancelled) {
            throw GenerationCancelled()
        } catch (e: IllegalArgumentException) {
            listOf(e.message ?: "The shape is empty.")
        }
    }
}
