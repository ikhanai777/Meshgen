package com.meshgen.core.llm

import com.meshgen.core.dsl.Templates
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/** A chat message. */
data class ChatMessage(val role: Role, val content: String) {
    enum class Role { SYSTEM, USER, ASSISTANT }
}

/**
 * The text the model sees. The system part (rules, reference, examples) never changes, so the runtime
 * can process it once and reuse it for every request.
 */
object ShapePrompt {
    /** Templates shown to the model as worked examples for custom recipes. */
    val EXAMPLE_IDS = listOf("hex_pen_holder", "planter", "wall_hook", "gear_ring")

    val SYSTEM: String by lazy {
        buildString {
            appendLine("You design 3D-printable objects. Reply with exactly one JSON shape recipe, nothing else.")
            appendLine()
            appendLine("Rules:")
            appendLine("- Units: millimetres (convert cm and inches). Angles: degrees. Z is up; the print bed is z=0.")
            appendLine("- Every primitive rests on the bed (z from 0 to its height) and is centred on the Z axis. Move parts with translate.")
            appendLine("- Put every important size in \"params\" with value, min, max, unit and label, and use the names in expressions such as \"height - wall\".")
            appendLine("- Printable: walls at least 1.2 mm, a flat bottom, all parts connected.")
            appendLine("- Containers (cups, pots, vases, bins, holders) = shell with \"open_top\": true around the outer solid.")
            appendLine("- Holes = subtract a cylinder that sticks 1 mm out of both sides.")
            appendLine("- Regular polygons (triangle, hexagon, octagon…) = extrude with \"sides\" and \"radius\" (centre-to-corner distance).")
            appendLine("- Build only what the request asks for. Do not copy parts from the examples that the request does not mention.")
            appendLine("- Every param needs min <= value <= max.")
            appendLine()
            appendLine("Node types (fields in this order):")
            appendLine("box: size [x,y,z], round?")
            appendLine("sphere: radius")
            appendLine("cylinder: radius, height, round?")
            appendLine("cone: radius_bottom, radius_top, height")
            appendLine("torus: major_radius, minor_radius (lies flat)")
            appendLine("capsule: radius, height (vertical, total height)")
            appendLine("extrude: height, then sides+radius or points [[x,y],...], twist?, taper?")
            appendLine("union: children [nodes]")
            appendLine("subtract: children [base, cut, ...]")
            appendLine("intersect: children [nodes]")
            appendLine("smooth_union: radius, children")
            appendLine("translate: offset [x,y,z], child")
            appendLine("rotate: angles [x,y,z], child")
            appendLine("scale: factor, child")
            appendLine("linear_array: count, spacing [x,y,z], child")
            appendLine("radial_array: count, radius?, child (copies around Z; child moved +X by radius first)")
            appendLine("shell: thickness, open_top?, child")
            appendLine("offset: distance, child")
            appendLine("Any node may have \"comment\" right after \"type\". Numbers may be expressions with + - * / ^ ( ) and min, max, sqrt, sin, cos, pi.")
            appendLine()
            appendLine("Examples:")
            for (id in EXAMPLE_IDS) {
                val doc = Templates.load(id)
                appendLine()
                appendLine("Request: ${doc.prompt}")
                appendLine("Recipe: ${compact(doc.toJsonElement())}")
            }
        }.trimEnd()
    }

    fun create(request: String) = ChatMessage(ChatMessage.Role.USER, "Request: ${request.trim()}")

    fun edit(currentRecipeJson: String, request: String) = ChatMessage(
        ChatMessage.Role.USER,
        "Current recipe: ${currentRecipeJson.trim()}\nChange: ${request.trim()}\nReply with the full updated recipe. Keep everything that the change does not affect.",
    )

    fun fix(problems: List<String>) = ChatMessage(
        ChatMessage.Role.USER,
        "That recipe has problems:\n" + problems.take(8).joinToString("\n") { "- $it" } + "\nReply with the corrected full recipe.",
    )

    /** Compact JSON for examples: no description/prompt/category, no indentation. */
    fun compact(doc: JsonObject): String {
        val trimmed = buildJsonObject {
            for ((k, v) in doc) if (k !in setOf("description", "prompt", "category")) put(k, if (k == "shape") DslGrammar.normalize(v) else v)
        }
        return Json.encodeToString(JsonElement.serializer(), trimmed)
    }
}
