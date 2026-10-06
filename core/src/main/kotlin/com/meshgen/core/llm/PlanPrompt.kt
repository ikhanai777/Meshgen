package com.meshgen.core.llm

import com.meshgen.core.dsl.ParamDef
import com.meshgen.core.dsl.ShapeDoc
import com.meshgen.core.dsl.Templates
import java.util.Locale

/**
 * Stage 1: map a request onto a template and its parameter values (or say "custom").
 * Also handles text edits of an existing design as parameter changes. Replies are short (~50 tokens),
 * which keeps this fast and reliable even for small models.
 */
object PlanPrompt {
    const val CUSTOM = "custom"
    const val CURRENT = "current"

    val SYSTEM: String by lazy {
        buildString {
            appendLine("You turn a request for a 3D-printable object into settings for one of the templates below. Reply with JSON only.")
            appendLine("- Pick the template that best matches what the object is for and its shape.")
            appendLine("- Set only the parameters the request states or clearly implies. Never invent other values; they keep their defaults.")
            appendLine("- Copy numbers exactly, converted to millimetres: 1 cm = 10 mm, 1 inch = 25.4 mm (so 20 mm stays 20, 2 cm becomes 20).")
            appendLine("- \"wide\", \"across\" and \"diameter\" set the width or diameter parameter; \"tall\" and \"high\" set the height.")
            appendLine("- For cups, pots and vases a single size like \"10cm\" is the height.")
            appendLine("- \"name\" is a short title for the object.")
            appendLine("- If none of the templates can make the object, reply {\"template\":\"custom\"}.")
            appendLine("- For a change to a current design, reply {\"template\":\"current\",\"params\":{...}} with only the changed values, or {\"template\":\"custom\"} if the change needs new or different parts.")
            appendLine()
            appendLine("Templates (id: what it is. parameter = default unit (min-max)):")
            for (id in Templates.IDS) {
                val d = Templates.load(id)
                appendLine("$id: ${d.description} ${describe(d.params)}")
            }
            appendLine()
            appendLine("Examples:")
            for ((req, reply) in EXAMPLES) {
                appendLine("Request: $req")
                appendLine("Reply: $reply")
            }
        }.trimEnd()
    }

    private val EXAMPLES = listOf(
        "a round planter 15 cm wide with a drainage hole" to """{"template":"planter","name":"Round planter","params":{"top_diameter":150}}""",
        "a hexagonal vase 20 cm tall, no twist" to """{"template":"twisted_vase","name":"Hexagonal vase","params":{"height":200,"sides":6,"twist":0}}""",
        "an open box 6 x 4 cm and 3 cm high with 1.6 mm walls" to """{"template":"storage_bin","name":"Small open box","params":{"length":60,"width":40,"height":30,"wall":1.6,"flare":1}}""",
        "a ring 18 mm across and 2 mm thick" to """{"template":"spacer","name":"Ring","params":{"outer_diameter":18,"height":2}}""",
        "a figurine of a cat" to """{"template":"custom"}""",
        "Current design \"Round planter\": top_diameter=150 mm, bottom_diameter=90 mm, height=110 mm, wall=2.4 mm, drain_hole=12 mm. Change: make it taller and the walls thicker" to
            """{"template":"current","params":{"height":140,"wall":3.2}}""",
        "Current design \"Round planter\": top_diameter=150 mm, bottom_diameter=90 mm, height=110 mm, wall=2.4 mm, drain_hole=12 mm. Change: add two handles" to
            """{"template":"custom"}""",
    )

    fun describe(params: List<ParamDef>): String = params.joinToString(", ") { p ->
        val unit = if (p.unit.isNotEmpty()) " ${p.unit}" else ""
        "${p.name} = ${fmt(p.value)}$unit (${fmt(p.min)}-${fmt(p.max)})"
    }

    fun create(request: String) = ChatMessage(ChatMessage.Role.USER, "Request: ${request.trim()}")

    fun edit(doc: ShapeDoc, request: String): ChatMessage {
        val values = doc.params.joinToString(", ") { p -> "${p.name}=${fmt(p.value)}${if (p.unit.isNotEmpty()) " " + p.unit else ""}" }
        return ChatMessage(ChatMessage.Role.USER, "Request: Current design \"${doc.name}\": $values. Change: ${request.trim()}")
    }

    internal fun fmt(v: Double): String =
        if (v == Math.rint(v)) v.toLong().toString() else String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
}
