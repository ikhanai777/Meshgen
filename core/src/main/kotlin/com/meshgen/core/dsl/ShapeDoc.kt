package com.meshgen.core.dsl

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One problem in a shape recipe, with a JSON path such as `shape.children[1].radius`. */
data class DslIssue(val path: String, val message: String) {
    override fun toString() = "$path: $message"
}

/** A user-tweakable number. Values are always kept within [min]..[max]. */
data class ParamDef(
    val name: String,
    val value: Double,
    val min: Double,
    val max: Double,
    val step: Double?,
    val unit: String,
    val label: String,
    val integer: Boolean,
) {
    fun clamp(v: Double): Double {
        val c = v.coerceIn(min, max)
        return if (integer) Math.round(c).toDouble() else c
    }
}

/** A parsed shape recipe: metadata, parameters and the (still symbolic) shape tree. */
class ShapeDoc(
    val name: String,
    val description: String,
    val prompt: String?,
    val category: String?,
    val params: List<ParamDef>,
    val shape: JsonObject,
) {
    val values: Map<String, Double> get() = params.associate { it.name to it.value }

    fun withValues(values: Map<String, Double>) =
        ShapeDoc(name, description, prompt, category, params.map { p -> values[p.name]?.let { p.copy(value = p.clamp(it)) } ?: p }, shape)

    fun toJsonElement(): JsonObject = buildJsonObject {
        put("version", 1)
        put("name", name)
        if (description.isNotEmpty()) put("description", description)
        prompt?.let { put("prompt", it) }
        category?.let { put("category", it) }
        put("units", "mm")
        put("params", buildJsonObject {
            for (p in params) put(p.name, buildJsonObject {
                put("value", num(p.value))
                put("min", num(p.min))
                put("max", num(p.max))
                p.step?.let { put("step", num(it)) }
                if (p.unit.isNotEmpty()) put("unit", p.unit)
                if (p.label.isNotEmpty()) put("label", p.label)
                if (p.integer) put("integer", true)
            })
        })
        put("shape", shape)
    }

    fun toJson(pretty: Boolean = true): String = (if (pretty) prettyJson else compactJson).encodeToString(JsonElement.serializer(), toJsonElement())

    companion object {
        private val prettyJson = Json { prettyPrint = true; prettyPrintIndent = "  " }
        private val compactJson = Json
        private fun num(v: Double): JsonPrimitive = if (v == Math.rint(v) && kotlin.math.abs(v) < 1e15) JsonPrimitive(v.toLong()) else JsonPrimitive(v)
    }
}
